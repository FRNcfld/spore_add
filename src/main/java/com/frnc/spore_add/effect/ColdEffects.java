package com.frnc.spore_add.effect;

import net.minecraft.world.entity.LivingEntity;

/**
 * 一个生物受到的全部寒冷效果：细雪式冻结 + 冻伤。三个来源共用这里：
 * 冷却液的接触、液态寒冷的接触、以及液态寒冷的区域效果。
 *
 * <h2>{@code setIsInPowderSnow} 为什么必须每 tick 都设</h2>
 * 原版 {@code Entity#baseTick} 每 tick 都会把那个标志清回 false，而冻结累积是 +1/tick、
 * 不在细雪里则 -2/tick。所以每 20 tick 才设一次的结果是净增长为负——<b>永远积累不起来</b>。
 *
 * <h2>更要紧的一条：必须在<b>实体自己的 tick 里</b>设</h2>
 * 这曾经是个 bug 的成因。液态寒冷的区域效果原本写在方块的调度刻里，而调度刻属于
 * {@code ServerLevel#tick} 的<b>方块刻阶段</b>，它排在<b>实体刻阶段之后</b>——于是那里设的标志
 * 会在实体下一 tick 的开头被 {@code baseTick} 清掉，实体永远读不到它。
 * 表现是：区域内冻伤照常叠（那个只依赖 addEffect，与 tick 顺序无关），但细雪冻结完全不积累
 * （没有屏幕冻结遮罩，只有冻伤减速带来的 FOV 变化）。
 *
 * <p>所以区域效果改由 {@code ModEvents#onLivingTick} 在实体自己的 tick 里调用本方法；
 * 接触那两条路走 {@code entityInside}，本来就发生在实体自己的 tick 内，没有这个问题。
 */
public final class ColdEffects {

    private ColdEffects() {
    }

    /**
     * 施加寒冷效果。
     *
     * @param frostbiteCap 冻伤层数上限（冷却液 10、液态寒冷 {@link FrostbiteLevels#UNLIMITED}）。
     *                     只封顶"涨"，不会压低已有层数
     */
    public static void chill(LivingEntity living, int frostbiteCap) {
        living.setIsInPowderSnow(true);
        FrostbiteLevels.add(living, frostbiteCap);
    }
}
