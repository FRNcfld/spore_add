package com.frnc.spore_add.entity;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.scavenger.Scavenger;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本 mod 的实体注册表：冰霜新星的弹体，以及它留下的粒子云。
 *
 * <p>两个都用 {@link MobCategory#MISC}——原版的雪球、鸡蛋、末影珍珠、箭矢、龙息云全是这一类，
 * 它表示"不算生物、不参与生物生成上限"。
 *
 * <p><b>尺寸不能省</b>：{@code EntityType.Builder} 的默认值是 {@code 0.6 × 1.8}（玩家尺寸），
 * 而投射物的命中检测是按包围盒扩展着算的，用默认值会明显提前命中。弹体照抄原版
 * {@code EntityType.SNOWBALL} 的 {@code 0.25 × 0.25} + {@code clientTrackingRange(4)}。
 *
 * <p>粒子云的碰撞箱刻意保持 {@code 0.5 × 0.5} 的固定小方块，<b>不</b>随云的球半径变大；
 * 理由见 {@link FrostNovaCloudEntity} 的类注释。
 *
 * <p>这几个投射物与粒子云都<b>不需要</b> {@code EntityAttributeCreationEvent}——那个事件的参数类型是
 * {@code EntityType<? extends LivingEntity>}，只对生物有意义。本类里唯一需要它的是
 * {@link #SCAVENGER}，见那边的注释。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SporeAdd.MOD_ID);

    /** 冰霜新星的弹体。 */
    public static final RegistryObject<EntityType<FrostNovaEntity>> FROST_NOVA =
            ENTITY_TYPES.register("frost_nova", () -> EntityType.Builder
                    .<FrostNovaEntity>of(FrostNovaEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("spore_add:frost_nova"));

    /**
     * 落点处那团持续 10 秒的粒子云。
     *
     * <p>{@code clientTrackingRange(10)} 要够大——粒子完全在客户端生成，追踪范围外的玩家
     * 看不到云、也就看不到那片霜雾。
     */
    public static final RegistryObject<EntityType<FrostNovaCloudEntity>> FROST_NOVA_CLOUD =
            ENTITY_TYPES.register("frost_nova_cloud", () -> EntityType.Builder
                    .<FrostNovaCloudEntity>of(FrostNovaCloudEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("spore_add:frost_nova_cloud"));

    /**
     * 冰球变成的延时炸弹：倒计时结束后二次引爆。
     *
     * <p>它自己不可见，所以追踪范围只需要保证客户端收得到同步数据——
     * 客户端的预警粒子靠实体里的冰球半径算撒点范围。20 tick 一次的同步足够，
     * 因为真正会变的只有半径（生成时定死）与倒计时（读的是本地 tickCount）。
     */
    public static final RegistryObject<EntityType<FrostNovaIceCoreEntity>> FROST_NOVA_ICE_CORE =
            ENTITY_TYPES.register("frost_nova_ice_core", () -> EntityType.Builder
                    .<FrostNovaIceCoreEntity>of(FrostNovaIceCoreEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("spore_add:frost_nova_ice_core"));

    /**
     * 「冰雪的叹息」的爆发本体（往外推的冲击环）。
     *
     * <p>它自己不可见、粒子全由服务端下发，所以客户端不需要追踪它——
     * {@code clientTrackingRange(0)} 即可，省掉一份没用的实体同步。
     */
    public static final RegistryObject<EntityType<FrostSighShockwaveEntity>> FROST_SIGH_SHOCKWAVE =
            ENTITY_TYPES.register("frost_sigh_shockwave", () -> EntityType.Builder
                    .<FrostSighShockwaveEntity>of(FrostSighShockwaveEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(0)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("spore_add:frost_sigh_shockwave"));

    /**
     * 冰封生物的那层冰壳。
     *
     * <p>尺寸给一个中性默认值（{@code 0.9 × 0.9}）：真正的碰撞箱由实体自己按被冰封生物的体型
     * 覆写 {@code getDimensions} 算，这里填什么都不影响运行，只影响"还没定尺寸时"的那一瞬间。
     *
     * <p>{@code fireImmune()} 是必要的：冰壳本身不该着火。它虽然一打就碎，
     * 但被火焰点着后持续掉血会在玩家没碰它的情况下自己碎掉。
     *
     * <p>{@code clientTrackingRange(10)} 而<b>不是</b> 0：它必须被客户端看见（要画冰壳、
     * 也要让里面的生物被渲染），所以得进追踪范围。{@code updateInterval(20)} 因为它生成之后就再也不动了。
     */
    public static final RegistryObject<EntityType<FrozenCapsuleEntity>> FROZEN_CAPSULE =
            ENTITY_TYPES.register("frozen_capsule", () -> EntityType.Builder
                    .<FrozenCapsuleEntity>of(FrozenCapsuleEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("spore_add:frozen_capsule"));

    /**
     * 爆发后的蘑菇云。
     *
     * <p>它自己不可见（注册到 {@code InvisibleEntityRenderer}），观感全靠客户端撒粒子，
     * 所以追踪范围要够大：{@code 17} 个区块 = 272 格，覆盖配置上限 256 的半径。
     * <b>调大配置里的 radius 时要回来改这里</b>，否则站在远端的玩家看不到蘑菇云。
     */
    public static final RegistryObject<EntityType<FrostSighCloudEntity>> FROST_SIGH_CLOUD =
            ENTITY_TYPES.register("frost_sigh_cloud", () -> EntityType.Builder
                    .<FrostSighCloudEntity>of(FrostSighCloudEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(17)
                    .updateInterval(20)
                    .build("spore_add:frost_sigh_cloud"));

    /**
     * 爆发后留在爆心的**降雪与冰雾**。
     *
     * <p>同蘑菇云：不可见、客户端撒粒子，追踪范围同样取 272 格。它由服务端用区块票钉住爆心
     * 那一个区块，所以哪怕玩家走远，雪与雾也不会停——这两种效果合成一个实体正是为了只有一张票。
     */
    public static final RegistryObject<EntityType<FrostSighAftermathEntity>> FROST_SIGH_AFTERMATH =
            ENTITY_TYPES.register("frost_sigh_aftermath", () -> EntityType.Builder
                    .<FrostSighAftermathEntity>of(FrostSighAftermathEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(17)
                    .updateInterval(20)
                    .build("spore_add:frost_sigh_aftermath"));

    private ModEntities() {
    }

    /**
     * 「拾荒者」——菌染人类的变体，见 {@link Scavenger}。
     *
     * <p>尺寸与 {@code spore:inf_human} 一致（僵尸那套 {@code 0.6 × 1.95}）。它<b>不</b>走
     * {@link MobCategory#MISC}：这是个真正的生物，要参与生物生成上限、也会被 Spore 的
     * Despawning System 一起统计（后者可能会把它清掉，见配置注释里的建议）。
     */
    public static final RegistryObject<EntityType<Scavenger>> SCAVENGER =
            ENTITY_TYPES.register("scavenger", () -> EntityType.Builder
                    .<Scavenger>of(Scavenger::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build("spore_add:scavenger"));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    /**
     * 给拾荒者绑属性。
     *
     * <p>本类里只有这一个实体是 {@code LivingEntity}，所以这是唯一一处
     * {@code EntityAttributeCreationEvent}——上面那几个投射物与粒子云都不需要它
     * （那个事件只对生物有意义）。
     *
     * <p>属性表直接转交 {@code InfectedHuman.createAttributes()}，所以血量、伤害、护甲、
     * 感知范围、击退抗性与菌染人类逐项一致。
     */
    @SubscribeEvent
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(SCAVENGER.get(), Scavenger.createAttributes().build());
    }
}
