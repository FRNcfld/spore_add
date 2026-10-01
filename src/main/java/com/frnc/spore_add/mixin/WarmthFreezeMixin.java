package com.frnc.spore_add.mixin;

import com.frnc.spore_add.enchantment.Warmth;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 穿着「烈阳」护甲的生物不会冻僵。
 *
 * <h2>为什么拦这里而不是逐个去防</h2>
 * 原版 {@code LivingEntity#canFreeze()} 是<b>整个冻结系统的总闸</b>：
 * <ul>
 *   <li>细雪里每 tick 累加冻结刻数（{@code aiStep} 里 {@code if (isInPowderSnow && canFreeze())}）；</li>
 *   <li>冻结满 140 tick 后每 40 tick 的冻结伤害；</li>
 *   <li>按冻结比例施加的移动减速（{@code tryAddFrost} 用 {@code getPercentFrozen()}）；</li>
 *   <li>{@code Entity#isFreezing()} 也读它，所以任何以"正在冻结"为条件的东西一并失效。</li>
 * </ul>
 * 让它返回 false，上面这些一次全免，而且顺带与"抗冻装备"（皮革套）走的是同一条原版通路。
 *
 * <p>注意它<b>不</b>覆盖其它模组直接造成的冻结伤害——那种走伤害事件，见 {@code ModEvents}。
 */
@Mixin(LivingEntity.class)
public abstract class WarmthFreezeMixin {

    @Inject(method = "canFreeze", at = @At("HEAD"), cancellable = true)
    private void sporeAdd$warmArmorCannotFreeze(CallbackInfoReturnable<Boolean> cir) {
        if (Warmth.isWorn((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
