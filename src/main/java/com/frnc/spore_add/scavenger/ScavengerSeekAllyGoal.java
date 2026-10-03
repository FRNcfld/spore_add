package com.frnc.spore_add.scavenger;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.fungus.CollectLootGoal;
import com.frnc.spore_add.fungus.FungusCombat;
import com.frnc.spore_add.hatred.HatredValues;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * 需求 5 的后半句：<b>存储即将蓄满时，拾荒者会去寻找队友</b>。
 *
 * <h2>它只在「快满了」时才跑</h2>
 * 三个条件都满足才启动：存量达到上限的 {@code seekAllyThreshold}（默认 80%）、冷却已过、
 * 且附近找得到接收者。没到线就什么都不做——拾荒者继续捡它的东西，这正是需求里那句
 * 「若存储未满，则优先拾荒，不会主动寻找队友」。
 *
 * <p>所以这个目标<b>不会</b>常驻运行：绝大多数时候它的 {@code canUse} 在第一行就返回 false，
 * 连实体查询都不会发生（存量是实体持久数据里的一次 double 读）。
 *
 * <h2>找谁</h2>
 * 与 {@link ScavengerDelivery} 的交付链顺序<b>刻意一致</b>——先残血同伙、再心智：
 * <ol>
 *   <li><b>残血同伙</b>：{@code isFungus} + 等级够格 + 血量低于阈值，取血最少的那只。
 *       与交付链第二级同一套判据，所以走过去之后 {@code flush} 一定能把它治好，不会白跑。</li>
 *   <li><b>心智</b>：只有<b>已链接</b>时才认——交付链第一级的入口条件就是 {@code getLinked()}，
 *       没链接的话走到心智面前也送不进去。</li>
 * </ol>
 * 两边都没有就返回 false，并起一次冷却，避免每 tick 重扫一遍半径内的实体。
 *
 * <h2>为什么找不到人时不就地折算进化点</h2>
 * 需求写的是「存满 + 找不到队友」才兜底折算进化点，而"找不到队友"这件事该由
 * {@link ScavengerDelivery} 的第四级在<b>下一次资源进来时</b>处理——那时才有明确的
 * "这一笔存不下"的量。在这里顺手折算的话，存量会在没人可给的世界里被悄悄清空，
 * 而它本来会在拾荒者死掉时作为战利品掉给玩家（见 {@code Scavenger#die}）。
 */
public class ScavengerSeekAllyGoal extends Goal {

    /**
     * 一趟"去找队友"最多走多久（tick），默认 30 秒。
     *
     * <p><b>这一条是防卡死的</b>：导航失败（接收者隔着墙、在洞里、在另一个区块）时
     * {@code canContinueToUse} 的那些条件仍然全部为真，而本目标的优先级<b>高于拾荒</b>——
     * 没有超时的话，拾荒者会永远站在原地"试图"走过去，等于<b>永久停止捡东西</b>。
     * 超时之后放弃、起冷却，回去捡东西，下一轮再试。
     *
     * <p>刻意不做成配置键：这是正确性下限，不是一个手感旋钮。
     */
    private static final int SEEK_TIMEOUT_TICKS = 600;

    private final Scavenger scavenger;

    /** 下一次允许"去找队友"的时刻。找不到接收者时也会推它，避免每 tick 重扫。 */
    private int nextSeekTick;

    /** 这一趟的放弃时刻，见 {@link #SEEK_TIMEOUT_TICKS}。 */
    private int deadlineTick;

    @Nullable
    private LivingEntity receiver;

    public ScavengerSeekAllyGoal(Scavenger scavenger) {
        this.scavenger = scavenger;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (scavenger.tickCount < nextSeekTick) {
            return false;
        }
        if (!isStoreNearlyFull()) {
            return false;
        }
        receiver = findReceiver();
        if (receiver == null) {
            // 没人可给：起冷却。不然它会每 tick 重扫一次半径 64 的实体盒
            startCooldown();
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        deadlineTick = scavenger.tickCount + SEEK_TIMEOUT_TICKS;
        moveToReceiver();
    }

    @Override
    public boolean canContinueToUse() {
        // 走太久没到就放弃（原因见 SEEK_TIMEOUT_TICKS）。这一条必须放在最前面：
        // 它是唯一能兜住"导航失败"的出口，而导航失败时下面每条判据都仍然成立。
        if (scavenger.tickCount > deadlineTick) {
            startCooldown();
            return false;
        }
        if (receiver == null || receiver.isRemoved() || !receiver.isAlive()) {
            return false;
        }
        // 已经倒空了（或者上限被配置调小了）就没必要再走
        if (!isStoreNearlyFull()) {
            return false;
        }
        // 接收者自己跑远了：放弃这一趟，下次重挑。用 1.5 倍半径当阈值，
        // 免得它贴着边缘反复"追—放弃—追"
        double limit = SporeAddFungusConfig.scavengerSeekAllyRadius() * 1.5D;
        return scavenger.distanceToSqr(receiver) <= limit * limit;
    }

    @Override
    public void tick() {
        if (receiver == null) {
            return;
        }
        scavenger.getLookControl().setLookAt(receiver, 30.0F, 30.0F);

        double delivery = SporeAddFungusConfig.scavengerDeliveryRadius();
        if (scavenger.distanceToSqr(receiver) <= delivery * delivery) {
            flushToReceiver();
            return;
        }
        // 目标会走，所以定期重寻路——与拾荒目标里那条"定期重算"同一个理由
        if (scavenger.tickCount % 20 == 0) {
            moveToReceiver();
        }
    }

    @Override
    public void stop() {
        receiver = null;
        scavenger.getNavigation().stop();
    }

    // ------------------------------------------------------------------

    /** 到位了：把整笔存量推出去。 */
    private void flushToReceiver() {
        if (scavenger.level() instanceof ServerLevel level) {
            ScavengerDelivery.flush(scavenger, level);
        }
        // 倒空之后也起冷却，免得它刚给完东西又立刻判定"该去找人"，
        // 而那时存量多半已经见底、白跑一趟
        startCooldown();
        receiver = null;
    }

    private void startCooldown() {
        nextSeekTick = scavenger.tickCount + SporeAddFungusConfig.scavengerSeekAllyCooldownTicks();
    }

    private void moveToReceiver() {
        if (receiver != null) {
            scavenger.getNavigation().moveTo(receiver, SporeAddFungusConfig.scavengerFleeSpeed());
        }
    }

    /** 存量达到上限的 {@code seekAllyThreshold} 比例了吗。 */
    private boolean isStoreNearlyFull() {
        double cap = SporeAddFungusConfig.scavengerStorageCap(scavenger.survivalMinutes());
        if (cap <= 0.0D) {
            return false;   // 上限配成 0 = 根本不存东西，也就没有"存满了"这回事
        }
        double stored = CollectLootGoal.pendingResource(scavenger);
        return stored >= cap * SporeAddFungusConfig.scavengerSeekAllyThreshold();
    }

    /**
     * 找最近的接收者：先残血同伙、再心智。都没有返回 {@code null}。
     *
     * <p>顺序与 {@link ScavengerDelivery} 的交付链一致，所以走到跟前 {@code flush} 必然能用上。
     */
    @Nullable
    private LivingEntity findReceiver() {
        double radius = SporeAddFungusConfig.scavengerSeekAllyRadius();
        AABB area = scavenger.getBoundingBox().inflate(radius);
        List<LivingEntity> candidates = scavenger.level().getEntitiesOfClass(LivingEntity.class, area);

        int minTier = SporeAddFungusConfig.scavengerHealMinTierOrdinal();
        float woundedFraction = (float) SporeAddFungusConfig.scavengerHealWoundedFraction();
        LivingEntity wounded = candidates.stream()
                .filter(candidate -> candidate != scavenger && !candidate.isRemoved() && candidate.isAlive())
                .filter(FungusCombat::isFungus)
                .filter(candidate -> HatredValues.tierOf(candidate).ordinal() >= minTier)
                .filter(candidate -> candidate.getHealth() < candidate.getMaxHealth() * woundedFraction)
                // 血最少的优先——与交付链那边同一个排序，走得最远的那一趟要救最危急的
                .min(Comparator.comparingDouble(candidate -> candidate.getHealth() / candidate.getMaxHealth()))
                .orElse(null);
        if (wounded != null) {
            return wounded;
        }

        // 没链接就不必去找心智：交付链第一级的入口条件就是 getLinked()
        if (!scavenger.getLinked()) {
            return null;
        }
        return candidates.stream()
                .filter(SporeCompat::isHivemind)
                .filter(candidate -> !candidate.isRemoved() && candidate.isAlive())
                .min(Comparator.comparingDouble(scavenger::distanceToSqr))
                .orElse(null);
    }
}
