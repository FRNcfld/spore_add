package com.frnc.spore_add.scavenger;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.frnc.spore_add.SporeAddDebugConfig.Area;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.debug.SporeAddDebug;
import com.frnc.spore_add.fungus.FungusCombat;
import com.frnc.spore_add.sound.ModSounds;

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
 * 它挂在 {@code scavenger.fleePriority}（默认 0），比拾荒（默认 2）与所有闲逛都高。
 * 而这个目标与它们<b>争同一个 {@code MOVE} 标志</b>——目标选择器的规矩是"优先级更高的
 * 一个在跑时，低优先级的根本起不来"。所以只要它在跑，拾荒者就只会跑，不会停下来捡东西。
 * 这就是"第一要务"的实现方式，不需要在别处再判一次。
 *
 * <h2>什么时候跑</h2>
 * 两种情况，任一成立就跑：
 * <ol>
 *   <li><b>被足够强的威胁盯上了</b>：见下面那两节。</li>
 *   <li><b>血量低于 {@code fleeHealthFraction}</b>：<b>无条件</b>跑。
 *       这一条正是需求里那句"即使面对玩家"——残血时不再问对方是谁、也不问自己打不打得过，
 *       直接撤。同阵营的生物不算威胁，但如果它已经残血，仍然会跑（躲开战场）。</li>
 * </ol>
 *
 * <h2>「威胁」的第一道闸门：它得真的盯上我</h2>
 * 以前是「16 格内任何 {@code canAttack(我)} 的生物」就算威胁，<b>完全不看它是不是冲我来的</b>。
 * 而拾荒者不还手、也没有 Spore 的 {@code HurtTargetGoal}，所以它只会跑——怪物一多就光顾着跑，
 * 什么也捡不到。现在要求下面任一条成立：
 * <ul>
 *   <li>{@code Mob#getTarget() == 自己}（它正冲我来）；</li>
 *   <li>它是 {@code getLastHurtByMob()}，且那次挨打还在 {@code fleeAggroMemoryTicks} 之内
 *       （它最近打过我）。<b>这是玩家唯一的入口</b>——玩家没有 {@code getTarget()}，
 *       所以「玩家在打我」只能靠记仇来认。</li>
 * </ul>
 * 创造模式的玩家排除在外：跑不掉也没意义（他们能飞）。
 *
 * <p><b>一个反直觉但正确的后果</b>：原版僵尸、骷髅这类敌对生物的目标选择里<b>不含"怪物"</b>，
 * 所以它们根本不会把拾荒者当目标——一群僵尸围着它也<b>不会</b>让它逃跑。
 * 真正会让它跑的只有：铁傀儡这类会打怪物的生物、别的模组里"攻击一切"的怪、以及打过它的玩家。
 *
 * <h2>「威胁」的第二道闸门：算得出危险</h2>
 * 光被盯上还不够——一只弱鸡僵尸盯着它就跑的话，阈值等于没有。所以把所有锁定者的输出加起来，
 * 换算成「我还能活多久」：
 * <pre>
 *   预计存活秒数 = 我方有效生命 ÷ Σ 锁定者的每秒输出
 * </pre>
 * 低于 {@code fleeSurviveSeconds} 才跑。有效生命用 {@link FungusCombat#effectiveHealth}，
 * 它把存活成长带来的血量与护甲一起算进去了——于是「活得越久越沉得住气」是<b>免费</b>的，
 * 不需要再为逃跑单开一条成长曲线。
 *
 * <h2>往哪跑</h2>
 * 用原版的 {@link DefaultRandomPos#getPosAway}——它专门算"背离某个位置、且落脚点可行"的点，
 * 正是逃跑要的语义（自己手写一个方向再找落脚点是重复造轮子，而且容易把怪跑进墙里）。
 * 找不到可行点就退回 {@link DefaultRandomPos#getPos} 随便找个地方，总比站着不动好。
 * 方向锚点是<b>最近的那个锁定者</b>；一个锁定者都没有时（残血那一支）就随机找个方向。
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
        // 一次扫描同时拿到"总输出"与"最近那一只"：分两趟扫就是两倍的实体盒查询
        Threat assessed = assess();
        threat = assessed.nearest();

        // 残血：无条件跑。有锁定者就背着它跑，没有就随便找个方向躲开。
        // 非残血：只有"锁定我的那些能在阈值秒数内打死我"才跑。
        // 两个条件写在一起是因为下面的日志要一次说清"跑/不跑"与理由，
        // 而求值顺序与原来的两段式完全一致（isOutmatched 只在没残血且有人锁定时才算）。
        boolean wounded = isWounded();
        boolean outmatched = !wounded && threat != null && isOutmatched(assessed.totalDps());

        // 本方法是**逐 tick** 被目标选择器调到的（每只拾荒者每 tick 一次），
        // 所以用 on() 守卫而不是直接 log()：参数里的 getHealth / 取配置都有代价，
        // 不先挡一下的话，关着开关也照样付。
        if (wounded || outmatched) {
            if (SporeAddDebug.on(Area.SCAVENGER)) {
                SporeAddDebug.log(Area.SCAVENGER, "逃跑（{}）：锁定者 {}、总输出 {}、血量 {}/{}",
                        wounded ? "残血" : "打不过", threat, assessed.totalDps(),
                        scavenger.getHealth(), scavenger.getMaxHealth());
            }
            return pickFleeTarget();
        }
        if (SporeAddDebug.on(Area.SCAVENGER)) {
            SporeAddDebug.log(Area.SCAVENGER, "不逃（{}）：锁定者 {}、总输出 {}、血量 {}/{}、阈值 {} 秒",
                    threat == null ? "无人锁定" : "打得过", threat, assessed.totalDps(),
                    scavenger.getHealth(), scavenger.getMaxHealth(),
                    SporeAddFungusConfig.scavengerFleeSurviveSeconds());
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return !scavenger.getNavigation().isDone();
    }

    @Override
    public void start() {
        scavenger.getNavigation().moveTo(targetX, targetY, targetZ, SporeAddFungusConfig.scavengerFleeSpeed());
        // 逃跑时喊一声：字幕会显示「拾荒者逃跑」，音调比它平时的低吼高（见 sounds.json）
        scavenger.playSound(ModSounds.SCAVENGER_FLEE.get());
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
     * 一次扫描的结果：锁定者们的总输出，以及其中最近的那一只（用来定逃跑方向）。
     *
     * @param totalDps 所有锁定者的每秒输出之和；一个都没有时为 0
     * @param nearest  最近的那个锁定者；一个都没有时为 {@code null}
     */
    private record Threat(double totalDps, @Nullable LivingEntity nearest) {
    }

    /**
     * 扫一圈，汇总所有「盯上我的」生物。
     *
     * <p><b>同阵营不算威胁</b>：真菌之间不打自己人，把同伴当成危险会让拾荒者在感染区里
     * 永远处于逃跑状态，那就什么都干不成了。
     */
    private Threat assess() {
        double radius = SporeAddFungusConfig.scavengerFleeThreatRadius();
        AABB area = scavenger.getBoundingBox().inflate(radius);
        double totalDps = 0.0D;
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (LivingEntity candidate : scavenger.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (candidate == scavenger || !isAggroed(candidate)) {
                continue;
            }
            totalDps += FungusCombat.dps(candidate);
            double distance = scavenger.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return new Threat(totalDps, nearest);
    }

    /**
     * 这东西是不是<b>盯上了我</b>。
     *
     * <p>两道排除（死亡/旁观、同阵营）之后，只要「它正冲我来」或「它最近打过我」就算。
     * 与 {@code Mob#canAttack} 相比这是<b>严格更窄</b>的判据：那个问的是"它有没有能力攻击我"，
     * 这里问的是"它有没有真的在攻击我"。
     */
    private boolean isAggroed(LivingEntity candidate) {
        if (candidate.isRemoved() || candidate.isDeadOrDying() || candidate.isSpectator()) {
            return false;
        }
        if (FungusCombat.isFungus(candidate)) {
            return false;
        }
        // 创造模式的玩家：追不上也打不着，对着躲是白费（与旧判据一致的那一点保留）
        if (candidate instanceof Player player && player.isCreative()) {
            return false;
        }
        // 正冲我来。玩家永远不会走这一条——他们的 target 不是这只拾荒者
        if (candidate instanceof Mob mob && mob.getTarget() == scavenger) {
            return true;
        }
        // 最近打过我，且还在记仇窗口内。玩家只能靠这一条进来
        return candidate == scavenger.getLastHurtByMob() && isWithinAggroMemory();
    }

    /** 上次挨打是否还在记仇窗口内。没挨过打时 {@code getLastHurtByMob()} 为 null，走不到这里。 */
    private boolean isWithinAggroMemory() {
        int memory = SporeAddFungusConfig.scavengerFleeAggroMemoryTicks();
        return scavenger.tickCount - scavenger.getLastHurtByMobTimestamp() <= memory;
    }

    /**
     * 这些锁定者能在我跑掉之前打死我吗。
     *
     * <p>判据是「预计存活秒数」而不是「有多少只」：后者对不同强度的怪一视同仁，
     * 而前者自动把"对方的输出"与"我自己的硬度"都算进去。十五只鸡和一只灾厄在数量上差得远，
     * 但只有后者真正致命。
     */
    private boolean isOutmatched(double totalDps) {
        if (totalDps <= 0.0D) {
            return false;   // 锁定了我却打不出伤害（比如没有攻击属性的生物），不跑
        }
        double secondsToDie = FungusCombat.effectiveHealth(scavenger) / totalDps;
        return secondsToDie < SporeAddFungusConfig.scavengerFleeSurviveSeconds();
    }

    /**
     * 选一个逃跑落点。选不到就返回 false（这一拍不跑）。
     *
     * <p>两个距离都来自配置（{@code scavenger.fleeTargetHorizontalDistance} /
     * {@code fleeTargetVerticalRange}），与原版寻路默认的 16/7 一致。
     * 注意它们与 {@code fleeThreatRadius} 是两回事：那一项管「多近算威胁」，
     * 这两项管「往哪跑、跑多远落脚」。
     */
    private boolean pickFleeTarget() {
        int horizontal = SporeAddFungusConfig.scavengerFleeTargetHorizontalDistance();
        int vertical = SporeAddFungusConfig.scavengerFleeTargetVerticalRange();
        Vec3 away = threat == null
                ? null
                : DefaultRandomPos.getPosAway(scavenger, horizontal, vertical, threat.position());
        Vec3 pos = away != null ? away : DefaultRandomPos.getPos(scavenger, horizontal, vertical);
        if (pos == null) {
            return false;
        }
        targetX = pos.x;
        targetY = pos.y;
        targetZ = pos.z;
        return true;
    }
}
