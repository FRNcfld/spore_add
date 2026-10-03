package com.frnc.spore_add.fungus;

import java.util.HashSet;
import java.util.List;

import com.Harbinger.Spore.Sentities.Organoids.Womb;
import com.frnc.spore_add.SporeAddDebugConfig.Area;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.debug.SporeAddDebug;

import net.minecraft.nbt.CompoundTag;

/**
 * 重构体的**孵化门槛**：没喂够属性就先别孵，但最多只许滞留这么久。
 *
 * <h2>为什么需要门槛</h2>
 * Spore 原版的孵化触发条件是「生物质攒够」，而生物质主要来自<b>吃掉</b>靠上来的真菌
 * （每只 5 点、每约 2 秒能吃一只），被动节拍每 30 秒才给 1 点——两条路差 75 倍。
 * 于是蹲在菌群里的重构体不到一分钟就吃饱了，根本等不到喂进去几种属性，
 * 孵出来的就是一只没有加成的白板灾厄。
 *
 * <p>而属性<b>只来自喂食</b>（Spore 一共 7 个同化配方，每个要喂对应的特定进化体），
 * 所以要"孵出来的灾厄别太普通"，只能要求它先被喂够。
 *
 * <h2>为什么还要一条滞留上限</h2>
 * 光有门槛的话，一只凑不齐属性的重构体会<b>永远卡在那里吃</b>——它不会孵，
 * 也不会消失，成为地图上一台一直运转的吞噬装置。所以加一条上限：
 * <b>基础条件（生物质）满足之后最多滞留这么久，到点无条件孵化</b>，
 * 孵出来的则是它当时攒到的那点属性。
 *
 * <p>{@code hatchMaxStallSeconds} 填 0 = 不设上限（允许无限滞留）。
 *
 * <h2>滞留从哪一刻算起</h2>
 * 就是<b>第一次被门槛拦下的那一刻</b>——而 {@code summon} 只在生物质攒够时才会被调用，
 * 所以那一刻正好等于"基础诞生条件满足"。计时记在重构体自己的持久数据里，
 * 随它一起存盘：区块卸载、服务器重启都不会把这段等待清零。
 *
 * <p>这个标记同时也是 {@code hivemind.WombSupport} 判断"这只重构体需要资助"的依据——
 * 它天然就是"生物质够了但属性不够"这个状态本身，不必再去读 Spore 的配置来算生物质。
 */
public final class WombGate {

    /** 滞留起点的存档键。{@code WombGate} 写、{@code WombSupport} 读。 */
    private static final String KEY_STALL_SINCE = "spore_add:womb_stall_since";

    private WombGate() {
    }

    /**
     * 现在允许它孵化吗。
     *
     * <p>三种情况放行：门槛关着、已经喂够、或者滞留超时。其余情况<b>顺带记下滞留起点</b>。
     */
    public static boolean allowHatch(Womb womb) {
        int required = SporeAddFungusConfig.wombHatchMinMutationTypes();
        if (required <= 0 || distinctMutations(womb) >= required) {
            return true;
        }
        int stallSeconds = SporeAddFungusConfig.wombHatchMaxStallSeconds();
        if (stallSeconds <= 0) {
            return false;   // 0 = 不设上限
        }

        CompoundTag data = womb.getPersistentData();
        long now = womb.level().getGameTime();
        if (!data.contains(KEY_STALL_SINCE)) {
            data.putLong(KEY_STALL_SINCE, now);
            // 检查点**只打这一处**——"第一次被拦下"这个瞬间。
            // 本方法是每 tick 被调到的（summon 在生物质够之后会一直尝试），
            // 所以下面那条 return 与"仍然拦着"的路径必须保持静默，
            // 否则一只凑不齐属性的重构体会把日志刷爆。
            // 这一行同时也是"WombSummonMixin 确实生效了"的证据：allowHatch 只有它一个调用方。
            // 放行那一刻不在这里打——正常的孵化会被 WombHatch 自己那几行 INFO 记下来。
            SporeAddDebug.log(Area.WOMB, "孵化被拦：突变 {} 种 / 需要 {} 种，开始计时（滞留上限 {} 秒）",
                    distinctMutations(womb), required, stallSeconds);
            return false;   // 刚开始等，这一拍先不放
        }
        return now - data.getLong(KEY_STALL_SINCE) >= stallSeconds * 20L;
    }

    /**
     * 这只重构体现在正卡在门槛上吗（生物质够了、属性还没够）。
     *
     * <p>两个条件都要：有滞留标记（说明它已经被拦过），且属性确实还差。
     * 只看标记是不够的——资助补够之后到下一次 {@code summon} 之间，标记还在。
     */
    public static boolean isStalled(Womb womb) {
        int required = SporeAddFungusConfig.wombHatchMinMutationTypes();
        if (required <= 0) {
            return false;   // 没有门槛就谈不上"卡住"
        }
        return distinctMutations(womb) < required
                && womb.getPersistentData().contains(KEY_STALL_SINCE);
    }

    /** {@code attributeIDs} 里有几种**不同**的属性。同一个属性喂两次算一种。 */
    public static int distinctMutations(Womb womb) {
        // getAttributeIDs 的签名是裸 List，转成 List<?> 就够去重了
        return new HashSet<>((List<?>) womb.getAttributeIDs()).size();
    }
}
