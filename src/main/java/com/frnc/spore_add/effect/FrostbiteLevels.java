package com.frnc.spore_add.effect;

import com.frnc.spore_add.SporeAddPlayerConfig;
import com.frnc.spore_add.compat.SporeCompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 冻伤层数的唯一入口。
 *
 * <h2>层数就是 amplifier，不自存</h2>
 * 这是与可燃/爆燃<b>相反</b>的选择，原因在 Spore 那边：{@code com.Harbinger.Spore.Effect.FrostBite}
 * 真的在读自己的 amplifier——
 * <ul>
 *   <li>冻结伤害是 {@code 最大生命值 × 系数 + amplifier}，amplifier 直接加平伤；</li>
 *   <li>它有一道抗性闸门 {@code if (amplifier < enduranceLevel) return;}，
 *       amplifier 为 0 时对 Spore 所有进化体（endurance ≥ 1）<b>一点伤害都不造成</b>；</li>
 *   <li>注册的移动减速修饰符是 {@code -10% × (amplifier + 1)}。</li>
 * </ul>
 * Spore 自己的 CDU 机器与冰霜肿瘤施加时都在做 {@code existing + 1}，堆的正是这个数——
 * 它是个会被读的强度计数器，不是空位。
 *
 * <p>所以这里直接把 amplifier 当层数。好处：vanilla 自带 amplifier 同步，不需要网络包；
 * Spore 的伤害、减速、抗性闸门在 127 以内全部正常。代价：超过 127 之后 Spore 那边不再变强
 * （amplifier 的网络与存档序列化都是字节，绕不过去）。
 *
 * <h2>同一时刻只加一层</h2>
 * 调用方有两处，都会重复进来：流体方块的 {@code entityInside}（每 tick 都调，且生物泡在两格深的
 * 液体里会按方块数各调一次）、液态寒冷的每秒调度刻（多个源头方块的中心会互相重叠）。
 * 这里用"距上次涨级不足 {@link #MIN_INTERVAL_TICKS} 就只刷新时长"来兜底，
 * 于是无论多少来源重叠，层数都是稳定每秒 +1。
 */
public final class FrostbiteLevels {

    // 时长、涨层间隔、以及伤害公式里的两个系数都来自配置的 frostbite 段——
    // **四种冰霜来源（冷却液 / 液态寒冷 / 冰霜新星 / 冰雪的叹息）共用同一组值**。
    // 每一项都在用到的那一刻才读（见 SporeAddPlayerConfig 的类注释），不做成静态常量：
    // 静态常量会在类初始化时定死，改配置就得重启。

    /** 无上限来源（液态寒冷）用的 cap：实际仍受 {@link #MAX_AMPLIFIER} 约束。 */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    /**
     * amplifier 的硬上限：超过 127 会在网络同步与存档时静默损坏，这里留一格余量。
     *
     * <p><b>这是协议硬限，不是没做成可配置</b>：层数 = amplifier + 1，而 amplifier 的同步与序列化
     * 都是 signed byte，所以「层数 127」之外的值根本传不出去。
     */
    private static final int MAX_AMPLIFIER = 126;

    private static final String KEY_LAST_BUMP = "spore_add:frostbite_bump";

    private FrostbiteLevels() {
    }

    /**
     * 在冻伤上叠一层，并刷新持续时间。
     *
     * @param cap 该来源允许的最大层数（例如冷却液用 {@code coolant.frostbiteCap}，默认 10；
     *            液态寒冷用 {@link #UNLIMITED}）。
     *            <b>封顶只作用于"涨"</b>：若现有层数已经到顶，就不再加，但也<b>不会压低</b>——
     *            从液态寒冷（可能几十层）走进冷却液时，层数原样保留
     */
    public static void add(LivingEntity entity, int cap) {
        MobEffect frostbite = SporeCompat.frostbite();
        if (frostbite == null) {
            return;
        }

        MobEffectInstance existing = entity.getEffect(frostbite);
        int current = existing == null ? -1 : existing.getAmplifier();

        CompoundTag data = entity.getPersistentData();
        boolean recorded = data.contains(KEY_LAST_BUMP);
        int lastBump = data.getInt(KEY_LAST_BUMP);

        // 这里必须把"记录值比当前 tickCount 还大"当成过期，不能只比差值。
        // KEY_LAST_BUMP 存在 ForgeData 里、随存档保留，而 entity.tickCount 不存盘
        // （原版里它就是个普通字段，载入时从 0 重新计）。所以重进世界后
        // tickCount - lastBump 是个很大的负数，"距今已过 20 tick" 永远不成立，
        // 于是冻伤一直不施加，直到这一局的 tick 数追上上次记录为止——之后再一切正常。
        // 症状就是"重进游戏后冻伤不触发，玩一阵忽然好了"。
        boolean stale = recorded && lastBump > entity.tickCount;
        boolean due = stale || !recorded
                || entity.tickCount - lastBump >= SporeAddPlayerConfig.frostbiteMinIntervalTicks();

        int next = current;
        if (due) {
            data.putInt(KEY_LAST_BUMP, entity.tickCount);
            if (current < Math.min(cap - 1, MAX_AMPLIFIER)) {
                next = current + 1;
            }
        }

        if (next < 0) {
            // 既没有现成的冻伤，这一刻又不该涨——什么都不做，免得凭空冒出一层
            return;
        }
        // amplifier 相同时 vanilla 会取更长的时长，所以逐秒调用就是"叠加并刷新"
        entity.addEffect(new MobEffectInstance(frostbite, SporeAddPlayerConfig.frostbiteDurationTicks(), next));
    }

    /**
     * 冻伤每满 10 层附加的冰冻伤害：{@code 档数 × 最大生命值 × 2%}，不足 10 层的零头不计。
     *
     * <p>档数按<b>显示层数</b>算，即 {@code amplifier + 1}：10 层（amplifier 9）为 1 档、20 层为 2 档，
     * 以此类推。127 层封顶时是 12 档 = 24% 最大生命值。
     *
     * <p>这份伤害由 {@code FrostbiteAllMobsMixin} <b>加进 Spore 那一次冻结伤害里</b>，不是单独再打一次。
     * 原因不只是"与 spore 保持一致"：原版 {@code LivingEntity#hurt} 有 20 tick 的受伤无敌帧，
     * {@code invulnerableTime > 10} 时若新伤害不大于上次就<b>整条丢弃</b>。所以同一 tick 里单独补一次
     * 很可能会被吃掉，加进同一次结算才可靠；顺带也让这 2% 与原版规则一致地接受 ×5 与免护甲这两个特性。
     *
     * <p>调用点在 Spore 的 tick 里，所以它的触发频率自动与 Spore 的冻伤伤害完全一致——
     * 包括那道 {@code amplifier >= enduranceLevel} 的抗性闸门（被闸门挡下的生物拿不到这份附加伤害）。
     *
     * @param amplifier 冻伤当前的 amplifier，无该 buff 时传 0
     */
    public static float bonusFreezeDamage(LivingEntity entity, int amplifier) {
        int tiers = (amplifier + 1) / SporeAddPlayerConfig.frostbiteLevelsPerBonusTier();
        return tiers <= 0 ? 0.0F
                : tiers * (float) SporeAddPlayerConfig.frostbiteMaxHealthBonusPerTenLevels() * entity.getMaxHealth();
    }
}
