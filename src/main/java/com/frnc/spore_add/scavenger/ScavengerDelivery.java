package com.frnc.spore_add.scavenger;

import java.util.Comparator;
import java.util.List;

import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.fungus.FungusCombat;
import com.frnc.spore_add.hatred.HatredData;
import com.frnc.spore_add.hatred.HatredValues;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * 需求 4：拾荒者把收获交给谁。按<b>优先级链</b>判一次，命中即停。
 *
 * <h2>三条路，顺序不能换</h2>
 * <ol>
 *   <li><b>已与心智链接</b> → 整笔交给最近的<b>心智</b>（Spore 的 biomass）。
 *       链接状态是 Spore 自己给的：{@code Proto#scanForHosts} 会给范围内的每个感染体置
 *       {@code linked}，所以"附近有心智"这件事不需要我们判断——白送。</li>
 *   <li><b>否则，若附近有够格又残血的同伙</b> → 折算成<b>回血</b>。
 *       够格 = 等级不低于 {@code healMinTier}，残血 = 血量低于 {@code healWoundedFraction}。
 *       按血量比例最低的优先，最多同时治 {@code healTargetCount} 个（这个数随存活时间从
 *       {@code healMinTargets} 涨到 {@code healMaxTargets}）。</li>
 *   <li><b>否则</b> → 折算成<b>进化点</b>分给附近的真菌（用 Spore 自己的
 *       {@code setEvoPoints}/{@code setKills}，也就是 {@code awardKillScore} 那套状态）。
 *       拾荒者不参战、拿不到杀戮点，但它用捡来的东西替同伙攒进化——这就是它的核心价值。</li>
 * </ol>
 *
 * <h2>「没送出去就退回来」</h2>
 * 本方法<b>返回实际用掉的资源数</b>。第 3 条路上可能凑不够 1 个进化点（换算速率默认 0.1，
 * 也就是手里不足 10 点资源时），这时返回 0、调用方把资源原样留在拾荒者身上，等下次捡到东西
 * 凑够了再送。不退的话，一只孤零零的拾荒者会把每一笔收获都变成"不到 1 个进化点"的零头丢掉。
 */
public final class ScavengerDelivery {

    /** 进化点的小数累加器存在哪。与冻伤层数、寒冷饥饿用的是同一套"存 ForgeData 防重启错乱"的思路。 */
    private static final String KEY_EVO_CREDIT = "spore_add:scavenger_evo_credit";

    private ScavengerDelivery() {
    }

    /**
     * 把 {@code amount} 点资源送出去。
     *
     * @return 实际用掉的资源数；调用方要把差额退还给拾荒者（见类注释）
     */
    public static int deliver(Scavenger scavenger, ServerLevel level, int amount) {
        if (amount <= 0) {
            return 0;
        }
        // 一、链接了心智 → 整笔给它
        if (scavenger.getLinked()) {
            int leftover = SporeCompat.grantResourcesToNearestHivemind(level, scavenger.position(), amount);
            if (leftover > 0) {
                // 附近没有同维度的心智：先存起来，等有心智了再补发（复用已有的暂存区）
                HatredData.get(level).addPending(leftover);
            }
            return amount;
        }

        // 二、有够格又残血的同伙 → 折算回血
        if (healWoundedAllies(scavenger, level, amount)) {
            return amount;
        }

        // 三、折算进化点分给附近真菌
        return grantEvoPoints(scavenger, level, amount);
    }

    // ------------------------------------------------------------------
    // 第二条路：治疗
    // ------------------------------------------------------------------

    /**
     * 把资源折算成血，治给附近最该救的几个同伙。
     *
     * @return 是否真的治了人（没人可治时返回 false，让调用方往第三条路走）
     */
    private static boolean healWoundedAllies(Scavenger scavenger, ServerLevel level, int amount) {
        int maxTargets = healTargetCount(scavenger);
        if (maxTargets <= 0) {
            return false;
        }
        double woundedFraction = SporeAddFungusConfig.scavengerHealWoundedFraction();
        int minTier = SporeAddFungusConfig.scavengerHealMinTierOrdinal();

        AABB area = scavenger.getBoundingBox().inflate(SporeAddFungusConfig.scavengerDeliveryRadius());
        List<LivingEntity> wounded = level.getEntitiesOfClass(LivingEntity.class, area).stream()
                .filter(candidate -> candidate != scavenger && !candidate.isRemoved() && candidate.isAlive())
                .filter(FungusCombat::isFungus)
                .filter(candidate -> HatredValues.tierOf(candidate).ordinal() >= minTier)
                .filter(candidate -> candidate.getHealth() < candidate.getMaxHealth() * (float) woundedFraction)
                // 血量**比例**最低的优先，不是绝对血量最低的——一只 100 血剩 30 的进化体
                // 比一只 20 血剩 15 的基础感染体更危急
                .sorted(Comparator.comparingDouble(
                        candidate -> candidate.getHealth() / candidate.getMaxHealth()))
                .limit(maxTargets)
                .toList();
        if (wounded.isEmpty()) {
            return false;
        }

        // 总量均分。这里不做"按缺口比例分配"：均分的结果更可预期，
        // 而且"血最少的那几个"已经由上面的排序挑出来了，均分不会浪费在满血目标上
        float perTarget = (float) (amount * SporeAddFungusConfig.scavengerHealthPerResource() / wounded.size());
        for (LivingEntity target : wounded) {
            target.heal(perTarget);
        }
        return true;
    }

    /**
     * 这一次最多能同时治几个。
     *
     * <p>需求：默认 2~5 个，随存活时间增长。线性爬升到 {@code healRampMinutes} 封顶。
     */
    private static int healTargetCount(Scavenger scavenger) {
        int min = SporeAddFungusConfig.scavengerHealMinTargets();
        int max = SporeAddFungusConfig.scavengerHealMaxTargets();
        if (max <= min) {
            return min;
        }
        double progress = Math.min(1.0D, scavenger.survivalMinutes() / SporeAddFungusConfig.scavengerHealRampMinutes());
        return (int) Math.round(min + (max - min) * progress);
    }

    // ------------------------------------------------------------------
    // 第三条路：进化点
    // ------------------------------------------------------------------

    /**
     * 把资源折算成进化点，分给附近的真菌。
     *
     * <h2>小数累加器</h2>
     * 默认换算率是 0.1（10 点资源 = 1 进化点），所以绝大多数单次收获都不够一个整点。
     * 不足的部分存在拾荒者身上累计，不是丢掉——否则每捡一块腐肉都白费。
     * 只有在<b>真的发出去了整数点</b>的那一刻才把零头记账，避免"发出 0 点却先把零头算进账"
     * 导致调用方退还资源时重复计数。
     *
     * <h2>怎么分</h2>
     * 进化点是整数，没法按小数均分，所以<b>轮着来</b>：从血最少/最近的同伙开始逐个 +1 点。
     * 这样单调也会让"哪只先攒够进化"这件事偏向更需要的那几只，而不是全喂给同一个。
     *
     * @return 实际用掉的资源数（凑不够整点时为 0）
     */
    private static int grantEvoPoints(Scavenger scavenger, ServerLevel level, int amount) {
        double rate = SporeAddFungusConfig.scavengerEvoPointsPerResource();
        if (rate <= 0.0D) {
            return 0;
        }
        CompoundTag data = scavenger.getPersistentData();
        double credit = data.getDouble(KEY_EVO_CREDIT) + amount * rate;
        int wholePoints = Mth.floor(credit);
        if (wholePoints <= 0) {
            // 凑不够一个整点：什么都不记，让调用方把资源原样退回来，下次再来
            return 0;
        }

        AABB area = scavenger.getBoundingBox().inflate(SporeAddFungusConfig.scavengerDeliveryRadius());
        List<com.Harbinger.Spore.Sentities.BaseEntities.Infected> allies = level
                .getEntitiesOfClass(com.Harbinger.Spore.Sentities.BaseEntities.Infected.class, area).stream()
                .filter(ally -> ally != scavenger && !ally.isRemoved() && ally.isAlive())
                // 不再喂拾荒者自己：它反正也不会进化（见 Scavenger#tickEvolution），喂了纯属浪费
                .filter(ally -> !(ally instanceof Scavenger))
                .toList();
        if (allies.isEmpty()) {
            // 附近没有能进化的同伙：同样不记账，资源退回去等下次
            return 0;
        }

        for (int i = 0; i < wholePoints; i++) {
            var ally = allies.get(i % allies.size());
            ally.setEvoPoints(ally.getEvoPoints() + 1);
            ally.setKills(ally.getKills() + 1);
        }
        // 真的发出去了，才把零头记上账
        data.putDouble(KEY_EVO_CREDIT, credit - wholePoints);
        return amount;
    }
}
