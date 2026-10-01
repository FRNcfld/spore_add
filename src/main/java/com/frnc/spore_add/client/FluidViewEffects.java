package com.frnc.spore_add.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * 玩家浸在流体里时"看到什么"的两半，都是纯客户端代码：
 * <ul>
 *   <li>{@link #renderFilter} —— 一层与流体同色的全屏滤镜；</li>
 *   <li>{@link #setFogDistance} —— 把雾的起止距离拉近到水里那样。</li>
 * </ul>
 *
 * <p>本类只由 {@link com.frnc.spore_add.fluid.SporeFluidType} 在客户端侧构造的
 * {@code IClientFluidTypeExtensions} 调用，服务端不会加载到它（详见那个类的说明）。
 * 之所以单独成类，是为了让公共侧的 {@code SporeFluidType} 不必沾染这些纯客户端 import。
 */
public final class FluidViewEffects {

    private FluidViewEffects() {
    }

    /**
     * 铺一层全屏纯色滤镜。
     *
     * <h2>为什么不能用 {@code GuiGraphics.fill}</h2>
     * 调用链是 {@code GameRenderer#renderItemInHand} → {@code ScreenEffectRenderer#renderScreenEffect}
     * → 流体的 {@code renderOverlay}，也就是在<b>世界渲染</b>阶段，此时绑定的是世界投影矩阵而不是 GUI 投影矩阵。
     * {@code GuiGraphics.fill} 按像素坐标 + GUI 投影绘制，在这里画出来的东西不会铺满屏幕。所以只能像原版的
     * {@code ScreenEffectRenderer#renderFluid} 那样，自己往 {@code BufferBuilder} 里塞一个裁剪空间的全屏四边形。
     *
     * <h2>渲染状态</h2>
     * 调用点不会为被调者准备任何状态（前面的方块叠加层甚至会留下它自己的 shader 和贴图绑定），
     * 所以本方法自己设 shader / 混合 / {@code ColorModulator}，并且只还原自己改过的那两项——
     * 这与原版各个 overlay 的做法一致（它们也是各管各的）。
     *
     * <p>刻意不动 {@code depthMask} / {@code depthFunc}：原版的 {@code renderFluid} 同样不碰，
     * 而 {@code z = -0.5} 的四边形在默认深度测试下就能通过。只有原版的火焰叠加层才需要额外设深度状态。
     *
     * @param poseStack 流体 overlay 回调给的变换。此调用点它已被 {@code setIdentity()}，
     *                  所以 {@code -1..1} 就是整屏，不需要再做任何缩放
     * @param argb      滤镜颜色（ARGB）。alpha 就是滤镜强度，0 表示完全透明
     */
    public static void renderFilter(PoseStack poseStack, int argb) {
        Matrix4f matrix = poseStack.last().pose();

        // POSITION_COLOR 顶点格式不带贴图，配 position_color 这个 shader。
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        // 颜色走顶点色，所以 ColorModulator 保持恒等；它会被乘进顶点色，不置 1 会叠乘出错误颜色。
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        // 原版的 renderFluid 只 enableBlend 而不设混合函数，靠当时恰好处于默认值。
        // 这里显式设一次，滤镜的 alpha 才是可预期的（SRC_ALPHA / ONE_MINUS_SRC_ALPHA）。
        RenderSystem.defaultBlendFunc();

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, -1.0F, -1.0F, -0.5F).color(argb).endVertex();
        buffer.vertex(matrix, 1.0F, -1.0F, -0.5F).color(argb).endVertex();
        buffer.vertex(matrix, 1.0F, 1.0F, -0.5F).color(argb).endVertex();
        buffer.vertex(matrix, -1.0F, 1.0F, -0.5F).color(argb).endVertex();
        // 1.20.1 里 BufferBuilder#end() 只是收尾并返回 RenderedBuffer，真正上传要靠 BufferUploader。
        BufferUploader.drawWithShader(buffer.end());

        // 还原自己改过的状态，避免污染同一帧后面的绘制。
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 把雾的起止距离改成给定值。由流体的 {@code modifyFogRender} 调用。
     *
     * <p>调用顺序上这是安全的：{@code FogRenderer} 先按自己的逻辑设好雾，随后
     * {@code ForgeHooksClient.onFogRender} 才回调本方法，最后才 post {@code ViewportEvent.RenderFog}；
     * 而那个事件只在被取消时才会覆盖回去。所以只要没有别的模组取消它，我们设的值就是最终值。
     *
     * @param start 雾开始的距离（可以为负，表示相机身后就开始起雾，原版水就是 -8）
     * @param end   雾完全遮蔽的距离
     */
    public static void setFogDistance(float start, float end) {
        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(end);
    }
}
