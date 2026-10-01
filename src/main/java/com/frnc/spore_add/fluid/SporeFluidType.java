package com.frnc.spore_add.fluid;

import java.util.function.Consumer;

import com.frnc.spore_add.client.FluidViewEffects;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * 本 mod 所有流体类型的基类。
 *
 * <p><b>这个类集中处理"玩家在流体里看到什么"</b>，改动前请先读完整段说明。目前有两种效果，
 * 都由同一次计算出来的流体颜色驱动：
 * <ol>
 *   <li><b>与流体同色的全屏滤镜</b>，强度由构造参数 {@code filterStrength} 控制；</li>
 *   <li><b>雾</b>，颜色同样取流体颜色，起止距离见 {@link #FOG_START} / {@link #FOG_END}。</li>
 * </ol>
 *
 * <p>两种效果的颜色都<b>从 {@code tintColor} 推导而不是各传一个值</b>，所以"滤镜/雾的颜色与流体一致"
 * 是结构上保证的，不会出现三处各写一个色值然后慢慢漂移。
 *
 * <h2>为什么仍然不加 {@code FluidTags.WATER}</h2>
 * 加了标签会同时引入<b>原版</b>的两层水效果：{@code FogRenderer} 的 {@code FogType.WATER} 分支
 * （蓝色雾，雾距 -8 → 96，以及生物群系水色）和 {@code ScreenEffectRenderer} 里的 {@code underwater.png}
 * 遮罩（蓝色）。那两层的颜色是原版写死的水蓝，会直接叠在我们的滤镜与雾色上、把流体各自的颜色糊掉。
 * 所以维持不入标签：视野效果只由我们自己这两层提供。标签还影响别的机制（游泳/浮力等），但那部分在
 * Forge 1.20.1 里由 {@code FluidType} 驱动、不需要标签，详见 {@link ModFluids} 的类注释。
 *
 * <h2>{@code getOverlayTexture} 仍然不覆盖</h2>
 * 那个方法是"方块表面的叠加层"，用来给方块贴一层额外的图，和全屏的屏幕效果不是一回事。
 * 留着默认的 {@code null}。
 *
 * <h2>客户端隔离</h2>
 * 下面这些覆盖都写在 {@link #initializeClient} 里构造的匿名类上，而 {@code initializeClient} 只在
 * 物理客户端被 {@link FluidType} 的构造函数回调（Forge 的 {@code initClient} 里有 dist 判断），
 * 所以引用客户端类的代码不会在服务端被加载。这也是 Forge 对流体客户端扩展的官方写法。
 */
public class SporeFluidType extends FluidType {

    /**
     * 原版水的静置贴图。
     *
     * <p>复用原版贴图而不是自带 PNG：{@code water_still.png} 实测是纯灰阶（所有不透明像素 r=g=b），
     * 所以流体的颜色完全由 {@link #tintColor} 决定，染出来的颜色准确可控。
     */
    private static final ResourceLocation WATER_STILL =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");

    /** 原版水的流动贴图。 */
    private static final ResourceLocation WATER_FLOW =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_flow");

    /**
     * 雾开始的距离。负值表示相机身后就开始起雾。
     *
     * <p>与 {@link #FOG_END} 一起对齐原版水的 -8 / 96。对照组：{@code FogType.NONE}（也就是空气里）
     * 走的是"起点 = 视距 - clamp(视距/10, 4, 64)"，默认 12 区块视距下约 173 格才开始起雾。
     * 所以这组值是明显能感觉到的变化，而不只是改个雾色。
     *
     * <p>三种流体共用这一组值。将来若要按流体分别设，把它挪成构造参数即可。
     */
    private static final float FOG_START = -8.0F;

    /** 雾完全遮蔽的距离。取值理由见 {@link #FOG_START}。 */
    private static final float FOG_END = 96.0F;

    /** 本流体的染色（ARGB）。贴图是灰阶的，这个值就是玩家看到的颜色。 */
    private final int tintColor;

    /**
     * 浸在流体里时的全屏滤镜颜色（ARGB）。等于 {@link #tintColor} 的 RGB 加上
     * {@code filterStrength} 换算出的 alpha——注意<b>不能</b>直接复用 tint 自己的 alpha：
     * 那个 alpha 表示的是"流体在方块里的通透程度"，冷却液和液态寒冷都是不透明的 {@code 0xFF}，
     * 拿来做滤镜会把整屏糊成实色。
     */
    private final int filterColor;

    /**
     * @param tintColor      流体的染色，ARGB。alpha 决定流体在方块里的通透程度
     * @param filterStrength 浸在流体里时全屏滤镜的强度，0 = 不加滤镜，1 = 完全不透明。
     *                       建议取 0.15 ~ 0.35 这个量级；原版水的水下遮罩是 0.1
     * @param properties     见 {@link ModFluids} 里那份"与水一致"的公共配置
     */
    public SporeFluidType(int tintColor, float filterStrength, Properties properties) {
        super(properties);
        this.tintColor = tintColor;
        this.filterColor = (tintColor & 0x00FFFFFF) | (Math.round(filterStrength * 255.0F) << 24);
    }

    /**
     * 构造 {@link IClientFluidTypeExtensions}。
     *
     * <p>注意本方法是在 {@link FluidType} 的构造函数里被回调的（Forge 的 {@code initClient}），
     * 那时本类的字段还没赋值；但下面这些方法都是渲染时才被调用的，届时字段早已就位，所以这样写是安全的。
     */
    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {

            @Override
            public ResourceLocation getStillTexture() {
                return WATER_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return WATER_FLOW;
            }

            @Override
            public int getTintColor() {
                return tintColor;
            }

            /**
             * 铺一层与本流体同色的全屏滤镜。具体怎么画见 {@link FluidViewEffects#renderFilter}。
             *
             * <p>Forge 只在"眼睛所在的流体"不是空气时调用它（{@code ScreenEffectRenderer} 里那个
             * {@code else if} 分支），且旁观者、非第一人称、睡觉时都走不到，条件与原版水的遮罩一致。
             */
            @Override
            public void renderOverlay(Minecraft minecraft, PoseStack poseStack) {
                FluidViewEffects.renderFilter(poseStack, filterColor);
            }

            /**
             * 把雾的颜色换成流体颜色。
             *
             * <p><b>必须每次返回新对象，不能返回缓存的 {@code Vector3f}。</b>Forge 拿到返回值之后会继续
             * {@code fluidFogColor.set(...)} 就地改写它（把 {@code ViewportEvent.ComputeFogColor} 的结果写回去，
             * 好让其它模组能改雾色），返回共享实例的话我们的雾色会被那次改写永久污染。
             *
             * <p>触发条件由 Forge 判断：相机低于流体液面时才会调用（{@code ForgeHooksClient#getFogColor}）。
             */
            @Override
            public @NotNull Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level,
                                                    int renderDistance, float darkenWorldAmount,
                                                    Vector3f fluidFogColor) {
                return new Vector3f(
                        ((tintColor >> 16) & 0xFF) / 255.0F,
                        ((tintColor >> 8) & 0xFF) / 255.0F,
                        (tintColor & 0xFF) / 255.0F);
            }

            /**
             * 把雾的起止距离拉近到水里的量级。只改距离，颜色由上面的
             * {@link #modifyFogColor} 负责，雾形（{@code FogShape}）保持原版设好的值不动。
             */
            @Override
            public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance,
                                        float partialTick, float nearDistance, float farDistance, FogShape shape) {
                FluidViewEffects.setFogDistance(FOG_START, FOG_END);
            }

            // getOverlayTexture 仍然刻意不覆盖，原因见类注释。
        });
    }
}
