package com.frnc.spore_add.scavenger;

import java.util.Comparator;
import java.util.List;

import com.frnc.spore_add.SporeAddDebugConfig.Area;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.debug.SporeAddDebug;
import com.frnc.spore_add.fungus.CollectLootGoal;
import com.frnc.spore_add.fungus.FungusCombat;
import com.frnc.spore_add.hatred.HatredValues;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * 需求 4：拾荒者把收获交给谁。按<b>优先级链</b>判一次，命中即停。
 *
 * <h2>四级链，顺序不能换</h2>
 * <ol>
 *   <li><b>已与心智链接</b> → 整笔交给最近的<b>心智</b>（Spore 的 biomass）。
 *       链接状态是 Spore 自己给的：{@code Proto#scanForHosts} 会给范围内的每个感染体置
 *       {@code linked}。附近没有同维度心智时它会把整笔还回来，自然落到下一级。</li>
 *   <li><b>否则，若附近有够格又残血的同伙</b> → 折算成<b>回血</b>。
 *       够格 = 等级不低于 {@code healMinTier}，残血 = 血量低于 {@code healWoundedFraction}。
 *       按血量比例最低的优先，最多同时治 {@code healTargetCount} 个（这个数随存活时间从
 *       {@code healMinTargets} 涨到 {@code healMaxTargets}）。</li>
 *   <li><b>否则，若身上还揣得下</b> → <b>先存着</b>，这一档<b>不消耗任何资源</b>。
 *       这是常态出口：以前"存着"只是前两级都没命中的副作用，现在它是显式的一级。
 *       之所以要显式，是因为「存满了该去找队友」这个行为需要一个明确的"没满"状态——
 *       而旧实现的第 3 级（折算进化点）几乎总能成功，资源根本留不住。</li>
 *   <li><b>连存都存不下了</b> → 兜底折算成<b>进化点</b>分给附近的真菌（用 Spore 自己的
 *       {@code setEvoPoints}/{@code setKills}）。拾荒者不参战、拿不到杀戮点，
 *       但它用捡来的东西替同伙攒进化——这就是它的核心价值。</li>
 * </ol>
 *
 * <h2>「没送出去就退回来」</h2>
 * 本方法<b>返回实际用掉的资源数</b>，调用方把差额留回拾荒者身上。第 3 档与第 4 档
 * （凑不够 1 个进化点、或附近没有能进化的同伙）都会让一部分资源退回去，等下次凑够了再送。
 * 不退的话，一只孤零零的拾荒者会把每一笔收获都变成零头丢掉。
 *
 * <p><b>注意第 3 档的返回值语义</b>：它把 {@code left} 归零表示"这一笔不用再找下家了"，
 * 而不是"用掉了"——所以那一部分不计入返回值，最终由调用方写回暂存。那正是"存在它身上"
 * 的实现方式（载体就是 {@code CollectLootGoal} 的那个暂存累加器，没有第三个账本）。
 *
 * <h2>为什么不把剩余资源推进世界暂存</h2>
 * 旧实现在"链接了心智但附近没心智"时会把整笔塞进 {@code HatredData.pending}（世界级、
 * 无上限、所有生产者共用）。那会让"拾荒者自己攒的一小堆"变成全服共享、永不丢失的钱包，
 * 与"存满了要找队友"正好冲突：永远不会满，也就永远不用找队友。
 */
public final class ScavengerDelivery {

    /** 进化点的小数累加器存在哪。与冻伤层数、寒冷饥饿用的是同一套"存 ForgeData 防重启错乱"的思路。 */
    private static final String KEY_EVO_CREDIT = "spore_add:scavenger_evo_credit";

    private ScavengerDelivery() {
    }

    /**
     * 把 {@code amount} 点资源送出去。四级链，命中哪一级就在哪一级停。
     *
     * @return 实际用掉的资源数；调用方要把差额退还给拾荒者（见类注释）
     */
    public static int deliver(Scavenger scavenger, ServerLevel level, int amount) {
        return deliver(scavenger, level, amount, true);
    }

    /**
     * 把身上存着的资源整笔推出去——「专程去找队友」到位时调这个。
     *
     * <p>与 {@link #deliver} 的唯一区别是<b>不许再存回去</b>：那个方法在「揣得下」时会把资源
     * 原样留着（那是常态），而这里人都已经走到接收者面前了，再留着就等于白跑一趟。
     *
     * @return 实际送出去的量
     */
    public static int flush(Scavenger scavenger, ServerLevel level) {
        double stored = CollectLootGoal.pendingResource(scavenger);
        int whole = Mth.floor(stored);
        if (whole <= 0) {
            return 0;
        }
        int used = deliver(scavenger, level, whole, false);
        CollectLootGoal.setPendingResource(scavenger, stored - used);
        return used;
    }

    /**
     * @param allowStorage 允许走「先存着」那一级吗。{@code true} = 常态（捡到东西时），
     *                     {@code false} = 专程去交付（见 {@link #flush}）
     */
    private static int deliver(Scavenger scavenger, ServerLevel level, int amount, boolean allowStorage) {
        if (amount <= 0) {
            return 0;
        }
        int left = amount;
        int used = 0;
        int toHivemind = 0;
        int healed = 0;
        int kept = 0;

        // 一、隶属于某个心智 → 交给最近的那个心智
        // 注意它附近没心智时会把整笔原样还回来，于是自然落到下一级
        if (scavenger.getLinked()) {
            int leftover = SporeCompat.grantResourcesToNearestHivemind(level, scavenger.position(), left);
            toHivemind = left - leftover;
            used += toHivemind;
            left = leftover;
        }

        // 二、有够格又残血的同伙 → 折算回血
        // 治疗优先于「存着」：同伙正在流血，而存着只是换个地方放着
        if (left > 0 && healWoundedAllies(scavenger, level, left)) {
            healed = left;
            used += left;
            left = 0;
        }

        // 三、先存着。【这一档不消耗任何资源】——left 归零表示"这一笔不用再找下家了"，
        // 而不是"用掉了"。它最终会由调用方写回暂存，那正是"存在它身上"的实现方式。
        if (allowStorage && left > 0 && hasStorageRoom(scavenger, left)) {
            kept = left;
            left = 0;
        }

        // 四、连存都存不下了，才兜底折算进化点
        int evo = 0;
        if (left > 0) {
            evo = grantEvoPoints(scavenger, level, left);
            used += evo;
        }

        // 四级链各走了多少，是「拾荒者捡的货到底去哪了」的唯一答案：
        // 全都进"存着"说明它附近既没心智也没残血同伙——那正是要它去找队友的状态。
        SporeAddDebug.log(Area.SCAVENGER, "交付 {}（链接={}）：给心智 {}、治疗同伙 {}、存着 {}、折算进化点 {}",
                amount, scavenger.getLinked(), toHivemind, healed, kept, evo);
        return used;
    }

    /**
     * 身上还揣得下这么多资源吗。
     *
     * <p>这是个<b>启发式</b>判断，不是硬保证：真正的上限由 {@code CollectLootGoal#collect}
     * 在写回暂存时夹住。这里问它只是为了决定"要不要退而求其次去换进化点"——
     * 差个一格半格无所谓，反正越界的那部分会被丢掉。
     */
    private static boolean hasStorageRoom(Scavenger scavenger, int amount) {
        double cap = SporeAddFungusConfig.scavengerStorageCap(scavenger.survivalMinutes());
        return CollectLootGoal.pendingResource(scavenger) + amount <= cap;
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
