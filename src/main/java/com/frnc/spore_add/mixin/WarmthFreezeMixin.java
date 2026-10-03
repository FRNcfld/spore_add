package com.frnc.spore_add.mixin;

import com.frnc.spore_add.SporeAddPlayerConfig;
import com.frnc.spore_add.enchantment.Warmth;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    // 下面两个数都来自配置的 warmth 段（frostDecayPerTick / meltTopUpPeriodTicks），
    // **在方法体内读、且读在 isClientSide 早退之后**：绝大多数实体每 tick 直接返回，
    // 连配置都不碰，热路径不受影响。
    //
    // BASE_DECAY 必须与 vanilla 实际的每 tick 衰减量一致（原版硬编码 2）——它不改原版行为，
    // 只改我们"补零头"时假设的基准，填错会让长期均值失真。

    /**
     * 「烈阳」让已经吃到的细雪效果<b>消失得更快</b>。
     *
     * <h2>为什么是"加速消失"而不是"免伤"</h2>
     * {@link #sporeAdd$warmArmorCannotFreeze} 只拦得住"将来"的冻结累积。已经积累在身上的冻结刻数
     * 不会因为穿上护甲就消失——它本来要按 vanilla 的每 tick -2 慢慢退，而那个速度是固定的。
     * 这一条把退的速度按件数加快：一件 +25%、两件 +50%、三件 +75%、四件<b>立即清零</b>。
     *
     * <h2>为什么不直接写成 {@code i - 2 * (1 + 抗性)}</h2>
     * 因为那个值不一定是整数（一件是 2.5），而 {@code ticksFrozen} 是 int。这里改成
     * "vanilla 照常减 2，再按 {@link #EXTRA_DECAY_PERIOD} 的相位补一次零头"：
     * 长期平均正好是 {@code 2 × (1 + 抗性)}/tick，既精确又不需要给每个实体额外存一个小数累加器。
     *
     * <h2>成本</h2>
     * 挂在 {@code aiStep} 的 RETURN 上（那是个很大的方法、有多个 return，但一次调用只会走其中一个）。
     * 第一道判据是 {@code getTicksFrozen() <= 0}——一个同步数据的 int 读，几乎为零，
     * 所以<b>绝大多数实体</b>（从没冻过的那些）每 tick 到这里就直接返回，
     * 连那四次附魔查询都不会发生。
     */
    @Inject(method = "aiStep", at = @At("RETURN"))
    private void sporeAdd$warmthSpeedsUpFrostMelt(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        // 与 vanilla 那段衰减一样只在服务端做：客户端的 ticksFrozen 是同步下来的，
        // 在客户端自己改会让屏幕遮罩与服务端对不上
        if (self.level().isClientSide() || self.getTicksFrozen() <= 0) {
            return;
        }
        float resistance = Warmth.resistanceFraction(self);
        if (resistance <= 0.0F) {
            return;   // 没穿烈阳：vanilla 刚减掉的 2/tick 就是全部
        }
        if (resistance >= 1.0F) {
            self.setTicksFrozen(0);   // 满四件：立即消失
            return;
        }
        int period = SporeAddPlayerConfig.warmthMeltTopUpPeriodTicks();
        if (self.tickCount % period == 0) {
            int extra = Math.round(SporeAddPlayerConfig.warmthFrostDecayPerTick() * resistance * period);
            self.setTicksFrozen(Math.max(0, self.getTicksFrozen() - extra));
        }
    }
}
