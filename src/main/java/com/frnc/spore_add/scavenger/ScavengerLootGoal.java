package com.frnc.spore_add.scavenger;

import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.fungus.CollectLootGoal;

import net.minecraft.server.level.ServerLevel;

/**
 * 拾荒者的拾荒目标。需求 3（优先拾荒）与需求 6（存活越久加成越高）都在这里。
 *
 * <h2>为什么继承而不是重写</h2>
 * 「找最近的、值钱的那件 → 走过去 → 够近了就收走 → 小数累计」这一整套和普通真菌捡东西
 * （{@link CollectLootGoal}）<b>完全一样</b>。差别只有两点，所以只覆写那两个钩子：
 * <ul>
 *   <li>{@link #adjustValue} —— 乘上存活时间加成（需求 6）</li>
 *   <li>{@link #deliver} —— 交付走拾荒者的优先级链（需求 4，见 {@link ScavengerDelivery}）</li>
 * </ul>
 * 再加一个 {@link #collectingEnabled} 恒真：捡东西是它的全部职能，不该被
 * {@code loot.collectEnabled}（那个开关是给普通真菌准备的）关掉。
 *
 * <h2>「优先拾荒，无掉落物时才会选择攻击生物」怎么落地</h2>
 * 需求 3 的后半句在这个变体上其实不会发生——拾荒者<b>根本没有攻击目标</b>
 * （见 {@link Scavenger#registerGoals}），所以"无掉落物时"它只是在闲逛，不会去打人。
 * 前半句则靠优先级保证：本目标挂在 {@code scavenger.lootPriority}（默认 2），
 * 高于闲逛、低于逃跑（0）。所以无论有没有掉落物，它都不会去战斗。
 */
public class ScavengerLootGoal extends CollectLootGoal {

    private final Scavenger scavenger;

    public ScavengerLootGoal(Scavenger scavenger) {
        super(scavenger);
        this.scavenger = scavenger;
    }

    /** 需求 6：存活时间越长，拾荒获得的资源越多。 */
    @Override
    protected double adjustValue(double rawValue) {
        return rawValue * (1.0D + survivalBonus());
    }

    /** 交付走自己那条优先级链；没送出去的退回暂存（父类负责退）。 */
    @Override
    protected int deliver(ServerLevel level, int whole) {
        int used = ScavengerDelivery.deliver(scavenger, level, whole);
        return whole - used;
    }

    /** 捡东西是拾荒者的全部职能，所以不受"让普通真菌也捡东西"那个开关约束。 */
    @Override
    protected boolean collectingEnabled() {
        return true;
    }

    /**
     * 存活时间带来的倍率加成（不含基础的 1.0）。
     *
     * <p>线性爬升、到 {@code lootBonusMax} 封顶。用实体的 {@code tickCount} 而不是自己记时间：
     * 那就是"它活了多久"，不需要额外存盘。
     */
    private double survivalBonus() {
        double perMinute = SporeAddFungusConfig.scavengerLootBonusPerMinute();
        double max = SporeAddFungusConfig.scavengerLootBonusMax();
        return Math.min(max, scavenger.survivalMinutes() * perMinute);
    }
}
