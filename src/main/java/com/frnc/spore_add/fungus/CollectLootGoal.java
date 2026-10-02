package com.frnc.spore_add.fungus;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.frnc.spore_add.SporeAddFungusConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * 新机制 1：「真菌会主动收集附近的掉落物，转化为资源」。
 *
 * <h2>它做什么</h2>
 * 隔一段时间在自身周围搜一次掉落物，挑<b>值资源</b>（按 {@link LootValues} 那张表算）最近的那件，
 * 走过去，够近了就把它收走并折算成资源交给最近的心智。
 *
 * <h2>三个限流，都不是可选的</h2>
 * <ol>
 *   <li><b>搜寻间隔</b>（{@code searchIntervalTicks}）——遍历半径内的实体是有成本的，
 *       而"附近有没有掉落物"几秒内不会变。用各实体自己的 {@code tickCount} 取模，
 *       于是同一批真菌天然错开，不会在同一拍集体搜一遍。</li>
 *   <li><b>转化冷却</b>（{@code convertCooldownTicks}）——没有它的话，一场大战后满地掉落物
 *       会被一只真菌在几秒内全部吞掉，资源数字瞬间跳一大截。有了它，收集速度有上限、
 *       玩家也来得及去捡。</li>
 *   <li><b>只捡有定价的东西</b>（{@code defaultValue} 默认 0）——见 {@link LootValues}。
 *       默认下真菌只拿你在转化表里列过的物品，不会把整片战场扫空。</li>
 * </ol>
 *
 * <h2>优先级与打断</h2>
 * 挂在移动标志（{@code Flag.MOVE}）上，优先级取得很低（见 {@link #PRIORITY}）。
 * 而且 {@link #canUse} 里明确要求"当前没有攻击目标"——真菌在打架时不该分心去捡垃圾。
 * 这两条加起来，收集永远让位于战斗与 Spore 自己的各种目标。
 */
public class CollectLootGoal extends Goal {

    /** 每次转化往哪个存档键上累计小数资源。 */
    private static final String KEY_PENDING_RESOURCE = "spore_add:loot_credit";

    private final Mob mob;

    @Nullable
    private ItemEntity target;

    /** 这一趟是否已经收到东西了。收到就结束，免得站在原地一趟趟地重复领同一件。 */
    private boolean collected;

    public CollectLootGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!collectingEnabled()) {
            return false;
        }
        // 打架优先。也顺带保证"被玩家引走"的真菌不会半路停下来捡东西
        if (mob.getTarget() != null && mob.getTarget().isAlive()) {
            return false;
        }
        // 各实体按自己的 tickCount 错开，不写成全局节拍
        if (mob.tickCount % SporeAddFungusConfig.lootSearchIntervalTicks() != 0) {
            return false;
        }
        target = findNearestValuable();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !collected && target != null && target.isAlive() && !target.getItem().isEmpty();
    }

    @Override
    public void start() {
        collected = false;
        moveToTarget();
    }

    @Override
    public void stop() {
        target = null;
        collected = false;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (mob.distanceToSqr(target) <= pickupDistanceSqr()) {
            collect(target);
            return;
        }
        // 目标没动过就偶尔重新寻路一次：掉落物会被水流/爆炸推走，而导航路线一旦算歪不会自愈
        if (mob.tickCount % SporeAddFungusConfig.lootRepathIntervalTicks() == 0) {
            moveToTarget();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    // ------------------------------------------------------------------

    private void moveToTarget() {
        if (target != null) {
            mob.getNavigation().moveTo(target, SporeAddFungusConfig.lootMoveSpeed());
        }
    }

    private double pickupDistanceSqr() {
        double distance = SporeAddFungusConfig.lootPickupDistance();
        return distance * distance;
    }

    /**
     * 找附近值资源的、最近的那件掉落物。
     *
     * <p>用一次 AABB 查询拿回候选，再逐件问 {@link LootValues}。**先算距离再看价值**——
     * 价值计算在未命中时要做标签匹配，比对距离贵；而只要最近的那件有价值就够了，
     * 没必要给视野里每一件掉落物都算一遍价。
     *
     * <p>用 {@code getEntitiesOfClass} 而不是 {@code getEntities}：需要的是掉落物这一个具体类型，
     * 让原版在收集时就按类型过滤掉绝大多数实体，比取回来再 {@code instanceof} 便宜。
     */
    @Nullable
    private ItemEntity findNearestValuable() {
        double radius = SporeAddFungusConfig.lootRadius();
        AABB area = mob.getBoundingBox().inflate(radius);
        ItemEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ItemEntity item : mob.level().getEntitiesOfClass(ItemEntity.class, area)) {
            if (item.isRemoved() || item.getItem().isEmpty()) {
                continue;
            }
            double distance = mob.distanceToSqr(item);
            if (distance >= nearestDistance) {
                continue;
            }
            if (LootValues.valueOf(item.getItem()) <= 0.0D) {
                continue;
            }
            nearestDistance = distance;
            nearest = item;
        }
        return nearest;
    }

    /**
     * 收走一件掉落物并折算成资源。
     *
     * <p><b>先把东西删掉再算钱</b>：算出 0 的边界情况（配置在两次搜寻之间被改小）下，
     * 东西已经没了也就没了——反过来"先算钱、为 0 就不删"会让真菌卡在原地反复搜同一件
     * 永远搬不动的东西。会归零的配置切换是罕见且一次性的，丢掉那一件可以接受。
     *
     * <p>资源不用整数直接发，而是<b>小数累计</b>（存在实体的持久数据里）：
     * 一格木头只值 0.5，不累计的话每次都被取整成 0，玩家会觉得"它捡了但什么都没发生"。
     */
    private void collect(ItemEntity item) {
        ItemStack stack = item.getItem().copy();
        double value = adjustValue(LootValues.valueOf(stack));
        item.discard();
        collected = true;

        if (value <= 0.0D || !(mob.level() instanceof ServerLevel level)) {
            return;
        }

        CompoundTag data = mob.getPersistentData();
        double credit = data.getDouble(KEY_PENDING_RESOURCE) + value;
        int whole = Mth.floor(credit);
        double remainder = credit - whole;
        if (whole > 0) {
            // 送不出去的（比如附近一个人都没有）退回暂存，下次接着算
            remainder += deliver(level, whole);
        }
        data.putDouble(KEY_PENDING_RESOURCE, remainder);
    }

    // ------------------------------------------------------------------
    // 给子类留的两个口子
    // ------------------------------------------------------------------
    //
    // 拾荒者（{@code scavenger.ScavengerLootGoal}）继承了本类，只覆写下面这几个钩子：
    // 它要额外乘一个"存活时间加成"，交付也要走自己那条优先级链。搜寻、走过去、收走、
    // 小数累计这些逻辑两边完全一样，没必要抄一遍。

    /**
     * 这一件掉落物价值多少资源。默认原样返回；子类可以在这里乘加成。
     *
     * <p>注意传进来的是<b>整堆</b>的价值（单价 × 数量），乘法直接作用在总量上是对的。
     */
    protected double adjustValue(double rawValue) {
        return rawValue;
    }

    /**
     * 把已经折算成整数的资源送出去，返回<b>没能送出去、应当退回暂存</b>的数量。
     *
     * <p>默认交给离得最近的心智（见 {@code Resources#deliverToNearest}），也就是"真菌捡到东西
     * 往最近的巢里送"。拾荒者覆写成自己那条优先级链。
     */
    protected int deliver(ServerLevel level, int whole) {
        Resources.deliverToNearest(level, mob.position(), whole);
        return 0;
    }

    /**
     * 总开关要不要生效。
     *
     * <p>默认看 {@code loot.collectEnabled}——那是"让普通真菌也捡东西"的开关。
     * 拾荒者覆写成恒真：<b>捡东西就是它的全部职能</b>，不该被那个给普通真菌准备的开关关掉。
     */
    protected boolean collectingEnabled() {
        return SporeAddFungusConfig.lootCollectEnabled();
    }
}
