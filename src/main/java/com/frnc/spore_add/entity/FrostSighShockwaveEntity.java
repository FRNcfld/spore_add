package com.frnc.spore_add.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.frnc.spore_add.SporeAddDebugConfig.Area;
import com.frnc.spore_add.SporeAddPlayerConfig;
import com.frnc.spore_add.debug.SporeAddDebug;
import com.frnc.spore_add.enchantment.Warmth;
import com.frnc.spore_add.particle.ModParticles;
import com.frnc.spore_add.world.FrostSighBlast;
import com.frnc.spore_add.world.FrostSighChunks;
import com.frnc.spore_add.world.FungalClearing;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「冰雪的叹息」的爆发本体：往外推的冲击环。
 *
 * <p>它自己不可见（见 {@code InvisibleEntityRenderer}），所有观感靠服务端下发的粒子。
 * 逐列的世界修改在 {@link FrostSighBlast} 里，这里只负责<b>推进的节奏</b>与
 * 那几件需要跨 tick 记状态的事（击杀、玩家冰封计时、区块票的收尾）。
 *
 * <h2>两个环，职责分开</h2>
 * 一次爆发会生成<b>两个同类的实体</b>，靠 {@link Mode} 区分：
 * <ul>
 *   <li><b>1 号环</b>（{@link Mode#KILL}）——只负责击杀环内的生物，包括 boss；穿整套「烈阳」的玩家
 *       不会被它杀死，代价是那套护甲<b>当场</b>碎裂。击杀掉出来的东西紧接着被封进冰块。</li>
 *   <li><b>2 号环</b>（{@link Mode#EFFECT}）——比 1 号环晚 {@code secondRingDelaySeconds} 秒起跑，
 *       负责其余<b>全部</b>效果：铺冰、冻流体、清真菌、施加冻伤、冰封玩家。</li>
 * </ul>
 * 分成两环是为了让"先清场、后铺冰"在观感上分得开。两者半径与推进速度完全一样，
 * 所以 2 号环始终稳稳落后 1 号环那段固定的时间。
 *
 * <h2>区块票归 2 号环放</h2>
 * 票是激活时由方块钉上的，但 1 号环推完时 2 号环还在推（2 号环起跑更晚、路程一样），
 * 所以<b>只有 2 号环负责释放</b>。1 号环的 {@code finish} 只补一遍掉落物。
 *
 * <h2>为什么是实体而不是 BlockEntity</h2>
 * 因为方块本身在爆发那一刻就被移除、换成中心那格液态寒冷了（需求 8），BlockEntity 随之消失。
 * 而冲击环还要往外推十几秒，必须有个能活那么久、又能存盘的东西——实体正好。
 *
 * <h2>推进用的是绝对游戏时间</h2>
 * 记的是开始时刻而不是已过 tick 数。理由与工程里其它几处一致：自减/自增计数器不存盘，
 * 世界重载后会从头开始，于是冲击环会重置回中心重推一遍。用绝对时间就不会。
 * 2 号环的"晚 2 秒"也是靠这个实现的——它的开始时刻直接就是"未来某个游戏时刻"，
 * 在那之前 {@code elapsed} 为负，什么都不做。
 *
 * <h2>粒子为什么自己算而不是 addParticle</h2>
 * {@code sendParticles} 的普通重载<b>只发给 32 格内的玩家</b>，而这里的环半径到 128——
 * 远处的玩家会完全看不到这次爆发。所以逐个玩家用长距离重载发。
 */
public class FrostSighShockwaveEntity extends Entity {

    /** 这一环的职责。 */
    public enum Mode {

        /** 1 号环：击杀环内的生物（玩家按「烈阳」规则处理），并把掉落物封进冰块。 */
        KILL,

        /** 2 号环：铺冰、冻流体、清真菌、施加冻伤、冰封玩家。 */
        EFFECT
    }

    private static final String KEY_START = "StartTime";
    private static final String KEY_TICKS = "ShockwaveTicks";
    private static final String KEY_RADIUS = "Radius";
    private static final String KEY_MODE = "Mode";

    /** 每 tick 在环上撒的粒子数上限（按"越靠外越稀"缩放后取整）。 */
    private static final int MAX_RING_PARTICLES = 120;

    /** 中心那团浓雾每 tick 的粒子数。 */
    private static final int CORE_PARTICLES = 8;

    /** 存档：开始时刻（绝对游戏时间）。 */
    private long startGameTime;

    /** 冲击环从中心推到边缘要多少 tick。 */
    private int shockwaveTicks = 400;

    /** 影响半径。 */
    private int radius = 128;

    /** 这一环的职责。 */
    private Mode mode = Mode.EFFECT;

    /** 上一 tick 的环半径，用来算这一 tick 该处理哪一圈环带。 */
    private double previousRingRadius;

    /** 玩家 UUID → 冰封到这个游戏时刻为止。 */
    private final Map<UUID, Long> frozenUntil = new HashMap<>();

    /** 本次爆发已经冰封了多少个掉落物，用来卡 {@code freezeMaxEntities}。 */
    private int encasedCount;

    /**
     * 1 号环已经判定过的玩家。
     *
     * <p><b>这个集合是必须的，不是优化。</b>1 号环每 tick 都会用<b>当前</b>半径重扫整个圆盘，
     * 所以同一个玩家在环推完之前会被反复看到。生物无所谓——死了就被 {@code isDeadOrDying} 跳过，
     * 天然幂等；但玩家那条分支做的是一次性的状态改变（碎甲），少了这个记录就会出现：
     * <b>第一 tick 判定"整套烈阳，放行并碎甲"，第二 tick 抗性已经变 0，于是把他杀掉</b>
     * ——玩家在"被放行"之后 50 毫秒就死了，根本轮不到 2 号环。
     *
     * <p>存的是 UUID 而不是实体 id：玩家会跨维度、会重连，UUID 才是他的身份。
     */
    private final Set<UUID> killRingSettledPlayers = new HashSet<>();

    /**
     * 已经吃过这次爆发那发冻伤的实体。
     *
     * <p><b>冻伤只施加一次</b>，不是每 tick 重刷。原来的写法是每 tick 都对环内所有生物 {@code addEffect}
     * 一遍，vanilla 在 amplifier 相同时取更长的时长，所以效果上"看起来一样"——但属性上一旦玩家的护甲
     * 在爆发途中被销毁，下一 tick 就会按"抗性 0"重新施加一次，免疫凭空失效。
     * 按"第一次被扫到的那一刻"结算一次，才是"你带着什么防护挨的这发"。
     */
    private final Set<Integer> frostbitten = new HashSet<>();

    /**
     * CDU 清真菌的规则集，整个爆发共用一个。
     *
     * <p>解析一次就够——配置表的内容在一次爆发期间不会变，而
     * {@link FrostSighBlast#sweepRing} 每 tick 要被几百列共用，绝不能在那里重解析。
     *
     * <p>刻意不进存档（{@code addAdditionalSaveData} 里没有它）：重载后重新解析一次即可，
     * 而配置表在运行期是稳定的，重解析的代价可以忽略。懒加载见 {@link #fungalRules()}。
     */
    private FungalClearing.Rules cachedFungalRules;

    /** 拿到规则集，第一次调用时解析。 */
    private FungalClearing.Rules fungalRules() {
        if (cachedFungalRules == null) {
            cachedFungalRules = FungalClearing.rules();
        }
        return cachedFungalRules;
    }

    public FrostSighShockwaveEntity(EntityType<? extends FrostSighShockwaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** 由 {@code FrostSighBlockEntity} 在引爆时调用。 */
    public static void begin(ServerLevel level, BlockPos center) {
        int radius = SporeAddPlayerConfig.frostSighRadius();
        int shockwaveTicks = SporeAddPlayerConfig.frostSighShockwaveTicks();
        long now = level.getGameTime();

        // 爆发瞬间的一次性效果：中心那格液态寒冷 + 把圆盘内的生物群系改成寒带。
        // 这里曾经漏掉过一次——detonate 写好了却没人调，于是这两件事一直没发生过。
        FrostSighBlast.detonate(level, center);

        // 1 号环（击杀）立刻起跑；2 号环（其余全部效果）晚一点，见类注释
        spawn(level, center, Mode.KILL, now, shockwaveTicks, radius);
        spawn(level, center, Mode.EFFECT, now + SporeAddPlayerConfig.frostSighSecondRingDelayTicks(),
                shockwaveTicks, radius);

        // 蘑菇云与「爆后残留」（降雪 + 冰雾）也都在这一处生成：它们是"爆发"的一部分，
        // 而爆发的发令枪就这一个。残留要留 10~60 分钟，比冲击环久得多，所以是独立实体
        // （见 FrostSighAftermathEntity）。
        double cx = center.getX() + 0.5D;
        double cy = center.getY() + 0.5D;
        double cz = center.getZ() + 0.5D;
        level.addFreshEntity(new FrostSighCloudEntity(level, cx, cy, cz, radius,
                SporeAddPlayerConfig.frostSighMushroomTicks()));
        FrostSighAftermathEntity.begin(level, center, radius);
    }

    /** 生成一环。{@code startGameTime} 可以是一个未来的时刻——在那之前它会一直待机。 */
    private static void spawn(ServerLevel level, BlockPos center, Mode mode, long startGameTime,
                              int shockwaveTicks, int radius) {
        FrostSighShockwaveEntity ring = new FrostSighShockwaveEntity(
                ModEntities.FROST_SIGH_SHOCKWAVE.get(), level);
        ring.setPos(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D);
        ring.mode = mode;
        ring.startGameTime = startGameTime;
        ring.shockwaveTicks = shockwaveTicks;
        ring.radius = radius;
        level.addFreshEntity(ring);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;   // 客户端什么都不做：粒子是服务端下发的
        }

        long elapsed = server.getGameTime() - startGameTime;
        if (elapsed < 0L) {
            return;   // 2 号环还没到起跑时刻，待机（见类注释里"晚 2 秒"那一段）
        }
        double progress = Math.min(1.0D, (double) elapsed / shockwaveTicks);
        double ringRadius = radius * progress;

        if (mode == Mode.KILL) {
            // 1 号环只清场：击杀，然后把掉出来的东西封进冰块。
            // 击杀必须排在冰封掉落物之前——那些掉落物正是接下来要封的东西。
            killLivingInside(server, ringRadius);
            encaseLootInside(server, ringRadius);
        } else {
            // 2 号环负责其余全部效果。
            //
            // 推进用的是"上一 tick 的半径 → 这一 tick 的半径"这一段环带，
            // 所以每一列只会被处理一次——不会重复铺冰，也不会漏掉中间那些列。
            if (ringRadius > previousRingRadius) {
                FrostSighBlast.sweepRing(server, blockPosition(), previousRingRadius, ringRadius, radius,
                        fungalRules());
                previousRingRadius = ringRadius;
            }
            FrostSighBlast.applyFrostbite(server, blockPosition(), ringRadius, frostbitten);
            freezePlayersInside(server, ringRadius);
        }

        emitParticles(server, ringRadius, progress);

        if (progress >= 1.0D) {
            finish(server);
        }
    }

    // ------------------------------------------------------------------
    // 击杀生物、冰封掉落物、玩家冰封
    // ------------------------------------------------------------------

    /**
     * 击杀环内的一切活物——这是 1 号环唯一的职责。
     *
     * <p>用 {@code Entity#kill()} 而不是自己拼一次 {@code hurt}，理由有两条：
     * <ul>
     *   <li>{@code generic_kill} 在 {@code #minecraft:bypasses_invulnerability} 里，无视无敌帧、
     *       抗性与护甲，必定生效——不需要担心"这一发被吃掉了"；</li>
     *   <li>它走的是正常的 {@code die()} 流程，所以掉落物、经验、死亡动画、死亡消息一个不少。
     *       <b>掉落物正是下一步要冰封的东西</b>，换成别的方式就得自己补一遍掉落逻辑。</li>
     * </ul>
     *
     * <p><b>不放过 boss。</b>末影龙、凋灵这些一样照杀——这是核弹，本来就该是这个量级。
     * （末影龙那套多部件的收尾由它自己的 {@code die()} 负责，不需要这里额外处理。）
     *
     * <p><b>玩家另走一条规则</b>：整套「烈阳」（{@code resistanceFraction >= 1.0}）可以顶住这一发，
     * 不足整套的照杀。这里刻意<b>不</b>做成"按件削弱"——挡住或死，没有中间态。
     *
     * <p>护甲<b>不在这里收</b>：它得一直撑到 2 号环推完，否则 2 秒后到来的冻伤与冰封就没人替玩家挡了。
     * 收甲的时机见 {@link #finish}。
     */
    private void killLivingInside(ServerLevel server, double ringRadius) {
        Vec3 center = position();
        double limitSqr = ringRadius * ringRadius;
        AABB area = new AABB(center, center).inflate(ringRadius + 1.0D);

        for (Mob mob : server.getEntitiesOfClass(Mob.class, area)) {
            if (mob.position().distanceToSqr(center) > limitSqr
                    || mob.isRemoved() || mob.isDeadOrDying()) {
                continue;
            }
            mob.kill();
        }

        for (ServerPlayer player : server.players()) {
            if (player.position().distanceToSqr(center) > limitSqr
                    || player.isRemoved() || player.isDeadOrDying()) {
                continue;
            }
            // 每个玩家只判定一次，理由见 killRingSettledPlayers 的字段注释
            if (!killRingSettledPlayers.add(player.getUUID())) {
                continue;
            }
            // 抗性只算一次并留用：下面 destroyWarmthArmor 会把护甲卸掉，
            // 之后再算就是 0 了，日志里那个数会变得没法解释。
            float resistance = Warmth.resistanceFraction(player);
            if (resistance >= 1.0F) {
                // 挡住了——代价是那套护甲<b>当场</b>碎裂。
                //
                // ⚠️ 碎完之后**不再记任何东西**：2 号环读的是"此刻身上穿了什么"。
                // 所以如果他能在 2 秒空档里再从背包掏一套穿上，2 号环的冻伤与冰封就会被那一套正常减免。
                // 这是刻意的取舍（"烈阳正常生效"优先），代价是"备一套备用甲"能换来整场爆发的免疫。
                Warmth.destroyWarmthArmor(player);
                // 「这颗核弹为什么没杀死那个人」的答案就在这一行与下一行之间。
                // 每个玩家每场爆发只判一次（上面 killRingSettledPlayers 挡着），不是逐 tick。
                SporeAddDebug.log(Area.COLD, "核弹 1 号环：{} 整套「烈阳」挡住（抗性 {}），护甲当场碎裂",
                        player.getGameProfile().getName(), resistance);
                continue;
            }
            SporeAddDebug.log(Area.COLD, "核弹 1 号环：{} 抗性 {} 不足 1.0，当场击杀",
                    player.getGameProfile().getName(), resistance);
            player.kill();
        }
    }

    /**
     * 把环内的掉落物封进冰块。
     *
     * <p><b>必须排在 {@link #killLivingInside} 之后</b>：击杀产生的掉落物就是这里要封的东西。
     *
     * <p>环每 tick 都会重扫整个圆盘（不是只扫这一圈的环带），所以 tick N 击杀出来的掉落物
     * 即便当 tick 还没进得了实体索引，tick N+1 也一定会被扫到。真正会漏的只有
     * <b>最后一 tick</b> 才掉出来的那批，由 {@link #finish} 用完整半径补一次。
     */
    private void encaseLootInside(ServerLevel server, double ringRadius) {
        if (!SporeAddPlayerConfig.frostSighFreezeItems()) {
            return;
        }
        Vec3 center = position();
        double limitSqr = ringRadius * ringRadius;

        for (ItemEntity item : server.getEntitiesOfClass(ItemEntity.class,
                new AABB(center, center).inflate(ringRadius + 1.0D))) {
            if (item.isRemoved() || item.getItem().isEmpty()
                    || item.position().distanceToSqr(center) > limitSqr) {
                continue;
            }
            if (encasedCount >= SporeAddPlayerConfig.frostSighFreezeMaxEntities()) {
                return;   // 到顶了，剩下的掉落物就地留着
            }
            FrozenCapsuleEntity.encaseLoot(server, item);
            encasedCount++;
        }
    }

    /**
     * 冰封环内的玩家。
     *
     * <p>玩家第一次被环扫到时按 {@code frozenSeconds} 冰封若干秒；穿「烈阳」护甲的话按件数削减时长，
     * 代价是那几件护甲当场碎裂。
     *
     * <p><b>玩家没有 {@code setNoAi} 那样的开关</b>，只能每 tick 把速度清零——副作用是会一起吞掉
     * 重力、击退、传送与骑乘。这是需求 7 明确要求的效果，代价写在注释里备查。
     *
     * <h2>为什么必须读 {@link #frozenUntil}</h2>
     * 清零速度曾经是<b>无条件</b>做的，而 {@code frozenUntil} 只是被写进去、从来没被读过。
     * 结果是"烈阳"的按件抗性对定身完全无效——满四件（抗性 1.0、冰封时长按计算是 0）的玩家照样
     * 被钉在原地，连 {@code frozenSeconds} 配 0 也照钉，而且时长恒等于整个冲击环的推进时间。
     * 现在只有"确实还在冰封期内"才清零。
     */
    private void freezePlayersInside(ServerLevel server, double ringRadius) {
        int frozenTicks = SporeAddPlayerConfig.frostSighFrozenTicks();
        long now = server.getGameTime();

        Vec3 center = position();
        double limitSqr = ringRadius * ringRadius;

        for (ServerPlayer player : server.players()) {
            // 已经被 1 号环杀掉的不算：他们不在名单里，也就不会被 {@link #finish} 收走护甲
            // ——那几件没挡住任何东西，不该跟着一起消失。
            if (player.isDeadOrDying() || player.position().distanceToSqr(center) > limitSqr) {
                continue;
            }
            // 只在"第一次被扫到"时结算冰封时长，之后每 tick 只是维持清零。
            // 护甲在这里**不动**——它要撑到整场爆发结束，见 killLivingInside 与 finish 的注释。
            long until = frozenUntil.computeIfAbsent(player.getUUID(), id ->
                    now + Math.round(frozenTicks * (1.0F - Warmth.resistanceFraction(player))));
            if (frozenTicks > 0 && now < until) {
                player.setDeltaMovement(Vec3.ZERO);
            }
        }

        // ⚠️ 这里**刻意不做**"到期就把条目摘掉"。
        //
        // 摘掉过一次：因为 {@link #frozenUntil} 是"只在第一次被扫到时写一次"，
        // 一旦到期被移除，下一 tick 的 computeIfAbsent 就会把它当成"新玩家"重新算一遍时长，
        // 于是"被冰封 frozenSeconds 秒"变成"只要还在环里就一直被反复冰封"——
        // 按件抗性算出来的那个缩短，实际上永远走完不了。表按玩家数封顶，留着不涨。
    }

    // ------------------------------------------------------------------
    // 粒子（需求 6、14）
    // ------------------------------------------------------------------

    /**
     * 沿着环撒粒子，<b>中心最浓、越往外越稀</b>，到边缘彻底消散（需求 14）。
     *
     * <p>逐个玩家用长距离重载发：普通重载只覆盖 32 格，而这里半径到 128。
     */
    private void emitParticles(ServerLevel server, double ringRadius, double progress) {
        Vec3 center = position();
        int ringCount = (int) Math.round(MAX_RING_PARTICLES * (1.0D - progress));
        int coreY = (int) center.y;

        for (ServerPlayer player : server.players()) {
            if (player.position().distanceToSqr(center) > (double) (radius + 96) * (radius + 96)) {
                continue;   // 离得太远，连长距离粒子也没必要发
            }
            // 环上：均匀撒一圈，数量随半径增大而减少
            for (int i = 0; i < ringCount; i++) {
                double angle = server.random.nextDouble() * Math.PI * 2.0D;
                double x = center.x + Math.cos(angle) * ringRadius;
                double z = center.z + Math.sin(angle) * ringRadius;
                server.sendParticles(player, ModParticles.FROST_SIGH_CLOUD.get(), true,
                        x, coreY + server.random.nextInt(4) - 1, z, 1, 1.5D, 1.5D, 1.5D, 0.02D);
            }
            // 中心那一团与开场闪光只由 1 号环负责——两个环都撒的话浓度直接翻倍。
            // 环体粒子则两环都撒，于是画面上是前后两圈同心环，正是想要的观感。
            if (mode == Mode.KILL) {
                server.sendParticles(player, ModParticles.FROST_SIGH_CORE.get(), true,
                        center.x, center.y + 1.0D, center.z, CORE_PARTICLES, 3.0D, 3.0D, 3.0D, 0.05D);
                if (progress < 0.1D) {
                    server.sendParticles(player, ModParticles.FROST_SIGH_FLARE.get(), true,
                            center.x, center.y + 2.0D, center.z, 24, 6.0D, 6.0D, 6.0D, 0.3D);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 收尾
    // ------------------------------------------------------------------

    /**
     * 这一环推完了。两个环的收尾完全不同。
     *
     * <ul>
     *   <li><b>1 号环</b>：补一遍掉落物。补这一趟是因为最后一 tick 击杀出来的东西可能还没进实体索引，
     *       那一 tick 的 {@link #encaseLootInside} 会漏掉它们。<b>不放区块票</b>——2 号环还在推。</li>
     *   <li><b>2 号环</b>：放区块票。它是最后一个结束的，票归它放。（护甲不在这里收
     *       ——1 号环扫到的那一刻就已经碎了，见 {@link #killLivingInside}。）</li>
     * </ul>
     */
    private void finish(ServerLevel server) {
        if (mode == Mode.KILL) {
            encaseLootInside(server, radius);
        } else {
            FrostSighChunks.release(server, blockPosition(), radius);
        }
        discard();
    }

    /**
     * 兜底释放区块票。
     *
     * <p>正常路径是 2 号环的 {@link #finish}。但实体被移除的原因不止那一种（{@code /kill}、
     * 维度卸载、世界关闭……），而票会被 Forge 持久化进存档——漏放一次，那 226 个区块就一直加载到重启。
     * 只有 2 号环放（理由见类注释），重复放是安全的：Forge 那边没有票时就是一次空操作。
     */
    @Override
    public void remove(RemovalReason reason) {
        if (mode == Mode.EFFECT && level() instanceof ServerLevel server) {
            FrostSighChunks.release(server, blockPosition(), radius);
        }
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong(KEY_START, startGameTime);
        tag.putInt(KEY_TICKS, shockwaveTicks);
        tag.putInt(KEY_RADIUS, radius);
        tag.putString(KEY_MODE, mode.name());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        startGameTime = tag.getLong(KEY_START);
        shockwaveTicks = Math.max(1, tag.getInt(KEY_TICKS));
        radius = Math.max(1, tag.getInt(KEY_RADIUS));
        mode = readMode(tag.getString(KEY_MODE));
    }

    /**
     * 解析存档里的职责名。
     *
     * <p>认不出来时退化成 {@link Mode#EFFECT}：那是"会放区块票"的一边，
     * 万一是老存档缺这个字段，至少不会把票漏在存档里。
     */
    private static Mode readMode(String name) {
        for (Mode candidate : Mode.values()) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        return Mode.EFFECT;
    }
}
