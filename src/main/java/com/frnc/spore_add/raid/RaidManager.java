package com.frnc.spore_add.raid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.SporeAddDebugConfig.Area;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.debug.SporeAddDebug;
import com.frnc.spore_add.fungus.FungusCombat;
import com.frnc.spore_add.hatred.HatredManager;
import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.Harbinger.Spore.Sentities.Utility.ArenaEntity;
import com.frnc.spore_add.hivemind.HivemindStorage;
import com.frnc.spore_add.hatred.HatredValues;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * 真菌袭击的调度中心：状态表、每 tick 推进、发动条件、传送与增益。
 *
 * <h2>状态不存盘（与计划里写的不一样，这是有意的改动）</h2>
 * 袭击状态只活在内存里。原计划打算存进 SavedData 以便跨重启保留，但仔细想下来不划算：
 * <ul>
 *   <li>袭击的<b>实际效果</b>（传送落点、增益）都已经落在实体身上、各自会存续，
 *       不存在"重启后效果丢失"；</li>
 *   <li>真正会丢的只有"这次编排到哪一步"这种瞬时状态，而恢复一个半截的编排
 *       （参战者可能早被清掉了、玩家可能换了维度）比从零开始更糟；</li>
 *   <li>代价是重启会静默取消进行中的袭击。玩家看到的是"台词响过、打了一阵、然后停了"，
 *       与"袭击打完了"在体感上没有区别。</li>
 * </ul>
 *
 * <h2>为什么所有查询口都是给 mixin 用的静态方法</h2>
 * 心智的三项加成与 Despawn 上限都必须在<b>Spore 自己的代码里</b>生效（改 {@code Proto} 的收支、
 * 改 {@code HandlerEvents} 算出来的上限），而那些地方只能通过 mixin 注入。
 * 所以本类对外暴露一组"给定原值、返回调整值"的纯函数，mixin 里只写一行转交。
 * 好处是那些 mixin 里不含任何判断逻辑，"袭击什么时候生效"这件事只有一个定义处。
 *
 * <p>每个查询口的第一行都是 {@code RAIDS.isEmpty()} 快路径：绝大多数时候没有袭击，
 * 而这些方法有的跑在每分钟几百次的实体循环里（{@code addBiomass} 就是）。
 */
public final class RaidManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 正在进行的袭击，键是触发它的玩家。一个玩家同时只会有一场。 */
    private static final Map<UUID, FungalRaid> RAIDS = new HashMap<>();

    /** 发动条件里"真菌数量/质量"的统计间隔。与上面同步，复用同一次遍历。 */
    private RaidManager() {
    }

    // ------------------------------------------------------------------
    // 对外：触发与查询
    // ------------------------------------------------------------------

    /**
     * 某个玩家的恨意值越档、且掷骰命中时被调用。概率判定已经在
     * {@code HatredManager#afterChange} 里做完了，这里<b>不再掷骰</b>。
     */
    public static void tryStart(ServerPlayer player) {
        UUID id = player.getUUID();
        if (RAIDS.containsKey(id)) {
            // 已经有一场在打。需求没写"能不能叠加"，但同一玩家同时开两场袭击
            // 只会让传送与增益重复生效、时长互相覆盖，所以直接忽略
            return;
        }

        // 需求：**只有世上存在心智时才可能触发**。
        // 这条不是平衡性调整而是设定问题——袭击是"由心智发动"的（见需求「真菌袭击（准备）」第 1 条），
        // 一只心智都没有的世界里不存在"发动者"，那就不该凭空冒出一场袭击。
        // 由于掷骰已经在 HatredManager 那边消耗掉了，这里是静默跳过并留一行日志：
        // 否则"越档了却什么都没发生"看起来会像 bug。
        if (SporeCompat.hiveminds().isEmpty()) {
            LOGGER.info("[SporeAdd] 恨意值越档触发了袭击，但世上没有心智，跳过：{}",
                    player.getGameProfile().getName());
            return;
        }

        // 需求：黑名单维度里不会发生袭击。名单是数据包形式的，见 RaidDimensionBlacklist。
        // 这一条与"有心智"那条一样是硬性的，不是平衡旋钮的值。
        if (!RaidDimensionBlacklist.raidsAllowed(player.serverLevel())) {
            LOGGER.info("[SporeAdd] 恨意值越档触发了袭击，但 {} 维度在黑名单里，跳过：{}",
                    player.serverLevel().dimension().location(), player.getGameProfile().getName());
            return;
        }

        RAIDS.put(id, new FungalRaid(id, player.serverLevel().getGameTime()));
        announcePrep(player);
        LOGGER.info("[SporeAdd] 真菌袭击进入准备阶段，目标玩家：{}", player.getGameProfile().getName());
    }

    /** 这个玩家身上有没有正在进行的袭击。 */
    public static boolean hasRaid(UUID player) {
        return RAIDS.containsKey(player);
    }

    /**
     * 有没有任意一场袭击已经<b>开打</b>（无论是竞技之须那段还是我方围剿那段）。
     * Despawn 上限只看这个。
     *
     * <p>攻击被拆成 ARENA 与 WAVES 两段之后，这里要判的是"不在准备阶段"而不是"等于某一个段"——
     * 写成 {@code phase != PREP} 的好处是：以后再加一段攻击子阶段，这里不用跟着改。
     * 准备阶段仍然要把上限留着原样，否则袭击还没开打就先堆满一地的怪。
     */
    public static boolean isAnyAttackPhase() {
        if (RAIDS.isEmpty()) {
            return false;
        }
        for (FungalRaid raid : RAIDS.values()) {
            if (raid.phase() != FungalRaid.Phase.PREP) {
                return true;
            }
        }
        return false;
    }

    /** 有没有任意一场袭击（准备或攻击）。心智的三项加成看这个。 */
    public static boolean isAnyRaidActive() {
        return !RAIDS.isEmpty();
    }

    // ------------------------------------------------------------------
    // 对外：给 mixin 用的三个调整口 + Despawn 上限
    // ------------------------------------------------------------------

    /**
     * 心智要花出去的量：袭击期间打折。
     *
     * <p>返回值至少为 1（除非原值就是 0）：Spore 里有 {@code eatBiomass(2)} 这种小额支出，
     * 乘 0.5 得 1；但如果原值是 1，乘 0.5 会得 0，那就变成"完全不花钱"了——
     * 需求说的是"消耗降低 50%"，不是"免费"。
     */
    public static int adjustResourceCost(int amount) {
        if (!isAnyRaidActive() || amount <= 0) {
            return amount;
        }
        double multiplier = SporeAddFungusConfig.raidPrepResourceCostMultiplier();
        return Math.max(1, (int) Math.round(amount * multiplier));
    }

    /** 心智收入：袭击期间翻倍。 */
    public static int adjustResourceGain(int amount) {
        if (!isAnyRaidActive() || amount <= 0) {
            return amount;
        }
        double multiplier = SporeAddFungusConfig.raidPrepResourceGainMultiplier();
        return Math.max(amount, (int) Math.round(amount * multiplier));
    }

    /**
     * 灾厄重构体的同化喂食：一次该往 {@code attributeIDs} 里追加几条突变。
     *
     * <p>{@code base} 是 Spore 原样的 1 条。平时乘 {@code wombMutationMultiplier}，
     * 袭击期间再乘 {@code wombRaidMutationMultiplier}——<b>两者相乘</b>才对得上
     * 需求里「再减少 50%（剩余值的 50%）」：先减半、再对剩下的减半。
     *
     * <p>用 {@link #isAnyRaidActive()} 而不是「有没有袭击打在这个 Womb 附近」，
     * 是为了与上面三项心智加成保持同一套判据：需求没要求按距离区分，
     * 而"袭击正在进行"这件事本来就是全服一个状态。
     *
     * <p>结果至少为 {@code base}：填 0 或调小只该关掉加成，不该让喂食反而比原版更亏。
     */
    public static int adjustWombMutationCount(int base) {
        if (base <= 0) {
            return base;
        }
        double multiplier = SporeAddFungusConfig.wombMutationMultiplier();
        if (isAnyRaidActive()) {
            multiplier *= SporeAddFungusConfig.wombRaidMutationMultiplier();
        }
        return Math.max(base, (int) Math.round(base * multiplier));
    }

    /**
     * 灾厄重构体的<b>孵化节拍</b>：袭击期间缩短。
     *
     * <p>Spore 的孵化是「计数器每 tick +1，攒到 {@code recontructor_clock × 20} 才涨 1 点生物质」，
     * 所以 {@code clock} 的单位是秒。这里把它按制造速度倍率折算短——
     * 与心智那边 {@link #adjustManufactureInterval} 是同一个倍率，见
     * {@code SporeAddFungusConfig#wombRaidBonusesEnabled} 的说明。
     *
     * <p>为什么改的是 clock 而不是那个 {@code × 20}：{@code Womb#tick} 里有两个 {@code 20}
     * （另一个是 {@code random.nextInt(20)} 的播放吃东西音效），而 {@code @ModifyConstant}
     * 在 Mixin 0.8.5 里<b>没有 ordinal</b>、两者之间又没有任何非原版调用可用来切 slice，
     * 所以按数值匹配会两个一起改（顺带把音效播放频率也乘 6）。改 clock 的读取点则是唯一的。
     *
     * <p>整数秒会被取整到最小 1 秒，再快也没有意义——阈值归零等于每 tick 涨 1 点生物质。
     */
    public static int adjustWombClock(int clock) {
        if (!SporeAddFungusConfig.wombRaidBonusesEnabled() || !isAnyRaidActive() || clock <= 1) {
            return clock;
        }
        double multiplier = SporeAddFungusConfig.raidAttackManufactureSpeedMultiplier();
        if (multiplier <= 1.0D) {
            return clock;
        }
        return Math.max(1, (int) Math.round(clock / multiplier));
    }

    /**
     * 灾厄重构体同化一只真菌得到的生物质：袭击期间按「资源获取」倍率放大。
     *
     * <p>与 {@link #adjustResourceGain} 用的是同一个倍率。挂在 {@code calculateAssimilation}
     * 的<b>返回值</b>上，所以"融合体 ×4 / 进化体 ×2 / 普通 ×1"三档会一起被放大，
     * 不会扭曲它们之间的相对关系。
     */
    public static int adjustWombAssimilation(int amount) {
        if (!SporeAddFungusConfig.wombRaidBonusesEnabled() || !isAnyRaidActive() || amount <= 0) {
            return amount;
        }
        double multiplier = SporeAddFungusConfig.raidPrepResourceGainMultiplier();
        return Math.max(amount, (int) Math.round(amount * multiplier));
    }

    /**
     * 心智的发育节拍：袭击期间缩短间隔。
     *
     * <p>节拍是一个 {@code tickCount % N == 0} 的常量 N，所以"速度提高 50%" = {@code N ÷ 1.5}。
     * 下限 1 tick：配置调到极端时也不该变成一个每 tick 都跑的循环。
     */
    public static int adjustGrowthInterval(int interval) {
        if (!isAnyRaidActive() || interval <= 1) {
            return interval;
        }
        double multiplier = SporeAddFungusConfig.raidPrepGrowthSpeedMultiplier();
        if (multiplier <= 1.0D) {
            return interval;
        }
        return Math.max(1, (int) Math.round(interval / multiplier));
    }

    /**
     * 心智的制造节拍：袭击期间缩短间隔。
     *
     * <p>与 {@link #adjustGrowthInterval} 是同一个模式、不同的那一处常量——Spore 在
     * {@code Proto#tick} 里用三个独立的 {@code tickCount % 200 == 0} 分别管
     * 生成菌壳、被动资源、召唤，改的是最后那一个（见 {@code ProtoRaidMixin} 的 slice）。
     */
    public static int adjustManufactureInterval(int interval) {
        if (!isAnyRaidActive() || interval <= 1) {
            return interval;
        }
        double multiplier = SporeAddFungusConfig.raidAttackManufactureSpeedMultiplier();
        if (multiplier <= 1.0D) {
            return interval;
        }
        return Math.max(1, (int) Math.round(interval / multiplier));
    }

    /** Despawn 上限：只在攻击阶段抬高。准备阶段就抬会让怪还没开打就先堆满。 */
    public static int adjustDespawnCap(int cap) {
        if (!isAnyAttackPhase() || cap <= 0) {
            return cap;
        }
        double multiplier = SporeAddFungusConfig.raidAttackDespawnCapMultiplier();
        return Math.max(cap, (int) Math.round(cap * multiplier));
    }

    // ------------------------------------------------------------------
    // 每 tick 推进
    // ------------------------------------------------------------------

    /** 应急投放的冷却，记在心智自己的持久数据里（键：下次允许投放的刻）。 */
    private static final String KEY_THREAT_COOLDOWN = "spore_add:deploy_cooldown";

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();

        // 心智受威胁时的应急投放：这是**心智的通用机制**，与有没有袭击无关，所以放在
        // 下面那个"没有袭击就返回"之前。每秒查一次足够——那是几件属性读取。
        if (server.getTickCount() % 20 == 0) {
            tickHivemindDefence(server);
        }

        if (RAIDS.isEmpty()) {
            return;
        }
        // 先收集要移除的，再统一摘：在 forEach 里改 map 会抛 ConcurrentModificationException
        UUID[] finished = RAIDS.entrySet().stream()
                .filter(entry -> tickRaid(server, entry.getValue()))
                .map(Map.Entry::getKey)
                .toArray(UUID[]::new);
        for (UUID id : finished) {
            RAIDS.remove(id);
        }
        // 所有袭击都结束了 → 把"本次袭击已投放"的标记清掉，下次袭击这些存货又能用了。
        // 只在**最后一场**结束时清：两场同时进行时提前清，会让另一场重复投放同一批兵。
        if (RAIDS.isEmpty()) {
            for (Proto hivemind : SporeCompat.hiveminds()) {
                HivemindStorage.resetDeployedMarks(hivemind);
            }
        }
    }

    /** 心智挨打或有目标时，从存储里应急投放一批。 */
    private static void tickHivemindDefence(MinecraftServer server) {
        if (!SporeAddFungusConfig.hivemindDeployWhenThreatened()) {
            return;
        }
        int count = SporeAddFungusConfig.hivemindThreatDeployCount();
        if (count <= 0) {
            return;
        }
        for (Proto hivemind : SporeCompat.hiveminds()) {
            if (hivemind.isRemoved() || !(hivemind.level() instanceof ServerLevel level) || !isThreatened(hivemind)) {
                continue;
            }
            var data = hivemind.getPersistentData();
            if (data.getInt(KEY_THREAT_COOLDOWN) > server.getTickCount()) {
                continue;   // 冷却中：被围殴时不该每拍都放
            }
            data.putInt(KEY_THREAT_COOLDOWN,
                    server.getTickCount() + SporeAddFungusConfig.hivemindThreatDeployCooldownTicks());
            HivemindStorage.deploy(hivemind, level, hivemind.position(), count);
        }
    }

    /**
     * 心智是不是正处于威胁之下。
     *
     * <p>用 Spore 自己的两个状态：有没有攻击目标、以及最近 {@code hivemindThreatWindowSeconds}
     * 秒内有没有挨过打。
     * 不去自己扫附近敌人——那既能省一次实体查询，也保证"威胁"的判定与 Spore 的 AI 一致。
     */
    private static boolean isThreatened(Proto hivemind) {
        return hivemind.getTarget() != null
                || hivemind.tickCount - hivemind.getLastHurtByMobTimestamp()
                        < SporeAddFungusConfig.hivemindThreatWindowTicks();
    }

    /** 推进一场袭击。返回 true 表示它已经结束。 */
    private static boolean tickRaid(MinecraftServer server, FungalRaid raid) {
        ServerPlayer player = server.getPlayerList().getPlayer(raid.target());
        // 目标下线就直接结束：袭击是"打某个人"，人不在就没有对象了。
        // 刻意不给他记"打赢"——那不叫打赢，叫逃跑，需求也没说该奖励这个。
        if (player == null || player.isDeadOrDying()) {
            LOGGER.info("[SporeAdd] 真菌袭击结束：目标玩家已不在或已死亡");
            return true;
        }

        // 玩家躲在黑名单维度里 → **冻结整场袭击，只累计缺席时间**。
        // 阶段计时一并停住（见 FungalRaid#tickAway）：不停的话躲进去几秒就会被竞技之须的
        // 超时判失败，等于"躲一下就直接输"，与"给 300 秒回战场"这个规则自相矛盾。
        // 逾期不归才判失败，走的是"死于真菌之手"那条线。
        if (!RaidDimensionBlacklist.raidsAllowed(player.serverLevel())) {
            raid.tickAway();
            if (raid.awayTicks() >= SporeAddFungusConfig.raidAbsenceTimeoutTicks()) {
                onPlayerDefeated(player, raid, "躲进黑名单维度超过时限未归");
                return true;
            }
            return false;
        }
        // 回来了：缺席计时归零，之后每次都从零开始算——所以"出去一下再回来"不会累积
        raid.resetAway();

        raid.tick();
        return switch (raid.phase()) {
            case PREP -> tickPrep(player, raid);
            case ARENA -> tickArena(player, raid);
            case WAVES -> tickWaves(player, raid);
        };
    }

    /**
     * 准备阶段。
     *
     * <p>发动要同时满足两件事：<b>至少熬过了 {@code raid.prep.seconds}</b>（这段是给玩家的反应窗口，
     * 也是台词之后"心智开始全力运转"能被感知到的那一段），以及<b>三项发动条件全满足</b>。
     * 熬到 {@code raid.prep.maxSeconds} 还没凑够就取消。
     */
    private static boolean tickPrep(ServerPlayer player, FungalRaid raid) {
        if (raid.totalTicks() >= SporeAddFungusConfig.raidPrepTimeoutTicks()) {
            player.displayClientMessage(Component.translatable("message.spore_add.raid_fizzled"), false);
            LOGGER.info("[SporeAdd] 真菌袭击取消：准备超时，条件未达成");
            return true;
        }
        if (raid.phaseTicks() < SporeAddFungusConfig.raidPrepTicks()
                || raid.phaseTicks() % SporeAddFungusConfig.raidLaunchCheckIntervalTicks() != 0) {
            return false;
        }
        // 离发动还有 warnLeadTicks 时发「恐惧吧」——需求里那句台词的基准是"竞技之须出现前 10 秒"，
        // 不是"准备阶段开始时"。放在这里就与"掷骰结果无关"这件事解耦了：
        // 无论后面抽不中竞技之须，这句警告都已经按同一个时间点发出去了。
        if (!raid.warned()
                && raid.phaseTicks() >= SporeAddFungusConfig.raidPrepTicks() - SporeAddFungusConfig.raidWarnLeadTicks()) {
            raid.markWarned();
            announceDread(player);
        }

        Assessment assessment = assess(player);
        if (!assessment.ready()) {
            // 「袭击一直不发动」唯一能看到原因的地方：三个实测值与三个阈值一起打出来。
            // 本行跑到这里时每 raid.launchCheckInterval 秒才一次，不算热路径，
            // 所以直接调 log()（它自己会判开关），不加 on() 守卫。
            SporeAddDebug.log(Area.RAID, "准备未达成：资源 {}/{}、数量 {}/{}、质量 {}/{}",
                    assessment.biomass(), SporeAddFungusConfig.raidLaunchMinBiomass(),
                    assessment.fungusCount(), SporeAddFungusConfig.raidLaunchMinFungusCount(),
                    assessment.quality(), SporeAddFungusConfig.raidLaunchMinQuality());
            return false;
        }
        launchAttack(player, raid);
        LOGGER.info("[SporeAdd] 真菌袭击正式发动（资源 {} / 数量 {} / 质量 {}）",
                assessment.biomass(), assessment.fungusCount(), assessment.quality());
        return false;
    }

    /**
     * 从准备阶段正式进入攻击。
     *
     * <p>先掷一次「竞技之须出不出现」——概率随恨意档位上升（需求：恨意值越高出现概率越大）。
     * 中了就先打它的波次，没中就直接进我们的围剿波次。
     */
    private static void launchAttack(ServerPlayer player, FungalRaid raid) {
        if (rollArenaChance(player)) {
            if (spawnArena(player, raid)) {
                raid.advanceTo(FungalRaid.Phase.ARENA);
                player.displayClientMessage(Component.translatable("message.spore_add.raid_arena"), false);
                return;
            }
            // 实体创建失败（理论上不会）：退化成直接打我们的波次，别把整场袭击卡住
            LOGGER.warn("[SporeAdd] 竞技之须创建失败，直接进入我方围剿波次");
        }
        enterOwnWaves(player, raid);
    }

    /** 竞技之须的出现概率：基准 + 档位 × 每档增量，再被上限夹住。 */
    private static boolean rollArenaChance(ServerPlayer player) {
        long tier = HatredValues.thresholdIndex(HatredManager.of(player));
        double chance = Math.min(SporeAddFungusConfig.raidArenaChanceMax(),
                SporeAddFungusConfig.raidArenaChanceBase()
                        + tier * SporeAddFungusConfig.raidArenaChancePerTier());
        double roll = player.getRandom().nextDouble();
        // 没出竞技之须有两种成因——掷骰没中，或者建实体失败（见 launchAttack 那条 warn）。
        // 这一行把前者与"档位/概率配错了"分开：每场袭击只跑一次，冷路径。
        SporeAddDebug.log(Area.RAID, "竞技之须掷骰：档位 {}、概率 {}、掷出 {} → {}",
                tier, chance, roll, roll < chance ? "出现" : "不出现");
        return roll < chance;
    }

    /** 在玩家附近种一只竞技之须，并记下它供后续追踪。 */
    private static boolean spawnArena(ServerPlayer player, FungalRaid raid) {
        var tendril = SporeCompat.spawnArenaTendril(player.serverLevel(), player.position(),
                SporeAddFungusConfig.raidArenaWaveSize(), SporeAddFungusConfig.raidArenaWaveLevel());
        if (tendril == null) {
            return false;
        }
        raid.setArenaTendril(tendril.getUUID());
        return true;
    }

    /**
     * 前半段：竞技之须在打，我们只负责盯它 + 给它的兵上增益。
     *
     * <h2>两种收场，判定的胜负是相反的</h2>
     * <ul>
     *   <li><b>它自己缩回消失了</b>——那是 Spore 的结束判定（场上真菌少于 4 只），
     *       也就是玩家把它的波次清干净了。<b>这是通过</b>，于是进我方围剿波次继续打。</li>
     *   <li><b>超时</b>（{@code raid.arena.timeoutSeconds}）——整整那段时间里场上真菌始终没被清干净，
     *       也就是玩家<b>没能通过</b>这段挑战。<b>这是失败</b>：整场袭击在此结束，
     *       按"玩家被真菌击杀"那条线结算——削他一大笔恨意值，并把损失换算成资源给所有心智。</li>
     * </ul>
     *
     * <p>超时不判失败的话，会有一个很难解释的漏洞：玩家只要跑远（竞技之须扫不到宿主、波次不再增长），
     * 就能白等到超时、然后照样去打我方波次——等于跳过了整段挑战。判失败之后，
     * "跑掉"就不再是一条比"打赢"更划算的路。
     */
    private static boolean tickArena(ServerPlayer player, FungalRaid raid) {
        applyCombatBuffs(player, 0);

        if (!arenaAlive(player, raid)) {
            LOGGER.info("[SporeAdd] 竞技之须已缩回（玩家清干净了它的波次），进入我方围剿波次");
            enterOwnWaves(player, raid);
            return false;
        }
        if (raid.phaseTicks() >= SporeAddFungusConfig.raidArenaTimeoutTicks()) {
            onPlayerDefeated(player, raid, "竞技之须超时仍未清场");
            return true;
        }
        return false;
    }

    /**
     * 玩家输掉这场袭击的结算。
     *
     * <p>走的是需求指定的那条线：{@link HatredManager#punishPlayerDefeat}——
     * 就是"死于真菌之手"用的同一段代码（削 {@code death.fungusDeathLossRatio}，
     * 并把损失 ×{@code death.resourceMultiplier} 的资源给所有心智）。
     *
     * <p>这里<b>不</b>发我方战利品：那是打赢才有的。竞技之须那一段的奖励由它自己的
     * {@code drops} 表决定，与玩家的胜负无关（它缩回时就掉）。
     */
    private static void onPlayerDefeated(ServerPlayer player, FungalRaid raid, String reason) {
        double lost = HatredManager.punishPlayerDefeat(player);
        player.displayClientMessage(Component.translatable("message.spore_add.raid_failed"), false);
        LOGGER.info("[SporeAdd] 真菌袭击结束：玩家失败（{}），恨意值 -{}", reason, (long) lost);
    }

    /** 我们种下的那只竞技之须还在不在。 */
    private static boolean arenaAlive(ServerPlayer player, FungalRaid raid) {
        UUID tendril = raid.arenaTendril();
        if (tendril == null) {
            return false;
        }
        return player.serverLevel().getEntity(tendril) instanceof ArenaEntity;
    }

    /** 定下波数、进入我方围剿波次。 */
    private static void enterOwnWaves(ServerPlayer player, FungalRaid raid) {
        long tier = HatredValues.thresholdIndex(HatredManager.of(player));
        int waves = (int) Math.min(SporeAddFungusConfig.raidOwnWaveMax(),
                SporeAddFungusConfig.raidOwnWaveBase() + tier * SporeAddFungusConfig.raidOwnWavePerTier());
        raid.setTotalWaves(waves);
        raid.advanceTo(FungalRaid.Phase.WAVES);
        player.displayClientMessage(Component.translatable("message.spore_add.raid_waves", waves), false);
        LOGGER.info("[SporeAdd] 我方围剿波次开始，共 {} 波", waves);
    }

    /**
     * 攻击阶段：按节拍传送 + 上增益，时间到就判定玩家"打赢"。
     *
     * <p>"打赢"的判据就是<b>活到攻击阶段结束</b>。这是需求 7 后半条（打赢袭击降 10% 恨意值）
     * 的接入点。玩家中途死了走的是另一条路（{@code HatredEvents} 里死于真菌之手那一条），
     * 两边不会重复结算——死亡会让 {@code tickRaid} 在下一 tick 就结束这次袭击。
     */
    /**
     * 后半段：我们自己的围剿波次。
     *
     * <h2>一波是怎么走的</h2>
     * <ol>
     *   <li><b>开头</b>：传送一批真菌到玩家周围（数量由 {@code raid.ownWave.countBase} 与
     *       {@code raid.ownWave.countPerWave} 按波次算，见 {@link SporeAddFungusConfig#raidOwnWaveCount}），
     *       并给全场真菌刷新参战增益——amplifier 随波数递增，即需求「波次越高，真菌越强」。</li>
     *   <li><b>结束</b>：玩家周围 {@code raid.ownWave.clearRadius} 内的真菌少于 {@code raid.ownWave.clearCount} 只
     *       ——也就是需求里的「清完了」——就进下一波。</li>
     *   <li><b>兜底</b>：{@code raid.ownWave.seconds} 到了还没清完也强行进下一波。
     *       没有它，一波打不完的仗会让整场袭击永远停在这里。</li>
     * </ol>
     *
     * <h2>"每波无上限"靠什么兜住</h2>
     * 需求把每波数量改成了随波次增长的公式、取消了人为上限。真正的上限来自世界本身：
     * <ul>
     *   <li>只传送<b>已经在世界里</b>的真菌（不凭空生成），所以 {@code raid.attack.teleportSearchRadius} 内
     *       一共有多少可调，就是这一波的上限；</li>
     *   <li>那些真菌的总数本身又受 Spore 的 Despawn 上限约束——而那个上限在攻击期间被我们翻倍了。</li>
     * </ul>
     * 换句话说：数字要得多，但拿不到那么多时就只能给现有的这些，不会凭空变出怪来。
     */
    private static boolean tickWaves(ServerPlayer player, FungalRaid raid) {
        // 参战增益每波都刷新，amplifier 随波数递增（第一波是 0）
        applyCombatBuffs(player, raid.waveAmplifierBonus(SporeAddFungusConfig.raidOwnWaveBuffsPerWave()));

        // 每一波开始时先送来一批，而不是等一个间隔——否则"这一波开始了"这个瞬间看不出来
        if (raid.phaseTicks() == 1) {
            teleportWave(player, raid.currentWave());
            deployFromStorage(player);
        }

        boolean cleared = fungusNear(player) < SporeAddFungusConfig.raidOwnWaveClearCount();
        if (!cleared && raid.phaseTicks() < SporeAddFungusConfig.raidOwnWaveTicks()) {
            return false;
        }

        raid.markWaveDone();
        if (!raid.allWavesDone()) {
            // 进入下一波：只把阶段内的计时归零，不换阶段
            raid.restartPhaseTicks();
            LOGGER.info("[SporeAdd] 围剿第 {} / {} 波{}", raid.wavesDone(), raid.totalWaves(),
                    cleared ? "清空" : "超时");
            return false;
        }

        // 全波打完 → 玩家存活，判定打赢：给奖励 + 削恨意值（需求 7 的后半条）
        awardOwnLoot(player, raid);
        HatredManager.reduceByRatio(player, SporeAddFungusConfig.raidWinLossRatio());
        player.displayClientMessage(Component.translatable("message.spore_add.raid_survived"), false);
        LOGGER.info("[SporeAdd] 真菌袭击结束：{} 波全部存活，判定为打赢", raid.totalWaves());
        return true;
    }

    /**
     * 给我方围剿的奖励：按配置的战利品表在玩家附近掉落。
     *
     * <p>掷骰次数 = 波数 × {@code raid.ownLoot.rollsPerWave}，所以波数越多（= 恨意值越高）奖励越丰厚，
     * 正是需求要的「完成后奖励越丰厚」。
     *
     * <p>掉在玩家附近而不是脚下：脚下会和他自己捡的东西混成一堆，太远又找不到。
     */
    private static void awardOwnLoot(ServerPlayer player, FungalRaid raid) {
        if (raid.totalWaves() <= 0) {
            return;
        }
        int rolls = raid.totalWaves() * SporeAddFungusConfig.raidOwnLootRollsPerWave();
        if (rolls <= 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        double radius = SporeAddFungusConfig.raidOwnLootRadius();

        for (String entry : SporeAddFungusConfig.raidOwnLoot()) {
            String[] parts = entry.split("\\|");
            if (parts.length != 3) {
                LOGGER.warn("[SporeAdd] 我方战利品条目格式应为 物品id|最少|最多，已跳过：{}", entry);
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item == null) {
                LOGGER.warn("[SporeAdd] 我方战利品里的物品 id 不存在，已跳过：{}", entry);
                continue;
            }
            int min;
            int max;
            try {
                min = Integer.parseInt(parts[1].trim());
                max = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("[SporeAdd] 我方战利品里的数字不合法，已跳过：{}", entry);
                continue;
            }
            for (int i = 0; i < rolls; i++) {
                int count = min >= max ? min : min + player.getRandom().nextInt(max - min + 1);
                if (count <= 0) {
                    continue;
                }
                Vec3 dropPos = scatterPosition(player, player.position(), radius);
                ItemEntity drop = new ItemEntity(level, dropPos.x(), player.getY(), dropPos.z(),
                        new ItemStack(item, count));
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
        }
    }

    /**
     * 给玩家附近所有真菌刷新参战增益。
     *
     * <p>用"扫一遍附近的真菌"而不是"给每只新生成的上 buff"：这样竞技之须自己召出来的、
     * 以及我们传送过来的，走的是同一条路，不必去 hook Spore 的召唤流程。
     * 扫的半径复用 {@code raid.prep.gatherRadius}。
     *
     * @param amplifierBonus 额外加到每个效果上的等级（波次越高越大）
     */
    private static void applyCombatBuffs(ServerPlayer player, int amplifierBonus) {
        if (SporeAddFungusConfig.raidAttackBuffs().isEmpty()) {
            return;
        }
        double radius = SporeAddFungusConfig.raidGatherRadius();
        AABB area = player.getBoundingBox().inflate(radius);
        List<Mob> fungus = player.serverLevel().getEntitiesOfClass(Mob.class, area).stream()
                .filter(mob -> !mob.isRemoved() && FungusCombat.isFungus(mob))
                // 把一个竞技之须排除在外——**尤其是玩家自己点燃大脑残块召出来的那种**。
                // 它本来就不在 #spore:fungus_entities 标签里（上面那行已经拦住了），
                // 这一行是防"某个整合包把它加进那个标签"：加了之后它会突然开始吃
                // 我们袭击的参战增益，而玩家手动召的那只显然不该接入我们的袭击系统。
                .filter(mob -> !SporeCompat.isArenaTendril(mob))
                .toList();
        for (Mob mob : fungus) {
            applyAttackBuffs(mob, amplifierBonus);
        }
    }

    // ------------------------------------------------------------------
    // 发动条件
    // ------------------------------------------------------------------

    /** 一次评估的结果。记下来主要是为了在日志里说明"为什么没发动"。 */
    private record Assessment(int biomass, int fungusCount, double quality) {

        boolean ready() {
            return biomass >= SporeAddFungusConfig.raidLaunchMinBiomass()
                    && fungusCount >= SporeAddFungusConfig.raidLaunchMinFungusCount()
                    && quality >= SporeAddFungusConfig.raidLaunchMinQuality();
        }
    }

    /**
     * 评估发动条件：资源、数量、质量。
     *
     * <p><b>资源</b>取所有心智里最高的那个，而不是总和或平均——发动袭击只需要"有一只心智准备好了"，
     * 不需要全阵营齐步走。
     *
     * <p><b>数量与质量</b>都统计目标玩家周围 {@code raid.prep.gatherRadius} 内的真菌，
     * 质量用的是恨意值那张等级表（{@link HatredValues#tierOf}），所以一只灾厄顶二十五只小兵。
     * 用同一个半径、同一次遍历把两个数一起算出来。
     */
    private static Assessment assess(ServerPlayer player) {
        int bestBiomass = 0;
        for (var hivemind : SporeCompat.hiveminds()) {
            bestBiomass = Math.max(bestBiomass, hivemind.getBiomass());
        }

        ServerLevel level = player.serverLevel();
        double radius = SporeAddFungusConfig.raidGatherRadius();
        AABB area = player.getBoundingBox().inflate(radius);
        int count = 0;
        double quality = 0.0D;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
            if (mob.isRemoved() || !FungusCombat.isFungus(mob)) {
                continue;
            }
            count++;
            quality += SporeAddFungusConfig.hatredTierMultiplier(HatredValues.tierOf(mob).ordinal());
        }
        return new Assessment(bestBiomass, count, quality);
    }

    // ------------------------------------------------------------------
    // 传送与增益
    // ------------------------------------------------------------------

    /**
     * 把一批真菌调到目标玩家附近。
     *
     * <p>三个约束对应需求里那句「不会过于贴近或远离」：
     * <ul>
     *   <li>只在距离 {@code [min, max]} 的环带上找落点，太近会怼到脸上、太远等于没传送；</li>
     *   <li>已经离玩家 {@code max} 以内的<b>不动</b>——它们本来就在场上，重新传送只会打乱已有的战斗；</li>
     *   <li>每波最多 {@code maxPerWave} 只，按离玩家的距离<b>由近到远</b>取，
     *       于是增援是"一批批到达"而不是一瞬间糊上来。</li>
     * </ul>
     *
     * <p>搜索中心优先取离玩家最近的心智：需求说的是"被心智传送"，那么从心智身边调兵才对。
     * 一只心智都没有时才退化成以玩家为中心搜——否则袭击会因为"心智离得远"而完全哑火。
     *
     * <p>落点用 {@code Mob#randomTeleport}：它自己会找附近的合法落脚点（不会塞进方块里），
     * 比我们硬算一个 y 稳。
     */
    /**
     * 把这一波该来的真菌调到玩家周围。
     *
     * @param wave 第几波（从 1 开始），用来算这一波送多少只
     */
    private static void teleportWave(ServerPlayer player, int wave) {
        ServerLevel level = player.serverLevel();
        Vec3 playerPos = player.position();
        Vec3 center = nearestHivemindPos(level, playerPos);
        double searchRadius = SporeAddFungusConfig.raidTeleportSearchRadius();
        double maxDistance = Math.max(SporeAddFungusConfig.raidTeleportMaxDistance(),
                SporeAddFungusConfig.raidTeleportMinDistance());
        double maxDistanceSqr = maxDistance * maxDistance;

        AABB area = new AABB(center, center).inflate(searchRadius);
        List<Mob> candidates = level.getEntitiesOfClass(Mob.class, area).stream()
                .filter(mob -> !mob.isRemoved() && FungusCombat.isFungus(mob))
                // 已经在玩家身边的跳过，见方法注释
                .filter(mob -> mob.distanceToSqr(playerPos) > maxDistanceSqr)
                .sorted((a, b) -> Double.compare(a.distanceToSqr(playerPos), b.distanceToSqr(playerPos)))
                // 数量按波次算、不设人为上限；调不够就是调不够，见 tickWaves 的类注释
                .limit(SporeAddFungusConfig.raidOwnWaveCount(wave))
                .toList();

        for (Mob mob : candidates) {
            Vec3 landing = ringPosition(player, playerPos);
            mob.randomTeleport(landing.x(), player.getY(), landing.z(), false);
        }
        LOGGER.info("[SporeAdd] 第 {} 波：实到 {} 只", wave, candidates.size());
    }

    /**
     * 从最近那只心智的存储里投放一批。
     *
     * <p>与我方原本的"传送一批"是<b>两条独立的供给</b>：传送调的是世界里已有的真菌，
     * 投放放的是存储里的存量。存储里的生物不打标记的话每波都能用，打了标记就一场只用一次
     * （见 {@code HivemindStorage}）。
     */
    private static void deployFromStorage(ServerPlayer player) {
        int count = SporeAddFungusConfig.hivemindDeployPerWave();
        if (count <= 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Proto hivemind = nearestHivemind(level, player.position());
        if (hivemind == null) {
            return;
        }
        int deployed = HivemindStorage.deploy(hivemind, level, player.position(), count);
        if (deployed > 0) {
            LOGGER.info("[SporeAdd] 从存储投放 {} 只（总存量 {}，本次袭击剩余可投 {}）",
                    deployed, HivemindStorage.count(hivemind), HivemindStorage.availableCount(hivemind));
        }
    }

    /** 离该位置最近的心智（限同维度）。没有就返回 null。 */
    @Nullable
    private static Proto nearestHivemind(ServerLevel level, Vec3 pos) {
        Proto nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Proto hivemind : SporeCompat.hiveminds()) {
            if (hivemind.isRemoved() || hivemind.level() != level) {
                continue;
            }
            double distance = hivemind.position().distanceToSqr(pos);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = hivemind;
            }
        }
        return nearest;
    }

    /** 玩家周围 {@code raid.ownWave.clearRadius} 内还有多少真菌。用于判断这一波清完了没有。 */
    private static int fungusNear(ServerPlayer player) {
        double radius = SporeAddFungusConfig.raidOwnWaveClearRadius();
        AABB area = player.getBoundingBox().inflate(radius);
        return player.serverLevel().getEntitiesOfClass(Mob.class, area).stream()
                .filter(mob -> !mob.isRemoved() && FungusCombat.isFungus(mob))
                .toList().size();
    }

    /** 在玩家周围 {@code [min, max]} 的环带上均匀取一点（面积均匀，所以半径要开方）。 */
    private static Vec3 ringPosition(ServerPlayer player, Vec3 playerPos) {
        double min = SporeAddFungusConfig.raidTeleportMinDistance();
        double max = Math.max(SporeAddFungusConfig.raidTeleportMaxDistance(), min);
        double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = Math.sqrt(min * min + player.getRandom().nextDouble() * (max * max - min * min));
        return new Vec3(playerPos.x + Math.cos(angle) * radius, playerPos.y,
                playerPos.z + Math.sin(angle) * radius);
    }

    /**
     * 在以 {@code center} 为心、{@code radius} 为半径的圆盘内均匀取一点。
     *
     * <p>掉战利品用这个（不是上面那个环带）：奖励散在玩家周围一小圈就好，
     * 不需要"不能太近"那种约束，所以半径从 0 开始、并且同样按面积开方。
     */
    private static Vec3 scatterPosition(ServerPlayer player, Vec3 center, double radius) {
        double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
        double distance = Math.sqrt(player.getRandom().nextDouble()) * radius;
        return new Vec3(center.x + Math.cos(angle) * distance, center.y,
                center.z + Math.sin(angle) * distance);
    }

    /** 离给定位置最近的心智的位置；一只都没有时返回给定位置本身（退化成以玩家为中心搜）。 */
    private static Vec3 nearestHivemindPos(ServerLevel level, Vec3 fallback) {
        Vec3 nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (var hivemind : SporeCompat.hiveminds()) {
            if (hivemind.isRemoved() || hivemind.level() != level) {
                continue;
            }
            double distance = hivemind.position().distanceToSqr(fallback);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = hivemind.position();
            }
        }
        return nearest == null ? fallback : nearest;
    }

    /**
     * 给一只真菌上参战增益。
     *
     * <p>效果 id 解析失败就跳过（配置是自由文本）。这里每次都重新解析配置——
     * 与 {@code LootValues} 那边不同，本方法的调用频率是"每次传送一只"（每 10 秒几只），
     * 一次解析的开销完全不值得为它加一层缓存与失效逻辑。
     */
    private static void applyAttackBuffs(Mob mob, int amplifierBonus) {
        for (String entry : SporeAddFungusConfig.raidAttackBuffs()) {
            String[] parts = entry.split("\\|");
            if (parts.length != 3) {
                LOGGER.warn("[SporeAdd] 袭击增益条目格式应为 效果id|持续tick|amplifier，已跳过：{}", entry);
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
            MobEffect effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id);
            if (effect == null) {
                LOGGER.warn("[SporeAdd] 袭击增益里的效果 id 不存在，已跳过：{}", entry);
                continue;
            }
            try {
                int duration = Integer.parseInt(parts[1].trim());
                int amplifier = Integer.parseInt(parts[2].trim());
                // 需求「波次越高，真菌越强」：在配置给的等级上再加当前波次的加成。
                // 上限 127 是 amplifier 的字节上限，超了会静默损坏（与 FrostbiteLevels 同一个坑）
                mob.addEffect(new MobEffectInstance(effect, duration,
                        Math.min(127, Math.max(0, amplifier) + Math.max(0, amplifierBonus))));
            } catch (NumberFormatException e) {
                LOGGER.warn("[SporeAdd] 袭击增益里的数字不合法，已跳过：{}", entry);
            }
        }
    }

    // ------------------------------------------------------------------
    // 台词
    // ------------------------------------------------------------------

    /**
     * 准备阶段开场：台词 + 音效。
     *
     * <p>照抄 Spore 自己在 {@code HiveSpawn} 里的那套写法（{@code displayClientMessage} 一条
     * translatable 文本 + {@code Ssounds.REBIRTH} 的 notify 音效）。
     *
     * <p>{@code displayClientMessage} 的第二个参数是 {@code overlay}：传 false 走聊天框，
     * 与 Spore 那边一致——这一句是要留在聊天记录里的，不是一闪而过的动作栏提示。
     */
    private static void announcePrep(ServerPlayer player) {
        SporeCompat.playHivemindSummonSound(player);
        player.displayClientMessage(Component.translatable("message.spore_add.raid_declare"), false);
    }

    /**
     * 需求：竞技之须出现前 10 秒，心智发「恐惧吧」。
     *
     * <p>与开场那句分开是有意的：开场那句是"有人盯上你了"，这一句是"它要来了"。
     * 两句之间隔着整段准备期，玩家能据此判断还有多久要接战——
     * 而这一句的时机是按 {@code warnLeadSeconds} 从<b>发动时刻</b>倒推的，
     * 与"这次抽不抽得中竞技之须"无关（抽不中也会发，只是后面直接进我方波次）。
     */
    private static void announceDread(ServerPlayer player) {
        SporeCompat.playHivemindSummonSound(player);
        player.displayClientMessage(Component.translatable("message.spore_add.raid_dread"), false);
    }
}
