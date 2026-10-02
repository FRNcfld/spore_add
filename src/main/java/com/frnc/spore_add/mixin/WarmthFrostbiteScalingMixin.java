package com.frnc.spore_add.mixin;

import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.enchantment.Warmth;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 让「烈阳」按<b>件数</b>削弱即将入体的冻伤：每件 25%，四件叠满即完全免疫。
 *
 * <h2>为什么必须用 mixin，而不是事件</h2>
 * 原先的做法是监听 {@code MobEffectEvent.Applicable} 直接 DENY——那个事件
 * <b>只能允许或拒绝，拿不到"改一改再放行"的能力</b>，而按比例削弱正是要改实例本身
 * （把时长与 amplifier 各乘 {@code 1 - 抗性}）。所以只能拦在效果真正被加进去之前。
 *
 * <p>选 {@code addEffect(MobEffectInstance, Entity)} 这个重载而不是单参的那个：
 * <ul>
 *   <li>单参那个是 {@code final}，但它<b>只是转调双参版本</b>（{@code addEffect(e, null)}），
 *       所以注入双参版本就能同时覆盖两条路径；</li>
 *   <li>双参版本不是 final，且它是真正把效果写进 {@code activeEffects} 的地方，
 *       在它开头改参数最干净——{@code canBeAffected} 与后续的合并逻辑看到的都已经是削弱后的实例。</li>
 * </ul>
 *
 * <h2>作用范围</h2>
 * 这里只认冻伤这一种效果，其它 buff 一概原样放行。但<b>冻伤的来源不限于本 mod</b>——
 * Spore 自己的机器、冰霜肿瘤、以及本 mod 的冷却液/液态寒冷/核弹，全都会经过这里，
 * 于是"每件 25%"在所有场景统一生效（这正是需求要的）。
 */
@Mixin(LivingEntity.class)
public abstract class WarmthFrostbiteScalingMixin {

    /**
     * {@code argsOnly = true} 表示改的是入参而不是局部变量；没有写 {@code index}，
     * 让 Mixin 按类型自己找——本方法的两个入参里只有一个是 {@code MobEffectInstance}，不会有歧义。
     */
    @ModifyVariable(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            argsOnly = true)
    private MobEffectInstance sporeAdd$scaleFrostbite(MobEffectInstance instance) {
        LivingEntity self = (LivingEntity) (Object) this;
        float resistance = Warmth.resistanceFraction(self);
        if (resistance <= 0.0F) {
            return instance;   // 没穿烈阳，原样放行
        }
        MobEffect frostbite = SporeCompat.frostbite();
        if (frostbite == null || instance.getEffect() != frostbite) {
            return instance;   // 不是冻伤，原样放行
        }

        float keep = 1.0F - resistance;
        int duration = Math.max(1, Math.round(instance.getDuration() * keep));
        int amplifier = Math.max(0, Math.round(instance.getAmplifier() * keep));
        return new MobEffectInstance(frostbite, duration, amplifier,
                instance.isAmbient(), instance.isVisible(), instance.showIcon());
    }
}
