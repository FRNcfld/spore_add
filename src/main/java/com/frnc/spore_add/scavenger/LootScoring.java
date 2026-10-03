package com.frnc.spore_add.scavenger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.fungus.LootValues;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 需求 4：同时探测到多处掉落物时，算一个<b>综合价值</b>挑最优的那一堆。
 *
 * <h2>先聚堆、再评分</h2>
 * 地上的一处战场会散着几十件掉落物。不聚堆的话，每一件都是一个独立候选，
 * "走过去能拿到多少"这件事就完全看不出来——评分会退化成"挑最贵的那一件"。
 * 所以先按 {@code lootClusterRadius} 立方格边长把它们聚成堆，一堆就是一个候选。
 *
 * <h2>六项因子</h2>
 * <pre>
 *   score = w距离   × 距离分
 *         + w到达   × 到达时间分
 *         + w数量   × 数量分
 *         + w类型   × 稀有度分
 *         + w效率   × 转化概率分
 *         + w危险   × 0            ← 权重键保留，当前恒为 0（算它要么不准、要么太贵）
 * </pre>
 *
 * <h2>归一化为什么全部用「绝对尺度」而不是「候选之间的相对排名」</h2>
 * 相对归一化（把候选集里每个因子缩放到 0~1）看着更"公平"，但有两处硬伤：
 * <ul>
 *   <li><b>只有一个候选时是 0/0</b>——而"只有一堆可捡"恰恰是最常见的情形；</li>
 *   <li>分数会随候选集抖动：旁边多掉一件腐肉，同一堆的分数就变了。</li>
 * </ul>
 * 所以距离与时间用<b>自身搜索半径</b>当尺度（那本来就是"最远能看多远"），
 * 数量与价值用<b>半饱和</b> {@code x/(x+半值)}——它单调、永远 &lt;1、且没有上界问题
 * （掉落物价值是没有上界的，整合包可以写一条价值 1000 的条目；硬除一个上限会让所有高价堆
 * 一律顶到满分、失去分辨力）。
 *
 * <h2>权重全配成 0 会怎样</h2>
 * 每一堆都得 0 分 → 全部并列 → 由并列时的"就近优先"决出结果，也就是退回成原来的
 * 「挑最近的」。所以"把权重调坏"不会退化成"不捡东西"。
 */
public final class LootScoring {

    private LootScoring() {
    }

    /**
     * 一堆掉落物。
     *
     * @param center        重心，用来算距离与到达时间
     * @param count         件数（把每件的堆叠数也算进去）
     * @param totalValue    预期资源总量：Σ(单价 × 数量)
     * @param maxUnitValue  堆里**单价最高**的那件——"这批东西稀不稀有"由它代表
     * @param weightedChance 按价值加权的平均转化概率，落在 (0,1]
     * @param members       成员，用来挑导航目标
     */
    private record Pile(Vec3 center, int count, double totalValue,
                        double maxUnitValue, double weightedChance, List<ItemEntity> members) {
    }

    /**
     * 从候选里挑出该去的那一件。
     *
     * @param searchRadius 自身的搜索半径，只用来给距离与时间定尺度
     * @return 选中那一堆里离拾荒者最近的一件；候选为空时返回 {@code null}
     */
    @Nullable
    public static ItemEntity choose(Scavenger scavenger, List<ItemEntity> candidates, double searchRadius) {
        if (candidates.isEmpty()) {
            return null;
        }
        Pile best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double bestDistance = Double.MAX_VALUE;

        for (Pile pile : cluster(candidates)) {
            double score = score(scavenger, pile, searchRadius);
            double distance = scavenger.distanceToSqr(pile.center());
            // 并列时取更近的。权重全配成 0 时所有 score 都是 0，于是这一步就是最终判据 ——
            // 等于自动退回"挑最近的"，不必为那种配置单写一个分支
            if (score > bestScore || (score == bestScore && distance < bestDistance)) {
                bestScore = score;
                bestDistance = distance;
                best = pile;
            }
        }
        return best == null ? null : nearestMember(scavenger, best);
    }

    // ------------------------------------------------------------------
    // 聚堆
    // ------------------------------------------------------------------

    /**
     * 按 {@code lootClusterRadius} 见方的三维格分组。
     *
     * <p><b>先排序再分组</b>，而不是塞进一个 HashMap：遍历顺序会决定并列时的胜负，
     * 而 HashMap 的顺序在不同 JVM/不同世界下不保证一致——那会让"同一堆东西，这次选它、
     * 下次选那件"这种无法复现的行为出现。排序键带上实体 id 是为了让同一格里的顺序也稳定。
     */
    private static List<Pile> cluster(List<ItemEntity> candidates) {
        double cell = SporeAddFungusConfig.lootClusterRadius();
        List<ItemEntity> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparingInt((ItemEntity item) -> cellIndex(item.getX(), cell))
                .thenComparingInt(item -> cellIndex(item.getY(), cell))
                .thenComparingInt(item -> cellIndex(item.getZ(), cell))
                .thenComparingInt(ItemEntity::getId));

        Map<String, List<ItemEntity>> grouped = new LinkedHashMap<>();
        for (ItemEntity item : sorted) {
            String key = cellIndex(item.getX(), cell) + "," + cellIndex(item.getY(), cell)
                    + "," + cellIndex(item.getZ(), cell);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }

        List<Pile> piles = new ArrayList<>(grouped.size());
        for (List<ItemEntity> members : grouped.values()) {
            piles.add(buildPile(members));
        }
        return piles;
    }

    private static int cellIndex(double coordinate, double cell) {
        return Mth.floor(coordinate / cell);
    }

    private static Pile buildPile(List<ItemEntity> members) {
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        int count = 0;
        double totalValue = 0.0D;
        double maxUnitValue = 0.0D;
        double weightedChance = 0.0D;

        for (ItemEntity item : members) {
            LootValues.Pricing pricing = LootValues.pricingOf(item.getItem());
            int stackSize = item.getItem().getCount();
            x += item.getX();
            y += item.getY();
            z += item.getZ();
            count += stackSize;
            totalValue += pricing.value() * stackSize;
            maxUnitValue = Math.max(maxUnitValue, pricing.value());
            weightedChance += pricing.value() * pricing.chance();
        }
        // 价值加权平均：一堆里贵的那几件对"这堆值不值得跑"的话语权更大
        double chance = totalValue > 0.0D ? weightedChance / totalValue : 0.0D;
        int size = members.size();
        return new Pile(new Vec3(x / size, y / size, z / size), count, totalValue,
                maxUnitValue, Mth.clamp(chance, 0.0D, 1.0D), members);
    }

    // ------------------------------------------------------------------
    // 评分
    // ------------------------------------------------------------------

    private static double score(Scavenger scavenger, Pile pile, double searchRadius) {
        Vec3 from = scavenger.position();
        double dx = pile.center().x - from.x;
        double dz = pile.center().z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        // 只罚向上：往下走不慢。这一项是"到达时间"与"距离"唯一的区别所在
        double climb = Math.max(0.0D, pile.center().y - from.y);
        double speed = travelSpeed(scavenger);
        double seconds = (horizontal + climb * SporeAddFungusConfig.lootMaxVerticalClimbFactor()) / speed;

        double distanceScore = 1.0D - Mth.clamp(horizontal / searchRadius, 0.0D, 1.0D);
        double arrivalScore = 1.0D - Mth.clamp(seconds / (searchRadius / speed), 0.0D, 1.0D);
        double countScore = saturate(pile.count(), SporeAddFungusConfig.lootCountHalf());
        double typeScore = saturate(pile.maxUnitValue(), SporeAddFungusConfig.lootValueHalf());

        // 「路径上的危险」：键在、权重在、算式也在，只是**取值恒为 0**。写成一整项而不是
        // 直接省略，是为了让"这一项被有意关掉了"看得出来——否则读代码的人会以为漏了一项。
        // 为什么不做：算它要么沿线采样（便宜，但看不见绕路）、要么真跑一次寻路（每个候选一次，太贵）。
        double dangerScore = 0.0D;

        return SporeAddFungusConfig.lootWeightDistance() * distanceScore
                + SporeAddFungusConfig.lootWeightArrival() * arrivalScore
                + SporeAddFungusConfig.lootWeightCount() * countScore
                + SporeAddFungusConfig.lootWeightType() * typeScore
                + SporeAddFungusConfig.lootWeightEfficiency() * pile.weightedChance()
                + SporeAddFungusConfig.lootWeightDanger() * dangerScore;
    }

    /** 半饱和：正好等于半值时得 0.5 分，再往上收益递减但永不封顶。 */
    private static double saturate(double value, double half) {
        double safe = Math.max(0.0D, value);
        return safe / (safe + half);
    }

    /**
     * 这只拾荒者每秒能走多远（格/秒）。
     *
     * <p>用 {@code getAttribute} 判空而不是 {@code getAttributeValue}：后者在属性缺失时
     * <b>抛异常</b>（本项目踩过一次）。移速属性理论上人人都有，但属性表是可以被别的模组改的。
     */
    private static double travelSpeed(Scavenger scavenger) {
        AttributeInstance instance = scavenger.getAttribute(Attributes.MOVEMENT_SPEED);
        double base = instance == null ? 0.1D : instance.getValue();
        // 乘上捡东西时用的那个移速倍率——它才是它实际走过去的速度
        return Math.max(0.05D, base * SporeAddFungusConfig.lootMoveSpeed());
    }

    private static ItemEntity nearestMember(Scavenger scavenger, Pile pile) {
        return pile.members().stream()
                .min(Comparator.comparingDouble(scavenger::distanceToSqr))
                .orElseThrow();
    }
}
