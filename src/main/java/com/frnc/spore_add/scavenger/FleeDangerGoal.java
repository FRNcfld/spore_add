package com.frnc.spore_add.scavenger;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.fungus.FungusCombat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 需求 5：拾荒者躲避危险（<b>第一要务</b>），血量不足时会逃跑（即使面对玩家）。
 *
 * <h2>「第一要务」在代码里是什么</h2>
 * 它挂在优先级 {@link Scavenger#FLEE_PRIORITY}（0），比拾荒（默认 2）与所有闲逛都高。
 * 而这个目标与它们<b>争同一个 {@code MOVE} 标志</b>——目标选择器的规矩是"优先级更高的
 * 一个在跑时，低优先级的根本起不来"。所以只要它在跑，拾荒者就只会跑，不会停下来捡东西。
 * 这就是"第一要务"的实现方式，不需要在别处再判一次。
 *
 * <h2>什么时候跑</h2>
 * 两种情况，任一成立就跑：
 * <ol>
 *   <li><b>察觉到了威胁</b>：{@code fleeThreatRadius} 内有玩家、或有能攻击它的生物。
 *       「能不能攻击我」交给原版的 {@code Mob#canAttack} 判，所以躲在墙后、隔着水的目标
 *       不会被误判成威胁——与普通生物的行为一致。</li>
 *   <li><b>血量低于 {@code fleeHealthFraction}</b>：<b>无条件</b>跑。
 *       这一条正是需求里那句"即使面对玩家"——残血时不再问对方是谁、也不问自己打不打得过，
 *       直接撤。同阵营的生物不算威胁，但如果它已经残血，仍然会跑（躲开战场）。</li>
 * </ol>
 *
 * <h2>往哪跑</h2>
 * 用原版的 {@link DefaultRandomPos#getPosAway}——它专门算"背离某个位置、且落脚点可行"的点，
 * 正是逃跑要的语义（自己手写一个方向再找落脚点是重复造轮子，而且容易把怪跑进墙里）。
 * 找不到可行点就退回 {@link DefaultRandomPos#getPos} 随便找个地方，总比站着不动好。
 */
public class FleeDangerGoal extends Goal {

    private final Scavenger scavenger;

    @Nullable
    private LivingEntity threat;

    private double targetX;
    private double targetY;
    private double targetZ;

    public FleeDangerGoal(Scavenger scavenger) {
        this.scavenger = scavenger;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (isWounded()) {
            // 残血：无条件跑。有威胁就背着它跑，没有就随便找个方向躲开
            threat = findNearestThreat();
            return pickFleeTarget();
        }
        threat = findNearestThreat();
        if (threat == null) {
            return false;
        }
        return pickFleeTarget();
    }

    @Override
    public boolean canContinueToUse() {
        return !scavenger.getNavigation().isDone();
    }

    @Override
    public void start() {
        scavenger.getNavigation().moveTo(targetX, targetY, targetZ, SporeAddFungusConfig.scavengerFleeSpeed());
    }

    @Override
    public void stop() {
        threat = null;
        scavenger.getNavigation().stop();
    }

    // ------------------------------------------------------------------

    /** 血量低于配置的比例。 */
    private boolean isWounded() {
        double fraction = SporeAddFungusConfig.scavengerFleeHealthFraction();
        return fraction > 0.0D && scavenger.getHealth() < scavenger.getMaxHealth() * (float) fraction;
    }

    /**
     * 找最近的威胁；没有就返回 {@code null}。
     *
     * <p><b>同阵营不算威胁</b>：真菌之间不打自己人，把同伴当成危险会让拾荒者在感染区里
     * 永远处于逃跑状态，那就什么都干不成了。
     */
    @Nullable
    private LivingEntity findNearestThreat() {
        double radius = SporeAddFungusConfig.scavengerFleeThreatRadius();
        AABB area = scavenger.getBoundingBox().inflate(radius);
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : scavenger.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (candidate == scavenger || !isThreat(candidate)) {
                continue;
            }
            double distance = scavenger.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /**
     * 这东西算不算威胁。
     *
     * <p>玩家一律算（旁观与创造模式不算——它们打不到你，对着躲是白费）。
     * 其它生物交给 {@code Mob#canAttack}，也就是"原版认为它能不能攻击我"，
     * 这样保护动物、同队成员、和平难度这些情况都自动处理好了。
     */
    private boolean isThreat(LivingEntity candidate) {
        if (candidate.isRemoved() || candidate.isDeadOrDying() || candidate.isSpectator()) {
            return false;
        }
        if (FungusCombat.isFungus(candidate)) {
            return false;
        }
        if (candidate instanceof Player player) {
            return !player.isCreative();
        }
        return candidate instanceof Mob mob && mob.canAttack(scavenger);
    }

    /** 选一个逃跑落点。选不到就返回 false（这一拍不跑）。 */
    private boolean pickFleeTarget() {
        Vec3 away = threat == null
                ? null
                : DefaultRandomPos.getPosAway(scavenger, 16, 7, threat.position());
        Vec3 pos = away != null ? away : DefaultRandomPos.getPos(scavenger, 16, 7);
        if (pos == null) {
            return false;
        }
        targetX = pos.x;
        targetY = pos.y;
        targetZ = pos.z;
        return true;
    }
}
