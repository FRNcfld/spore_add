package com.frnc.spore_add.scavenger;

import com.frnc.spore_add.SporeAddFungusConfig;

import net.minecraft.world.entity.Entity;

/**
 * 拾荒者的"现存数量"，需求 3 的两条都靠它：存在上限、以及"数量越多转换概率越低"。
 *
 * <h2>为什么是一个计数器，而不是每次去数</h2>
 * 概率是在<b>每一只菌染人类生成时</b>算的，而"数一遍世上有多少拾荒者"意味着遍历每个维度
 * 的全部实体——那是个上万级的集合，而生成事件可能一秒好几次。所以这里改成增量维护：
 * 实体进入世界时 +1、离开时 -1。
 *
 * <p>用 {@code EntityJoinLevelEvent} / {@code EntityLeaveLevelEvent} 这一对，是因为它们
 * <b>对区块卸载也成立</b>：区块卸载时实体会离开世界（触发 Leave），但不会死——
 * 只挂死亡事件的话，玩家走远一次计数就再也降不下来了。
 *
 * <h2>精度</h2>
 * 计数可能因为漏事件而漂移，而它是用来卡上限的，漂移了会一直卡在"满员"。
 * 所以 {@link #count()} 只在<b>明显异常</b>时会自然回落——两条事件是所有进出世界的路径，
 * 目前没有发现绕过它们的路。真要出错，症状是"拾荒者不再出现"，把配置上限调大或重启即可恢复。
 */
public final class ScavengerPopulation {

    /** 现存拾荒者数量。只由 {@link #onJoin} / {@link #onLeave} 改动。 */
    private static int count;

    private ScavengerPopulation() {
    }

    /** 实体进入世界。 */
    public static void onJoin(Entity entity) {
        if (entity instanceof Scavenger) {
            count++;
        }
    }

    /** 实体离开世界（含区块卸载、死亡、传送跨维度）。 */
    public static void onLeave(Entity entity) {
        if (entity instanceof Scavenger) {
            // 下限 0：万一事件顺序异常导致多减了一次，也不该变成负数，
            // 否则"数量越多概率越低"会算出大于 1 的倍率
            count = Math.max(0, count - 1);
        }
    }

    /** 现存多少只。 */
    public static int count() {
        return count;
    }

    /**
     * 现在还能不能再变出一只拾荒者。
     *
     * <p>需求要的是"存在上限"与"数量越多概率越低"两件事，这里用一个线性衰减同时满足：
     * <pre>
     *   系数 = max(0, 1 - 现存 / 上限)
     * </pre>
     * 现存为 0 时系数 1（不削减），到上限时正好 0（再也不会转出新的）——
     * 所以<b>上限本身就是"概率降到零"的那个点</b>，不必再单独判一次上限，
     * 也就不会出现"上限与概率两条规则互相打架"的情况。
     *
     * <p>上限配成 0 或负数时视为"禁止出现拾荒者"，返回 0。
     */
    public static double transformFactor() {
        int limit = SporeAddFungusConfig.scavengerMaxCount();
        if (limit <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, 1.0D - (double) count / limit);
    }
}
