package com.frnc.spore_add.fungus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.frnc.spore_add.SporeAddDebugConfig.Area;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.debug.SporeAddDebug;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 新机制 1：「真菌会主动收集附近的掉落物，转化为资源」。
 *
 * <h2>它做什么</h2>
 * 隔一段时间在自身周围搜一次掉落物，挑<b>值资源</b>（按 {@link LootValues} 那张表算）最近的那件，
 * 走过去，够近了就把它收走并折算成资源交给最近的心智。
 *
 * <h2>两个限流，都不是可选的</h2>
 * <ol>
 *   <li><b>搜寻间隔</b>（{@code loot.searchIntervalTicks}）——遍历半径内的实体是有成本的，
 *       而「附近有没有掉落物」几秒内不会变。各实体各记各的下次搜索时刻
 *       （见 {@link #nextSearchTick}），于是同一批真菌天然错开，不会在同一拍集体搜一遍。
 *       <b>它只限流"搜索"，不限流"起步"</b>——手上攥着有效目标时任何空闲时刻都能起，
 *       理由写在 {@link #canUse} 的注释里。</li>
 *   <li><b>一趟只收一件</b>（见 {@link #collected}）——收完就结束这次目标，要等下一个搜寻间隔
 *       才会重新起。**这就是"转化速度的上限"，不需要另设一个冷却配置项**：
 *       一场大战后的掉落物可能有几百件，但一只真菌最快也只能每 searchIntervalTicks 收一件，
 *       玩家来得及去捡。</li>
 * </ol>
 *
 * <h2>「值不值得捡」由两张数据包名单决定</h2>
 * {@link LootBlacklist} 最优先（里面的东西根本不去捡）；其余按 {@link LootValues} 定价，
 * <b>没列到的物品也算数</b>（{@code loot.defaultValue} 默认 1.0、{@code loot.defaultChance} 默认 1.0，
 * 也就是全都收）——需求要的是「所有掉落物全部纳入」，想排除某类东西请写黑名单，而不是把默认值调回 0。
 *
 * <h2>优先级与打断</h2>
 * 挂在移动标志（{@code Flag.MOVE}）上，优先级取得很低（见 {@code loot.priority}）。
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

    /**
     * 下一次允许「重新找目标」的时刻（游戏刻）。各实体各算各的，不是全局节拍。
     *
     * <p><b>不存盘</b>：它纯粹是个限流用的时间戳，重启后从 0 重算只会让它早搜一次，
     * 没有任何副作用（与 {@code Scavenger} 那个必须存盘的存活时长不同）。
     */
    private int nextSearchTick;

    /**
     * 追一件东西最多花多久（tick），到点还没拿到就放弃它。默认 10 秒。
     *
     * <p><b>这一条防的是"永久卡死"</b>：导航失败（掉落物在墙后、悬空、在另一侧的水下）时，
     * {@link #isTargetStillGood} 的每一条判据都仍然成立——东西还在、还在半径内——于是本目标
     * 会一直重寻路却永远走不到，而它持有 {@code MOVE} 标志，<b>闲逛、环顾全都起不来</b>。
     * 一只真菌就这么站着不动到天荒地老。
     *
     * <p>超时之后放弃，并且把那一件**暂时拉黑**（见 {@link #abandonedTarget}）——
     * 只放弃不拉黑是不够的：下一个搜索窗口（最快 40 tick 之后）会重新选中<b>同一件</b>
     * （挑的是最近的那件），于是它每 40 tick 里仍有几十 tick 耗在这件够不着的东西上。
     */
    private static final int CHASE_TIMEOUT_TICKS = 200;

    /** 放弃之后多久之内不再考虑那一件。默认 30 秒——够它去捡点别的，也够世界变一变。 */
    private static final int ABANDON_BLACKLIST_TICKS = 600;

    /** 这一趟的放弃时刻。{@link #start} 时重置。 */
    private int chaseDeadline;

    /** 刚刚放弃的那一件，以及它被拉黑到什么时候。 */
    @Nullable
    private ItemEntity abandonedTarget;
    private int abandonedUntilTick;

    /** 上一次下令寻路时用的移速，用来发现"速度变了"。{@code -1} = 还没走过。 */
    private double lastMoveSpeed = -1.0D;

    public CollectLootGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /**
     * <h2>为什么不是「按 {@code tickCount} 取模」</h2>
     * 最初的写法是 {@code mob.tickCount % searchIntervalTicks == 0}——每 40 tick 只有<b>一个</b>
     * tick 返回 true。而闲逛（{@code RandomStrollGoal}）用的是同一个 {@code MOVE} 标志：
     * 只要它在那个窗口恰好占着，本目标当拍就被跳过，得再等 40 tick。
     * 环顾、受惊、浮潜、吃残骸也都在抢同一个标志，所以「明明看见了却不去捡」是常态而非偶发。
     *
     * <p>现在把两件事分开：<b>搜索</b>仍然限流（那是性能旋钮，扫一次要遍历半径内所有掉落物），
     * 但<b>起步</b>不再受它约束——只要手上还攥着一个有效目标，任何 {@code MOVE} 空闲的时刻都能起。
     */
    @Override
    public boolean canUse() {
        if (!collectingEnabled()) {
            return false;
        }
        // 打架优先。也顺带保证"被玩家引走"的真菌不会半路停下来捡东西
        if (mob.getTarget() != null && mob.getTarget().isAlive()) {
            return false;
        }
        if (!isTargetStillGood()) {
            // 到点之前不重复扫：搜索本身是这里唯一有成本的动作
            if (mob.tickCount < nextSearchTick) {
                return false;
            }
            nextSearchTick = mob.tickCount + SporeAddFungusConfig.lootSearchIntervalTicks();
            target = findNearestValuable();
        }
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (collected) {
            return false;
        }
        // 追太久了：放弃这一件，并把它拉黑一阵子（理由见 CHASE_TIMEOUT_TICKS）
        if (target != null && mob.tickCount > chaseDeadline) {
            abandonedTarget = target;
            abandonedUntilTick = mob.tickCount + ABANDON_BLACKLIST_TICKS;
            target = null;
            return false;
        }
        return isTargetStillGood();
    }

    /**
     * 手上这个目标还值不值得继续走。
     *
     * <p>比 {@code canContinueToUse} 原来的判据多一条距离检查：目标被水流或爆炸推远之后
     * 不该再一路追下去——那是"追一件已经不在附近的东西"，既蠢又费导航。
     * 推远了就丢掉，下一个搜索窗口自然会重新挑一件近的。
     */
    private boolean isTargetStillGood() {
        if (target == null || !target.isAlive() || target.getItem().isEmpty()) {
            return false;
        }
        return isWithinReach(target);
    }

    /**
     * 这一件还在"够得着"的范围里吗。
     *
     * <p>默认只看它离<b>自己</b>多远。拾荒者覆写它：它可以从别人那里听说远处有东西，
     * 所以判据变成「离我够近，**或者**离我的某个链接对象够近」——否则它会因为
     * "目标不在我自己身边"而当场放弃一个明明可以走过去的圆心。
     */
    protected boolean isWithinReach(ItemEntity item) {
        double radius = searchRadius();
        return mob.distanceToSqr(item) <= radius * radius;
    }

    @Override
    public void start() {
        collected = false;
        chaseDeadline = mob.tickCount + CHASE_TIMEOUT_TICKS;
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
        // 目标没动过就偶尔重新寻路一次：掉落物会被水流/爆炸推走，而导航路线一旦算歪不会自愈。
        // **另有一条**：移速变了就立刻重寻路——移速是 moveTo 那一刻写死的，
        // 不重新下令的话"刚踏进奔袭范围"那一下要等最多一个周期（默认 1 秒）才生效，
        // 看起来就不像扑过去、而像走两步忽然想起自己该跑。
        double speed = moveSpeedFor(target);
        if (mob.tickCount % SporeAddFungusConfig.lootRepathIntervalTicks() == 0 || speed != lastMoveSpeed) {
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
            lastMoveSpeed = moveSpeedFor(target);
            mob.getNavigation().moveTo(target, lastMoveSpeed);
        }
    }

    /**
     * 追这一件时用多快的移速。
     *
     * <p>默认恒定（{@code loot.moveSpeed}）。拾荒者覆写成"靠近了就奔袭"——
     * 移速在 {@code Navigation#moveTo} 的那一刻写死，所以<b>中途改速度必须重新寻路一次</b>，
     * 见 {@link #tick()} 里那条"速度变了就立刻重寻路"。
     */
    protected double moveSpeedFor(ItemEntity item) {
        return SporeAddFungusConfig.lootMoveSpeed();
    }

    private double pickupDistanceSqr() {
        double distance = SporeAddFungusConfig.lootPickupDistance();
        return distance * distance;
    }

    /**
     * 一个额外的搜索圆心：以某个同伴的位置为圆心、以某个半径扫一圈掉落物。
     *
     * <p>必须是 {@code public}——{@code ScavengerLootGoal} 在<b>另一个包</b>里，
     * 而 {@code protected} 的嵌套类型在跨包子的类里连 {@code new} 都构造不出来
     * （protected 的可见性只对"通过 this 或子类引用访问成员"开放，不含构造嵌套类型）。
     *
     * @param position 圆心
     * @param radius   半径
     */
    public record SearchCenter(Vec3 position, double radius) {
    }

    /**
     * 除了自己之外，还要从哪几个圆心各扫一圈。
     *
     * <p>默认<b>没有</b>——普通真菌只扫自己周围那一个盒子，行为与本轮改动之前一字不差。
     * 拾荒者覆写它，把每个链接对象变成一个圆心（见 {@code ScavengerLinks}）。
     */
    protected List<SearchCenter> searchCenters() {
        return List.of();
    }

    /**
     * 搜集候选掉落物：自己周围那一圈，加上 {@link #searchCenters()} 给的每个额外圆心。
     *
     * <p><b>只做"是不是一件东西"这一层过滤，不做价值过滤</b>——那一步留给
     * {@link #chooseTarget}，因为它默认是"由近到远逐件问价、命中即停"的惰性写法，
     * 在这里把价都算完就白费了。
     *
     * <p>用 {@code getEntitiesOfClass} 而不是 {@code getEntities}：需要的是掉落物这一个具体类型，
     * 让原版在收集时就按类型过滤掉绝大多数实体，比取回来再 {@code instanceof} 便宜。
     *
     * <p>用 {@code LinkedHashSet} 去重：多个圆心的盒子会互相重叠，同一件掉落物会被扫到好几次。
     */
    private List<ItemEntity> gatherCandidates() {
        Set<ItemEntity> found = new LinkedHashSet<>();
        collect(found, mob.getBoundingBox().inflate(searchRadius()));
        for (SearchCenter center : searchCenters()) {
            collect(found, new AABB(center.position(), center.position()).inflate(center.radius()));
        }
        return new ArrayList<>(found);
    }

    private void collect(Set<ItemEntity> found, AABB area) {
        for (ItemEntity item : mob.level().getEntitiesOfClass(ItemEntity.class, area)) {
            if (item.isRemoved() || item.getItem().isEmpty()) {
                continue;
            }
            // 刚刚追不到而放弃的那一件，在拉黑期内不再考虑——否则下一个搜索窗口又会选中它
            // （挑的是最近的那件），于是每轮都耗在同一个够不着的东西上
            if (item == abandonedTarget && mob.tickCount < abandonedUntilTick) {
                continue;
            }
            found.add(item);
        }
    }

    /**
     * 从候选里挑出该去的那一件。
     *
     * <p>默认实现：按离自己的距离排序，然后<b>由近到远逐件问价</b>，第一件既没进黑名单、
     * 又能转化出资源的就是它。问价要做标签匹配、比距离贵，所以"先看距离再看价值"这一点
     * 是<em>按需</em>保留的——排序本身是 O(n log n)，但问价只到命中为止。
     *
     * <p>拾荒者覆写成"聚堆 + 六因子评分"（见 {@code LootScoring}）。
     *
     * @return 挑中的那一件；没有值得捡的就返回 {@code null}
     */
    @Nullable
    protected ItemEntity chooseTarget(List<ItemEntity> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(mob::distanceToSqr))
                // 黑名单最优先：里面的东西根本不去捡（与它值多少无关）。
                // 再看"有没有可能转化出资源"——价值或概率为 0 的都算永远转化不出来，不必白跑一趟
                .filter(item -> !LootBlacklist.isBlacklisted(item.getItem()))
                .filter(item -> LootValues.pricingOf(item.getItem()).canEverYield())
                .findFirst()
                .orElse(null);
    }

    /** 搜一次并挑一件。 */
    @Nullable
    private ItemEntity findNearestValuable() {
        return chooseTarget(gatherCandidates());
    }

    /**
     * 捡起一件掉落物之后的钩子。默认什么都不做。
     *
     * <p>拾荒者覆写它来播「拾荒者捡拾」那条字幕（见 {@code ScavengerLootGoal}）——
     * 普通真菌没有自己的字幕，所以这里留空。
     */
    protected void onCollected() {
    }

    /**
     * 收走一件掉落物并折算成资源。
     *
     * <p><b>先把东西删掉，再掷骰、再算钱</b>：算出 0 的边界情况（概率没中，或配置在两次搜寻
     * 之间被改小）下，东西已经没了也就没了——反过来「先算钱、为 0 就不删」会让真菌卡在原地
     * 反复搜同一件永远搬不动的东西。会归零的情况是罕见且一次性的，丢掉那一件可以接受。
     *
     * <p>资源不用整数直接发，而是<b>小数累计</b>（存在实体的持久数据里）：
     * 拾荒者的存活加成会把整数单价乘成小数（如 3 × 1.25），不累计的话每次都被取整成 0，
     * 玩家会觉得"它捡了但什么都没发生"。
     */
    private void collect(ItemEntity item) {
        ItemStack stack = item.getItem().copy();
        LootValues.Pricing pricing = LootValues.pricingOf(stack);
        item.discard();
        collected = true;
        onCollected();

        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }

        // 需求：每件掉落物各掷各的骰。没中就是这次什么都没转化出来，
        // 但东西**照样被捡走**（上面已经 discard 了）——概率管的是"转化出多少资源"，
        // 不是"捡不捡"。选目标那一步已经确认过它有可能转化，所以这里不必再判 canEverYield。
        if (mob.getRandom().nextDouble() >= pricing.chance()) {
            // 「捡起来了但什么都没发生」第一个来源：转化骰没中。东西照样被收走了（上面已 discard）。
            SporeAddDebug.log(Area.LOOT, "转化未中：{} ×{}（表里价值 {}、概率 {}）",
                    stack.getItem(), stack.getCount(), pricing.value(), pricing.chance());
            return;
        }
        double value = adjustValue(pricing.value() * stack.getCount());
        if (value <= 0.0D) {
            // 第二个来源：掷中了，但价值算出来是 0（表里价值为 0，或子类的加成把它乘没了）。
            SporeAddDebug.log(Area.LOOT, "转化为 0：{} ×{}（表里价值 {}）",
                    stack.getItem(), stack.getCount(), pricing.value());
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
        // 封顶放在最后：无论资源是"从没送出去"还是"送出去又被退回来"，都不该越过上限。
        // 超出的部分**直接消失**——这让"存储满了"成为一件有代价的事，也是"该去找队友了"
        // 这个行为存在的前提。普通真菌的上限是无限（见 storageCap），行为一字未变。
        double kept = Math.min(remainder, storageCap());
        data.putDouble(KEY_PENDING_RESOURCE, kept);
        // 成功这一路也留一行：否则"捡了但没反应"的三条路径里有一条静默，
        // 分不清是"没转化"还是"转化了但存不下"。打的是**封顶之后**的值，
        // 与真实存量一致（封顶生效时那部分确实消失了，不是还在它身上）。
        SporeAddDebug.log(Area.LOOT, "转化成功：{} ×{} → {} 资源（存量 {}/{}）",
                stack.getItem(), stack.getCount(), value, Mth.floor(kept), Mth.floor(storageCap()));
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

    /**
     * 搜寻掉落物的半径。
     *
     * <p>默认看 {@code loot.radius}——那个键同时作用于满地的普通真菌，
     * 所以它是个要顾着性能的保守值。拾荒者覆写成 {@code scavenger.lootRadius}（默认 32）：
     * 它世上最多只有十来只，放宽半径的代价可以忽略，而"能找到东西"正是它存在的意义。
     */
    protected double searchRadius() {
        return SporeAddFungusConfig.lootRadius();
    }

    /**
     * 这个生物身上那份「还没送出去的资源」最多能攒多少。
     *
     * <p>默认没有上限——普通真菌的资源是"捡到就往最近的巢里送"，送不掉的那点零头也不值得管。
     * 拾荒者覆写成一条随存活时间成长的曲线（见 {@code scavenger.storageBase} 那一组）：
     * 它会把资源揣在身上等着交给同伙，所以必须有个"揣不下了"的点，
     * 否则「存满了该去找队友」这件事永远不会发生。
     *
     * <p>这一层封顶是<b>唯一</b>让资源消失的地方，所以调用方要把"超出就丢"写清楚。
     */
    protected double storageCap() {
        return Double.MAX_VALUE;
    }

    // ------------------------------------------------------------------
    // 那份暂存的读与清（键本身不对外暴露）
    // ------------------------------------------------------------------

    /**
     * 这个生物身上还揣着多少没送出去的资源。
     *
     * <p>给「存满了该去找队友」与「死后把存量掉出来」两个读者用——它们都在别的类里，
     * 而存档键是本类的私有细节，所以从这两个方法进出，别让键名散出去。
     */
    public static double pendingResource(Mob mob) {
        return mob.getPersistentData().getDouble(KEY_PENDING_RESOURCE);
    }

    /**
     * 改写暂存。
     *
     * <p>两个用处：死后掉落把存量清成 0（否则读档/复活会把同一笔再掉一次）、
     * 以及「专程去交付」到位后把送出去的部分扣掉。
     */
    public static void setPendingResource(Mob mob, double value) {
        mob.getPersistentData().putDouble(KEY_PENDING_RESOURCE, Math.max(0.0D, value));
    }
}
