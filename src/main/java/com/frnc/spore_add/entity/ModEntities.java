package com.frnc.spore_add.entity;

import com.frnc.spore_add.SporeAdd;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
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
 * <p>这两个都<b>不需要</b> {@code EntityAttributeCreationEvent}——那个事件的参数类型是
 * {@code EntityType<? extends LivingEntity>}，只对生物有意义。
 */
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

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
