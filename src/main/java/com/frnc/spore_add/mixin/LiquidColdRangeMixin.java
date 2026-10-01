package com.frnc.spore_add.mixin;

import com.frnc.spore_add.effect.ColdEffects;
import com.frnc.spore_add.effect.FrostbiteLevels;
import com.frnc.spore_add.world.FrozenChunks;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 液态寒冷的影响范围效果：生物只要处在范围内，就持续受到寒冷效果（细雪式冻结 + 无上限冻伤）。
 *
 * <h2>为什么非要 mixin，不能用事件</h2>
 * 因为一个 tick 内的<b>顺序</b>要求很苛刻，而 Forge 没有落在那段窗口里的事件。原版
 * {@code LivingEntity#tick} 里相关几步的先后是：
 *
 * <pre>
 *   2400  ForgeHooks.onLivingTick(this)                  ← LivingTickEvent 在这里
 *   2401  super.tick()                                   ← Entity#baseTick 在这里把 isInPowderSnow 清成 false
 *   2440  this.aiStep()                                  ← 移动，真实方块的 entityInside 在这里被调用
 *   2758  if (this.isInPowderSnow &amp;&amp; this.canFreeze())   ← 冻结判断读标志
 * </pre>
 *
 * 要生效，标志必须设在 2401 之后、2758 之前。Forge 的事件（{@code LivingTickEvent} 与
 * {@code PlayerTickEvent}）都在 2400 那一带，<b>设了立刻被 2401 清掉</b>——这正是这个 bug 反复出现的原因：
 * 先写在方块的调度刻里（方块刻阶段整体排在实体刻之后），改到事件里又踩在 2401 之前。
 * 所以注入 {@code aiStep} 的开头，那是唯一干净的落点。
 *
 * <h2>症状为什么具有迷惑性</h2>
 * 顺序错了的时候，区域内的<b>冻伤照常叠</b>（那个只依赖 {@code addEffect}，与 tick 顺序无关），
 * 而<b>细雪冻结完全不积累</b>。于是屏幕上没有粉末雪那层冻结遮罩，却仍有冻伤减速属性带来的 FOV 变化
 * ——看起来像"冻结在工作、只是少了个效果"，很容易误判成显示问题。
 *
 * <h2>成本</h2>
 * 每个生物每 tick 一次范围判定。{@link FrozenChunks#isWithinRange} 先按区块 O(1) 粗筛，
 * 只有落在已登记区块里才去算精确距离，所以离得远的生物每 tick 只花一次映射查询。
 */
@Mixin(LivingEntity.class)
public abstract class LiquidColdRangeMixin {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void sporeAdd$chillInRange(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Level level = self.level();
        if (level.isClientSide()) {
            return;
        }
        if (FrozenChunks.isWithinRange(level, self.blockPosition())) {
            ColdEffects.chill(self, FrostbiteLevels.UNLIMITED);
        }
    }
}
