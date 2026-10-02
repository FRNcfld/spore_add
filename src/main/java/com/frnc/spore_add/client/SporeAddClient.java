package com.frnc.spore_add.client;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.SporeAddConfig;
import com.frnc.spore_add.client.particle.FrostMoteParticle;
import com.frnc.spore_add.entity.FrostNovaCloudEntity;
import com.frnc.spore_add.entity.FrostNovaEntity;
import com.frnc.spore_add.entity.FrostNovaIceCoreEntity;
import com.frnc.spore_add.entity.FrostSighAftermathEntity;
import com.frnc.spore_add.entity.FrostSighCloudEntity;
import com.frnc.spore_add.entity.FrostSighShockwaveEntity;
import com.frnc.spore_add.entity.ModEntities;
import com.frnc.spore_add.fluid.ModFluids;
import com.frnc.spore_add.item.ModItems;
import com.frnc.spore_add.particle.ModParticles;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 本 mod 的客户端初始化：流体渲染层、冰霜新星弹体的渲染器、以及它的蓄力物品属性。
 *
 * <p><b>这个类必须只在客户端加载</b>：它引用的 {@link ItemBlockRenderTypes}、{@code ItemProperties}、
 * {@code ThrownItemRenderer} 都是纯客户端类，服务端一旦加载到这个类就会因缺类而崩。
 * {@code @Mod.EventBusSubscriber} 上的 {@code value = Dist.CLIENT} 就是干这个的——Forge 只会在客户端
 * 注册本类，服务端连类都不会碰，所以不需要在方法里手写 {@code if (dist.isClient())} 判断。
 *
 * <p>下面两个事件都挂在<b>模组总线</b>上（{@code bus = Bus.MOD}）。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SporeAddClient {

    private SporeAddClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            registerFluidRenderLayers();
            registerFrostNovaProperties();
        });
    }

    /**
     * 注册三个实体的渲染器。
     *
     * <p><b>这里必须与 {@code ModEntities} 一一对应，一个都不能少。</b>
     * {@code EntityRenderDispatcher#shouldRender} 会直接对 {@code getRenderer(entity)} 的返回值解引用，
     * 所以世界里只要出现一个没有渲染器的实体，客户端就会在那一帧崩掉，而且报的是个看不出实体的
     * NPE（本 mod 就是这么崩过一次：弹体配了、云漏了）。新增实体时，两个文件要一起改。
     *
     * <p>弹体用原版的 {@link ThrownItemRenderer}——它把实体画成"物品图标朝向相机的公告板"，
     * 正是原版雪球、鸡蛋、末影珍珠的观感，不需要自己写渲染器类。最后一个参数 {@code true}
     * 是"全亮"，让它在暗处也发光。粒子云与冰球里的延时炸弹则是"什么都不画"，
     * 理由见 {@link InvisibleEntityRenderer}。
     *
     * <p>挂的是 {@link EntityRenderersEvent.RegisterRenderers}（实现 {@code IModBusEvent} → 模组总线）。
     * <b>不是</b> {@code EntityRenderers.register}——后者是原版内部用的静态方法，两者混用会对同一个
     * 实体类型重复注册、后者覆盖前者。
     */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FROST_NOVA.get(),
                context -> new ThrownItemRenderer<FrostNovaEntity>(context, 1.0F, true));
        event.registerEntityRenderer(ModEntities.FROST_NOVA_CLOUD.get(),
                context -> new InvisibleEntityRenderer<FrostNovaCloudEntity>(context));
        event.registerEntityRenderer(ModEntities.FROST_NOVA_ICE_CORE.get(),
                context -> new InvisibleEntityRenderer<FrostNovaIceCoreEntity>(context));
        event.registerEntityRenderer(ModEntities.FROST_SIGH_SHOCKWAVE.get(),
                context -> new InvisibleEntityRenderer<FrostSighShockwaveEntity>(context));
        // 冰封生物的冰壳：唯一一个要自己画的（一个按体型拉伸的冰块），
        // 里面的生物是它的乘客，由原版渲染器照常绘制，这里不用管
        event.registerEntityRenderer(ModEntities.FROZEN_CAPSULE.get(),
                FrozenCapsuleRenderer::new);
        event.registerEntityRenderer(ModEntities.FROST_SIGH_CLOUD.get(),
                context -> new InvisibleEntityRenderer<FrostSighCloudEntity>(context));
        event.registerEntityRenderer(ModEntities.FROST_SIGH_AFTERMATH.get(),
                context -> new InvisibleEntityRenderer<FrostSighAftermathEntity>(context));
    }

    /**
     * 注册三个霜冻粒子的渲染器。
     *
     * <p>三个共用 {@link FrostMoteParticle}，只是外观参数不同：
     * <ul>
     *   <li><b>云雾</b>——大（2.2 格）、很透明（0.35）、活得久，负责把云的体积撑出来。
     *       尺寸给得大是为了让粒子总数不必多：粒子云是持续 10 秒、每 tick 都在补的，
     *       数量直接决定帧数开销。</li>
     *   <li><b>雪花</b>——小、亮、缓慢上飘，只是点缀。</li>
     *   <li><b>冰屑</b>——最小、最短命、带一点重力，用在弹体拖尾与命中爆发上。</li>
     * </ul>
     *
     * <p>挂的 {@link RegisterParticleProvidersEvent} 同样是模组总线事件。
     */
    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.FROST_MIST.get(),
                sprites -> FrostMoteParticle.provider(sprites, 2.2F, 0.35F, 50, 90, 0.0F));
        event.registerSpriteSet(ModParticles.FROST_SNOWFLAKE.get(),
                sprites -> FrostMoteParticle.provider(sprites, 0.5F, 0.9F, 30, 60, -0.008F));
        event.registerSpriteSet(ModParticles.FROST_SHARD.get(),
                sprites -> FrostMoteParticle.provider(sprites, 0.4F, 1.0F, 10, 20, 0.18F));

        // 「冰雪的叹息」那一组：同样是共用实现，只是尺寸/透明度/寿命不同。
        // 尺寸给得比新星那组大得多——核弹的体量摆在那里，小粒子撑不起来。
        event.registerSpriteSet(ModParticles.FROST_SIGH_CLOUD.get(),
                sprites -> FrostMoteParticle.provider(sprites, 2.6F, 0.50F, 60, 110, 0.0F));
        event.registerSpriteSet(ModParticles.FROST_SIGH_HAZE.get(),
                sprites -> FrostMoteParticle.provider(sprites, 3.2F, 0.60F, 80, 140, 0.0F));
        event.registerSpriteSet(ModParticles.FROST_SIGH_CORE.get(),
                sprites -> FrostMoteParticle.provider(sprites, 1.6F, 0.80F, 40, 70, 0.0F));
        event.registerSpriteSet(ModParticles.FROST_SIGH_FLARE.get(),
                sprites -> FrostMoteParticle.provider(sprites, 1.2F, 0.90F, 20, 40, 0.05F));
        event.registerSpriteSet(ModParticles.FROST_SIGH_SNOW.get(),
                sprites -> FrostMoteParticle.provider(sprites, 0.4F, 0.90F, 40, 80, -0.01F));
        // 警示粒子：透明度压到 0.45、寿命也短，做出"淡淡的"效果。
        // 它是边界标记不是演出主体，抢戏就本末倒置了。
        event.registerSpriteSet(ModParticles.FROST_SIGH_WARNING.get(),
                sprites -> FrostMoteParticle.provider(sprites, 0.9F, 0.45F, 30, 60, 0.0F));
    }

    private static void registerFluidRenderLayers() {
        // 流体默认走 RenderType.solid()，也就是不透明；不改成 translucent 的话，
        // 水状流体会渲染成一块实心方块（颜色仍然对，但完全没有液体的通透感）。
        // 静置与流动两个变体都要设：渲染时是按 FluidState 的 type 查的，而源头和流动
        // 在注册表里是两条独立条目，只设一个会出现"源头通透、流出去的部分变实心"。
        ItemBlockRenderTypes.setRenderLayer(ModFluids.COOLANT.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_COOLANT.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(ModFluids.LIQUID_COLD.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_LIQUID_COLD.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(ModFluids.HIGH_ENERGY_FUEL.get(), RenderType.translucent());
        ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_HIGH_ENERGY_FUEL.get(), RenderType.translucent());
    }

    /**
     * 给冰霜新星补上 {@code pull} 与 {@code pulling} 两个物品属性，模型靠它们切换蓄力外观。
     *
     * <h2>为什么必须自己注册</h2>
     * 这两个名字看着像通用属性，其实原版是用 {@code ItemProperties.register(Items.BOW, ...)} /
     * {@code register(Items.CROSSBOW, ...)} <b>逐个物品</b>注册的，并不存在"所有物品都能用"的通用版本。
     * 模型 JSON 的 {@code overrides} 里如果引用了没注册的属性，取值恒为 {@code Float.NEGATIVE_INFINITY}，
     * 那条 override 永远不命中——<b>不报错、只是图标不随蓄力变化</b>，所以漏了会很难查。
     *
     * <h2>为什么放在这里</h2>
     * 属性是在<b>渲染时</b>才按名字查的，而模型烘焙发生在客户端初始化之后的资源重载里，
     * 所以这个时机不存在竞态。真正要注意的是线程——{@code ItemProperties} 内部是普通 HashMap，
     * 因此放在 {@code enqueueWork} 里（客户端主线程），而不是回调体外面。
     *
     * <p>两个属性的公式与原版弓逐字一致（见 {@code ItemProperties} 里 BOW 那两条），所以模型 JSON
     * 里那组阈值 {@code 0.65 / 0.9} 可以直接沿用。{@code getUseDuration} 那一项读的就是
     * {@code ItemStack.getUseDuration()}，也就是 {@link com.frnc.spore_add.item.FrostNovaItem}
     * 返回的 72000——两者要一起理解，改动其中一个会让档位整体错位。
     */
    // 接受这个警告：能从外部访问的 register 重载只有参数为 ItemPropertyFunction 的那个，
    // 而它被标注了 @Deprecated；带 clamp 的 ClampedItemPropertyFunction 那个重载是 private。
    // 所以这不是用了过时写法，是唯一可行的写法（下面自己补了一次 clamp）。
    @SuppressWarnings("deprecation")
    private static void registerFrostNovaProperties() {
        Item frostNova = ModItems.FROST_NOVA.get();

        // 拉弓进度。原版的 clamp 写在 ClampedItemPropertyFunction#call 的默认实现里，但那个接口
        // 对应的 register 重载是 private，从外部只能走公开的 ItemPropertyFunction 重载，所以自己夹一次。
        ItemProperties.register(frostNova, new ResourceLocation("pull"),
                (stack, level, entity, seed) -> {
                    if (entity == null || entity.getUseItem() != stack) {
                        return 0.0F;
                    }
                    // 除以**配置里**的 chargeTicks，不是原版弓那个写死的 20。
                    // 模型里的两档阈值 0.65 / 0.9 是比例，所以只要这里归一化对了，
                    // 改 chargeTicks 之后三段蓄力外观仍然均匀铺满整个蓄力过程。
                    // 之前写死 20 时，chargeTicks 一旦改成 100，动画会在第一秒内走完三段、
                    // 剩下四秒卡在最后一段。
                    int chargeTicks = SporeAddConfig.frostNovaChargeTicks();
                    float charge = (float) (stack.getUseDuration() - entity.getUseItemRemainingTicks())
                            / (float) chargeTicks;
                    return Mth.clamp(charge, 0.0F, 1.0F);
                });

        // "正在使用中"的开关，模型靠它决定要不要切到蓄力模型
        ItemProperties.register(frostNova, new ResourceLocation("pulling"),
                (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
    }
}
