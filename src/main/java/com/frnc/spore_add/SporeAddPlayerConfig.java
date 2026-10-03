package com.frnc.spore_add;

import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * <b>玩家侧</b>的配置，写进 {@code config/spore_add-player-common.toml}。
 *
 * <h2>两份配置是按「谁受益」分的</h2>
 * <table border="1">
 *   <caption>想调什么，就开哪一份</caption>
 *   <tr><th>文件</th><th>里面是什么</th></tr>
 *   <tr><td>{@code spore_add-player-common.toml}（本类）</td>
 *       <td><b>让玩家更强、更好用</b>的东西：冰霜武器（冰霜新星、液态寒冷、冰雪的叹息）、
 *           恨意值给玩家的增益</td></tr>
 *   <tr><td>{@code spore_add-fungus-common.toml}（{@link SporeAddFungusConfig}）</td>
 *       <td><b>让真菌更强</b>的东西：真菌加强、恨意值系统本身、世界恨意值给真菌的减伤、
 *           资源、真菌袭击</td></tr>
 * </table>
 * 于是「想把怪调强一点」只动真菌那一份，「想让自己舒服一点」只动这一份，
 * 不必在两份文件之间来回对照。
 *
 * <p>还有第三份 {@code spore_add-debug-common.toml}（{@link SporeAddDebugConfig}），
 * 它<b>不参与</b>上面这条分类——那是默认全关的调试检查点，横跨玩家侧与真菌侧。
 *
 * <h2>为什么文件名是手写的</h2>
 * 三份都用显式文件名注册。Forge 默认按 {@code modId-类型} 拼名字，三个 COMMON 会撞成同一个
 * {@code spore_add-common.toml}，而 {@code ConfigTracker} 撞名会直接抛
 * {@code "Config conflict detected!"} 把游戏崩掉。
 *
 * <h2>为什么是 COMMON 而不是 SERVER</h2>
 * 这里所有数值都是<b>服务端权威</b>的玩法参数，客户端一个都不读，
 * 所以两种类型都能用。选 COMMON 的理由是：它不依赖世界存在（专用服务端一启动就能生成文件），
 * 而且只有一份全局文件；SERVER 类型会按存档分别生成、并把值同步给客户端——那份同步对我们毫无用处。
 * 代价是它不跟存档走：换个存档用的是同一份数值。
 *
 * <h2>这一份不会、也不能搬进数据包</h2>
 * 有几项要被<b>客户端</b>读取：{@code FrostNovaItem} 的 tooltip（射程 / 威力 / 二次爆炸那几行
 * {@code %s}）与 {@code SporeAddClient} 的蓄力动画都靠它。而数据包（{@code SimpleJsonResourceReloadListener}
 * 那一类）<b>只在服务端存在，也不会同步给客户端</b>——搬过去之后，专用服务器上客户端的 tooltip
 * 会显示错误数字。留在 COMMON 配置里是这个原因，不是懒得改。
 *
 * <p>真菌侧那一份同样是配置，但理由不同（它其实是纯服务端的）：那份注释里有完整的
 * 「表走数据包、旋钮留配置」的分工说明，见 {@link SporeAddFungusConfig}。
 *
 * <h2>段落顺序</h2>
 * 按「冰霜武器 → 冰霜的共用机制与对策 → 另一套武器体系 → 增益」排：
 * {@code frostNova}（含 {@code [frostNova.secondary]}）→ {@code frostSigh}（含 {@code [frostSigh.spike]}）
 * → {@code liquidCold} → {@code coolant} → {@code frostbite} → {@code warmth} →
 * {@code combustion} → {@code playerBuffs}。
 *
 * <p>其中 {@code frostbite} 与 {@code warmth} 是<b>所有冰霜来源共用</b>的机制与对策，
 * 所以排在各个武器之后而不是塞进某一件武器里：冷却液、液态寒冷、冰霜新星、冰雪的叹息
 * 全都走 {@code frostbite} 那一组数值。
 *
 * <p><b>三处顺序必须一致</b>：静态块里 {@code new} 的顺序（决定 toml 排布）、
 * 各内部 class 的声明顺序、以及访问器的排列顺序。加段或改段名时三处一起改。
 *
 * <h2>配置值一律不在静态初始化器里读</h2>
 * {@link #SPEC} 的静态块只<b>声明</b>各项，真正的 {@code get()} 全部发生在下面的访问器里，也就是
 * 游戏运行期。这样与配置何时加载无关，不会出现"配置还没读就取值"的异常。
 * {@code LiquidColdBlock} 与 {@code FrozenChunks} 也遵守这条——它们把原来的
 * {@code static final} 半径常量改成了调用时读。
 *
 * <h2>为什么冻伤等级的上限是 127</h2>
 * 配置里的 {@code frostbiteLevel} 指的是<b>界面上的显示层数</b>（本 mod 的约定：层数 = amplifier + 1，
 * 与 {@code coolant.frostbiteCap} 默认的 10 表示"封顶 10 层"一致）。它最终会写成 amplifier，
 * 而 amplifier 的网络同步与存档序列化都是字节，超过 127 会静默损坏（详见 {@code FrostbiteLevels} 的类注释），
 * 所以这里封到 127——正好是"层数 127 对应 amplifier 126"。
 */
public final class SporeAddPlayerConfig {

    /** 本类的配置文件名（含扩展名）。必须是显式的，理由见类注释。 */
    public static final String FILE_NAME = "spore_add-player-common.toml";

    /** 配置规格。主类构造时注册到 {@code ModConfig.Type.COMMON}。 */
    public static final ForgeConfigSpec SPEC;

    private static final FrostNova FROST_NOVA;
    private static final LiquidCold LIQUID_COLD;
    private static final Coolant COOLANT;
    private static final FrostSigh FROST_SIGH;
    private static final MistClear MIST_CLEAR;
    private static final Frostbite FROSTBITE;
    private static final Warmth WARMTH;
    private static final Combustion COMBUSTION;
    private static final PlayerBuffs PLAYER_BUFFS;

    static {
        // **这里的顺序就是生成出来的 toml 里的段落顺序**，也就是玩家打开文件看到的顺序：
        // 本文件里唯一决定"段落排布"的地方就是这里，别在别处找。
        // 按「冰霜武器 → 冰霜的共用机制与对策 → 另一套武器体系 → 增益」排。
        // 加段或改段名时，下面写进 toml 的那张段落清单必须同步，否则玩家看到的索引就是错的。
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // 写进 toml 文件开头的总说明。类注释里那张表玩家看不到（那只在源码里），
        // 所以这里必须再写一遍——玩家打开文件的第一眼就该知道"这份管什么、另一份在哪"。
        builder.comment(
                "Spore Add —— 玩家侧配置。",
                "",
                "这一份管「让玩家更强、更好用」的东西。",
                "让真菌更强的那些在另一份文件里：spore_add-fungus-common.toml",
                "还有一份调试用的 spore_add-debug-common.toml（默认全关的检查点），不属于上面那条分类。",
                "（真菌加强、恨意值系统、世界恨意值给真菌的减伤、资源、真菌袭击）",
                "",
                "本文件的段落（按下面的顺序排列）：",
                "  frostNova        冰霜新星：长按右键蓄力的冰系武器",
                "      frostNova.secondary   首次爆炸后冰球变成的延时炸弹",
                "  frostSigh        冰雪的叹息：核弹方块",
                "      frostSigh.spike       它长出来的冰刺地形",
                "  mistClear       冰雾的持续清理——上面两件武器爆发后那团雾共用的机制",
                "  liquidCold       液态寒冷：影响半径与冰扩散",
                "  coolant          冷却液：它能把冻伤推到几层（封顶）",
                "  frostbite        冻伤层数机制——上面四件武器全部共用这一组数值",
                "  warmth           烈阳附魔：按件削弱冻伤",
                "  combustion       可燃 / 爆燃：高能燃料那一套",
                "  playerBuffs      恨意值给玩家的增益（攻击力 / 防御力 / 幸运值 / 减伤 / 最终伤害）",
                "",
                "改动在重启游戏、或执行 /reload 之后生效。",
                "完整的数值表与计算公式见随 jar 分发的 docs/values-and-formulas.md。");

        FROST_NOVA = new FrostNova(builder);
        FROST_SIGH = new FrostSigh(builder);
        MIST_CLEAR = new MistClear(builder);
        LIQUID_COLD = new LiquidCold(builder);
        COOLANT = new Coolant(builder);
        FROSTBITE = new Frostbite(builder);
        WARMTH = new Warmth(builder);
        COMBUSTION = new Combustion(builder);
        PLAYER_BUFFS = new PlayerBuffs(builder);
        SPEC = builder.build();
    }

    private SporeAddPlayerConfig() {
    }

    // ------------------------------------------------------------------
    // 冰霜新星
    // ------------------------------------------------------------------

    /** 满蓄力时替换方块的球半径（格）。蓄力不足时按 {@link #scaledByPower} 折算。 */
    public static int frostNovaBlockRadius() {
        return FROST_NOVA.blockRadius.get();
    }

    /** 满蓄力时受影响的实体球半径（格）。 */
    public static int frostNovaEntityRadius() {
        return FROST_NOVA.entityRadius.get();
    }

    /** 满蓄力时冻伤的持续秒数。 */
    public static int frostNovaFrostbiteSeconds() {
        return FROST_NOVA.frostbiteSeconds.get();
    }

    /** 满蓄力时的冻伤<b>显示层数</b>（= amplifier + 1）。 */
    public static int frostNovaFrostbiteLevel() {
        return FROST_NOVA.frostbiteLevel.get();
    }

    /** 满蓄力所需的 tick 数。 */
    public static int frostNovaChargeTicks() {
        return Math.max(1, FROST_NOVA.chargeTicks.get());
    }

    /** 低于这个蓄力 tick 数就完全不发射。 */
    public static int frostNovaMinChargeTicks() {
        // 夹到 chargeTicks 以内：配置是自由文本，写出"门槛比满蓄力还长"的话这里兜住，
        // 免得威力系数算出负数
        return Mth.clamp(FROST_NOVA.minChargeTicks.get(), 0, frostNovaChargeTicks());
    }

    /** 满蓄力时的爆炸强度。原版 TNT 是 4.0（见 {@code PrimedTnt#explode}），默认 8.0 即两倍。 */
    public static float frostNovaExplosionPower(double power) {
        return (float) (FROST_NOVA.explosionPower.get() * powerFactor(power));
    }

    /** 弹体的飞行速度，格/tick。 */
    public static double frostNovaSpeed() {
        return FROST_NOVA.speedPerTick.get();
    }

    /**
     * 弹体在多少 tick 之后自动引爆。
     *
     * <p>它防的是"弹体飞出已加载区块后原地冻结、越积越多"：本 mod 的弹体继承
     * {@code ThrowableProjectile}，那个基类<b>没有</b>原版火焰弹（{@code AbstractHurtingProjectile}）
     * 里那句 {@code hasChunkAt} 守卫，所以进入未加载区块时不会自己消失，只是停在那里不再 tick。
     * 有了这个上限，它在区块重新加载时会立刻引爆，而不是变成一颗悬着的活弹。
     */
    public static int frostNovaAutoDetonateTicks() {
        return FROST_NOVA.autoDetonateSeconds.get() * 20;
    }

    /** 一次爆炸那团霜雾的持续 tick 数。 */
    public static int frostNovaPrimaryCloudTicks() {
        return FROST_NOVA.cloudSeconds.get() * 20;
    }

    /** 二次爆炸的冰雾持续 tick 数。 */
    public static int frostNovaSecondaryCloudTicks() {
        return FROST_NOVA.secondaryCloudSeconds.get() * 20;
    }

    // ------------------------------------------------------------------
    // 二次爆炸（冰球变成的延时炸弹）
    // ------------------------------------------------------------------

    /** 首次爆炸之后多少 tick 二次引爆。 */
    public static int frostNovaSecondaryDelayTicks() {
        return FROST_NOVA.secondaryDelaySeconds.get() * 20;
    }

    /** 二次爆炸的影响范围倍率。只作用于范围（冰雾与冻伤的半径），<b>不含伤害</b>。 */
    public static double frostNovaSecondaryRangeMultiplier() {
        return FROST_NOVA.secondaryRangeMultiplier.get();
    }

    /**
     * 二次爆炸的冻伤强度倍率，层数与秒数一起乘。
     *
     * <p>二次爆炸没有伤害，所以"加强威力"只能体现在冻伤上，于是单独开一个旋钮，
     * 让它和范围倍率互不牵制。
     */
    public static double frostNovaSecondaryPowerMultiplier() {
        return FROST_NOVA.secondaryPowerMultiplier.get();
    }

    /**
     * 蓄力系数 → 威力倍数：最低蓄力时取 {@code minPowerFraction}，随蓄力<b>线性</b>升到 1。
     *
     * <p>所有随蓄力变化的数值都过这一个函数，所以它们的曲线必然一致——
     * 不会出现"半径涨得比层数快"这种只有改了才发现的不一致。整数类数值走
     * {@link #scaledByPower}，爆炸强度那种小数直接用它自己乘。
     *
     * @param power 蓄力系数，0 = 刚够发射，1 = 满蓄力；超出范围会被夹住
     */
    public static double powerFactor(double power) {
        double floor = FROST_NOVA.minPowerFraction.get();
        return floor + (1.0D - floor) * Mth.clamp(power, 0.0D, 1.0D);
    }

    /**
     * 把蓄力系数折算成某个"满蓄力上限"的当前值，用于整数类参数（两个半径、秒数、层数）。
     *
     * <p>结果至少为 1——半径 0 的爆发是"什么都不发生"，那不该是一个能发射出来的结果。
     *
     * @param max 满蓄力时的取值（也就是配置里填的那个数）
     */
    public static int scaledByPower(int max, double power) {
        return Math.max(1, (int) Math.round(max * powerFactor(power)));
    }

    // ------------------------------------------------------------------
    // 冰雪的叹息（核弹）
    // ------------------------------------------------------------------

    /** 影响半径（格）。圆形，不是球。 */
    public static int frostSighRadius() {
        return FROST_SIGH.radius.get();
    }

    /** 冲击环从中心扩散到边缘所需的秒数。 */
    public static int frostSighShockwaveTicks() {
        // 至少 1 tick，否则除法会炸
        return Math.max(1, FROST_SIGH.shockwaveSeconds.get() * 20);
    }

    /** 激活后到爆发所需的 tick 数。 */
    public static int frostSighCountdownTicks() {
        return FROST_SIGH.countdownSeconds.get() * 20;
    }

    /** 是否在倒计时期间强制加载圆盘覆盖的区块。 */
    public static boolean frostSighForceLoadChunks() {
        return FROST_SIGH.forceLoadChunks.get();
    }

    /** 玩家被冲击环冰封的 tick 数。 */
    public static int frostSighFrozenTicks() {
        return FROST_SIGH.frozenSeconds.get() * 20;
    }

    /** 冲击环施加的冻伤<b>显示层数</b>（= amplifier + 1）。 */
    public static int frostSighFrostbiteLevel() {
        return FROST_SIGH.frostbiteLevel.get();
    }

    /** 冲击环施加的冻伤持续 tick 数。 */
    public static int frostSighFrostbiteTicks() {
        return FROST_SIGH.frostbiteMinutes.get() * 60 * 20;
    }

    /** 降雪持续的 tick 数。 */
    public static int frostSighSnowTicks() {
        return FROST_SIGH.snowMinutes.get() * 60 * 20;
    }

    /** 冰雾持续的 tick 数。 */
    public static int frostSighMistTicks() {
        return FROST_SIGH.mistMinutes.get() * 60 * 20;
    }

    /** 爆发后把影响范围内的生物群系改成哪一个（字符串形式的生物群系 id）。 */
    public static String frostSighColdBiome() {
        return FROST_SIGH.coldBiome.get();
    }

    /** 是否把冲击环扫过的掉落物冰封起来。 */
    public static boolean frostSighFreezeItems() {
        return FROST_SIGH.freezeItems.get();
    }

    /** 单次爆发最多冰封多少个掉落物。 */
    public static int frostSighFreezeMaxEntities() {
        return FROST_SIGH.freezeMaxEntities.get();
    }

    /**
     * 冰壳自动融化的 tick 数。
     *
     * <p>返回 <b>0 表示"永不自动融化"</b>（只能敲碎），<b>不是</b>"立刻融化"。
     * 调用方拿 0 当哨兵值判断要不要设到期时刻，所以配置里的 0 必须原样透传——
     * 这里刻意不套 {@code Math.max(1, ...)}，那会把"永不融化"悄悄变成"1 tick 后融化"。
     */
    public static int frostSighIceMeltTicks() {
        return FROST_SIGH.iceMeltSeconds.get() * 20;
    }

    /** 2 号冲击环比 1 号环晚多少 tick 起跑。 */
    public static int frostSighSecondRingDelayTicks() {
        return FROST_SIGH.secondRingDelaySeconds.get() * 20;
    }

    /** 蘑菇云的存活 tick 数。 */
    public static int frostSighMushroomTicks() {
        // 至少 1 tick，否则云生成的那一 tick 就把自己删了
        return Math.max(1, FROST_SIGH.mushroomSeconds.get() * 20);
    }

    /** 圆盘内每个流体源变成液态寒冷的概率。 */
    public static double frostSighSourceToLiquidColdChance() {
        return FROST_SIGH.sourceToLiquidColdChance.get();
    }

    /** 铺三层冰的相对半径分界（小于它一律三层）。 */
    public static double frostSighFullLayersUntil() {
        return FROST_SIGH.fullLayersUntil.get();
    }

    /** 铺两层冰的相对半径分界（到它之前两层，再往外一层）。低于上一层分界时会被抬平。 */
    public static double frostSighTwoLayersUntil() {
        return Math.max(frostSighFullLayersUntil(), FROST_SIGH.twoLayersUntil.get());
    }

    /** 冰刺能长到多外（相对半径）。 */
    public static double frostSighSpikeMaxRelativeRadius() {
        return Math.max(0.01D, FROST_SIGH.spikeMaxRelativeRadius.get());
    }

    /** 低于这个高度干脆不长冰刺。 */
    public static int frostSighSpikeMinHeight() {
        return FROST_SIGH.spikeMinHeight.get();
    }

    /** 冰刺山尖的格子边长（格）。 */
    public static int frostSighSpikeCellSize() {
        return Math.max(1, FROST_SIGH.spikeCellSize.get());
    }

    /** 多少个格子里长一个山尖。越小越密。 */
    public static int frostSighSpikeRarity() {
        return Math.max(1, FROST_SIGH.spikeRarity.get());
    }

    /** 单根冰刺的底面半径（格）。 */
    public static int frostSighSpikeBaseRadius() {
        return Math.max(1, FROST_SIGH.spikeBaseRadius.get());
    }

    /** 正中心最高那根冰刺的高度（格）。 */
    public static int frostSighSpikeMaxHeight() {
        return Math.max(0, FROST_SIGH.spikeMaxHeight.get());
    }

    // ------------------------------------------------------------------
    // 冰雾的持续清理
    // ------------------------------------------------------------------

    /** 冰雾是否在存续期间持续清理真菌方块。 */
    public static boolean mistClearEnabled() {
        return MIST_CLEAR.enabled.get();
    }

    /** 冰雾每隔多少 tick 清理一次。至少 1。 */
    public static int mistClearIntervalTicks() {
        return Math.max(1, MIST_CLEAR.intervalTicks.get());
    }

    /**
     * 核弹的冰雾里，一道环从中心推到边缘要多少秒。
     *
     * <p>只对<b>核弹</b>有意义——冰霜新星那团雾是个小球，每次整球清一遍，不受本项影响。
     *
     * <p>它是那一段真正的性能旋钮：整个圆盘约 2000 万格，摊到多少秒上就是每 tick 多少格。
     */
    public static int mistClearPulseSeconds() {
        return Math.max(1, MIST_CLEAR.pulseSeconds.get());
    }

    /** 核弹的冰雾里，两道环之间歇多少秒。0 表示一道推完立刻接下一道。 */
    public static int mistClearPulseIdleSeconds() {
        return Math.max(0, MIST_CLEAR.pulseIdleSeconds.get());
    }

    // ------------------------------------------------------------------
    // 液态寒冷
    // ------------------------------------------------------------------

    /**
     * 液态寒冷的影响半径（格）。
     *
     * <p><b>区域寒冷效果、冰扩散、以及"范围内的冰不融化"必须用同一个半径</b>，
     * 否则"能看到冰的地方"与"会被冻的地方"就不再是同一片区域。所以
     * {@code LiquidColdBlock} 与 {@code FrozenChunks} 都从这里取值，不再各自持有常量。
     */
    public static int liquidColdRadius() {
        return LIQUID_COLD.radius.get();
    }

    /** 每个液态寒冷源头每秒做多少次冰扩散取样。 */
    public static int liquidColdSpreadAttemptsPerSecond() {
        return Math.max(1, LIQUID_COLD.spreadAttemptsPerSecond.get());
    }

    /** 每次取样成功替换方块的按概率。 */
    public static double liquidColdSpreadSuccessChance() {
        return LIQUID_COLD.spreadSuccessChance.get();
    }

    // ------------------------------------------------------------------
    // 冷却液
    // ------------------------------------------------------------------

    /**
     * 冷却液能把冻伤推到多少<b>显示层数</b>（= amplifier + 1）。
     *
     * <p><b>只封顶"涨"</b>：它不会压低从液态寒冷带过来的更高层数，
     * 所以冷却液在这个体系里的角色是"弱化源"而不是"解毒剂"。
     */
    public static int coolantFrostbiteCap() {
        return Math.max(1, COOLANT.frostbiteCap.get());
    }

    // ------------------------------------------------------------------
    // 冻伤层数机制（所有冰霜来源共用）
    // ------------------------------------------------------------------

    /**
     * 本模组施加的冻伤持续多少 tick。
     *
     * <p>只在<b>我们</b>调 {@code FrostbiteLevels#add} 时用得上：vanilla 在 amplifier 相同时
     * 取时长更长的一方，所以调小不会削弱 Spore 自己给的冻伤，只影响"离开冷源后多久自然退完"。
     */
    public static int frostbiteDurationTicks() {
        return Math.max(1, FROSTBITE.durationTicks.get());
    }

    /** 两次涨层之间的最短间隔（tick）。多个冷源重叠时靠它保证"每秒最多 +1"。 */
    public static int frostbiteMinIntervalTicks() {
        return Math.max(1, FROSTBITE.minIntervalTicks.get());
    }

    /** 每满一档附加的最大生命比例（加进 Spore 那一次冻结伤害）。 */
    public static double frostbiteMaxHealthBonusPerTenLevels() {
        return FROSTBITE.maxHealthBonusPerTenLevels.get();
    }

    /** 多少层算一档。它是除数，所以下限锁 1。 */
    public static int frostbiteLevelsPerBonusTier() {
        return Math.max(1, FROSTBITE.levelsPerBonusTier.get());
    }

    // ------------------------------------------------------------------
    // 烈阳附魔：按件削弱冻伤
    // ------------------------------------------------------------------

    /** 每件「烈阳」抵消的冻伤比例。四件叠满即全免。 */
    public static double warmthFrostbiteImmunityPerPiece() {
        return WARMTH.frostbiteImmunityPerPiece.get();
    }

    /**
     * 「烈阳」加速细雪冻结消退时，假设的 vanilla 每 tick 衰减量。
     *
     * <p><b>它必须等于原版实际的 2</b>（见 {@code aiStep} 里那个 {@code Math.max(0, i - 2)}）。
     * 这一项不改原版行为，只改我们"补零头"时用的基准——填错会让长期均值公式失真。
     */
    public static int warmthFrostDecayPerTick() {
        return Math.max(0, WARMTH.frostDecayPerTick.get());
    }

    /** 补发零头的周期（tick）。它是取模的模数，下限锁 1。 */
    public static int warmthMeltTopUpPeriodTicks() {
        return Math.max(1, WARMTH.meltTopUpPeriodTicks.get());
    }

    // ------------------------------------------------------------------
    // 可燃 / 爆燃
    // ------------------------------------------------------------------

    /** 「可燃」buff 的持续 tick 数。 */
    public static int combustionIgnitableDurationTicks() {
        return Math.max(1, COMBUSTION.ignitableDurationTicks.get());
    }

    /** 「爆燃」buff 的持续 tick 数。每次触发刷回满值。 */
    public static int combustionDeflagrationDurationTicks() {
        return Math.max(1, COMBUSTION.deflagrationDurationTicks.get());
    }

    /** 每层爆燃附加的固定火焰伤害点数。 */
    public static double combustionFireDamagePerStack() {
        return COMBUSTION.fireDamagePerStack.get();
    }

    /** 每满一档爆燃附加的最大生命比例。 */
    public static double combustionMaxHealthBonusPerTenStacks() {
        return COMBUSTION.maxHealthBonusPerTenStacks.get();
    }

    /** 多少层爆燃算一档。它是除数，所以下限锁 1。 */
    public static int combustionStacksPerBonusTier() {
        return Math.max(1, COMBUSTION.stacksPerBonusTier.get());
    }

    // ------------------------------------------------------------------
    // 恨意值给玩家的增益
    // ------------------------------------------------------------------
    //
    // 五项都是「每点换多少 + 封顶」两个数。前三项走原版属性修饰符，后两项走事件，
    // 具体在哪一层生效见 {@code PlayerHatredBuffs} 的类注释。

    /** 玩家每 1 点个人恨意值换到的基础攻击力。 */
    public static double buffAttackDamagePerHatred() {
        return PLAYER_BUFFS.attackDamagePerHatred.get();
    }

    /** 基础攻击力加成的上限。 */
    public static double buffMaxAttackDamage() {
        return PLAYER_BUFFS.maxAttackDamage.get();
    }

    /** 玩家每 1 点个人恨意值换到的防御力（原版护甲值）。 */
    public static double buffArmorPerHatred() {
        return PLAYER_BUFFS.armorPerHatred.get();
    }

    /** 防御力加成的上限。 */
    public static double buffMaxArmor() {
        return PLAYER_BUFFS.maxArmor.get();
    }

    /** 玩家每 1 点个人恨意值换到的幸运值。 */
    public static double buffLuckPerHatred() {
        return PLAYER_BUFFS.luckPerHatred.get();
    }

    /** 幸运值加成的上限。 */
    public static double buffMaxLuck() {
        return PLAYER_BUFFS.maxLuck.get();
    }

    /** 玩家每 1 点个人恨意值换到的减伤比例。 */
    public static double buffDamageReductionPerHatred() {
        return PLAYER_BUFFS.damageReductionPerHatred.get();
    }

    /** 玩家减伤的上限。不能到 1.0，否则玩家会变成打不死的存在。 */
    public static double buffMaxDamageReduction() {
        return PLAYER_BUFFS.maxDamageReduction.get();
    }

    /** 玩家每 1 点个人恨意值换到的最终伤害加成比例。 */
    public static double buffFinalDamagePerHatred() {
        return PLAYER_BUFFS.finalDamagePerHatred.get();
    }

    /** 玩家最终伤害加成的上限。 */
    public static double buffMaxFinalDamage() {
        return PLAYER_BUFFS.maxFinalDamage.get();
    }
    // ------------------------------------------------------------------
    // 各段配置的定义
    // ------------------------------------------------------------------

    /** 冰霜新星那一段。 */
    private static final class FrostNova {

        // 主表
        private final ForgeConfigSpec.IntValue blockRadius;
        private final ForgeConfigSpec.IntValue entityRadius;
        private final ForgeConfigSpec.DoubleValue explosionPower;
        private final ForgeConfigSpec.IntValue frostbiteSeconds;
        private final ForgeConfigSpec.IntValue frostbiteLevel;
        private final ForgeConfigSpec.DoubleValue minPowerFraction;
        private final ForgeConfigSpec.IntValue chargeTicks;
        private final ForgeConfigSpec.IntValue minChargeTicks;
        private final ForgeConfigSpec.DoubleValue speedPerTick;
        private final ForgeConfigSpec.IntValue autoDetonateSeconds;
        private final ForgeConfigSpec.IntValue cloudSeconds;

        // [frostNova.secondary] —— 这里的字段名保留 secondary 前缀，只是为了不与主表那个
        // cloudSeconds 撞名；它们在 toml 里的键名是去掉前缀的（表名已经表达了那层意思）。
        private final ForgeConfigSpec.IntValue secondaryDelaySeconds;
        private final ForgeConfigSpec.IntValue secondaryCloudSeconds;
        private final ForgeConfigSpec.DoubleValue secondaryRangeMultiplier;
        private final ForgeConfigSpec.DoubleValue secondaryPowerMultiplier;

        private FrostNova(ForgeConfigSpec.Builder builder) {
            builder.comment("冰霜新星（物品与它的投射物）：长按右键蓄力，松手扔出一枚直线飞行的弹体。",
                            "所有威力参数都随蓄力**线性**缩放，最低蓄力时只有满蓄力的 minPowerFraction 倍",
                            "（见 accessor 里的 powerFactor——曲线只有一处，所以不会出现「半径涨得比层数快」）。")
                    .push("frostNova");

            this.blockRadius = builder
                    .comment("满蓄力时，落点处被替换成冰的球半径（格）。1 ~ 16。",
                            "内半半径换蓝冰、其余换浮冰；不可破坏的方块（基岩等）永远不换。")
                    .defineInRange("blockRadius", 4, 1, 16);

            this.entityRadius = builder
                    .comment("满蓄力时，落点处被施加冻伤的实体球半径（格）。1 ~ 32。",
                            "这个半径只作用于实体，与换方块的 blockRadius 各管各的。")
                    .defineInRange("entityRadius", 12, 1, 32);

            this.explosionPower = builder
                    .comment("满蓄力时的爆炸强度。原版 TNT 是 4.0，默认 8.0 即 TNT 的两倍。0 ~ 32。",
                            "注意原版把伤害与波及范围绑在同一个值上：伤害的作用半径 = 该值 × 2 格，",
                            "中心满暴露伤害约 7 × 该值 × 2。所以调大时两者一起变大——这正是需求要的。",
                            "和其它威力参数一样随蓄力缩放：最低蓄力只有它的 minPowerFraction 倍。",
                            "填 0 表示不产生爆炸（伤害、击退、爆炸音效都没有，其它效果照常）。")
                    .defineInRange("explosionPower", 8.0D, 0.0D, 32.0D);

            this.frostbiteSeconds = builder
                    .comment("满蓄力时冻伤的持续秒数。1 ~ 600。")
                    .defineInRange("frostbiteSeconds", 32, 1, 600);

            this.frostbiteLevel = builder
                    .comment("满蓄力时的冻伤显示层数（界面上的那个数字，= amplifier + 1）。1 ~ 127。",
                            "上限 127 是因为它会写成 amplifier，而 amplifier 的同步与存档都是字节。")
                    .defineInRange("frostbiteLevel", 10, 1, 127);

            this.minPowerFraction = builder
                    .comment("最低蓄力（刚够发射）时的威力系数，0 ~ 1。",
                            "0.3 表示刚够发射时半径、层数、秒数都只有满蓄力的 30%，随蓄力线性升到 100%。")
                    .defineInRange("minPowerFraction", 0.20D, 0.0D, 1.0D);

            this.chargeTicks = builder
                    .comment("拉满所需的时间（tick）。20 与原版弓一致。1 ~ 200。")
                    .defineInRange("chargeTicks", 100, 1, 200);

            this.minChargeTicks = builder
                    .comment("低于这个蓄力时间（tick）松手就完全不发射。0 ~ 200。",
                            "比 chargeTicks 还大时会被夹到 chargeTicks。")
                    .defineInRange("minChargeTicks", 20, 0, 200);

            this.speedPerTick = builder
                    .comment("弹体的飞行速度。0.1 ~ 10.0，默认 1.5。",
                            "**单位是格/tick，不是格/秒**：1.5 格/tick 约合 30 格/秒，",
                            "配合 5 秒引信射程约 150 格。填 1.5 得到的不是 1.5 格/秒。",
                            "速度不随蓄力变化——蓄力影响的是落点爆发的威力，弹道保持一致更好预期。")
                    .defineInRange("speedPerTick", 1.5D, 0.1D, 10.0D);

            this.autoDetonateSeconds = builder
                    .comment("弹体投出后多少秒仍未命中就自动引爆。1 ~ 60。",
                            "默认 5 秒；弹速 1.5 格/tick，所以这也是约 150 格的最大射程。",
                            "它防的是弹体飞出已加载区块后原地冻结、越积越多（见访问器上的说明）。")
                    .defineInRange("autoDetonateSeconds", 5, 1, 60);

            this.cloudSeconds = builder
                    .comment("一次爆炸那团霜雾持续多少秒。1 ~ 600，默认 10。",
                            "它在主表里就是「这团雾」，不必再带 primary 前缀——",
                            "二次爆炸那团在下面的子表里，两边一眼能分清。",
                            "注意它和雾里冻伤的持续时间是两回事：冻伤时长由 frostbiteSeconds 决定。")
                    .defineInRange("cloudSeconds", 10, 1, 600);

            // ------------------------------------------------------------------
            // frostNova.secondary —— 首次爆炸后冰球变成的延时炸弹
            // ------------------------------------------------------------------
            // 单独成表，与访问器区里那块独立的「二次爆炸」注释对齐。
            // 它是一整套独立的效果（延时、范围、强度、雾），有自己的四个旋钮。
            builder.comment("二次爆炸：首次爆炸后，落点那团冰会变成一个延时炸弹，到点再炸一次。",
                            "**它不产生任何伤害**，只放大范围与冻伤强度——所以「加强二次爆炸」",
                            "就是加强冻伤，不会让它变成第二颗炸弹。",
                            "二次引爆会把那批冰清掉（变成空气），所以地表不会永久留着一个冰球。")
                    .push("secondary");

            this.secondaryDelaySeconds = builder
                    .comment("首次爆炸后多少秒二次引爆。1 ~ 600，默认 30。")
                    .defineInRange("delaySeconds", 30, 1, 600);

            this.secondaryRangeMultiplier = builder
                    .comment("影响范围倍率。1.0 ~ 4.0，默认 2.0。",
                            "只放大**范围**——冰雾与冻伤的半径；二次爆炸不产生任何伤害，",
                            "所以这里调多大都不会让伤害变高。")
                    .defineInRange("rangeMultiplier", 2.0D, 1.0D, 4.0D);

            this.secondaryPowerMultiplier = builder
                    .comment("冻伤强度倍率：层数与秒数一起乘。1.0 ~ 5.0，默认 1.5。",
                            "填 1.0 就是「只有范围变大、强度不变」。",
                            "层数会被夹到 127 上限（amplifier 的字节限制），超出部分不生效。")
                    .defineInRange("powerMultiplier", 1.5D, 1.0D, 5.0D);

            this.secondaryCloudSeconds = builder
                    .comment("冰雾持续多少秒。1 ~ 600，默认 20。",
                            "刻意比一次爆炸的 10 秒更长：二次爆炸范围更大，雾也该留得更久才撑得住。",
                            "注意它和冰雾里冻伤的持续时间是两回事：冻伤时长由 powerMultiplier 那一项决定。")
                    .defineInRange("cloudSeconds", 20, 1, 600);

            builder.pop();

            builder.pop();
        }
    }

    /** 冰雪的叹息那一段。 */
    private static final class FrostSigh {

        private final ForgeConfigSpec.IntValue radius;
        private final ForgeConfigSpec.IntValue shockwaveSeconds;
        private final ForgeConfigSpec.IntValue countdownSeconds;
        private final ForgeConfigSpec.BooleanValue forceLoadChunks;
        private final ForgeConfigSpec.IntValue frozenSeconds;
        private final ForgeConfigSpec.IntValue frostbiteLevel;
        private final ForgeConfigSpec.IntValue frostbiteMinutes;
        private final ForgeConfigSpec.IntValue snowMinutes;
        private final ForgeConfigSpec.IntValue mistMinutes;
        private final ForgeConfigSpec.ConfigValue<String> coldBiome;
        private final ForgeConfigSpec.BooleanValue freezeItems;
        private final ForgeConfigSpec.IntValue freezeMaxEntities;
        private final ForgeConfigSpec.IntValue iceMeltSeconds;
        private final ForgeConfigSpec.IntValue mushroomSeconds;
        private final ForgeConfigSpec.IntValue secondRingDelaySeconds;
        private final ForgeConfigSpec.DoubleValue sourceToLiquidColdChance;
        private final ForgeConfigSpec.DoubleValue fullLayersUntil;
        private final ForgeConfigSpec.DoubleValue twoLayersUntil;

        // [frostSigh.spike] —— 冰刺地形那一组
        private final ForgeConfigSpec.DoubleValue spikeMaxRelativeRadius;
        private final ForgeConfigSpec.IntValue spikeMinHeight;
        private final ForgeConfigSpec.IntValue spikeCellSize;
        private final ForgeConfigSpec.IntValue spikeRarity;
        private final ForgeConfigSpec.IntValue spikeBaseRadius;
        private final ForgeConfigSpec.IntValue spikeMaxHeight;

        private FrostSigh(ForgeConfigSpec.Builder builder) {
            builder.comment("冰雪的叹息（核弹方块）").push("frostSigh");

            this.radius = builder
                    .comment("影响半径（格）。圆形，不是球。1 ~ 256。",
                            "圆盘内的 (x,z) 列数按 π·r² 增长：半径 128 是 51,433 列、跨约 226 个区块。",
                            "调大它会让冲击环每 tick 要处理的列数成平方增长。")
                    .defineInRange("radius", 128, 1, 256);

            this.sourceToLiquidColdChance = builder
                    .comment("圆盘内每个**流体源**变成液态寒冷的概率。0.0 ~ 1.0，默认 0.01（1%）。",
                            "只对源头生效，流动的流体格只冻成冰——否则一片海会整片变成液态寒冷，",
                            "而液态寒冷又会往外扩散，等于一发核弹把整片水体变成会传染的冷源。")
                    .defineInRange("sourceToLiquidColdChance", 0.01D, 0.0D, 1.0D);

            this.fullLayersUntil = builder
                    .comment("铺三层冰的相对半径分界。0.0 ~ 1.0，默认 0.75。",
                            "相对半径小于它就一律铺 3 层，调小会让「薄冰区」提前出现、观感变成「没铺满」。",
                            "**注意层数本身（3/2/1）是写死的**：本项与下面那条只改「在哪变薄」，",
                            "不改「变到几层」——别指望靠它们铺出四层或两层平的冰。")
                    .defineInRange("fullLayersUntil", 0.75D, 0.0D, 1.0D);

            this.twoLayersUntil = builder
                    .comment("铺两层冰的相对半径分界。0.0 ~ 1.0，默认 0.92。",
                            "到它之前铺 2 层、再往外 1 层。**必须 >= fullLayersUntil**，",
                            "否则分层顺序会反转（访问器里会把它抬平）。")
                    .defineInRange("twoLayersUntil", 0.92D, 0.0D, 1.0D);

            this.shockwaveSeconds = builder
                    .comment("冲击环从中心扩散到边缘所需的秒数。1 ~ 300。",
                            "**这一档是整个方块最要紧的性能旋钮**：环的最外圈每 tick 要处理的列数",
                            "约为 2π·R/(20×该秒数)。半径 128 时：20 秒 → 每 tick 约 257 列（可行）；",
                            "1 秒 → 每 tick 约 5147 列（必然卡服）。")
                    .defineInRange("shockwaveSeconds", 20, 1, 300);

            this.countdownSeconds = builder
                    .comment("用冰霜新星激活后，多少秒爆发。1 ~ 600。")
                    .defineInRange("countdownSeconds", 60, 1, 600);

            this.forceLoadChunks = builder
                    .comment("倒计时期间是否强制加载圆盘覆盖的区块。",
                            "关掉的话，未加载的区块不会被影响——玩家事后走过去会看到圆盘边缘一圈「没被冻到」，",
                            "像效果没做完。开着则效果完整，但那片区域（半径 128 时约 226 个区块）会常驻加载几十秒。")
                    .define("forceLoadChunks", true);

            this.frozenSeconds = builder
                    .comment("冲击环扫过时，玩家被冰封（无法移动）的秒数。0 ~ 300。0 表示不冰封。")
                    .defineInRange("frozenSeconds", 10, 0, 300);

            this.frostbiteLevel = builder
                    .comment("冲击环扫过时施加的冻伤显示层数（= amplifier + 1）。1 ~ 127。",
                            "默认 100 是刻意做成必死的量级；它同样受「烈阳」的按件削弱影响。")
                    .defineInRange("frostbiteLevel", 100, 1, 127);

            this.frostbiteMinutes = builder
                    .comment("上述冻伤持续多少分钟。1 ~ 60。")
                    .defineInRange("frostbiteMinutes", 10, 1, 60);

            this.snowMinutes = builder
                    .comment("爆发后降雪持续多少分钟。1 ~ 60。默认 10（需求原本写 60，但那是很重的负担）。",
                            "注意原版天气是按维度的，这里只做到「玩家附近撒雪粒子」；",
                            "真正的雪景靠把生物群系改成寒带、让原版降水在该处自然变成雪。")
                    .defineInRange("snowMinutes", 10, 1, 60);

            this.mistMinutes = builder
                    .comment("冰雾持续多少分钟。1 ~ 60。",
                            "冰雾用的是核弹爆发前那团云的同一个粒子，深蓝色，铺在 2 号环扫过的区域上，",
                            "范围跟着 2 号环的环半径长。雾里的生物会被持续施加冻伤，",
                            "**等级与时长就是上面那一组 frostbiteLevel / frostbiteMinutes**。")
                    .defineInRange("mistMinutes", 10, 1, 60);

            this.coldBiome = builder
                    .comment("爆发后把影响范围内的生物群系改成哪一个。",
                            "填完整的生物群系 id，例如 minecraft:snowy_plains、minecraft:ice_spikes、",
                            "minecraft:frozen_peaks、minecraft:snowy_taiga。填错会在日志里报一行警告并跳过改群系。")
                    .define("coldBiome", "minecraft:snowy_plains");

            this.freezeItems = builder
                    .comment("是否把冲击环范围内的掉落物冰封起来：外面裹一层可敲碎的半透明冰壳，",
                            "敲碎才掉出来。这些掉落物主要是被击杀的生物掉出来的战利品。")
                    .define("freezeItems", true);

            this.freezeMaxEntities = builder
                    .comment("单次爆发最多冰封多少个掉落物。0 ~ 4096。",
                            "默认 512。一发打在刷怪塔上的核弹能掉出非常多的东西，",
                            "每件都配一层冰壳（一个实体）会明显吃性能，所以留一道闸门。",
                            "到顶之后剩下的掉落物就地留着，不封冰。")
                    .defineInRange("freezeMaxEntities", 512, 0, 4096);

            this.iceMeltSeconds = builder
                    .comment("冰壳多少秒后自动融化、把东西掉出来。0 ~ 86400。",
                            "**0 表示永不自动融化**（只能敲碎），不是「立刻融化」。",
                            "默认 0。想给「一地冰壳没人清理」留一个安全阀就调成别的值。")
                    .defineInRange("iceMeltSeconds", 0, 0, 86400);

            this.mushroomSeconds = builder
                    .comment("爆发后蘑菇云持续多少秒。1 ~ 600。",
                            "形状按「底部涌浪 + 茎 + 伞盖」采样撒粒子，尺寸随半径缩放，粒子总量有硬上限。")
                    .defineInRange("mushroomSeconds", 45, 1, 600);

            this.secondRingDelaySeconds = builder
                    .comment("2 号冲击环比 1 号环晚多少秒起跑。0 ~ 60。",
                            "两个环的职责是分开的：**1 号环只负责击杀**环内的生物，**2 号环负责其余全部效果**",
                            "（铺冰、冻流体、清真菌、施加冻伤、冰封玩家）。",
                            "留出这段间隔，是为了让「先死、后冻」在观感上分得开：先是一圈冲击过去清场，",
                            "隔一拍才铺开冰与霜。填 0 就是两环同时推。")
                    .defineInRange("secondRingDelaySeconds", 2, 0, 60);

            // ------------------------------------------------------------------
            // frostSigh.spike —— 冰刺地形
            // ------------------------------------------------------------------
            // 单独成表是因为它是一套**独立的地形生成算法**（一个 (x,z) 的纯函数），
            // 六个键互相耦合（间距、密度、底面半径、最高），自成一组；
            // 塞在主表里会让那 18 个键更长，也不好找。
            builder.comment("冰刺：爆发后在地面上长出来的锥形冰柱。",
                            "形状是 (x, z) 的纯函数（粗网格上的山尖 + 线性收锥），",
                            "所以世界重载后不需要任何存表就能复原；爆心只作为一个盐，让每场爆发布局不同。")
                    .push("spike");

            this.spikeMaxRelativeRadius = builder
                    .comment("冰刺能长到多外（相对半径）。0.01 ~ 1.0，默认 0.75。",
                            "它是中心系数的**除数**（相对半径 ÷ 本项 = 由内向外的衰减进度），",
                            "所以下限锁 0.01；填 0 会算出无穷高。",
                            "调小 = 冰刺只集中在中心一小片，外围全是平冰面。")
                    .defineInRange("maxRelativeRadius", 0.75D, 0.01D, 1.0D);

            this.spikeMinHeight = builder
                    .comment("低于这个高度干脆不长。0 ~ 320，默认 10（格）。",
                            "**这一项是冰刺好不好看的关键**：圆锥算出来大多是三五格高的矮桩子，",
                            "如果照单全收，地面会变成一片扎脚的碎冰而不是「冰刺之地」。",
                            "调 0 = 满地矮桩子（那正是原本要避免的东西）。")
                    .defineInRange("minHeight", 10, 0, 320);

            this.spikeCellSize = builder
                    .comment("冰刺山尖的格子边长（格）。1 ~ 64，默认 7。",
                            "它是 floorDiv 的**除数**，下限锁 1。",
                            "调小 = 山尖排得更密（更像一片林），调大 = 稀稀拉拉几根巨柱。")
                    .defineInRange("cellSize", 7, 1, 64);

            this.spikeRarity = builder
                    .comment("多少个格子里长一个山尖。1 ~ 64，默认 2（约一半）。",
                            "它是取模的**模数**，下限锁 1——**填 0 会直接抛 ArithmeticException 崩服**。",
                            "调大 = 更稀疏（很多格子完全没有刺）。")
                    .defineInRange("rarity", 2, 1, 64);

            this.spikeBaseRadius = builder
                    .comment("单根冰刺的底面半径（格）。1 ~ 32，默认 3。",
                            "它是高度公式里的**除数**，下限锁 1。",
                            "调小 = 更「针」（细而陡），调大 = 更「丘」（粗而缓）。")
                    .defineInRange("baseRadius", 3, 1, 32);

            this.spikeMaxHeight = builder
                    .comment("正中心最高那根冰刺的高度（格）。0 ~ 320，默认 70。",
                            "实际还会被世界高度上限截断（见 getMaxBuildHeight），",
                            "所以填得比建筑高度还大没有意义。")
                    .defineInRange("maxHeight", 70, 0, 320);

            builder.pop();

            builder.pop();
        }
    }

    /**
     * 冰雾的持续清理那一段。
     *
     * <p><b>冰霜新星与冰雪的叹息共用这一组数值</b>——两件武器爆发后都会留下一团雾，
     * 而需求是"这团雾在消散之前持续产生与 CDU 一样的效果"。
     *
     * <h2>为什么是"持续"而不是"爆发时多清几遍"</h2>
     * 爆发那一刻的清理是<b>一次性整片扫</b>的（见 {@code FungalClearing}），形状与雾一致。
     * 但雾会存在 10 秒到 10 分钟，这期间被感染的地形还在长——只清一次的话，
     * 雾还在飘、底下却已经重新长满，观感上"这雾没在做事"。
     *
     * <h2>为什么不用 CDU 的概率</h2>
     * {@code FungalClearing} 的类注释里写着它刻意丢掉了 CDU 的概率（数据包表 20%、生物质 10%、落叶 20%），
     * 理由是"新星是一次性爆发，照抄概率会留下 80% 的真菌方块"。这里沿用同一个决定：
     * 一次扫到的就必定清掉，靠<b>反复经过</b>而不是靠概率把范围覆盖完。
     */
    private static final class MistClear {

        private final ForgeConfigSpec.BooleanValue enabled;
        private final ForgeConfigSpec.IntValue intervalTicks;
        private final ForgeConfigSpec.IntValue pulseSeconds;
        private final ForgeConfigSpec.IntValue pulseIdleSeconds;

        private MistClear(ForgeConfigSpec.Builder builder) {
            builder.comment("冰雾的持续清理：雾在消散之前一直按 CDU 的规则清真菌方块。",
                            "「CDU 的规则」就是爆发那一下用的同一套（残骸→冰冻残骸、生物质→冻伤生物质、",
                            "配置/数据包的转换表、以及最后兜底变空气），见 FungalClearing 的类注释。",
                            "**形状与各自爆发一致**：冰霜新星那团雾是个小球，每次整球清一遍；",
                            "冰雪的叹息那团铺在圆盘上，**每隔一段时间从中心推出一道可见的环**，",
                            "环推到哪就清到哪。")
                    .push("mistClear");

            this.enabled = builder
                    .comment("总开关。默认开。关掉 = 雾只剩粒子与冻伤，不再清方块（回到改动之前的行为）。")
                    .define("enabled", true);

            this.intervalTicks = builder
                    .comment("每隔多少 tick 清一次。1 ~ 200，默认 20（1 秒）。",
                            "**对冰霜新星而言这是性能旋钮**：它每次要扫整个球",
                            "（半径 10 时约 4 千个方块，半径 32 时约 13 万）。",
                            "**对冰雪的叹息而言本项只决定环的推进精度**——那边是逐 tick 沿环带推的，",
                            "每一步都很窄，所以调大它只会让环一顿一顿的，不会更省。")
                    .defineInRange("intervalTicks", 20, 1, 200);

            this.pulseSeconds = builder
                    .comment("冰雪的叹息：一道环从中心推到边缘要多少秒。1 ~ 600，默认 60。",
                            "**这是那一段真正的性能旋钮**：整盘 5 万列 × 每列 384 格高 ≈ 2000 万格，",
                            "摊到多少秒上，每 tick 就要处理 2000万 ÷ (秒 × 20) 格。",
                            "60 秒约 16k 格/tick——比爆发时那道 2 号环（20 秒扫完同样这些格子，约 49k/tick）更轻。",
                            "**调得太小会明显卡**：10 秒就是约 100k 格/tick。",
                            "调大则环推得从容，但同一段时间内覆盖全盘的遍数变少。")
                    .defineInRange("pulseSeconds", 60, 1, 600);

            this.pulseIdleSeconds = builder
                    .comment("两道环之间歇多少秒（上一道推完之后算起）。0 ~ 600，默认 30。",
                            "填 0 = 一道推完立刻接下一道，整段时间连绵不断地推。",
                            "**间隔越长，两次清理之间留给真菌再长的时间就越长**——",
                            "这是「看得见的稀有事件」与「地面一直干净」之间的取舍。")
                    .defineInRange("pulseIdleSeconds", 30, 0, 600);

            builder.pop();
        }
    }

    /** 液态寒冷那一段。 */
    private static final class LiquidCold {

        private final ForgeConfigSpec.IntValue radius;
        private final ForgeConfigSpec.IntValue spreadAttemptsPerSecond;
        private final ForgeConfigSpec.DoubleValue spreadSuccessChance;

        private LiquidCold(ForgeConfigSpec.Builder builder) {
            builder.comment("液态寒冷：影响半径，以及它往外冻的速度。").push("liquidCold");

            this.radius = builder
                    .comment("液态寒冷的影响半径（格）。1 ~ 16。",
                            "区域寒冷效果、冰扩散、以及范围内的原版冰不融化共用这一个半径，",
                            "所以改它就是同时改这三件事——它们本来就该是同一片区域。",
                            "上限 16 是冰扩散的取样密度决定的：它每秒只在球的外接立方体里随机取若干个点，",
                            "半径越大球内占比越低（半径 6 约 52%，半径 16 只剩约 12%），再大就几乎抽不到点、",
                            "扩散形同停止。范围判定本身没有这个限制（FrozenChunks 的区块窗口是跟着半径算的）。")
                    .defineInRange("radius", 8, 1, 16);

            this.spreadAttemptsPerSecond = builder
                    .comment("每个源头方块每秒做多少次冰扩散取样。1 ~ 256，默认 16。",
                            "这是扩散速度**唯一**的数量旋钮：取样次数多，才更容易探到球内的点。",
                            "它同时也是这一段的主要开销来源——次数翻倍，每秒的方块查询就翻倍。",
                            "下限锁 1：填 0 会让扩散彻底停摆，那不是「慢」，是「永远不扩散」，",
                            "而玩家只会以为流体坏了。想关掉扩散请把 spreadSuccessChance 填 0——",
                            "那个至少语义清楚。")
                    .defineInRange("spreadAttemptsPerSecond", 16, 1, 256);

            this.spreadSuccessChance = builder
                    .comment("每次取样成功替换方块的**概率**。0.0 ~ 1.0，默认 0.5。",
                            "**空气、流体、固体共用这一个值**：三种目标没有各自的优先级或独立概率，",
                            "\"空气先被换掉\"是取样落点分布的自然结果，不是写死的规则。",
                            "所以改它同时改变三类的相对速度，不会出现\"冰长得快但空气冻不上\"这种事。",
                            "填 0 = 只影响范围（失温与冻伤照旧），但不再长冰。")
                    .defineInRange("spreadSuccessChance", 0.5D, 0.0D, 1.0D);

            builder.pop();
        }
    }

    /**
     * 冷却液那一段。
     *
     * <p>冷却液本身很简单（像细雪一样失温并叠冻伤），唯一值得调的就是它<b>能把冻伤推到几层</b>——
     * 它是四种冰霜来源里最弱的一个，所以单独给它一个封顶。
     */
    private static final class Coolant {

        private final ForgeConfigSpec.IntValue frostbiteCap;

        private Coolant(ForgeConfigSpec.Builder builder) {
            builder.comment("冷却液：它能把冻伤推到几层为止。").push("coolant");

            this.frostbiteCap = builder
                    .comment("冷却液能把冻伤推到的**显示层数**上限（= amplifier + 1）。1 ~ 127，默认 10。",
                            "**只封顶「涨」、不压低已有的层数**：从液态寒冷（可能几十层）走进冷却液时，",
                            "层数原样保留、只是不再往上加。所以它是「弱化源」而不是「解毒剂」。",
                            "上限 127 是 amplifier 的字节同步硬限（层数 = amplifier + 1），再高也不会生效。",
                            "填 1 = 泡在里面也不再叠冻伤（但失温与减速照旧）。")
                    .defineInRange("frostbiteCap", 10, 1, 127);

            builder.pop();
        }
    }

    /**
     * 冻伤层数机制那一段。<b>四种冰霜来源全部共用这一组数值</b>。
     *
     * <p>它原先是 {@code FrostbiteLevels} 里的私有常量，提到配置里是因为
     * "冻伤每层打多少伤害"是玩家最想调、也最该能调的一项。
     */
    private static final class Frostbite {

        private final ForgeConfigSpec.IntValue durationTicks;
        private final ForgeConfigSpec.IntValue minIntervalTicks;
        private final ForgeConfigSpec.DoubleValue maxHealthBonusPerTenLevels;
        private final ForgeConfigSpec.IntValue levelsPerBonusTier;

        private Frostbite(ForgeConfigSpec.Builder builder) {
            builder.comment("冻伤层数机制：本 mod 施加的冻伤持续多久、涨得多快、每层打多少伤害。",
                            "**冷却液、液态寒冷、冰霜新星、冰雪的叹息全部走这一组数值**，",
                            "所以调它就是同时调这四件武器——它们本来就该是同一套冻伤。")
                    .push("frostbite");

            this.durationTicks = builder
                    .comment("本模组施加的冻伤持续多少 tick。20 ~ 2400，默认 240（12 秒）。",
                            "Spore 自己用的是 600 / 1200 tick，而 vanilla 在 amplifier 相同时取更长的一方，",
                            "所以这一项**不会削弱 Spore 自己的冻伤**：泡在冷源里被我们每秒刷新到本项时长，",
                            "一旦离开就按本项自然倒数。",
                            "调短 = 离开冷源后冻伤退得更快（对手更难受、你自己也更容易解脱）。")
                    .defineInRange("durationTicks", 240, 20, 2400);

            this.minIntervalTicks = builder
                    .comment("两次涨层之间的最短间隔（tick）。1 ~ 200，默认 20（每秒最多 +1 层）。",
                            "多个冷源重叠时（比如同时泡在液态寒冷里又被冰霜新星打中）靠它限速——",
                            "没有它的话同一 tick 里几个来源各加一层，层数会瞬间堆满。",
                            "调小 = 泡在冷源里涨得更快、上限到得更早。")
                    .defineInRange("minIntervalTicks", 20, 1, 200);

            this.maxHealthBonusPerTenLevels = builder
                    .comment("每满一档额外附加的最大生命比例。0.0 ~ 1.0，默认 0.02（2%）。",
                            "它是**加进 Spore 那一次冻结伤害**里的（调用方用 setAmount），",
                            "所以照常吃护甲、抗性与无敌帧，不会打出第二跳。",
                            "不足一档的零头不计——比如 19 层只算一档。")
                    .defineInRange("maxHealthBonusPerTenLevels", 0.02D, 0.0D, 1.0D);

            this.levelsPerBonusTier = builder
                    .comment("多少层算一档。1 ~ 127，默认 10。",
                            "**它是除数**（层数 ÷ 本项 = 档数），所以下限锁 1；填 0 会直接崩。",
                            "调小 = 每一档来得更快（配合上一项就是「总加成更高」），",
                            "调大则相反，但每层那 +1 点平伤不受本项影响。")
                    .defineInRange("levelsPerBonusTier", 10, 1, 127);

            builder.pop();
        }
    }

    /**
     * 「烈阳」附魔那一段：按件削弱冻伤。
     *
     * <p>它属于<b>玩家的对策</b>，与 {@link Frostbite} 正好是一对：那边管冻伤有多强，
     * 这边管玩家能挡掉多少。
     */
    private static final class Warmth {

        private final ForgeConfigSpec.DoubleValue frostbiteImmunityPerPiece;
        private final ForgeConfigSpec.IntValue frostDecayPerTick;
        private final ForgeConfigSpec.IntValue meltTopUpPeriodTicks;

        private Warmth(ForgeConfigSpec.Builder builder) {
            builder.comment("「烈阳」附魔：按件削弱冻伤。",
                            "它管两件事：① 挡掉**新来**的冻伤（按件按比例缩小层数与时长）；",
                            "② 让**已经吃到的**细雪冻结消退得更快（穿上护甲不会让已有的冻结刻数消失）。")
                    .push("warmth");

            this.frostbiteImmunityPerPiece = builder
                    .comment("每件「烈阳」抵消的冻伤比例。0.0 ~ 1.0，默认 0.25（四件叠满即全免）。",
                            "**影响所有冻伤来源**，不只核弹：冷却液、液态寒冷、Spore 自己的冻伤都按件削弱。",
                            "改小 = 凑齐四件不再等于免疫，玩家得靠别的手段。")
                    .defineInRange("frostbiteImmunityPerPiece", 0.25D, 0.0D, 1.0D);

            this.frostDecayPerTick = builder
                    .comment("原版每 tick 把冻结刻数减掉多少。0 ~ 20，默认 2。",
                            "**这一项必须与 vanilla 实际的衰减量一致**（原版硬编码为 2，见 aiStep 里那个",
                            "Math.max(0, i - 2)）。它不改原版行为，只改我们「补零头」时假设的基准——",
                            "填错会让下面那条「每 tick 额外减 2×抗性」算出的长期均值失真。",
                            "除非你同时改了原版，否则不要动它。")
                    .defineInRange("frostDecayPerTick", 2, 0, 20);

            this.meltTopUpPeriodTicks = builder
                    .comment("补发零头的周期（tick）。1 ~ 20，默认 2。",
                            "一件烈阳的抗性是 0.25，算出来的「额外衰减」是 2.5 这种非整数，",
                            "而原版只认整数 tick——所以攒够一个整数的零头再补发一次。",
                            "**它是取模的模数**，填 0 会崩，所以下限锁 1。调大 = 补发更迟、更不平滑。")
                    .defineInRange("meltTopUpPeriodTicks", 2, 1, 20);

            builder.pop();
        }
    }

    /**
     * 可燃 / 爆燃那一段（高能燃料那一套）。
     *
     * <p>它跟冰霜没有关系，是另一套元素体系，所以单独成段而不是塞进 frostSigh 或 frostbite。
     */
    private static final class Combustion {

        private final ForgeConfigSpec.IntValue ignitableDurationTicks;
        private final ForgeConfigSpec.IntValue deflagrationDurationTicks;
        private final ForgeConfigSpec.DoubleValue fireDamagePerStack;
        private final ForgeConfigSpec.DoubleValue maxHealthBonusPerTenStacks;
        private final ForgeConfigSpec.IntValue stacksPerBonusTier;

        private Combustion(ForgeConfigSpec.Builder builder) {
            builder.comment("可燃 / 爆燃：高能燃料与 Spore 焦油那一套。",
                            "链条是「泡在燃料里 → 积累 Spore 的【可燃】→ 可燃被触发时转化为本 mod 的【爆燃】」，",
                            "爆燃按层放大火焰伤害，层数无上限。")
                    .push("combustion");

            this.ignitableDurationTicks = builder
                    .comment("「可燃」buff 的持续 tick 数。20 ~ 2400，默认 200（10 秒）。",
                            "高能燃料与 Spore 自己的焦油共用本项。vanilla 在 amplifier 相同时取更长的一方，",
                            "所以默认的 200 会覆盖 Spore 焦油原本的 100 tick（5 秒）——",
                            "泡在焦油里最终也是 10 秒，与泡在燃料里一致。")
                    .defineInRange("ignitableDurationTicks", 200, 20, 2400);

            this.deflagrationDurationTicks = builder
                    .comment("「爆燃」buff 的持续 tick 数。20 ~ 2400，默认 200（10 秒）。",
                            "每次重新触发都会把时长顶回满值，所以它是「离开火源后多久熄」，不是总时长。")
                    .defineInRange("deflagrationDurationTicks", 200, 20, 2400);

            this.fireDamagePerStack = builder
                    .comment("每层爆燃附加的固定火焰伤害点数。0.0 ~ 100.0，默认 1.0。",
                            "它与下面那条**相加之后**才进同一次伤害结算（调用方用 setAmount），",
                            "所以会吃护甲、抗性与无敌帧，也不会产生第二次伤害或递归。")
                    .defineInRange("fireDamagePerStack", 1.0D, 0.0D, 100.0D);

            this.maxHealthBonusPerTenStacks = builder
                    .comment("每满一档额外附加的最大生命比例。0.0 ~ 1.0，默认 0.01（1%）。",
                            "不足一档的零头不计——比如 19 层只算一档。")
                    .defineInRange("maxHealthBonusPerTenStacks", 0.01D, 0.0D, 1.0D);

            this.stacksPerBonusTier = builder
                    .comment("多少层算一档。1 ~ 127，默认 10。",
                            "**它是除数**，下限锁 1；填 0 会直接崩。与 frostbite 段那一项是对称的。")
                    .defineInRange("stacksPerBonusTier", 10, 1, 127);

            builder.pop();
        }
    }

    /** 个人恨意值给玩家的增益。 */
    private static final class PlayerBuffs {

        private final ForgeConfigSpec.DoubleValue attackDamagePerHatred;
        private final ForgeConfigSpec.DoubleValue maxAttackDamage;
        private final ForgeConfigSpec.DoubleValue armorPerHatred;
        private final ForgeConfigSpec.DoubleValue maxArmor;
        private final ForgeConfigSpec.DoubleValue luckPerHatred;
        private final ForgeConfigSpec.DoubleValue maxLuck;
        private final ForgeConfigSpec.DoubleValue damageReductionPerHatred;
        private final ForgeConfigSpec.DoubleValue maxDamageReduction;
        private final ForgeConfigSpec.DoubleValue finalDamagePerHatred;
        private final ForgeConfigSpec.DoubleValue maxFinalDamage;

        private PlayerBuffs(ForgeConfigSpec.Builder builder) {
            builder.comment("个人恨意值给玩家的增益（恨意值越高越强）。每一项都是「每点换多少 + 封顶」两个数。",
                            "前四项里，攻击力 / 防御力 / 幸运值走原版属性修饰符，所以会正常参与原版的一切结算；",
                            "减伤与最终伤害走事件——前者在护甲之前生效，后者在护甲之后生效。",
                            "**每个倍率系数都很小**（默认 0.001 那一档），因为恨意值是几百到几万的量级：",
                            "调这一组之前先想清楚你期望的「毕业值」是多少恨意值。")
                    .push("playerBuffs");

            this.attackDamagePerHatred = builder
                    .comment("每 1 点个人恨意值换到的基础攻击力。默认 0.001（即 1000 点换 1 点攻击力）。")
                    .defineInRange("attackDamagePerHatred", 0.001D, 0.0D, 100.0D);

            this.maxAttackDamage = builder
                    .comment("基础攻击力加成的上限。默认 20.0（约等于一把下界合金剑的伤害量级）。")
                    .defineInRange("maxAttackDamage", 20.0D, 0.0D, 10000.0D);

            this.armorPerHatred = builder
                    .comment("每 1 点个人恨意值换到的防御力（原版护甲值）。默认 0.0005（即 2000 点换 1 点）。")
                    .defineInRange("armorPerHatred", 0.0005D, 0.0D, 100.0D);

            this.maxArmor = builder
                    .comment("防御力加成的上限。默认 10.0（原版满套装约 20 点）。")
                    .defineInRange("maxArmor", 10.0D, 0.0D, 10000.0D);

            this.luckPerHatred = builder
                    .comment("每 1 点个人恨意值换到的幸运值。默认 0.0005（即 2000 点换 1 点）。",
                            "幸运值影响原版的钓鱼与战利品表判定。")
                    .defineInRange("luckPerHatred", 0.0005D, 0.0D, 100.0D);

            this.maxLuck = builder
                    .comment("幸运值加成的上限。默认 5.0。")
                    .defineInRange("maxLuck", 5.0D, 0.0D, 10000.0D);

            this.damageReductionPerHatred = builder
                    .comment("每 1 点个人恨意值换到的减伤比例。默认 0.00002（即 25000 点减伤 50%）。")
                    .defineInRange("damageReductionPerHatred", 0.00002D, 0.0D, 1.0D);

            this.maxDamageReduction = builder
                    .comment("玩家减伤的上限。0.0 ~ 0.99，默认 0.5。",
                            "**刻意封在 1.0 以下**：到 1.0 就是完全免疫，玩家会变成打不死的存在。",
                            "上限 0.99 而不是 1.0，是让「配到顶」仍然留一线。")
                    .defineInRange("maxDamageReduction", 0.5D, 0.0D, 0.99D);

            this.finalDamagePerHatred = builder
                    .comment("每 1 点个人恨意值换到的最终伤害加成比例。默认 0.00002（即 25000 点 +50%）。",
                            "它作用在**护甲与抗性都结算完之后**，所以与「基础攻击力」是两条不同的通路，",
                            "会互相叠乘而不是重复计入。")
                    .defineInRange("finalDamagePerHatred", 0.00002D, 0.0D, 1.0D);

            this.maxFinalDamage = builder
                    .comment("最终伤害加成的上限。默认 0.5（+50%）。")
                    .defineInRange("maxFinalDamage", 0.5D, 0.0D, 100.0D);

            builder.pop();
        }
    }
}
