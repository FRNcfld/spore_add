package com.frnc.spore_add.mixin;

import com.Harbinger.Spore.sEvents.HandlerEvents;
import com.frnc.spore_add.raid.RaidManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 需求「攻击」第 1 条：袭击期间 Despawning System 的上限暂时提高 100%。
 *
 * <h2>为什么改 {@code despawnExcess} 的入参，而不是那几个配置项</h2>
 * Spore 在 {@code cleanUpMobs} 里是这样调的：
 * <pre>
 *   despawnExcess(level, infected, SConfig.SERVER.max_infected_cap.get());
 *   despawnExcess(level, evolved,  SConfig.SERVER.max_evolved_cap.get());
 *   ... 一共五处，外加弹射物写死的 100
 * </pre>
 * 想去改那五处 {@code get()} 的话，{@code @Redirect} 会因为同一个方法里有五处一模一样的调用
 * 而匹配到多个目标（Mixin 会直接判定注入失败）。而 {@code despawnExcess} 是这五条路唯一的汇合点，
 * 它只有一个 {@code int} 参数，所以 {@code ordinal = 0} 毫无歧义。
 *
 * <p>顺带也把弹射物那一路的写死值 100 一起抬了——它们在袭击期间同样属于"真菌的东西"，
 * 一起放宽是合理的，而且袭击结束就自动恢复。
 *
 * <h2>{@code remap = false}</h2>
 * {@code despawnExcess} 是 Spore 自己的私有静态方法，生产环境不改名。
 * 本类没有引用任何原版成员，所以这个设定没有副作用。
 */
@Mixin(HandlerEvents.class)
public abstract class DespawnCapMixin {

    /**
     * 抬高上限。{@code adjustDespawnCap} 只在<b>攻击阶段</b>才真的动手，
     * 准备阶段保持原值——理由见 {@code RaidManager#adjustDespawnCap}。
     */
    @ModifyVariable(method = "despawnExcess", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static int sporeAdd$raiseDespawnCapForRaid(int cap) {
        return RaidManager.adjustDespawnCap(cap);
    }
}
