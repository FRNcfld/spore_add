package com.frnc.spore_add;

import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 本 mod 的配置，写进 {@code config/spore_add-common.toml}。
 *
 * <h2>为什么是 COMMON 而不是 SERVER</h2>
 * 这里所有数值都是<b>服务端权威</b>的玩法参数，客户端一个都不读，所以两种类型都能用。选 COMMON 的理由是：
 * 它不依赖世界存在（专用服务端一启动就能生成文件），而且只有一份文件；SERVER 类型会按存档分别生成、
 * 并把值同步给客户端——那份同步对我们毫无用处。代价是它不跟存档走：换个存档用的是同一份数值。
 *
 * <h2>配置值一律不在静态初始化器里读</h2>
 * {@link #SPEC} 的静态块只<b>声明</b>各项，真正的 {@code get()} 全部发生在下面的访问器里，也就是
 * 游戏运行期。这样与配置何时加载无关，不会出现"配置还没读就取值"的异常。
 * {@code LiquidColdBlock} 与 {@code FrozenChunks} 也遵守这条——它们把原来的
 * {@code static final} 半径常量改成了调用时读。
 *
 * <h2>为什么冻伤等级的上限是 127</h2>
 * 配置里的 {@code frostbiteLevel} 指的是<b>界面上的显示层数</b>（本 mod 的约定：层数 = amplifier + 1，
 * 与 {@code CoolantBlock.FROSTBITE_CAP = 10} 表示"封顶 10 层"一致）。它最终会写成 amplifier，
 * 而 amplifier 的网络同步与存档序列化都是字节，超过 127 会静默损坏（详见 {@code FrostbiteLevels} 的类注释），
 * 所以这里封到 127——正好是"层数 127 对应 amplifier 126"。
 */
public final class SporeAddConfig {

    /** 配置规格。主类构造时注册到 {@code ModConfig.Type.COMMON}。 */
    public static final ForgeConfigSpec SPEC;

    private static final FrostNova FROST_NOVA;
    private static final LiquidCold LIQUID_COLD;
    private static final FrostSigh FROST_SIGH;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        FROST_NOVA = new FrostNova(builder);
        LIQUID_COLD = new LiquidCold(builder);
        FROST_SIGH = new FrostSigh(builder);
        SPEC = builder.build();
    }

    private SporeAddConfig() {
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
        return FROST_NOVA.primaryCloudSeconds.get() * 20;
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

    // ------------------------------------------------------------------
    // 各段配置的定义
    // ------------------------------------------------------------------

    /** 冰霜新星那一段。 */
    private static final class FrostNova {

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
        private final ForgeConfigSpec.IntValue secondaryDelaySeconds;
        private final ForgeConfigSpec.IntValue primaryCloudSeconds;
        private final ForgeConfigSpec.IntValue secondaryCloudSeconds;
        private final ForgeConfigSpec.DoubleValue secondaryRangeMultiplier;
        private final ForgeConfigSpec.DoubleValue secondaryPowerMultiplier;

        private FrostNova(ForgeConfigSpec.Builder builder) {
            builder.comment("冰霜新星（物品与它的投射物）").push("frostNova");

            this.blockRadius = builder
                    .comment("满蓄力时，落点处被替换成冰的球半径（格）。1 ~ 16。",
                            "内半半径换蓝冰、其余换浮冰；不可破坏的方块（基岩等）永远不换。")
                    .defineInRange("blockRadius", 4, 1, 16);

            this.explosionPower = builder
                    .comment("满蓄力时的爆炸强度。原版 TNT 是 4.0，默认 8.0 即 TNT 的两倍。0 ~ 32。",
                            "注意原版把伤害与波及范围绑在同一个值上：伤害的作用半径 = 该值 × 2 格，",
                            "中心满暴露伤害约 7 × 该值 × 2。所以调大时两者一起变大——这正是需求要的。",
                            "和其它威力参数一样随蓄力缩放：最低蓄力只有它的 minPowerFraction 倍。",
                            "填 0 表示不产生爆炸（伤害、击退、爆炸音效都没有，其它效果照常）。")
                    .defineInRange("explosionPower", 8.0D, 0.0D, 32.0D);

            this.entityRadius = builder
                    .comment("满蓄力时，落点处被施加冻伤的实体球半径（格）。1 ~ 32。",
                            "这个半径只作用于实体，与换方块的 blockRadius 各管各的。")
                    .defineInRange("entityRadius", 12, 1, 32);

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

            this.secondaryDelaySeconds = builder
                    .comment("首次爆炸后，冰球变成的延时炸弹在多少秒后二次引爆。1 ~ 600。",
                            "二次引爆会把那批冰清掉（变成空气），所以地表不会永久留着一个冰球。")
                    .defineInRange("secondaryDelaySeconds", 30, 1, 600);

            this.primaryCloudSeconds = builder
                    .comment("一次爆炸那团霜雾持续多少秒。1 ~ 600。",
                            "注意它和雾里冻伤的持续时间是两回事：冻伤时长由 frostbiteSeconds 决定。")
                    .defineInRange("primaryCloudSeconds", 10, 1, 600);

            this.secondaryCloudSeconds = builder
                    .comment("二次爆炸的冰雾持续多少秒。1 ~ 600。默认 20，比一次爆炸的 10 秒更长——",
                            "二次爆炸范围更大，雾也该留得更久一点才撑得住。",
                            "注意它和冰雾里冻伤的持续时间是两回事：冻伤时长由 secondaryPowerMultiplier 那一项决定。")
                    .defineInRange("secondaryCloudSeconds", 20, 1, 600);

            this.secondaryRangeMultiplier = builder
                    .comment("二次爆炸的影响范围倍率。1.0 ~ 4.0。",
                            "只放大**范围**——冰雾与冻伤的半径；二次爆炸不产生任何伤害，",
                            "所以这里调多大都不会让伤害变高。")
                    .defineInRange("secondaryRangeMultiplier", 2.0D, 1.0D, 4.0D);

            this.secondaryPowerMultiplier = builder
                    .comment("二次爆炸的冻伤强度倍率：层数与秒数一起乘。1.0 ~ 5.0。",
                            "填 1.0 就是「只有范围变大、强度不变」。",
                            "层数会被夹到 127 上限（amplifier 的字节限制），超出部分不生效。")
                    .defineInRange("secondaryPowerMultiplier", 1.5D, 1.0D, 5.0D);

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

        private FrostSigh(ForgeConfigSpec.Builder builder) {
            builder.comment("冰雪的叹息（核弹方块）").push("frostSigh");

            this.radius = builder
                    .comment("影响半径（格）。圆形，不是球。1 ~ 256。",
                            "圆盘内的 (x,z) 列数按 π·r² 增长：半径 128 是 51,433 列、跨约 226 个区块。",
                            "调大它会让冲击环每 tick 要处理的列数成平方增长。")
                    .defineInRange("radius", 128, 1, 256);

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

            builder.pop();
        }
    }

    /** 液态寒冷那一段。 */
    private static final class LiquidCold {

        private final ForgeConfigSpec.IntValue radius;

        private LiquidCold(ForgeConfigSpec.Builder builder) {
            builder.comment("液态寒冷").push("liquidCold");

            this.radius = builder
                    .comment("液态寒冷的影响半径（格）。1 ~ 16。",
                            "区域寒冷效果、冰扩散、以及范围内的原版冰不融化共用这一个半径，",
                            "所以改它就是同时改这三件事——它们本来就该是同一片区域。",
                            "上限 16 是冰扩散的取样密度决定的：它每秒只在球的外接立方体里随机取 16 个点，",
                            "半径越大球内占比越低（半径 6 约 52%，半径 16 只剩约 12%），再大就几乎抽不到点、",
                            "扩散形同停止。范围判定本身没有这个限制（FrozenChunks 的区块窗口是跟着半径算的）。")
                    .defineInRange("radius", 8, 1, 16);

            builder.pop();
        }
    }
}
