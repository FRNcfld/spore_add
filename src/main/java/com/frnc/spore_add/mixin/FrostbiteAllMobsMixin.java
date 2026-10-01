package com.frnc.spore_add.mixin;

import com.Harbinger.Spore.Effect.FrostBite;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.effect.FrostbiteLevels;

import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 改 Spore 冻伤 {@code applyEffectTick} 里的两处调用，一次实现两个需求。
 *
 * <h2>一、让冻伤对<b>所有</b>生物生效</h2>
 * 该方法的第一件事是 {@code if (!entity.getType().is(coldWeakness)) return;}，而 {@code coldWeakness} 就是
 * {@code minecraft:freeze_hurts_extra_types} 标签——Spore 把自己的 {@code #spore:fungus_entities}
 * 塞了进去。所以默认情况下玩家和原版生物身上挂再多层冻伤也不掉血。
 *
 * <p><b>为什么不用数据包把生物加进那个标签</b>：标签没法表达"全部实体"，没有"所有实体"这种上级标签可以引用，
 * 只能逐个列，既列不完也会随版本失效。所以直接注入那一次判断，让它恒为真。
 *
 * <h2>二、按层数附加 2% 最大生命值的冰冻伤害</h2>
 * 冻伤每满 10 层（显示层数）附加一档，每档为目标最大生命值的 2%，见
 * {@link FrostbiteLevels#bonusFreezeDamage}。
 *
 * <p>做法是<b>把这份伤害加进 Spore 那一次 {@code hurt} 里</b>，不另起一次结算。除了"与 Spore 保持一致"
 * 这个要求，还有个技术原因：原版 {@code LivingEntity#hurt} 有 20 tick 的受伤无敌帧，
 * {@code invulnerableTime > 10} 时若新伤害不大于上次就整条丢弃——同一 tick 里单独补一次很可能被吃掉。
 * 加进同一次则必然生效，而且顺带继承了那次结算的两个原版特性：对 {@code freeze_hurts_extra_types}
 * 内的实体 ×5、以及 {@code freeze} 属于 {@code bypasses_armor} 所以不吃护甲。
 *
 * <p>两个重定向都要求目标调用在该方法里<b>唯一</b>——已经用 {@code javap} 核对过：
 * {@code EntityType.is} 与 {@code LivingEntity.hurt} 各出现一次。
 *
 * <h2>没有绕过的闸门</h2>
 * 那道 {@code if (amplifier < enduranceLevel) return;} 是<b>另一道</b>判断（不是标签那道），本 mixin 不动它。
 * 所以被抗寒性挡下的生物连 {@code hurt} 都走不到，附加伤害自然也拿不到——这正是"与 Spore 保持一致"。
 *
 * <p>目标类属于 Spore，登记在 {@code spore_add.mixins.json} 的 {@code "mixins"}（通用）列表。
 */
@Mixin(FrostBite.class)
public abstract class FrostbiteAllMobsMixin {

    /** 把 {@code entity.getType().is(coldWeakness)} 的结果强制为真。 */
    @Redirect(
            method = "applyEffectTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/EntityType;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean sporeAdd$affectEveryMob(EntityType<?> type, TagKey<EntityType<?>> tag) {
        return true;
    }

    /**
     * 在 Spore 那一次冻结伤害上叠加按层数算出的附加伤害，然后把这次调用原样转交给 {@code hurt}。
     *
     * <p>处理器里的 {@code entity.hurt(...)} 就是被重定向掉的那次调用本身；重定向只作用于目标方法的字节码，
     * 不会作用到本处理器，所以不存在递归。
     */
    @Redirect(
            method = "applyEffectTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean sporeAdd$addBonusFreezeDamage(LivingEntity entity, DamageSource source, float amount) {
        MobEffectInstance frostbite = entity.getEffect(SporeCompat.frostbite());
        int amplifier = frostbite == null ? 0 : frostbite.getAmplifier();
        return entity.hurt(source, amount + FrostbiteLevels.bonusFreezeDamage(entity, amplifier));
    }
}
