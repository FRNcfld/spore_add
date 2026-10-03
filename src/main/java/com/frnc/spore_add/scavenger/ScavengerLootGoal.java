package com.frnc.spore_add.scavenger;

import java.util.ArrayList;
import java.util.List;

import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.fungus.CollectLootGoal;
import com.frnc.spore_add.sound.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

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
     * 拾荒者用自己那个更大的搜索半径（{@code scavenger.lootRadius}，默认 32）。
     *
     * <p>不复用普通真菌的 {@code loot.radius}（默认 16）：那个键要顾着满地的普通真菌，
     * 半径翻倍 = 搜索盒体积翻四倍；而拾荒者世上最多十来只，放宽的代价可以忽略。
     * 见 {@code ScavengerWanderGoal}——那一项负责"闲逛时朝战利品走"，
     * 与本项（"捡的时候能看见多远"）是两件事。
     */
    @Override
    protected double searchRadius() {
        return SporeAddFungusConfig.scavengerLootRadius();
    }

    /**
     * 拾荒者的资源存储上限：随存活时间成长（见 {@code scavenger.storageBase} 那一组）。
     *
     * <p>普通真菌不限量（捡到就送，送不掉那点零头无所谓），而拾荒者会把资源揣在身上等着
     * 交给同伙——没有上限的话「存满了该去找队友」永远不会触发，第 5 条那套机制整个不成立。
     *
     * <p>公式收在 {@code SporeAddFungusConfig.scavengerStorageCap} 一处，因为还有两个读者：
     * 「该不该去找队友了」与「死后掉多少」。
     */
    @Override
    protected double storageCap() {
        return SporeAddFungusConfig.scavengerStorageCap(scavenger.survivalMinutes());
    }

    // ------------------------------------------------------------------
    // 链接侦察：把每个同伴变成一个额外的搜索圆心
    // ------------------------------------------------------------------

    /**
     * 每个链接对象提供一个搜索圆心（半径 {@code linkProbeRadius}）。
     *
     * <p>「一个链接对象 = 一个圆心」而不是"把半径按链接数放大"：圆心跟着同伴走，
     * 于是它探的是<b>同伴脚下那片地方</b>有没有东西，而不是"我周围更大的范围"。
     * 前者才是"通过同伴探测"的意思，后者只是把同一个圆心放大、成本还更高（面积是平方增长的）。
     */
    @Override
    protected List<SearchCenter> searchCenters() {
        List<SearchCenter> centers = new ArrayList<>();
        double probe = SporeAddFungusConfig.scavengerLinkProbeRadius();
        for (LivingEntity linked : ScavengerLinks.linked(scavenger)) {
            centers.add(new SearchCenter(linked.position(), probe));
        }
        return centers;
    }

    /**
     * 目标还在够得着的范围里吗。
     *
     * <p>比基类多一条：目标离<b>我的某个链接对象</b>够近也算数。没有这一条的话，
     * 它会因为"东西不在我自己身边"而当场丢掉一个从同伴那里听来、明明可以走过去的圆心。
     */
    @Override
    protected boolean isWithinReach(ItemEntity item) {
        double radius = searchRadius();
        if (scavenger.distanceToSqr(item) <= radius * radius) {
            return true;
        }
        double probe = SporeAddFungusConfig.scavengerLinkProbeRadius();
        for (LivingEntity linked : ScavengerLinks.linked(scavenger)) {
            if (linked.distanceToSqr(item) <= probe * probe) {
                return true;
            }
        }
        return false;
    }

    /**
     * 目标选择改成「聚堆 + 六因子综合评分」，而不是"挑最近的"。
     *
     * <p>只有它能这么挑：普通真菌的搜索圆心只有自己一个，选哪一件几乎等价于选哪个方向；
     * 而拾荒者手上有好几个分散的圆心，"去哪一堆"是真正需要权衡的决策。
     */
    @Override
    protected ItemEntity chooseTarget(List<ItemEntity> candidates) {
        return LootScoring.choose(scavenger, candidates, searchRadius());
    }

    /**
     * 靠近目标之后**奔袭**过去。
     *
     * <p>只在最后那一段加速（默认 8 格），所以观感是"走到跟前突然扑过去"，
     * 而不是全程飞奔。它乘在<b>已经算上存活成长</b>的移速之上——两者是相乘的。
     *
     * <p>加速的那一瞬间由基类负责重新寻路（移速是 {@code moveTo} 那一刻写死的），
     * 见 {@code CollectLootGoal#tick}。
     */
    @Override
    protected double moveSpeedFor(ItemEntity item) {
        double base = SporeAddFungusConfig.lootMoveSpeed();
        double radius = SporeAddFungusConfig.scavengerLootSprintRadius();
        if (radius <= 0.0D) {
            return base;   // 填 0 = 关掉奔袭
        }
        if (scavenger.distanceToSqr(item) > radius * radius) {
            return base;
        }
        return base * SporeAddFungusConfig.scavengerLootSprintSpeed();
    }

    /**
     * 收进兜里的那一刻响一声（字幕会显示「拾荒者捡拾」），并把饥饿清零。
     *
     * <p>音频是原版的拾取音（{@code random/pop}，音调略沉），见 {@code sounds.json}。
     *
     * <h2>为什么捡东西要顺带把饥饿清零</h2>
     * Spore 的饥饿是这么设计的：{@code canStarve()} 要求「进化点为 0」，而<b>击杀会把饥饿清零</b>
     * （{@code Infected#awardKillScore} 里那句 {@code setHunger(0)}）——也就是说，
     * 普通真菌靠<b>打猎</b>填饱自己。拾荒者永远不击杀、进化点也就永远停在 0，
     * 于是它是全阵营里唯一一个<b>只挨饿、没有任何填饱手段</b>的成员；
     * 一旦所在区域没有残骸可吃（它保留了 Spore 的 {@code InfectedConsumeFromRemains}，
     * 吃残骸会给 +1 进化点、从而关掉饥饿），它就会以每 4 秒 1 点的速度慢慢饿死。
     *
     * <p>让它<b>捡到东西就算吃到</b>，等于把「打猎」换成「觅食」——这与它的定位一致，
     * 也给了它一条与其它真菌对等的活路。判据放在这里而不是"掷中转化概率之后"：
     * 掉落物在那一刻已经被 {@code discard()} 掉了，**它确实吃下去了**，
     * 与那次转化有没有掷中概率无关。
     */
    @Override
    protected void onCollected() {
        scavenger.playSound(ModSounds.SCAVENGER_PICKUP.get());
        scavenger.setHunger(0);
    }

    /**
     * 存活时间带来的倍率加成（不含基础的 1.0）。
     *
     * <p>线性爬升、到 {@code lootBonusMax} 封顶。时间基准是 {@code Scavenger#survivalMinutes()}，
     * 也就是那只拾荒者<b>自己维护并存盘</b>的存活时长——不能用 {@code tickCount}，
     * 那个不写进存档，区块卸载一次就归零了（理由见 {@code Scavenger#KEY_SURVIVAL} 的注释）。
     */
    private double survivalBonus() {
        double perMinute = SporeAddFungusConfig.scavengerLootBonusPerMinute();
        double max = SporeAddFungusConfig.scavengerLootBonusMax();
        return Math.min(max, scavenger.survivalMinutes() * perMinute);
    }
}
