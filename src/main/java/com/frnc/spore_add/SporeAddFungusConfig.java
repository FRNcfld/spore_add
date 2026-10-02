package com.frnc.spore_add;

import java.util.List;

import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * <b>真菌侧</b>的配置，写进 {@code config/spore_add-fungus-common.toml}。
 *
 * <h2>两份配置是按「谁受益」分的</h2>
 * <table border="1">
 *   <caption>想调什么，就开哪一份</caption>
 *   <tr><th>文件</th><th>里面是什么</th></tr>
 *   <tr><td>{@code spore_add-player-common.toml}（{@link SporeAddPlayerConfig}）</td>
 *       <td><b>让玩家更强、更好用</b>的东西：冰霜武器、恨意值给玩家的增益</td></tr>
 *   <tr><td>{@code spore_add-fungus-common.toml}（本类）</td>
 *       <td><b>让真菌更强</b>的东西：真菌加强、恨意值系统本身、世界恨意值给真菌的减伤、
 *           资源、真菌袭击</td></tr>
 * </table>
 * 所以「想把怪调强一点」只动这一份，「想让自己舒服一点」只动那一份。
 *
 * <p>本文件内部按「真菌本身多强 → 恨意值怎么涨、怎么降 → 恨意值反过来怎么帮真菌 → 袭击 → 资源」
 * 的顺序排段，与静态块里的构造顺序一致（那段注释说明了两者的关系）。
 *
 * <h2>为什么文件名是手写的</h2>
 * Forge 默认按 {@code modId-类型} 拼文件名（COMMON → {@code spore_add-common.toml}），
 * 两个 COMMON 会撞成同一个名字，而 {@code ConfigTracker} 撞名会直接抛
 * {@code "Config conflict detected!"} 把游戏崩掉。所以两份都显式给文件名。
 *
 * <p>选 COMMON 而不是 SERVER：SERVER 类型的配置文件会按存档分别生成、放在
 * {@code <存档>/serverconfig/} 下。这些数值是"整合包作者设一次、所有存档通用"的性质，
 * 不该跟着存档走——与 {@link SporeAddPlayerConfig} 同一个理由。
 *
 * <h2>配置值一律不在静态初始化器里读</h2>
 * 与 {@link SporeAddPlayerConfig} 同规矩：{@link #SPEC} 的静态块只<b>声明</b>各项，
 * 真正的 {@code get()} 全部发生在下面的访问器里（游戏运行期），
 * 这样与配置何时加载无关。
 */
public final class SporeAddFungusConfig {

    /**
     * 配置文件全名（含扩展名）。
     *
     * <p>两份配置的名字是一对：{@code spore_add-player-common.toml} 与
     * {@code spore_add-fungus-common.toml}——前缀相同、中间那个词区分「谁受益」，
     * 在 config 目录里排在一起也一眼能看出是同一个 mod 的东西。
     *
     * <p>{@code -common} 的后缀是跟着 Forge 的默认命名规则（{@code modId-类型.toml}）写的，
     * 所以这两份在观感上仍是"标准的 COMMON 配置"，只是名字被人为指到了显式路径上。
     */
    public static final String FILE_NAME = "spore_add-fungus-common.toml";

    /** 配置规格。主类构造时注册。 */
    public static final ForgeConfigSpec SPEC;

    /**
     * 转化表的默认值。
     *
     * <p>用的都是 Spore 自己的物品标签（{@code data/spore/tags/items/*}，逐个核对过标签文件确实存在），
     * 所以一组标签就能覆盖它那几十种掉落物，不必逐个列 id。
     *
     * <p>定价逻辑：Spore 的"身体部件"类偏贵（那是在它自己的合成体系里有用的材料），
     * 原版的基础材料便宜，稀有矿物贵。整体量级参考 {@code despawnBaseValue}——
     * 清理一只基础感染体给 5 点，所以捡一块铁矿石给 6 点属于"值得跑一趟"。
     */
    private static final List<? extends String> DEFAULT_LOOT_VALUES = List.of(
            "#spore:amalgamated_biomass|6",
            "#spore:inf_parts|4",
            "#spore:body_parts|4",
            "#spore:corrosive_parts|6",
            "#spore:reagents|3",
            "#spore:stitches|3",
            "#spore:putrid_parts|3",
            "#spore:meat|1",
            "minecraft:rotten_flesh|1",
            "minecraft:bone|1",
            "minecraft:iron_ingot|6",
            "minecraft:gold_ingot|8",
            "minecraft:diamond|16");

    /**
     * 攻击阶段默认给参战真菌的增益。
     *
     * <p>格式是 {@code 效果id|持续tick|amplifier}，与 Spore 自己配置里那几张 debuff 表同构
     * （它的 {@code levi_debuffs} 写的就是 {@code "minecraft:mining_fatigue|600|0"}）。
     *
     * <p>默认这一组偏保守：速度 I 让它们追得上人，力量 I 让攻击有分量，抗性 I 让它们不至于
     * 一照面就被清掉。刻意<b>没有</b>默认给生命提升或回复——那些会让袭击变成消耗战，
     * 而"袭击该有个头"这件事由波数（{@code ownWaveMax}）与竞技之须的超时（{@code arenaTimeoutSeconds}）
     * 两处兜底。
     *
     * <p>我方围剿阶段还会在这个等级之上<b>逐波加级</b>（见 {@code ownWaveBuffsPerWave}）。
     *
     * <p>持续 200 tick 是配合默认 10 秒（= 200 tick）的刷新间隔：刚好无缝续上，
     * 玩家中途退出重进也不会看到增益断档。
     */
    private static final List<? extends String> DEFAULT_RAID_BUFFS = List.of(
            "minecraft:speed|200|1",
            "minecraft:strength|200|1",
            "minecraft:resistance|200|0");

    /**
     * 我方围剿波次的默认战利品表。
     *
     * <p>格式 {@code 物品id|最少|最多}，与 Spore 自己 {@code drops} 表那套 {@code id|min|max} 同构。
     *
     * <p>挑的都是 Spore 自己 {@code drops} 表里出现过的材料（所以一定存在），
     * 再掺一点原版钻石当"硬通货"。量级参考：默认 10 波上限、每条掷 波数×1 次，
     * 所以一场满档袭击大约能拿到十几件材料加一两颗钻石。
     */
    private static final List<? extends String> DEFAULT_OWN_LOOT = List.of(
            "spore:living_core|1|1",
            "spore:hardened_bind|1|3",
            "spore:fleshy_claw|1|2",
            "spore:calcified_tumor|1|2",
            "spore:corrosive_sack|1|2",
            "minecraft:diamond|1|1");

    private static final Fungus FUNGUS;
    private static final Scavenger SCAVENGER;
    private static final Hatred HATRED;
    private static final Food FOOD;
    private static final Death DEATH;
    private static final FungusResistance FUNGUS_RESISTANCE;
    private static final Raid RAID;
    private static final Hivemind HIVEMIND;
    private static final Loot LOOT;

    static {
        // **这里的顺序就是生成出来的 toml 里的段落顺序**，也就是玩家打开文件看到的顺序：
        // 本文件里唯一决定"段落排布"的地方就是这里，别在别处找。
        // 按「真菌本身多强 → 恨意值怎么涨、怎么降 → 恨意值反过来怎么帮真菌 → 袭击 → 资源」排。
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // 写进 toml 文件开头的总说明。类注释里那张表玩家看不到（那只在源码里），
        // 所以这里必须再写一遍——玩家打开文件的第一眼就该知道"这份管什么、另一份在哪"。
        builder.comment(
                "Spore Add —— 真菌侧配置。",
                "",
                "这一份管「让真菌更强」的东西。",
                "让玩家更强的那些在另一份文件里：spore_add-player-common.toml",
                "（冰霜武器、恨意值给玩家的增益）",
                "",
                "本文件的段落（按下面的顺序排列）：",
                "  fungus            真菌加强：进化更快、攻击一切生物、冰冻伤害倍率、感知范围、抗寒",
                "  hatred            恨意值怎么涨：击杀真菌能拿多少、各项权重",
                "  food              吃真菌类食物时的恨意值变化",
                "  death             死亡 / 击杀心智 / 打赢袭击时的恨意值削减",
                "  fungusResistance  世界恨意值给真菌的减伤（只对非玩家伤害生效）",
                "  raid              真菌袭击：触发 → 准备 → 攻击",
                "  hivemind          心智的通用机制：把造出来的生物收进存储、需要时投放",
                "  loot              真菌捡掉落物、以及被系统清理时回收成的资源",
                "",
                "改动在重启游戏、或执行 /reload 之后生效。");

        FUNGUS = new Fungus(builder);
        SCAVENGER = new Scavenger(builder);
        HATRED = new Hatred(builder);
        FOOD = new Food(builder);
        DEATH = new Death(builder);
        FUNGUS_RESISTANCE = new FungusResistance(builder);
        RAID = new Raid(builder);
        HIVEMIND = new Hivemind(builder);
        LOOT = new Loot(builder);
        SPEC = builder.build();
    }

    private SporeAddFungusConfig() {
    }

    // ------------------------------------------------------------------
    // 真菌加强（第一批的内容，原本与冰霜内容同处一份配置，后来按受益方拆到这边）
    // ------------------------------------------------------------------

    /**
     * 每秒额外获得的进化进度。Spore 自己每秒 +1，所以默认 +4 是"快五倍"。
     *
     * <p>与 Spore 不同的是，这一笔<b>不因冻伤而暂停</b>。
     */
    public static int evolutionSpeedBonus() {
        return FUNGUS.evolutionSpeedBonus.get();
    }

    /** 「攻击一切生物」的总开关。关掉后目标与判定一起失效。 */
    public static boolean huntEnabled() {
        return FUNGUS.huntEnabled.get();
    }

    /** 「打得过才打」的裕度，见 {@code FungusCombat#canWin}。 */
    public static double huntCautionRatio() {
        return FUNGUS.huntCautionRatio.get();
    }

    /** 真菌受到的冰冻伤害倍率。 */
    public static double freezeDamageMultiplier() {
        return FUNGUS.freezeDamageMultiplier.get();
    }

    /** 真菌 {@code FOLLOW_RANGE} 的倍率（相对各实体自己的基础值）。 */
    public static double sensingMultiplier() {
        return FUNGUS.sensingMultiplier.get();
    }

    /** 寒冷环境两次自我施加冻伤之间的最短间隔（tick）。 */
    public static int coldFrostbiteIntervalTicks() {
        return FUNGUS.coldFrostbiteIntervalTicks.get();
    }

    /** 寒冷加速饥饿时，额外那一点保留的比例。 */
    public static double coldHungerPenaltyFactor() {
        return FUNGUS.coldHungerPenaltyFactor.get();
    }

    /** 真菌眼里的「寒冷」生物群系温度上限。 */
    public static double coldBiomeThreshold() {
        return FUNGUS.coldBiomeThreshold.get();
    }

    /** 猎杀目标多久搜一次（tick）。 */
    public static int huntSearchIntervalTicks() {
        return Math.max(1, FUNGUS.huntSearchIntervalTicks.get());
    }

    /**
     * 「打得过才打」判定里，每点护甲折算成多少有效生命倍率。
     *
     * <p>见 {@code FungusCombat#canWin}——那是"开打前的粗略掂量"，不是战斗模拟器。
     */
    public static double huntArmorEhpPerPoint() {
        return FUNGUS.huntArmorEhpPerPoint.get();
    }

    /**
     * 灾厄重构体（Womb）同化喂食时，一次喂食算几条突变。Spore 原样是 1 条。
     *
     * <p>需求「叠满属性所需的生物减少 50%」的落点在 {@code Womb#addMutation}：
     * 它只是往 {@code attributeIDs} 里追加一条，而那份列表<b>不去重、不封顶</b>，
     * 孵化时 {@code Womb#summon} 会把整个列表逐条套到灾厄身上、每条 {@code +1.0} 基础值。
     * 也就是说「叠满要喂多少只」完全等于「这份列表有多长」，
     * 所以少喂一半 = 一次喂食追加两条，而不是去改某个并不存在的上限常量。
     *
     * <p>实际条数还要乘上袭击那一档，见 {@code RaidManager#adjustWombMutationCount}。
     */
    public static double wombMutationMultiplier() {
        return FUNGUS.wombMutationMultiplier.get();
    }

    /** 上面那条在真菌袭击期间的<b>额外</b>倍率（需求：在减半的基础上再减半）。 */
    public static double wombRaidMutationMultiplier() {
        return FUNGUS.wombRaidMutationMultiplier.get();
    }

    /**
     * 拾荒者随存活时间成长的四条曲线。四条都是「每分钟涨多少、涨到上限为止」，
     * 与 {@link #scavengerLootBonusPerMinute()} 用的是同一套算法（线性 + 封顶）。
     *
     * <p>时间基准是实体的 {@code tickCount}（见 {@code Scavenger#survivalMinutes}），不额外存盘。
     */
    public static double scavengerHealthPerMinute() {
        return SCAVENGER.healthPerMinute.get();
    }

    /** 最大生命成长的上限。 */
    public static double scavengerHealthMaxBonus() {
        return SCAVENGER.healthMaxBonus.get();
    }

    /** 每存活一分钟增加多少护甲。 */
    public static double scavengerArmorPerMinute() {
        return SCAVENGER.armorPerMinute.get();
    }

    /** 护甲成长的上限。 */
    public static double scavengerArmorMaxBonus() {
        return SCAVENGER.armorMaxBonus.get();
    }

    /** 每存活一分钟增加多少移动速度（属性绝对值，不是倍率）。 */
    public static double scavengerSpeedPerMinute() {
        return SCAVENGER.speedPerMinute.get();
    }

    /** 移动速度成长的上限。 */
    public static double scavengerSpeedMaxBonus() {
        return SCAVENGER.speedMaxBonus.get();
    }

    /** 每存活一分钟，每秒回复的生命点数涨多少。 */
    public static double scavengerRegenHpsPerMinute() {
        return SCAVENGER.regenHpsPerMinute.get();
    }

    /** 每秒回复生命的上限。 */
    public static double scavengerRegenMaxHps() {
        return SCAVENGER.regenMaxHps.get();
    }

    /**
     * 袭击的「制造速度 / 资源获取」两项加成是否也作用到灾厄重构体（Womb）。
     *
     * <p>需求原文（袭击第 1 条）说的是「<b>心智</b>制造速度加快 500%」，Womb 不是心智——
     * 它自带独立的 {@code BIOMASS}、独立配方体系，与心智之间没有任何数据通路
     * （反编译确认：它的 {@code summon} 直接 {@code entityType.create + addFreshEntity}，
     * 不走 {@code Proto#summonMob}）。所以这项加成<b>原本到不了它</b>，
     * 是应要求单独接上去的扩展，故留一个总开关。
     */
    public static boolean wombRaidBonusesEnabled() {
        return FUNGUS.wombRaidBonusesEnabled.get();
    }

    /**
     * 灾厄孵化后是否把它挪到重构体外面去（否则它会被塞在重构体自己那堆实心方块里窒息）。
     *
     * <p>见 {@code WombHatch} 的类注释：Spore 把孵化出来的灾厄放在<b>重构体自己身上</b>，
     * 而重构体是埋在生物质里的土丘、体型还会长到 3 档，于是大 hitbox 的灾厄一出来就卡在方块里。
     */
    public static boolean wombHatchExitEnabled() {
        return FUNGUS.wombHatchExitEnabled.get();
    }

    /** 找"壳外空位"时最多向外找多远（格）。找不到就维持原位。 */
    public static double wombHatchExitSearchRadius() {
        return FUNGUS.wombHatchExitSearchRadius.get();
    }

    /**
     * 孵化出的灾厄要不要被送出<b>心智的生物质穹顶</b>，而不仅仅是送出重构体本身。
     *
     * <p>默认开。「壳」在这条需求里指的是心智那两层生物质球壳（半径 32 与 16，见
     * {@code DomeBreach} 的类注释），不是重构体自己那圈方块——只是把灾厄挪出重构体的包围盒
     * 并不足以让它离开穹顶，它照样会在穹顶内部把壳挖烂。
     */
    public static boolean wombHatchExitFromDome() {
        return FUNGUS.wombHatchExitFromDome.get();
    }

    /** 送出穹顶时，在穹顶半径之外再留出多少格。 */
    public static double wombHatchExitDomeMargin() {
        return FUNGUS.wombHatchExitDomeMargin.get();
    }

    // ------------------------------------------------------------------
    // 拾荒者
    // ------------------------------------------------------------------

    /** 菌染人类生成时转变成拾荒者的概率下限（附近一件掉落物都没有时）。 */
    public static double scavengerTransformMinChance() {
        return SCAVENGER.transformMinChance.get();
    }

    /** 转变概率的上限（附近掉落物多到饱和时）。 */
    public static double scavengerTransformMaxChance() {
        return SCAVENGER.transformMaxChance.get();
    }

    /** 统计"附近有多少掉落物"时的半径（格）。 */
    public static double scavengerTransformItemRadius() {
        return SCAVENGER.transformItemRadius.get();
    }

    /** 掉落物到多少件时转变概率达到上限。 */
    public static int scavengerTransformItemCountForMax() {
        return SCAVENGER.transformItemCountForMax.get();
    }

    /** 存活时间换算成拾荒加成的速率：每存活一分钟加多少倍率。 */
    public static double scavengerLootBonusPerMinute() {
        return SCAVENGER.lootBonusPerMinute.get();
    }

    /** 存活时间加成的上限（不含基础的 1.0）。 */
    public static double scavengerLootBonusMax() {
        return SCAVENGER.lootBonusMax.get();
    }

    /** 血量低于最大生命的这个比例时，无条件逃跑。 */
    public static double scavengerFleeHealthFraction() {
        return SCAVENGER.fleeHealthFraction.get();
    }

    /** 感知到多远的威胁就跑（格）。 */
    public static double scavengerFleeThreatRadius() {
        return SCAVENGER.fleeThreatRadius.get();
    }

    /** 逃跑时的移动速度倍率。 */
    public static double scavengerFleeSpeed() {
        return SCAVENGER.fleeSpeed.get();
    }

    /** 把收获交给同伙时扫描的半径（格）。 */
    public static double scavengerDeliveryRadius() {
        return SCAVENGER.deliveryRadius.get();
    }

    /** 一次最多同时治疗几个残血同伙。 */
    public static int scavengerHealMinTargets() {
        return SCAVENGER.healMinTargets.get();
    }

    /** 随存活时间最多能涨到几个（见 {@link #scavengerHealRampMinutes()}）。 */
    public static int scavengerHealMaxTargets() {
        return Math.max(scavengerHealMinTargets(), SCAVENGER.healMaxTargets.get());
    }

    /** 从 Min 涨到 Max 需要存活多少分钟。 */
    public static double scavengerHealRampMinutes() {
        return Math.max(0.01D, SCAVENGER.healRampMinutes.get());
    }

    /** 多高的等级才算"值得优先抢救"（{@code HatredValues.Tier} 的序号）。 */
    public static int scavengerHealMinTierOrdinal() {
        return SCAVENGER.healMinTier.get();
    }

    /** 血量低于最大生命的这个比例才算"残血"、才值得治疗。 */
    public static double scavengerHealWoundedFraction() {
        return SCAVENGER.healWoundedFraction.get();
    }

    /** 1 点资源能换多少点生命值。 */
    public static double scavengerHealthPerResource() {
        return SCAVENGER.healthPerResource.get();
    }

    /** 1 点资源能换多少进化点。 */
    public static double scavengerEvoPointsPerResource() {
        return SCAVENGER.evoPointsPerResource.get();
    }

    /** 逃跑目标的优先级（数字越小越优先）。 */
    public static int scavengerFleePriority() {
        return SCAVENGER.fleePriority.get();
    }

    /** 世上同时存在的拾荒者上限。到顶后转换概率降到 0。 */
    public static int scavengerMaxCount() {
        return SCAVENGER.maxCount.get();
    }

    /** 拾荒目标挂在行动目标选择器上的优先级。数字越小越优先。 */
    public static int scavengerLootPriority() {
        return SCAVENGER.lootPriority.get();
    }

    // ------------------------------------------------------------------
    // 恨意值：来源与权重
    // ------------------------------------------------------------------

    /** 击杀一只真菌的基础恨意值，其余倍率都乘在它上面。 */
    public static double hatredBasePerKill() {
        return HATRED.basePerKill.get();
    }

    /** 单次击杀最多能拿多少恨意值。防止某个倍率配错把数值瞬间拉爆。 */
    public static double hatredMaxPerKill() {
        return HATRED.maxPerKill.get();
    }

    /** 按等级取倍率。参数是 {@code HatredValues.Tier} 的序号，见那边的说明。 */
    public static double hatredTierMultiplier(int tierOrdinal) {
        return switch (tierOrdinal) {
            case 1 -> HATRED.tierEvolved.get();
            case 2 -> HATRED.tierHyper.get();
            case 3 -> HATRED.tierOrganoid.get();
            case 4 -> HATRED.tierCalamity.get();
            case 5 -> HATRED.tierOther.get();
            default -> HATRED.tierInfected.get();
        };
    }

    /** 该真菌与心智链接时（{@code Infected#getLinked()}）的倍率。 */
    public static double hatredLinkedMultiplier() {
        return HATRED.linkedMultiplier.get();
    }

    /** 未与心智链接时的倍率。默认 1.0，即不因未链接而扣分，只是拿不到链接加成。 */
    public static double hatredUnlinkedMultiplier() {
        return HATRED.unlinkedMultiplier.get();
    }

    /** 「发育程度」每 1 进化点换算成多少额外倍率。 */
    public static double hatredDevelopmentWeight() {
        return HATRED.developmentWeight.get();
    }

    /** 发育程度带来的额外倍率上限（不含基础的 1.0）。 */
    public static double hatredDevelopmentCap() {
        return HATRED.developmentCap.get();
    }

    /** 「类型」维度的按实体覆盖表，格式 {@code 实体id|倍率}。默认空 = 全 1.0。 */
    public static List<? extends String> hatredTypeMultipliers() {
        return HATRED.typeMultipliers.get();
    }

    /** 「重要性」维度的按实体覆盖表，格式 {@code 实体id|倍率}。默认空 = 用下面的职能默认值。 */
    public static List<? extends String> hatredImportanceMultipliers() {
        return HATRED.importanceMultipliers.get();
    }

    /** 重要性：会加载区块的真菌（{@code ChunkLoaderMob}）的倍率。 */
    public static double hatredImportanceChunkLoader() {
        return HATRED.importanceChunkLoader.get();
    }

    /** 重要性：能生成菌壳的真菌（{@code CasingGenerator}）的倍率。 */
    public static double hatredImportanceCasingGenerator() {
        return HATRED.importanceCasingGenerator.get();
    }

    /** 重要性：会铺开植被的真菌（{@code FoliageSpread}）的倍率。 */
    public static double hatredImportanceFoliageSpread() {
        return HATRED.importanceFoliageSpread.get();
    }

    /** 重要性：还有进化余地的真菌（{@code EvolvingInfected}）的倍率。 */
    public static double hatredImportanceEvolving() {
        return HATRED.importanceEvolving.get();
    }

    // ------------------------------------------------------------------
    // 恨意值：作用于真菌
    // ------------------------------------------------------------------

    /** 世界恨意值每 1 点给真菌带来的减伤比例。 */
    public static double fungusResistancePerHatred() {
        return FUNGUS_RESISTANCE.perHatred.get();
    }

    /** 真菌减伤的上限。不能到 1.0，否则真菌对非玩家伤害完全无敌。 */
    public static double fungusMaxResistance() {
        return FUNGUS_RESISTANCE.maxResistance.get();
    }

    // ------------------------------------------------------------------
    // 真菌类食物
    // ------------------------------------------------------------------

    /** 吃真菌类食物时「降低恨意值」这一支的概率。 */
    public static double foodReduceChance() {
        return FOOD.reduceChance.get();
    }

    /** 该支降低当前恨意值的比例。 */
    public static double foodReduceRatio() {
        return FOOD.reduceRatio.get();
    }

    /** 吃真菌类食物时「提高恨意值」这一支的概率。 */
    public static double foodIncreaseChance() {
        return FOOD.increaseChance.get();
    }

    /** 该支提高当前恨意值的比例。 */
    public static double foodIncreaseRatio() {
        return FOOD.increaseRatio.get();
    }

    // ------------------------------------------------------------------
    // 死亡与其它降低途径
    // ------------------------------------------------------------------

    /** 死于真菌之手时，个人恨意值损失的比例。 */
    public static double deathLossRatio() {
        return DEATH.fungusDeathLossRatio.get();
    }

    /** 那笔损失换算成资源时乘的倍率（需求写的是「X2」）。 */
    public static double deathResourceMultiplier() {
        return DEATH.resourceMultiplier.get();
    }

    /** 击杀心智时，个人恨意值损失的比例。 */
    public static double hivemindKillLossRatio() {
        return DEATH.hivemindKillLossRatio.get();
    }

    /** 打赢一次真菌袭击时，个人恨意值损失的比例。 */
    public static double raidWinLossRatio() {
        return DEATH.raidWinLossRatio.get();
    }

    // ------------------------------------------------------------------
    // 袭击触发
    // ------------------------------------------------------------------

    /** 个人恨意值每涨满这么多，就算跨过一个档位并掷一次触发骰。 */
    public static double raidThresholdStep() {
        return RAID.thresholdStep.get();
    }

    /** 每跨过一个档位时触发袭击的概率。 */
    public static double raidTriggerChance() {
        return RAID.triggerChance.get();
    }

    /** 准备阶段持续多少 tick 后无条件转入攻击（够不够格是另说的）。 */
    public static int raidPrepTicks() {
        return Math.max(1, RAID.prepSeconds.get() * 20);
    }

    /** 准备阶段最多拖多久；到点还没凑够发动条件就取消这次袭击。 */
    public static int raidPrepTimeoutTicks() {
        return Math.max(raidPrepTicks(), RAID.prepMaxSeconds.get() * 20);
    }

    /** 准备期间心智的资源消耗倍率（需求：降低 50%）。 */
    public static double raidResourceCostMultiplier() {
        return RAID.prepResourceCostMultiplier.get();
    }

    /** 准备期间心智的资源获取倍率（需求：提高 100%）。 */
    public static double raidResourceGainMultiplier() {
        return RAID.prepResourceGainMultiplier.get();
    }

    /** 袭击期间心智的制造（召唤）速度倍率（需求：加快 500%）。 */
    public static double raidManufactureSpeedMultiplier() {
        return RAID.manufactureSpeedMultiplier.get();
    }

    /** 准备期间心智的发育速度倍率（需求：提高 50%）。 */
    public static double raidGrowthSpeedMultiplier() {
        return RAID.prepGrowthSpeedMultiplier.get();
    }

    /** 发动条件之一：至少有一只心智的资源达到这个数。 */
    public static int raidLaunchMinBiomass() {
        return RAID.launchMinBiomass.get();
    }

    /** 发动条件之二：目标玩家周围至少要有这么多真菌。 */
    public static int raidLaunchMinFungusCount() {
        return RAID.launchMinFungusCount.get();
    }

    /** 发动条件之三：这些真菌的「质量」（等级倍率之和）至少要到这个数。 */
    public static double raidLaunchMinQuality() {
        return RAID.launchMinQuality.get();
    }

    /** 统计"目标玩家附近有多少真菌"时用的半径（格）。 */
    public static double raidGatherRadius() {
        return RAID.gatherRadius.get();
    }

    /** 攻击期间 Despawning System 上限的倍率（需求：提高 100%）。 */
    public static double raidDespawnCapMultiplier() {
        return RAID.despawnCapMultiplier.get();
    }

    /** 传染送落点距目标玩家的最近距离（格）。 */
    public static double raidTeleportMinDistance() {
        return RAID.teleportMinDistance.get();
    }

    /** 传染送落点距目标玩家的最远距离（格）。 */
    public static double raidTeleportMaxDistance() {
        return RAID.teleportMaxDistance.get();
    }

    /**
     * 我方某一波该送来多少只。
     *
     * <p>需求改成"每波无上限、具体数量与波次有关"，所以这里是个随波次线性增长的公式，
     * 不再有固定的每波上限。真正的上限来自世界本身：{@code teleportSearchRadius} 内一共只有
     * 那么多真菌可调，而它们的总数又受 Spore 的 Despawn 上限约束（那个上限在攻击期间被我们翻倍了）。
     *
     * @param wave 第几波，从 1 开始
     */
    public static int raidOwnWaveCount(int wave) {
        int count = RAID.ownWaveCountBase.get() + Math.max(0, wave - 1) * RAID.ownWaveCountPerWave.get();
        // 下限 1：某一波一只都不送的话，那这一波就没有任何事发生，玩家只会觉得卡住了
        return Math.max(1, count);
    }

    /** 判断"这一波清完了没有"时统计真菌的半径（格）。 */
    public static double raidOwnWaveClearRadius() {
        return RAID.ownWaveClearRadius.get();
    }

    /** 玩家周围这个半径内的真菌少于这个数，就算这一波清完了。 */
    public static int raidOwnWaveClearCount() {
        return RAID.ownWaveClearCount.get();
    }

    /** 从多大范围内把真菌"调"过来（格）。 */
    // ------------------------------------------------------------------
    // 心智的存储
    // ------------------------------------------------------------------

    /** 心智是否把造出来的生物收进存储（而不是放进世界）。 */
    public static boolean hivemindStoreEnabled() {
        return HIVEMIND.storeEnabled.get();
    }

    /** 一只心智最多存多少生物。存满后照旧放进世界。 */
    public static int hivemindStoreMaxCount() {
        return Math.max(0, HIVEMIND.storeMaxCount.get());
    }

    /** 我方每一波开始时从存储里投放几只。 */
    public static int hivemindDeployPerWave() {
        return Math.max(0, HIVEMIND.deployPerWave.get());
    }

    /** 心智受威胁时是否应急投放。 */
    public static boolean hivemindDeployWhenThreatened() {
        return HIVEMIND.deployWhenThreatened.get();
    }

    /** 应急投放一次放几只。 */
    public static int hivemindThreatDeployCount() {
        return Math.max(0, HIVEMIND.threatDeployCount.get());
    }

    /** 应急投放的冷却。围殴时不该每 tick 都放。 */
    public static int hivemindThreatDeployCooldownTicks() {
        return Math.max(1, HIVEMIND.threatDeployCooldownSeconds.get() * 20);
    }

    /** 存储被打散时最多漏出几只。 */
    public static int hivemindSpillCount() {
        return Math.max(0, HIVEMIND.spillCount.get());
    }

    /**
     * 穹顶每被破坏一块方块，判一次「漏出」的概率。
     *
     * <p>{@code spillCount} 与这一项都填 0 就等于关掉整个漏出机制，没有另设开关。
     */
    public static double hivemindSpillChance() {
        return HIVEMIND.spillChance.get();
    }

    /** 两次「漏出」之间的最短间隔（tick）。一次爆炸会掀掉几十块穹顶，只该漏一次。 */
    public static int hivemindSpillCooldownTicks() {
        return Math.max(1, HIVEMIND.spillCooldownSeconds.get() * 20);
    }

    /** 判定一块躯壳方块属于哪只心智：心智周围多大半径内算它的穹顶（格）。 */
    public static double hivemindDomeRadius() {
        return HIVEMIND.domeRadius.get();
    }

    /** 「心智正受威胁」的判定窗口（tick）：最近这么久内挨过打，就算它正在被威胁。 */
    public static int hivemindThreatWindowTicks() {
        return Math.max(1, HIVEMIND.threatWindowSeconds.get() * 20);
    }

    /** 把一只生物收进存储要花多少资源。 */
    public static int hivemindStoreCost() {
        return Math.max(0, HIVEMIND.storeCost.get());
    }

    /** 投放时，落点被占住的话最多在中心周围多少格内另找空位（格）。 */
    public static double hivemindDeployScatterRadius() {
        return HIVEMIND.deployScatterRadius.get();
    }

    /** 从多大范围内把真菌"调"过来（格）。 */
    public static double raidTeleportSearchRadius() {
        return RAID.teleportSearchRadius.get();
    }

    /** 攻击期间加在参战真菌身上的增益，格式 {@code 效果id|持续tick|amplifier}。 */
    public static List<? extends String> raidAttackBuffs() {
        return RAID.attackBuffs.get();
    }

    /** 准备阶段每隔多少 tick 检查一次发动条件。 */
    public static int raidLaunchCheckIntervalTicks() {
        return Math.max(1, RAID.launchCheckIntervalTicks.get());
    }

    /** 「恐惧吧」比竞技之须出现提前多少 tick 发出。 */
    public static int raidWarnLeadTicks() {
        return Math.max(0, RAID.warnLeadSeconds.get() * 20);
    }

    /** 竞技之须出现概率的<b>基准</b>（刚跨过第一档、恨意值最低时）。 */
    public static double raidArenaChanceBase() {
        return RAID.arenaChanceBase.get();
    }

    /** 恨意档位每高一级，竞技之须的出现概率加多少。 */
    public static double raidArenaChancePerTier() {
        return RAID.arenaChancePerTier.get();
    }

    /** 竞技之须出现概率的上限。 */
    public static double raidArenaChanceMax() {
        return Math.max(raidArenaChanceBase(), RAID.arenaChanceMax.get());
    }

    /** 种给竞技之须的初始波次规模。 */
    public static int raidArenaWaveSize() {
        return RAID.arenaWaveSize.get();
    }

    /** 种给竞技之须的初始波次等级。 */
    public static int raidArenaWaveLevel() {
        return RAID.arenaWaveLevel.get();
    }

    /** 竞技之须阶段最长持续多少 tick（它不自己缩回时的兜底）。 */
    public static int raidArenaTimeoutTicks() {
        return Math.max(1, RAID.arenaTimeoutSeconds.get() * 20);
    }

    /** 玩家躲进黑名单维度后，最多允许缺席多少 tick 才判失败。 */
    public static int raidAbsenceTimeoutTicks() {
        return Math.max(1, RAID.absenceTimeoutSeconds.get() * 20);
    }

    /** 我方围剿波次的<b>基础</b>数量（恨意值最低时）。 */
    public static int raidOwnWaveBase() {
        return RAID.ownWaveBase.get();
    }

    /** 恨意档位每高一级，我方波次多几波。 */
    public static int raidOwnWavePerTier() {
        return RAID.ownWavePerTier.get();
    }

    /** 我方波次数量的上限。 */
    public static int raidOwnWaveMax() {
        return Math.max(raidOwnWaveBase(), RAID.ownWaveMax.get());
    }

    /**
     * 我方每一波最多等多少 tick。
     *
     * <p><b>它是超时上限，不是固定时长</b>：默认情况下这一波什么时候结束由
     * {@link #raidOwnWaveClearCount()} 决定（玩家把场上清干净就提前进下一波）；
     * 这一项只在"清不掉"时兜底——没有它，一波打不完的仗会让整场袭击永远停在那里。
     */
    public static int raidOwnWaveTicks() {
        return Math.max(1, RAID.ownWaveSeconds.get() * 20);
    }

    /** 每往后一波，参战增益的 amplifier 加多少（越往后越强）。 */
    public static int raidOwnWaveBuffsPerWave() {
        return RAID.ownWaveBuffsPerWave.get();
    }

    /** 我方战利品表，格式 {@code 物品id|最少|最多}。 */
    public static List<? extends String> raidOwnLoot() {
        return RAID.ownLoot.get();
    }

    /** 我方战利品表里每一条独立掷多少次（再乘以波次档位）。 */
    public static int raidOwnLootRollsPerWave() {
        return RAID.ownLootRollsPerWave.get();
    }

    /** 我方战利品掉在离玩家多远的范围内（格）。 */
    public static double raidOwnLootRadius() {
        return RAID.ownLootRadius.get();
    }

    // ------------------------------------------------------------------
    // 资源：掉落物收集与 Despawn 清理
    // ------------------------------------------------------------------

    /** 是否让真菌主动收集附近的掉落物。 */
    public static boolean lootCollectEnabled() {
        return LOOT.collectEnabled.get();
    }

    /** 真菌搜寻掉落物的半径（格）。 */
    public static double lootRadius() {
        return LOOT.radius.get();
    }

    /** 两次搜寻之间至少间隔多少 tick。 */
    public static int lootSearchIntervalTicks() {
        return LOOT.searchIntervalTicks.get();
    }

    /**
     * 已经锁定目标、但目标没动过时，隔多少 tick 重新寻一次路。
     *
     * <p>掉落物会被水流与爆炸推走，而导航路线一旦算歪不会自愈，所以要定期重算。
     * 它比 {@link #lootSearchIntervalTicks()} 短得多，因为两者成本差得远：
     * 搜寻是「找有没有新目标」（要遍历半径内所有掉落物，贵），
     * 重算是「照着已经选好的目标继续走」（一次寻路，便宜）。
     */
    public static int lootRepathIntervalTicks() {
        return Math.max(1, LOOT.repathIntervalTicks.get());
    }

    /** 靠到多近才算"捡起来了"（格）。 */
    public static double lootPickupDistance() {
        return LOOT.pickupDistance.get();
    }

    /** 走过去捡东西时的移动速度倍率。 */
    public static double lootMoveSpeed() {
        return LOOT.moveSpeed.get();
    }

    /** 普通真菌那条拾荒目标的优先级（数字越小越优先）。 */
    public static int lootPriority() {
        return LOOT.priority.get();
    }

    /** 两次转化之间至少间隔多少 tick（限流，免得一口吞掉一整片战场）。 */
    public static int lootConvertCooldownTicks() {
        return LOOT.convertCooldownTicks.get();
    }

    /** 转化表，格式 {@code 物品id|资源值} 或 {@code #物品标签|资源值}。 */
    public static List<? extends String> lootValues() {
        return LOOT.values.get();
    }

    /** 转化表里没列到的物品值多少资源。默认 0 = 不捡。 */
    public static double lootDefaultValue() {
        return LOOT.defaultValue.get();
    }

    /** 是否把 Despawning System 清理掉的真菌折算成资源。 */
    public static boolean despawnHarvestEnabled() {
        return LOOT.despawnHarvestEnabled.get();
    }

    /** 被 Despawn 清理掉的一只真菌折算多少资源（再乘它所在等级的倍率）。 */
    public static double despawnBaseValue() {
        return LOOT.despawnBaseValue.get();
    }

    // ------------------------------------------------------------------
    // 各段配置的定义
    // ------------------------------------------------------------------

    /** 真菌加强那一段（第一批的内容，原样搬过来）。 */
    private static final class Fungus {

        private final ForgeConfigSpec.IntValue evolutionSpeedBonus;
        private final ForgeConfigSpec.BooleanValue huntEnabled;
        private final ForgeConfigSpec.DoubleValue huntCautionRatio;
        private final ForgeConfigSpec.DoubleValue freezeDamageMultiplier;
        private final ForgeConfigSpec.DoubleValue sensingMultiplier;
        private final ForgeConfigSpec.IntValue coldFrostbiteIntervalTicks;
        private final ForgeConfigSpec.DoubleValue coldHungerPenaltyFactor;
        private final ForgeConfigSpec.DoubleValue coldBiomeThreshold;
        private final ForgeConfigSpec.IntValue huntSearchIntervalTicks;
        private final ForgeConfigSpec.DoubleValue huntArmorEhpPerPoint;
        private final ForgeConfigSpec.DoubleValue wombMutationMultiplier;
        private final ForgeConfigSpec.DoubleValue wombRaidMutationMultiplier;
        private final ForgeConfigSpec.BooleanValue wombRaidBonusesEnabled;
        private final ForgeConfigSpec.BooleanValue wombHatchExitEnabled;
        private final ForgeConfigSpec.DoubleValue wombHatchExitSearchRadius;
        private final ForgeConfigSpec.BooleanValue wombHatchExitFromDome;
        private final ForgeConfigSpec.DoubleValue wombHatchExitDomeMargin;

        private Fungus(ForgeConfigSpec.Builder builder) {
            builder.comment("真菌加强：让真菌进化更快、更主动、更抗寒。全部为增益方向，填 0 / 关掉即可还原 Spore 原样。")
                    .push("fungus");

            this.evolutionSpeedBonus = builder
                    .comment("每秒额外获得的进化进度点。0 ~ 60，默认 4。",
                            "Spore 自己每秒 +1，所以默认是「快五倍」：进化冷却门槛 300 秒（hyper 是 600）",
                            "约 60 秒（hyper 约 120 秒）就能跨过去。填 0 = 完全按 Spore 原速。",
                            "**与 Spore 不同的一点**：这一笔不看冻伤。Spore 在真菌带冻伤时会把进化进度完全停住，",
                            "这里照常累加——寒冷该削弱真菌的战斗力，但不该把它的成长彻底摁死。",
                            "注意门槛（要杀够多少生物）不受本项影响，只在够格之后才加速。")
                    .defineInRange("evolutionSpeedBonus", 4, 0, 60);

            this.huntEnabled = builder
                    .comment("是否让真菌主动猎杀一切生物（带「打得过才打」的判定）。默认开。",
                            "开启后：真菌会主动攻击**除真菌自己、Spore 黑名单、玩家以外**的所有生物——",
                            "包括 Spore 默认放过的动物（Spore 的 at_an 开关在这里不生效）。",
                            "玩家完全不受影响：锁定与还手都沿用 Spore 原有逻辑，判定不管玩家。",
                            "判定只在「目标落定」那一步生效（拦 LivingChangeTargetEvent），",
                            "所以 Spore 自己那条「攻击所有生物」的目标也会一起受约束——否则判定形同虚设。",
                            "**被打时一定还手，不受判定约束**：否则真菌会站着挨打。")
                    .define("huntEnabled", true);

            this.huntCautionRatio = builder
                    .comment("「打得过」的裕度。0.1 ~ 5.0，默认 1.0。",
                            "判据是比较「谁先死」：我打死它要多久 vs 它打死我要多久（见 FungusCombat#canWin）。",
                            "1.0 = 只有明显占优才打；调大更莽（愿意拿更长的击杀时间去赌），调小更怂。",
                            "要留意这个判据只看攻击力、护甲与最大生命：射程、药水、自爆、图腾都不计入，",
                            "它是开打前的粗略掂量，不是战斗模拟器。")
                    .defineInRange("huntCautionRatio", 1.0D, 0.1D, 5.0D);

            this.freezeDamageMultiplier = builder
                    .comment("真菌受到的冰冻伤害倍率。0.0 ~ 10.0，默认 2.0。",
                            "作用于所有带 minecraft:is_freezing 标签的伤害，主要是 Spore 的冻伤 tick",
                            "（伤害源就是 damageSources().freeze()）与本 mod 冰霜新星 / 冰雪的叹息造成的冻伤，",
                            "也包括原版细雪的冻结伤害。填 1.0 = 与 Spore 原样。",
                            "**注意它只放大伤害，不放大冻伤的减速与冻结计时。**",
                            "这一项刻意不通过调整 Spore 的「抗寒等级」（ColdEndurance）实现：",
                            "那个等级同时决定伤害系数与「冻伤到几层才生效」的门槛，动它会反过来削弱冰冻伤害，",
                            "正好与真菌抗寒那一项抵消掉。")
                    .defineInRange("freezeDamageMultiplier", 2.0D, 0.0D, 10.0D);

            this.sensingMultiplier = builder
                    .comment("真菌感知范围（FOLLOW_RANGE）的倍率。1.0 ~ 4.0，默认 2.0（16 格 → 32 格）。",
                            "同时放大两件事：**视觉**（目标搜索半径）与**声音**",
                            "（同伙被打时 alertOthers 的警报传播半径）——Spore 这两处读的是同一个属性。",
                            "上限说明：巢群广播（LocalTargettingGoal）在 Spore 里被硬编码封顶 32 格，",
                            "所以倍率超过 2.0 之后，继续增长的只有视觉与警报，巢群广播不再跟着变。",
                            "填 1.0 = 原样。")
                    .defineInRange("sensingMultiplier", 2.0D, 1.0D, 4.0D);

            this.coldFrostbiteIntervalTicks = builder
                    .comment("寒冷环境两次「自我施加冻伤」之间的最短间隔（tick）。20 ~ 2400，默认 400。",
                            "Spore 原本是「身上没有冻伤就立刻补一个 100 tick 的」，于是真菌在寒冷里",
                            "**始终**挂着冻伤（掉血 + 减速 + 屏幕结霜）。本项把它改成按间隔补，",
                            "默认 400 tick 配合 100 tick 的时长 = 冻伤占空比从约 100% 降到约 25%。",
                            "**这是削弱而不是移除**：真菌在寒冷里仍会周期性被冻伤，只是不再是常驻。",
                            "填 100 = 与 Spore 原样。")
                    .defineInRange("coldFrostbiteIntervalTicks", 400, 20, 2400);

            this.coldHungerPenaltyFactor = builder
                    .comment("寒冷加速饥饿时，额外那一点保留的比例。0.0 ~ 1.0，默认 0.5。",
                            "Spore 在真菌受冻时把饥饿增速从每秒 1 点提到 2 点，本项控制多出来的那 1 点保留多少：",
                            "1.0 = 原样（每秒 2 点），0.5 = 每秒 1.5 点，0.0 = 寒冷不再加速饥饿。",
                            "之所以用小数累加器而不是简单地取整，是因为整数没法精确表达「一半」，",
                            "直接取整会让 0.5 这个档位要么等于 0、要么等于 1，两头的语义都不对。")
                    .defineInRange("coldHungerPenaltyFactor", 0.5D, 0.0D, 1.0D);

            this.coldBiomeThreshold = builder
                    .comment("真菌眼里的「寒冷」生物群系温度上限。-1.0 ~ 0.2，默认 -0.2。",
                            "Spore 用 0.2，于是雪原（0.0）也算冷。本项收紧到 -0.2 之后：",
                            "雪原不再触发寒冷惩罚，而雪针叶林 / 冰刺之地（-0.5）、冻峰（-0.7）仍然算。",
                            "**阈值天生就是「削弱」而非「移除」**——越冷的地方真菌照样难受，只是范围变小了。",
                            "它影响的是「寒冷自我冻伤」「寒冷加速饥饿」「死亡时留冰冻残骸」这三处共同的前提判定。",
                            "填 0.2 = 与 Spore 原样。")
                    .defineInRange("coldBiomeThreshold", -0.2D, -1.0D, 0.2D);

            this.huntSearchIntervalTicks = builder
                    .comment("「攻击一切生物」那条目标多久搜一次目标（tick）。1 ~ 200，默认 10。",
                            "10 是原版 NearestAttackableTargetGoal 各简版构造器用的默认值，Spore 自己也用它。",
                            "改小的代价是空转成本：每 tick 搜一次全实体是没必要的。")
                    .defineInRange("huntSearchIntervalTicks", 10, 1, 200);

            this.huntArmorEhpPerPoint = builder
                    .comment("「打得过才打」判定里，每 1 点护甲折算成多少有效生命倍率。0.0 ~ 1.0，默认 0.04。",
                            "原版的护甲减伤同时取决于护甲与这一击的伤害，而判定要做的是「还没交手，先估谁更硬」，",
                            "拿不到「这一击多大」，所以退化成「20 点护甲减伤 80%」这个上界 —— 也就是每点 4%。",
                            "它只影响判定，不改真实伤害。0.04 → 20 点护甲 = 2 倍有效生命；",
                            "调大 = 判定更忌惮护甲高的目标（更常放弃），调小则相反。")
                    .defineInRange("huntArmorEhpPerPoint", 0.04D, 0.0D, 1.0D);

            this.wombMutationMultiplier = builder
                    .comment("灾厄重构体同化喂食时，一次喂食算几条突变。1.0 ~ 10.0，默认 2.0。",
                            "需求：叠满属性所需的生物减少 50%，所以默认 2.0——喂一只等于原来的两只。",
                            "**机制说明（反编译 Spore 2.2.0j 确认）**：Womb 并没有「最多叠 N 条」的上限常量。",
                            "addMutation 只是把配方的属性 id 追加进一份不去重、不封顶的列表 attributeIDs，",
                            "孵化灾厄时 summon 再遍历整个列表、每条给对应属性 +1.0 基础值（重复的照样各算一次）。",
                            "所以「叠满」= 玩家自己喂出来的列表长度，本项就是那个长度的倍率。",
                            "填 1.0 = 与 Spore 原样。",
                            "注意它只影响喂食，不影响 Womb 攒生物质成型的快慢（那是另一套 BIOMASS）。")
                    .defineInRange("wombMutationMultiplier", 2.0D, 1.0D, 10.0D);

            this.wombRaidMutationMultiplier = builder
                    .comment("上一条在真菌袭击期间的额外倍率。1.0 ~ 10.0，默认 2.0。",
                            "需求：袭击期间再减少 50%（剩余值的一半）。与上一条相乘，",
                            "所以默认在袭击期间喂一只等于原来的四只。",
                            "**倍率在喂食那一刻锁定**：追加进列表的是重复条目本身，",
                            "于是关掉袭击之后，先前喂进去的那些条目不会被追溯放大——",
                            "这也是不去改 summon 里那个 +1.0D 常量的原因，那个常量是所有条目共用的。",
                            "填 1.0 = 袭击期间与平时一样。")
                    .defineInRange("wombRaidMutationMultiplier", 2.0D, 1.0D, 10.0D);

            this.wombRaidBonusesEnabled = builder
                    .comment("袭击的制造速度 / 资源获取加成是否也作用到灾厄重构体。默认开。",
                            "需求原文说的是「心智制造速度加快 500%」，而灾厄重构体（Womb）不是心智：",
                            "它有自己的 BIOMASS、自己的配方体系，与心智之间没有任何数据通路",
                            "（它孵化灾厄是直接 create + addFreshEntity，不走心智的 summonMob）。",
                            "所以那项加成原本到不了它——这一项就是把袭击的加成单独接到它身上，属于扩展。",
                            "接的是两条已有的倍率，不新增数值：",
                            "  制造速度（raid.manufactureSpeedMultiplier）→ 孵化节拍（recontructor_clock 秒数）",
                            "  资源获取（raid.prepResourceGainMultiplier）→ 每次同化得到的生物质",
                            "关掉 = 灾厄重构体完全按 Spore 原速。")
                    .define("wombRaidBonusesEnabled", true);

            this.wombHatchExitEnabled = builder
                    .comment("灾厄孵化后是否把它挪到重构体外面去。默认开。",
                            "Spore 原本把孵化出的灾厄放在**重构体自己身上**（setPos 用的就是它自己的坐标），",
                            "而重构体是埋在生物质里的土丘——它的 aiStep 在被方块卡住时会挖掉周围方块，",
                            "说明它本来就嵌在实心方块里，体型还会随生物质长到 3 档。",
                            "于是大 hitbox 的灾厄一孵化出来就卡在方块中窒息，进而把那圈壳挖烂。",
                            "开启后：向外找最近的空位再放它（见 WombHatch）；找不到空位就维持原位。")
                    .define("wombHatchExitEnabled", true);

            this.wombHatchExitSearchRadius = builder
                    .comment("找壳外空位时最多向外找多远（格）。1.0 ~ 32.0，默认 8.0。",
                            "起点由两个包围盒的半宽决定（刚好不重叠的距离），从这个半径起逐格向外找，",
                            "水平八个方向都试；水平全被堵住时再试着往上抬。",
                            "调大 = 更愿意把它丢远一点，代价是可能落到玩家没预料的地方；",
                            "找不到任何空位时不动它——宁可维持原位，也不扔进岩层或虚空。")
                    .defineInRange("wombHatchExitSearchRadius", 8.0D, 1.0D, 32.0D);

            this.wombHatchExitFromDome = builder
                    .comment("孵化出的灾厄是否要被送出心智的生物质穹顶（不只是送出重构体本身）。默认开。",
                            "**这里的「壳」指心智那两层生物质球壳**（半径 32 厚 2、半径 16 厚 1，",
                            "由 Spore 的 CasingGenerator 生成，见 DomeBreach 的类注释），",
                            "不是重构体自己那圈方块。需求要防的是「灾厄在穹顶里把壳挖烂」，",
                            "而只把它挪出重构体的包围盒根本不足以让它离开穹顶——所以默认走穹顶判定。",
                            "落点用的穹顶半径取自 hivemind 段的 domeRadius，两边共用同一个「穹顶有多大」。")
                    .define("wombHatchExitFromDome", true);

            this.wombHatchExitDomeMargin = builder
                    .comment("送出穹顶时，在穹顶半径之外再留出多少格。0.0 ~ 16.0，默认 2.0。",
                            "Spore 的外层壳是「半径 32、厚 2」，所以壳的外表面到 33；",
                            "默认在 32 之外再加 2，落点稳定在壳外。",
                            "**注意这个距离是从心智的位置算的**，也就是说灾厄会被丢到离心智",
                            "三十几格远的地方——它本来就不该待在巢里，这个距离是刻意的。",
                            "放不下（那一圈全是实心方块）时会依次试别的角度；一圈都不行就退回",
                            "「只送出重构体」那个较弱的落点，而不是硬塞进石头里。")
                    .defineInRange("wombHatchExitDomeMargin", 2.0D, 0.0D, 16.0D);

            builder.pop();
        }
    }

    /**
     * 拾荒者那一段。
     *
     * <p>拾荒者是菌染人类的一个变体：不参战、专职捡掉落物、把收获供给同伙。它的一切数值都在这里。
     */
    private static final class Scavenger {

        private final ForgeConfigSpec.DoubleValue transformMinChance;
        private final ForgeConfigSpec.DoubleValue transformMaxChance;
        private final ForgeConfigSpec.DoubleValue transformItemRadius;
        private final ForgeConfigSpec.IntValue transformItemCountForMax;
        private final ForgeConfigSpec.DoubleValue lootBonusPerMinute;
        private final ForgeConfigSpec.DoubleValue lootBonusMax;
        private final ForgeConfigSpec.DoubleValue healthPerMinute;
        private final ForgeConfigSpec.DoubleValue healthMaxBonus;
        private final ForgeConfigSpec.DoubleValue armorPerMinute;
        private final ForgeConfigSpec.DoubleValue armorMaxBonus;
        private final ForgeConfigSpec.DoubleValue speedPerMinute;
        private final ForgeConfigSpec.DoubleValue speedMaxBonus;
        private final ForgeConfigSpec.DoubleValue regenHpsPerMinute;
        private final ForgeConfigSpec.DoubleValue regenMaxHps;
        private final ForgeConfigSpec.DoubleValue fleeHealthFraction;
        private final ForgeConfigSpec.DoubleValue fleeThreatRadius;
        private final ForgeConfigSpec.DoubleValue fleeSpeed;
        private final ForgeConfigSpec.DoubleValue deliveryRadius;
        private final ForgeConfigSpec.IntValue healMinTargets;
        private final ForgeConfigSpec.IntValue healMaxTargets;
        private final ForgeConfigSpec.DoubleValue healRampMinutes;
        private final ForgeConfigSpec.IntValue healMinTier;
        private final ForgeConfigSpec.DoubleValue healWoundedFraction;
        private final ForgeConfigSpec.DoubleValue healthPerResource;
        private final ForgeConfigSpec.DoubleValue evoPointsPerResource;
        private final ForgeConfigSpec.IntValue maxCount;
        private final ForgeConfigSpec.IntValue fleePriority;
        private final ForgeConfigSpec.IntValue lootPriority;

        private Scavenger(ForgeConfigSpec.Builder builder) {
            builder.comment("拾荒者：菌染人类的变体，不参战、专职捡掉落物、把收获供给同伙。",
                            "它的模型/贴图/音效与菌染人类完全一致（复用 Spore 那一套），所以只能靠行为辨认。",
                            "注意：想让它不被 Despawning System 清掉、从而活得够久吃满存活加成，",
                            "建议把 spore_add:scavenger 加进 Spore 自己的 despawn_blacklist（那是 Spore 的配置，本 mod 不动它）。")
                    .push("scavenger");

            this.transformMinChance = builder
                    .comment("菌染人类生成时转变成拾荒者的概率**下限**。0.0 ~ 1.0，默认 0.10（需求写的 10%）。",
                            "附近一件掉落物都没有时取这个值。")
                    .defineInRange("transformMinChance", 0.10D, 0.0D, 1.0D);

            this.transformMaxChance = builder
                    .comment("转变概率的**上限**。0.0 ~ 1.0，默认 0.50（需求写的 50%）。",
                            "低于下限时会被自动抬到下限，否则曲线会反向。")
                    .defineInRange("transformMaxChance", 0.50D, 0.0D, 1.0D);

            this.transformItemRadius = builder
                    .comment("统计「附近有多少掉落物」时的半径（格）。1.0 ~ 64.0，默认 16.0。",
                            "它决定拾荒者在什么样的地方更容易出现：刚打完一架、满地战利品的战场。")
                    .defineInRange("transformItemRadius", 16.0D, 1.0D, 64.0D);

            this.transformItemCountForMax = builder
                    .comment("掉落物到多少件时转变概率达到上限。1 ~ 512，默认 32。",
                            "概率在「0 件 → 下限」与「本项件数 → 上限」之间**线性**插值，超过本项就封顶。")
                    .defineInRange("transformItemCountForMax", 32, 1, 512);

            this.lootBonusPerMinute = builder
                    .comment("存活时间换算成拾荒加成的速率：每存活一分钟，收获倍率加多少。0.0 ~ 10.0，默认 0.25。",
                            "需求 6：拾荒者活得越久，捡东西的收益越高。")
                    .defineInRange("lootBonusPerMinute", 0.25D, 0.0D, 10.0D);

            this.lootBonusMax = builder
                    .comment("存活加成的上限（不含基础的 1.0）。0.0 ~ 100.0，默认 2.0（即最多 ×3）。")
                    .defineInRange("lootBonusMax", 2.0D, 0.0D, 100.0D);

            this.healthPerMinute = builder
                    .comment("拾荒者每存活一分钟增加多少最大生命。0.0 ~ 100.0，默认 2.0。",
                            "需求：拾荒者的生命、防御、速度、生命恢复速度都随存活时间增长。",
                            "四条曲线都是「线性爬升、各自封顶」，与 lootBonusPerMinute 同一套算法；",
                            "时间基准是实体的 tickCount（它活了多久），不额外存盘。",
                            "这条的封顶见 healthMaxBonus。填 0 = 生命不随存活时间成长。")
                    .defineInRange("healthPerMinute", 2.0D, 0.0D, 100.0D);

            this.healthMaxBonus = builder
                    .comment("最大生命成长的上限。0.0 ~ 500.0，默认 20.0。",
                            "以拾荒者自身的基础生命为基准往上加，所以最终生命 = 基础 + 本项（封顶后）。",
                            "换算：默认值下存活 10 分钟加满 20 点生命。")
                    .defineInRange("healthMaxBonus", 20.0D, 0.0D, 500.0D);

            this.armorPerMinute = builder
                    .comment("每存活一分钟增加多少护甲。0.0 ~ 10.0，默认 0.5。",
                            "护甲是属性绝对值（原版每点护甲约减伤 4%，上限 20 点护甲），默认值下存活 10 分钟加满 5 点。")
                    .defineInRange("armorPerMinute", 0.5D, 0.0D, 10.0D);

            this.armorMaxBonus = builder
                    .comment("护甲成长的上限。0.0 ~ 100.0，默认 5.0。")
                    .defineInRange("armorMaxBonus", 5.0D, 0.0D, 100.0D);

            this.speedPerMinute = builder
                    .comment("每存活一分钟增加多少移动速度。0.0 ~ 1.0，默认 0.01。",
                            "注意这是**属性的绝对值**，不是倍率：Spore 给菌染人类的移动速度基础值是 0.2",
                            "（javap 实测），所以 0.01 约等于「每分钟快 5%」；",
                            "默认值下存活 5 分钟加满 0.05，也就是快 25%。")
                    .defineInRange("speedPerMinute", 0.01D, 0.0D, 1.0D);

            this.speedMaxBonus = builder
                    .comment("移动速度成长的上限（属性绝对值）。0.0 ~ 1.0，默认 0.05。",
                            "调大要谨慎：超过基础值本身太多会让它在逃跑时难以被追上，",
                            "而「会逃跑、且越活越难抓」正是拾荒者的设计意图——但要留出反制空间。")
                    .defineInRange("speedMaxBonus", 0.05D, 0.0D, 1.0D);

            this.regenHpsPerMinute = builder
                    .comment("每存活一分钟，每秒回复的生命点数涨多少。0.0 ~ 10.0，默认 0.5。",
                            "「生命恢复速度」做成每秒回复 N 点，N 随存活时间线性爬升、由 regenMaxHps 封顶。",
                            "默认值下存活 4 分钟到达每秒 2 点。填 0 = 不回复。")
                    .defineInRange("regenHpsPerMinute", 0.5D, 0.0D, 10.0D);

            this.regenMaxHps = builder
                    .comment("每秒回复生命的上限。0.0 ~ 50.0，默认 2.0。",
                            "实现上用一个小数累加器攒够 1 点才真的回一次血——",
                            "heal() 只收整数，0.5 点/秒这种速率直接取整会变成永不回复。")
                    .defineInRange("regenMaxHps", 2.0D, 0.0D, 50.0D);

            this.fleeHealthFraction = builder
                    .comment("血量低于最大生命的这个比例时，**无条件**逃跑（即使面对玩家）。0.0 ~ 1.0，默认 0.5。",
                            "需求 5：躲避危险是第一要务，血量不足时逃跑。",
                            "填 0.0 = 只在察觉到威胁时才跑，不再因为残血而主动跑。")
                    .defineInRange("fleeHealthFraction", 0.5D, 0.0D, 1.0D);

            this.fleeThreatRadius = builder
                    .comment("感知到多远的威胁就跑（格）。1.0 ~ 64.0，默认 16.0。",
                            "「威胁」= 把它当敌人的生物（原版判定），所以躲在墙后、隔着一片水通常不会被当成威胁。")
                    .defineInRange("fleeThreatRadius", 16.0D, 1.0D, 64.0D);

            this.fleeSpeed = builder
                    .comment("逃跑时的移动速度倍率。0.1 ~ 5.0，默认 1.5。",
                            "菌染人类的基础移速是 0.2，所以 1.5 倍约等于 0.3——比普通僵尸快一点，但追不上玩家。")
                    .defineInRange("fleeSpeed", 1.5D, 0.1D, 5.0D);

            this.deliveryRadius = builder
                    .comment("把收获交给同伙时扫描的半径（格）。1.0 ~ 128.0，默认 24.0。",
                            "三条交付路径（给心智 / 治疗残血同伙 / 折算进化点）共用这一个半径。")
                    .defineInRange("deliveryRadius", 24.0D, 1.0D, 128.0D);

            this.healMinTargets = builder
                    .comment("一次最多同时治疗几个残血同伙（存活时间短时）。0 ~ 32，默认 2。",
                            "需求写的是「最多同时治疗 2-5 个，随存活时间增加」。")
                    .defineInRange("healMinTargets", 2, 0, 32);

            this.healMaxTargets = builder
                    .comment("随存活时间最多能涨到几个。0 ~ 32，默认 5。",
                            "低于 healMinTargets 时会被自动抬到它。")
                    .defineInRange("healMaxTargets", 5, 0, 32);

            this.healRampMinutes = builder
                    .comment("从 min 涨到 max 需要存活多少分钟。0.01 ~ 120.0，默认 10.0。",
                            "线性增长，到点封顶。")
                    .defineInRange("healRampMinutes", 10.0D, 0.01D, 120.0D);

            this.healMinTier = builder
                    .comment("多高的等级才算「值得优先抢救」。0 ~ 5，默认 1。",
                            "用的是恨意值那张等级表的序号：",
                            "  0 = 基础感染体    1 = 进化体    2 = 超级体    3 = 器官类    4 = 灾厄类    5 = 其他",
                            "默认 1 表示「进化体及以上」才值得把资源花在它身上——",
                            "基础感染体满地都是，救它不如拿去换进化点。")
                    .defineInRange("healMinTier", 1, 0, 5);

            this.healWoundedFraction = builder
                    .comment("血量低于最大生命的这个比例才算「残血」、才值得治疗。0.0 ~ 1.0，默认 0.6。",
                            "三条交付路径里，**治疗优先于折算进化点**：",
                            "先看有没有够格又残血的同伙，有就把资源变成它们的血（按血量最低优先，最多 healMaxTargets 个）；",
                            "一个都没有，才把资源折算成进化点分给附近真菌。")
                    .defineInRange("healWoundedFraction", 0.6D, 0.0D, 1.0D);

            this.healthPerResource = builder
                    .comment("1 点资源能换多少点生命值。0.0 ~ 1000.0，默认 1.0。",
                            "拾荒者捡一块铁矿石换 6 点资源，也就是 6 点血——量级参考这个来调。")
                    .defineInRange("healthPerResource", 1.0D, 0.0D, 1000.0D);

            this.evoPointsPerResource = builder
                    .comment("1 点资源能换多少进化点。0.0 ~ 100.0，默认 0.1。",
                            "Spore 里「杀一个生物 = 1 进化点」，而进化门槛默认是 1 点（基础）或 7 点（超级）。",
                            "所以默认 0.1 表示「10 点资源养出一次进化机会」——一块钻石的收获就够一多半。",
                            "**换算出的进化点按整数发放，余数留在拾荒者身上继续攒**（与冻伤层数用的是同一套小数累加思路）。")
                    .defineInRange("evoPointsPerResource", 0.1D, 0.0D, 100.0D);

            this.maxCount = builder
                    .comment("世上同时存在的拾荒者**上限**。0 ~ 500，默认 12。",
                            "需求：数量上限 + 数量越多转换概率越低。这两件事由同一条公式实现——",
                            "  实际概率 = 基础概率 × max(0, 1 - 现存 / 上限)",
                            "所以现存为 0 时概率不打折，到上限时正好降到 0，**上限本身就是概率归零点**，",
                            "不必再单独判一次上限，也就不会出现两条规则互相打架。",
                            "现存数量是增量维护的（实体进出世界时 ±1），不每次遍历世界去数。",
                            "调小可以限制拾荒者泛滥；调 0 = 完全不再出现（已经存在的不会消失）。")
                    .defineInRange("maxCount", 12, 0, 500);

            this.fleePriority = builder
                    .comment("逃跑目标的优先级。0 ~ 20，默认 0（数字越小越优先）。",
                            "**必须小于 lootPriority**，否则它会在该跑的时候停下来捡东西。",
                            "需求把「躲避危险」定为第一要务，所以默认给它 0——那是所有目标里的最优先。")
                    .defineInRange("fleePriority", 0, 0, 20);

            this.lootPriority = builder
                    .comment("拾荒目标挂在行动目标选择器上的优先级。0 ~ 20，默认 2。",
                            "**必须比逃跑目标低**（数字大），否则它会为了捡东西而不跑；",
                            "但要比闲逛、环顾那一批高得多，否则它根本轮不到捡东西。",
                            "逃跑目标固定在优先级 0，所以本项填 1 以上才有意义。")
                    .defineInRange("lootPriority", 2, 0, 20);

            builder.pop();
        }
    }

    /** 恨意值的来源与权重。 */
    private static final class Hatred {

        private final ForgeConfigSpec.DoubleValue basePerKill;
        private final ForgeConfigSpec.DoubleValue maxPerKill;
        private final ForgeConfigSpec.DoubleValue tierInfected;
        private final ForgeConfigSpec.DoubleValue tierEvolved;
        private final ForgeConfigSpec.DoubleValue tierHyper;
        private final ForgeConfigSpec.DoubleValue tierOrganoid;
        private final ForgeConfigSpec.DoubleValue tierCalamity;
        private final ForgeConfigSpec.DoubleValue tierOther;
        private final ForgeConfigSpec.DoubleValue linkedMultiplier;
        private final ForgeConfigSpec.DoubleValue unlinkedMultiplier;
        private final ForgeConfigSpec.DoubleValue developmentWeight;
        private final ForgeConfigSpec.DoubleValue developmentCap;
        private final ForgeConfigSpec.ConfigValue<List<? extends String>> typeMultipliers;
        private final ForgeConfigSpec.ConfigValue<List<? extends String>> importanceMultipliers;
        private final ForgeConfigSpec.DoubleValue importanceChunkLoader;
        private final ForgeConfigSpec.DoubleValue importanceCasingGenerator;
        private final ForgeConfigSpec.DoubleValue importanceFoliageSpread;
        private final ForgeConfigSpec.DoubleValue importanceEvolving;

        private Hatred(ForgeConfigSpec.Builder builder) {
            builder.comment("恨意值：玩家击杀真菌能拿多少。最终值 = 基础值 × 等级 × 类型 × 重要性 × 链接 × (1+发育)。")
                    .push("hatred");

            this.basePerKill = builder
                    .comment("击杀一只真菌的基础恨意值，其余倍率都乘在它上面。0.0 ~ 10000.0，默认 10.0。")
                    .defineInRange("basePerKill", 10.0D, 0.0D, 10000.0D);

            this.maxPerKill = builder
                    .comment("单次击杀最多能拿多少恨意值。0.0 ~ 1000000.0，默认 1000.0。",
                            "它是一道保险：等级倍率与按实体覆盖表相乘时很容易手滑多打一个零，",
                            "有这个上限，配错也不会让某个玩家一夜之间变成神。")
                    .defineInRange("maxPerKill", 1000.0D, 0.0D, 1000000.0D);

            this.tierInfected = builder
                    .comment("【等级】基础感染体（Infected 本身，如感染人类 / 感染者 / 感染村民）的倍率。默认 1.0。")
                    .defineInRange("tierInfected", 1.0D, 0.0D, 1000.0D);

            this.tierEvolved = builder
                    .comment("【等级】进化体（EvolvedInfected，如骑士 / 蛮兽）的倍率。默认 2.5。")
                    .defineInRange("tierEvolved", 2.5D, 0.0D, 1000.0D);

            this.tierHyper = builder
                    .comment("【等级】超级体（Hyper）的倍率。默认 6.0。",
                            "注意 Spore 里 Hyper 与 EvolvedInfected **是兄弟不是父子**（都直接继承 Infected），",
                            "本 mod 的判定按「先查 Hyper 再查 EvolvedInfected」的顺序，所以两者不会串。")
                    .defineInRange("tierHyper", 6.0D, 0.0D, 1000.0D);

            this.tierOrganoid = builder
                    .comment("【等级】器官类（Organoid：心智 Proto、蜂巢肿瘤 HiveTumor、丘 Mound……）的倍率。默认 4.0。")
                    .defineInRange("tierOrganoid", 4.0D, 0.0D, 1000.0D);

            this.tierCalamity = builder
                    .comment("【等级】灾厄类（Calamity 及其子类：吞噬者 / 利维坦 / 洞食者 / 围攻者 / 榴弹者……）的倍率。默认 25.0。",
                            "它们是 Spore 里最难对付的一档，杀掉理应记一大笔。",
                            "**判据只认 Calamity 这个父类，不认 TrueCalamity 接口**：Spore 里利维坦与洞食者的",
                            "分节实体（LeviathanMultipart / HohlMultipart）是独立的 LivingEntity 且**也实现了**",
                            "TrueCalamity，但它们是 BOSS 的尾巴与节肢、不是 BOSS 本身。认接口的话，",
                            "砍一节尾巴会按灾厄算，那显然不对。")
                    .defineInRange("tierCalamity", 25.0D, 0.0D, 1000.0D);

            this.tierOther = builder
                    .comment("【等级】上面都不匹配的真菌实体的倍率。0.0 ~ 1000.0，**默认 0.0**。",
                            "默认给 0 是刻意的：落到这一档的绝大多数是「不是生物的生物」——BOSS 的分节实体、",
                            "感染爪之类的工具实体。它们走的是与本体无关的独立实体，杀了不该记恨意值。",
                            "（分节实体必须是独立的 LivingEntity 才能有独立判定箱，所以它们确实会触发死亡事件。）",
                            "如果整合包里想让某类工具实体也计入，把这一项调大，或用下面的 typeMultipliers 单独指定。")
                    .defineInRange("tierOther", 0.0D, 0.0D, 1000.0D);

            this.linkedMultiplier = builder
                    .comment("【是否与心智链接】链接状态（Infected#getLinked()）为真时的倍率。默认 2.0。",
                            "Spore 里被心智扫描到的真菌会被置为 linked，这类真菌是被「登记在册」的，",
                            "杀掉它更「疼」，所以默认给双倍。非 Infected 的器官类没有这个状态，不参与本项。")
                    .defineInRange("linkedMultiplier", 2.0D, 0.0D, 1000.0D);

            this.unlinkedMultiplier = builder
                    .comment("【是否与心智链接】未链接时的倍率。默认 1.0（不给惩罚，只是拿不到链接加成）。")
                    .defineInRange("unlinkedMultiplier", 1.0D, 0.0D, 1000.0D);

            this.developmentWeight = builder
                    .comment("【进化难度 / 发育程度】每 1 进化点（Infected#getEvoPoints()）换算成多少额外倍率。",
                            "0.0 ~ 10.0，默认 0.1。",
                            "Spore 的真菌靠击杀累积进化点、攒够了就进化，所以进化点越高 = 这只真菌越「成熟」、",
                            "越接近下一次进化，杀它拿到的东西理应更多。默认 0.1 表示 10 点进化点 = +100%。",
                            "非 Infected 的器官类没有进化点，本项对它们恒为 0。")
                    .defineInRange("developmentWeight", 0.1D, 0.0D, 10.0D);

            this.developmentCap = builder
                    .comment("发育程度带来的额外倍率上限（不含基础的 1.0）。0.0 ~ 100.0，默认 5.0（即最多 +500%）。")
                    .defineInRange("developmentCap", 5.0D, 0.0D, 100.0D);

            this.typeMultipliers = builder
                    .comment("【类型】按实体微调，格式 \"实体id|倍率\"，可写多行。默认空 = 全部按 1.0。",
                            "例：[\"spore:inf_human|2.0\", \"spore:leaper|0.5\"]",
                            "与下面那张「重要性」表是**两个独立的维度**：这一张是「这个物种我另外想给多少」，",
                            "那一张是「这个生物在阵营里的职能有多关键」。任一张留空就等于关掉该维度。",
                            "写错的条目会被跳过并在日志里留一行警告，不会崩。")
                    .defineList("typeMultipliers", List.of(), o -> o instanceof String);

            this.importanceMultipliers = builder
                    .comment("【重要性】按实体覆盖，格式 \"实体id|倍率\"。默认空 = 用下面的职能默认值。")
                    .defineList("importanceMultipliers", List.of(), o -> o instanceof String);

            this.importanceChunkLoader = builder
                    .comment("【重要性】会加载区块的真菌（Spore 的 ChunkLoaderMob 接口）的倍率。默认 4.0。",
                            "这类真菌离得远也在维持感染区的加载，是阵营的「基建」。")
                    .defineInRange("importanceChunkLoader", 4.0D, 0.0D, 1000.0D);

            this.importanceCasingGenerator = builder
                    .comment("【重要性】能生成菌壳的真菌（CasingGenerator 接口，主要是心智 Proto）的倍率。默认 3.0。")
                    .defineInRange("importanceCasingGenerator", 3.0D, 0.0D, 1000.0D);

            this.importanceFoliageSpread = builder
                    .comment("【重要性】会铺开植被的真菌（FoliageSpread 接口）的倍率。默认 2.0。")
                    .defineInRange("importanceFoliageSpread", 2.0D, 0.0D, 1000.0D);

            this.importanceEvolving = builder
                    .comment("【重要性】还有进化余地的真菌（EvolvingInfected 接口）的倍率。默认 1.5。",
                            "一只随时可能进化成进化体的基础感染体，比一只已经走到头的不那么「浪费」。")
                    .defineInRange("importanceEvolving", 1.5D, 0.0D, 1000.0D);

            builder.pop();
        }
    }



    /** 真菌类食物。 */
    private static final class Food {

        private final ForgeConfigSpec.DoubleValue reduceChance;
        private final ForgeConfigSpec.DoubleValue reduceRatio;
        private final ForgeConfigSpec.DoubleValue increaseChance;
        private final ForgeConfigSpec.DoubleValue increaseRatio;

        private Food(ForgeConfigSpec.Builder builder) {
            builder.comment("食用真菌类食物的恨意值变化。",
                            "「真菌类食物」由本 mod 的物品标签 #spore_add:fungal_food 定义",
                            "（见 data/spore_add/tags/items/fungal_food.json），整合包可以自己增删。")
                    .push("food");

            this.reduceChance = builder
                    .comment("触发「降低恨意值」的概率。0.0 ~ 1.0，默认 0.10。")
                    .defineInRange("reduceChance", 0.10D, 0.0D, 1.0D);

            this.reduceRatio = builder
                    .comment("降低当前恨意值的比例。0.0 ~ 1.0，默认 0.10。")
                    .defineInRange("reduceRatio", 0.10D, 0.0D, 1.0D);

            this.increaseChance = builder
                    .comment("触发「提高恨意值」的概率。0.0 ~ 1.0，默认 0.05。",
                            "两支是**独立掷骰**的，所以理论上有可能同一次同时命中（先减后加）。")
                    .defineInRange("increaseChance", 0.05D, 0.0D, 1.0D);

            this.increaseRatio = builder
                    .comment("提高当前恨意值的比例。0.0 ~ 1.0，默认 0.20。")
                    .defineInRange("increaseRatio", 0.20D, 0.0D, 1.0D);

            builder.pop();
        }
    }

    /** 死亡与其它降低途径。 */
    private static final class Death {

        private final ForgeConfigSpec.DoubleValue fungusDeathLossRatio;
        private final ForgeConfigSpec.DoubleValue resourceMultiplier;
        private final ForgeConfigSpec.DoubleValue hivemindKillLossRatio;
        private final ForgeConfigSpec.DoubleValue raidWinLossRatio;

        private Death(ForgeConfigSpec.Builder builder) {
            builder.comment("死亡与其它降低恨意值的途径。三处都是「按当前值的比例削减」，不是减一个固定数。")
                    .push("death");

            this.fungusDeathLossRatio = builder
                    .comment("死于真菌之手时，个人恨意值损失的比例。0.0 ~ 1.0，默认 0.8。")
                    .defineInRange("fungusDeathLossRatio", 0.8D, 0.0D, 1.0D);

            this.resourceMultiplier = builder
                    .comment("那笔损失换算成资源时乘的倍率。需求写的是「X2」，所以默认 2.0。",
                            "资源给所有心智（Proto）均分；一只心智都没有时先存起来，",
                            "等有心智出现再补发。")
                    .defineInRange("resourceMultiplier", 2.0D, 0.0D, 100.0D);

            this.hivemindKillLossRatio = builder
                    .comment("击杀心智（Proto）时，个人恨意值损失的比例。0.0 ~ 1.0，默认 0.9。",
                            "心智是阵营的核心，所以这条削得最狠。")
                    .defineInRange("hivemindKillLossRatio", 0.9D, 0.0D, 1.0D);

            this.raidWinLossRatio = builder
                    .comment("打赢一次真菌袭击时，个人恨意值损失的比例。0.0 ~ 1.0，默认 0.10。")
                    .defineInRange("raidWinLossRatio", 0.10D, 0.0D, 1.0D);

            builder.pop();
        }
    }

    /** 世界恨意值给真菌的减伤。 */
    private static final class FungusResistance {

        private final ForgeConfigSpec.DoubleValue perHatred;
        private final ForgeConfigSpec.DoubleValue maxResistance;

        private FungusResistance(ForgeConfigSpec.Builder builder) {
            builder.comment("世界恨意值给真菌的减伤。**只对非玩家伤害生效**，玩家造成的伤害完全不受影响。")
                    .push("fungusResistance");

            this.perHatred = builder
                    .comment("世界恨意值每 1 点给真菌带来的减伤比例。默认 0.00002（即全服合计 30000 点减伤 60%）。",
                            "世界恨意值 = 所有玩家（含离线）个人恨意值之和。")
                    .defineInRange("perHatred", 0.00002D, 0.0D, 1.0D);

            this.maxResistance = builder
                    .comment("真菌减伤的上限。0.0 ~ 0.99，默认 0.6。",
                            "**不能配到 1.0**：那会让真菌对摔落、岩浆、其它生物等一切非玩家伤害完全免疫，",
                            "整个世界的真菌会变成只会被玩家打死的静止物。")
                    .defineInRange("maxResistance", 0.6D, 0.0D, 0.99D);

            builder.pop();
        }
    }

    /** 袭击触发。 */
    private static final class Raid {

        private final ForgeConfigSpec.DoubleValue thresholdStep;
        private final ForgeConfigSpec.DoubleValue triggerChance;
        private final ForgeConfigSpec.IntValue prepSeconds;
        private final ForgeConfigSpec.IntValue prepMaxSeconds;
        private final ForgeConfigSpec.DoubleValue prepResourceCostMultiplier;
        private final ForgeConfigSpec.DoubleValue prepResourceGainMultiplier;
        private final ForgeConfigSpec.DoubleValue prepGrowthSpeedMultiplier;
        private final ForgeConfigSpec.DoubleValue manufactureSpeedMultiplier;
        private final ForgeConfigSpec.IntValue launchMinBiomass;
        private final ForgeConfigSpec.IntValue launchMinFungusCount;
        private final ForgeConfigSpec.DoubleValue launchMinQuality;
        private final ForgeConfigSpec.DoubleValue gatherRadius;
        private final ForgeConfigSpec.DoubleValue despawnCapMultiplier;
        private final ForgeConfigSpec.DoubleValue teleportMinDistance;
        private final ForgeConfigSpec.DoubleValue teleportMaxDistance;
        private final ForgeConfigSpec.DoubleValue teleportSearchRadius;
        private final ForgeConfigSpec.ConfigValue<List<? extends String>> attackBuffs;
        private final ForgeConfigSpec.IntValue launchCheckIntervalTicks;
        private final ForgeConfigSpec.IntValue warnLeadSeconds;
        private final ForgeConfigSpec.DoubleValue arenaChanceBase;
        private final ForgeConfigSpec.DoubleValue arenaChancePerTier;
        private final ForgeConfigSpec.DoubleValue arenaChanceMax;
        private final ForgeConfigSpec.IntValue arenaWaveSize;
        private final ForgeConfigSpec.IntValue arenaWaveLevel;
        private final ForgeConfigSpec.IntValue arenaTimeoutSeconds;
        private final ForgeConfigSpec.IntValue absenceTimeoutSeconds;
        private final ForgeConfigSpec.IntValue ownWaveBase;
        private final ForgeConfigSpec.IntValue ownWavePerTier;
        private final ForgeConfigSpec.IntValue ownWaveCountBase;
        private final ForgeConfigSpec.IntValue ownWaveCountPerWave;
        private final ForgeConfigSpec.DoubleValue ownWaveClearRadius;
        private final ForgeConfigSpec.IntValue ownWaveClearCount;
        private final ForgeConfigSpec.IntValue ownWaveMax;
        private final ForgeConfigSpec.IntValue ownWaveSeconds;
        private final ForgeConfigSpec.IntValue ownWaveBuffsPerWave;
        private final ForgeConfigSpec.ConfigValue<List<? extends String>> ownLoot;
        private final ForgeConfigSpec.IntValue ownLootRollsPerWave;
        private final ForgeConfigSpec.DoubleValue ownLootRadius;

        private Raid(ForgeConfigSpec.Builder builder) {
            builder.comment("真菌袭击：由心智发起，分准备与攻击两个阶段。",
                            "触发链是「个人恨意值跨档 → 掷 triggerChance → 进入准备阶段」。",
                            "**只有世上存在心智（Spore 的 Proto）时才可能触发**——袭击是心智发动的，",
                            "一只心智都没有的世界里没有发动者，那时越档会被静默跳过（日志里会留一行）。",
                            "**维度黑名单也是硬性的**，但它在数据包里而不是这里：",
                            "data/<命名空间>/raid_blacklist/<任意名字>.json，格式见 RaidDimensionBlacklist 的类注释。",
                            "**袭击是内存里的状态，不存盘**：服务器重启会静默取消正在进行的袭击。",
                            "已落地的效果（传送、增益）本来就作用在实体身上、各自会存续，",
                            "而「这次袭击编排到哪一步」这种瞬时状态，重启后从零开始比恢复一个半截的更好。")
                    .push("raid");

            this.thresholdStep = builder
                    .comment("个人恨意值每涨满这么多，就算跨过一个档位并掷一次触发骰。1.0 ~ 100000.0，默认 500.0。",
                            "判据是 floor(新值 / 本项) 是否比 floor(旧值 / 本项) 大，",
                            "所以每一次**跨档**才掷一次，同一档位内反复涨落不会重复触发。")
                    .defineInRange("thresholdStep", 500.0D, 1.0D, 100000.0D);

            this.triggerChance = builder
                    .comment("每跨过一个档位时触发袭击的概率。0.0 ~ 1.0，默认 0.5（需求写的就是 50%）。")
                    .defineInRange("triggerChance", 0.5D, 0.0D, 1.0D);

            this.prepSeconds = builder
                    .comment("准备阶段持续多少秒后转入攻击。1 ~ 600，默认 60。",
                            "**这一档决定玩家有多少反应时间**：准备阶段唯一的可见信号就是开场那句台词，",
                            "以及心智开始「全力运转」（资源、发育加成）。真打起来是攻击阶段的事。")
                    .defineInRange("prepSeconds", 60, 1, 600);

            this.prepMaxSeconds = builder
                    .comment("准备阶段最多拖多少秒。1 ~ 3600，默认 180。",
                            "到点还没凑够发动条件（见下面三项）就**取消**这次袭击，而不是硬打。",
                            "取值必须 >= prepSeconds，否则会被自动抬到 prepSeconds——",
                            "不然「准备期一结束就超时」会让袭击永远发动不了。")
                    .defineInRange("prepMaxSeconds", 180, 1, 3600);

            this.prepResourceCostMultiplier = builder
                    .comment("准备期间心智的资源**消耗**倍率。0.0 ~ 1.0，默认 0.5（需求：降低 50%）。",
                            "作用于 Proto.eatBiomass 的入参，也就是每次召唤、每次铺菌毯要花的量。")
                    .defineInRange("resourceCostMultiplier", 0.5D, 0.0D, 1.0D);

            this.prepResourceGainMultiplier = builder
                    .comment("准备期间心智的资源**获取**倍率。1.0 ~ 10.0，默认 2.0（需求：提高 100%）。",
                            "作用于 Proto.addBiomass 的入参。")
                    .defineInRange("resourceGainMultiplier", 2.0D, 1.0D, 10.0D);

            this.manufactureSpeedMultiplier = builder
                    .comment("袭击期间心智的**制造**（召唤真菌生物）速度倍率。1.0 ~ 20.0，默认 6.0。",
                            "需求：袭击全过程中制造速度加快 500%，也就是 6 倍。",
                            "作用的是 Proto 召唤那个分支的节拍常量 200 tick（默认 200 ÷ 6 ≈ 33 tick 一次）。",
                            "**与 growthSpeedMultiplier 是两回事**：那一项管的是生成菌壳（铺感染区），",
                            "这一项管的是造兵；Spore 在同一个 tick 里用三个不同的节拍分别管这两件事。",
                            "填 1.0 = 与 Spore 原样。")
                    .defineInRange("manufactureSpeedMultiplier", 6.0D, 1.0D, 20.0D);

            this.prepGrowthSpeedMultiplier = builder
                    .comment("准备期间心智的**发育速度**倍率。1.0 ~ 5.0，默认 1.5（需求：提高 50%）。",
                            "实现方式是缩短「生成菌壳」那个分支的节拍间隔（默认 200 tick ÷ 1.5 ≈ 133 tick），",
                            "于是同样的时间里它会铺开更多感染区。",
                            "**刻意只作用于发育那一支**，不动被动资源收入与召唤节拍——",
                            "否则会和上面的「资源获取 ×2」叠成一个说不清的倍数。")
                    .defineInRange("growthSpeedMultiplier", 1.5D, 1.0D, 5.0D);

            this.launchMinBiomass = builder
                    .comment("发动条件之一：至少有一只心智的资源达到这个数。0 ~ 100000，默认 200。",
                            "注意准备期间资源获取是翻倍的，所以这个门槛通常几十秒就能攒到。")
                    .defineInRange("launchMinBiomass", 200, 0, 100000);

            this.launchMinFungusCount = builder
                    .comment("发动条件之二：目标玩家周围至少要有这么多真菌。0 ~ 1000，默认 8。")
                    .defineInRange("launchMinFungusCount", 8, 0, 1000);

            this.launchMinQuality = builder
                    .comment("发动条件之三：那些真菌的「质量」之和至少要到这个数。0.0 ~ 10000.0，默认 30.0。",
                            "质量用的就是 hatred 段那张等级表（基础感染体 1、进化体 2.5、超级体 6、器官类 4、灾厄 25），",
                            "所以 30 大约是「十几只基础感染体」或「五只进化体」或「一只灾厄加几只杂兵」。",
                            "三项条件是**与**关系，全满足才正式发动。")
                    .defineInRange("launchMinQuality", 30.0D, 0.0D, 10000.0D);

            this.gatherRadius = builder
                    .comment("统计「目标玩家附近有多少真菌」时的半径（格）。8.0 ~ 256.0，默认 64.0。",
                            "同时也是发动条件里「数量」与「质量」两项的取样范围。",
                            "统计每 20 tick 做一次，遍历半径内的实体——半径翻倍，盒子体积翻四倍。")
                    .defineInRange("gatherRadius", 64.0D, 8.0D, 256.0D);

            this.despawnCapMultiplier = builder
                    .comment("攻击期间 Despawning System 上限的倍率。1.0 ~ 10.0，默认 2.0（需求：提高 100%）。",
                            "作用于 Spore 自己那几个 max_*_cap 配置项算出来的上限。",
                            "**只在攻击阶段生效**：准备阶段就把上限抬高会让袭击还没开始就先把怪堆满。")
                    .defineInRange("despawnCapMultiplier", 2.0D, 1.0D, 10.0D);

            this.teleportMinDistance = builder
                    .comment("传送落点距目标玩家的**最近**距离（格）。1.0 ~ 128.0，默认 12.0。",
                            "需求里的「不会过于贴近」：太近会直接怼到脸上，没有反应时间。")
                    .defineInRange("teleportMinDistance", 12.0D, 1.0D, 128.0D);

            this.teleportMaxDistance = builder
                    .comment("传送落点距目标玩家的**最远**距离（格）。1.0 ~ 256.0，默认 32.0。",
                            "需求里的「不会过于远离」：太远等于没传送。",
                            "必须 >= teleportMinDistance，否则会被自动抬平。")
                    .defineInRange("teleportMaxDistance", 32.0D, 1.0D, 256.0D);

            this.ownWaveCountBase = builder
                    .comment("我方第 1 波送过去几只。1 ~ 200，默认 3。",
                            "需求改成了「每波无上限、数量与波次有关」，所以这里与下一项一起构成一条公式：",
                            "  第 N 波的数量 = 本项 + (N-1) × ownWaveCountPerWave",
                            "**没有人为的每波上限**。真正的上限来自世界：能调动的真菌只限于",
                            "teleportSearchRadius 内已有的那些，而它们的总数又受 Spore 的 Despawn 上限约束",
                            "（那个上限在攻击期间被我们翻倍了）。")
                    .defineInRange("ownWaveCountBase", 3, 1, 200);

            this.ownWaveCountPerWave = builder
                    .comment("每往后一波多送几只。0 ~ 100，默认 2。",
                            "默认下：第 1 波 3 只、第 5 波 11 只、第 10 波 21 只（上限 10 波）。")
                    .defineInRange("ownWaveCountPerWave", 2, 0, 100);

            this.teleportSearchRadius = builder
                    .comment("从多大范围内把真菌「调」过来（格）。16.0 ~ 512.0，默认 96.0。",
                            "它是以**心智**为中心搜的：心智把它的部下送过去，而不是凭空生成。",
                            "搜不到心智时以目标玩家为中心搜——这样即使心智离得远，袭击也不会哑火。")
                    .defineInRange("teleportSearchRadius", 128.0D, 16.0D, 512.0D);

            this.attackBuffs = builder
                    .comment("攻击期间加在参战真菌身上的增益，格式 \"效果id|持续tick|amplifier\"。",
                            "例：[\"minecraft:speed|200|1\", \"minecraft:strength|200|1\"]",
                            "每次进入新的一波都会整体刷新一遍，所以持续 tick 只要够撑过一波里最短的那段就行；",
                            "配得太短会在波与波之间断档，配得太长则会让增益在袭击结束后仍挂着一会儿。",
                            "写错的条目会被跳过并在日志里留一行警告。",
                            "默认给的是「速度 I + 力量 I + 抗性 I」这一组偏保守的生存向增益——",
                            "想要更凶的话，spore 自己的那些效果 id 也可以填进来。")
                    .defineList("attackBuffs", DEFAULT_RAID_BUFFS, o -> o instanceof String);

            this.launchCheckIntervalTicks = builder
                    .comment("准备阶段每隔多少 tick 检查一次发动条件。1 ~ 200，默认 20（1 秒）。",
                            "检查要扫玩家周围的真菌并按等级表算质量，所以它同时是这一段的主要开销来源。",
                            "调小会让「条件一满足就发动」更灵敏，代价是每秒多扫几次。")
                    .defineInRange("launchCheckIntervalTicks", 20, 1, 200);

            this.warnLeadSeconds = builder
                    .comment("「恐惧吧」比**竞技之须出现**提前多少秒发出。0 ~ 120，默认 10（需求写的就是 10 秒）。",
                            "注意基准是「竞技之须出现」那一刻，不是「准备阶段开始」。",
                            "准备阶段本身也有一句台词（message.spore_add.raid_declare），",
                            "所以玩家的体感是：先被告知有人盯上他，再过一会儿听到第二句，然后触须钻出来。")
                    .defineInRange("warnLeadSeconds", 10, 0, 120);

            this.arenaChanceBase = builder
                    .comment("竞技之须出现概率的**基准**。0.0 ~ 1.0，默认 0.2。",
                            "需求：恨意值越高，竞技之须出现概率越大。",
                            "所以实际概率 = 基准 + 恨意档位 × arenaChancePerTier，再被 arenaChanceMax 夹住。",
                            "「档位」就是 hatred.thresholdStep 切出来的那个序号。")
                    .defineInRange("arenaChanceBase", 0.2D, 0.0D, 1.0D);

            this.arenaChancePerTier = builder
                    .comment("恨意档位每高一级，竞技之须的出现概率加多少。0.0 ~ 1.0，默认 0.1。")
                    .defineInRange("arenaChancePerTier", 0.1D, 0.0D, 1.0D);

            this.arenaChanceMax = builder
                    .comment("竞技之须出现概率的上限。0.0 ~ 1.0，默认 0.9。",
                            "刻意默认不给到 1.0：留一成「这次没有触须」，让高恨意值的袭击仍有变化。",
                            "低于 arenaChanceBase 时会被自动抬到基准。")
                    .defineInRange("arenaChanceMax", 0.9D, 0.0D, 1.0D);

            this.arenaWaveSize = builder
                    .comment("种给竞技之须的初始**波次规模**。0 ~ 100，默认 6。",
                            "它的规模决定每次出怪几只（>3 时随机 1~4 只），等级决定从哪张表出怪。",
                            "**注意它会自己往上加**：竞技之须每 40 tick 按附近玩家的护甲/生命/背包重新算一遍，",
                            "所以这里给的是「起步值」，不是上限——这正是它「竞技」的地方。")
                    .defineInRange("arenaWaveSize", 6, 0, 100);

            this.arenaWaveLevel = builder
                    .comment("种给竞技之须的初始**波次等级**。0 ~ 2，默认 1。",
                            "0/1/2 分别对应 Spore 自己的 Raid level 1/2/3 出怪表（在它自己的配置里）。",
                            "等级 ≥ 2 时它还会开始丢 FleshBomb。")
                    .defineInRange("arenaWaveLevel", 1, 0, 2);

            this.arenaTimeoutSeconds = builder
                    .comment("竞技之须阶段最长持续多少秒。1 ~ 3600，默认 300。",
                            "它自己会在「场上真菌少于 4 只」时缩回消失——那是**通过**，之后接着打我方围剿波次。",
                            "这一项管的是另一种收场：到点还没清场，就判**玩家失败**，整场袭击到此结束，",
                            "并按「死于真菌之手」那条线结算——削他的恨意值、把损失换算成资源给所有心智",
                            "（用的就是 death 段那两个数，不另开一套）。",
                            "**它同时堵了一个漏洞**：不判失败的话，玩家只要跑远（竞技之须扫不到宿主、",
                            "波次不再增长），就能白等到超时再继续打我方波次，等于整段挑战被跳过。")
                    .defineInRange("arenaTimeoutSeconds", 300, 1, 3600);

            this.absenceTimeoutSeconds = builder
                    .comment("玩家躲进**黑名单维度**后，最多允许缺席多少秒才判失败。1 ~ 3600，默认 300。",
                            "躲进去不会立刻结束袭击：整场袭击（含阶段计时）会**冻结**，只累计缺席时间，",
                            "玩家回到允许的维度就归零继续打——所以出去一下再回来不算数。",
                            "逾期不归才判**玩家失败**，走「死于真菌之手」那条线（削恨意值 + 资源给心智）。",
                            "**冻结阶段计时是必须的**：不停的话，躲进下界的玩家几秒后就会被",
                            "arenaTimeoutSeconds 判失败，等于「躲一下就直接输」，与这里的 300 秒窗口自相矛盾。")
                    .defineInRange("absenceTimeoutSeconds", 300, 1, 3600);

            this.ownWaveBase = builder
                    .comment("我方围剿波次的**基础**数量。0 ~ 50，默认 1。",
                            "需求：最后几波是我们自己的袭击，波次数取决于恨意值大小。",
                            "实际波数 = 基础 + 恨意档位 × ownWavePerTier，被 ownWaveMax 夹住。")
                    .defineInRange("ownWaveBase", 1, 0, 50);

            this.ownWavePerTier = builder
                    .comment("恨意档位每高一级，我方波次多几波。0 ~ 10，默认 1。")
                    .defineInRange("ownWavePerTier", 2, 0, 10);

            this.ownWaveMax = builder
                    .comment("我方波次数量的上限。0 ~ 50，默认 10。",
                            "低于 ownWaveBase 时会被自动抬到基础。")
                    .defineInRange("ownWaveMax", 10, 0, 50);

            this.ownWaveSeconds = builder
                    .comment("我方每一波**最多**等多少秒。1 ~ 600，默认 60。",
                            "**这不是固定时长**：正常情况下这一波什么时候结束由「清完了没有」决定——",
                            "玩家周围 ownWaveClearRadius 内的真菌少于 ownWaveClearCount 只就提前进下一波。",
                            "本项只在清不掉时兜底：没有它，一波打不完的仗会让整场袭击永远停在那里。")
                    .defineInRange("ownWaveSeconds", 60, 1, 600);

            this.ownWaveClearRadius = builder
                    .comment("判断「这一波清完了没有」时统计真菌的半径（格）。4.0 ~ 128.0，默认 32.0。",
                            "**故意比 gatherRadius 小**：那一项管的是「附近有没有值得一战的菌群」，",
                            "而这一项管的是「我眼前这片打完了没有」。范围太大会把远处游荡的真菌也算进来，",
                            "于是玩家明明清完了却迟迟不进下一波。")
                    .defineInRange("ownWaveClearRadius", 32.0D, 4.0D, 128.0D);

            this.ownWaveClearCount = builder
                    .comment("玩家周围 ownWaveClearRadius 内的真菌少于这个数，就算这一波清完了。0 ~ 64，默认 4。",
                            "需求写的是「被清到 <4」，所以默认 4 表示剩 0~3 只时进入下一波。",
                            "填 0 表示必须一只不剩——那在小怪散开时很难达成，通常会让每波都走超时。")
                    .defineInRange("ownWaveClearCount", 4, 0, 64);

            this.ownWaveBuffsPerWave = builder
                    .comment("每往后一波，参战增益的 amplifier 加多少。0 ~ 10，默认 1。",
                            "需求：波次越高，真菌越强。第 3 波时 attackBuffs 里的每个效果都会 +2 级。",
                            "amplifier 有 127 的字节上限，所以配得很大时会自动封顶（见实现）。")
                    .defineInRange("ownWaveBuffsPerWave", 1, 0, 10);

            this.ownLoot = builder
                    .comment("我方战利品表，格式 \"物品id|最少|最多\"，可写多行。",
                            "**只在我方围剿波次打完时掷**；竞技之须那一段的奖励走 Spore 自己的 drops 表，",
                            "两者互不干扰（这也是需求里说「各自用自己的表」的意思）。",
                            "例：[\"spore:living_core|1|1\", \"minecraft:diamond|1|2\"]",
                            "写坏的条目会被跳过并在日志里留一行警告。")
                    .defineList("ownLoot", DEFAULT_OWN_LOOT, o -> o instanceof String);

            this.ownLootRollsPerWave = builder
                    .comment("战利品表里每一条独立掷多少次（再乘以波次档位）。0 ~ 100，默认 1。",
                            "需求：波次越高，完成后奖励越丰厚。",
                            "实际掷骰次数 = 波数 × 本项，所以默认下 5 波就是每一条掷 5 次。")
                    .defineInRange("ownLootRollsPerWave", 1, 0, 100);

            this.ownLootRadius = builder
                    .comment("我方战利品掉在离玩家多远的范围内（格）。1.0 ~ 64.0，默认 6.0。",
                            "掉太远玩家会找不到，掉在脚下又会和已有的东西堆在一起。")
                    .defineInRange("ownLootRadius", 6.0D, 1.0D, 64.0D);

            builder.pop();
        }
    }

    /** 资源：掉落物收集与 Despawn 清理。 */
    private static final class Loot {

        private final ForgeConfigSpec.BooleanValue collectEnabled;
        private final ForgeConfigSpec.DoubleValue radius;
        private final ForgeConfigSpec.IntValue searchIntervalTicks;
        private final ForgeConfigSpec.IntValue repathIntervalTicks;
        private final ForgeConfigSpec.DoubleValue pickupDistance;
        private final ForgeConfigSpec.DoubleValue moveSpeed;
        private final ForgeConfigSpec.IntValue priority;
        private final ForgeConfigSpec.IntValue convertCooldownTicks;
        private final ForgeConfigSpec.ConfigValue<List<? extends String>> values;
        private final ForgeConfigSpec.DoubleValue defaultValue;
        private final ForgeConfigSpec.BooleanValue despawnHarvestEnabled;
        private final ForgeConfigSpec.DoubleValue despawnBaseValue;

        private Loot(ForgeConfigSpec.Builder builder) {
            builder.comment("真菌的资源：捡掉落物、以及被 Despawning System 清理时的回收。",
                            "资源就是 Spore 心智（Proto）身上那份 biomass。",
                            "掉落物交给**最近的**心智；一只心智都没有（或不在同一维度）时进暂存区，",
                            "等有心智了再补发——暂存区与世界恨意值放在同一份存档数据里。")
                    .push("loot");

            this.collectEnabled = builder
                    .comment("是否让真菌主动收集附近的掉落物。默认开。",
                            "只有会走动的那一类真菌（Infected 及其进化体）会捡；器官类基本不移动，捡不了。")
                    .define("collectEnabled", true);

            this.radius = builder
                    .comment("真菌搜寻掉落物的半径（格）。1.0 ~ 64.0，默认 16.0。",
                            "**这个值是性能旋钮**：搜寻每 {@code searchIntervalTicks} tick 做一次，",
                            "每次要遍历半径内所有掉落物。半径翻倍 = 体积（掉落物的搜索盒）翻四倍。")
                    .defineInRange("radius", 16.0D, 1.0D, 64.0D);

            this.searchIntervalTicks = builder
                    .comment("两次搜寻之间至少间隔多少 tick。5 ~ 200，默认 40（2 秒）。",
                            "每个真菌各算各的，不是全局同步。")
                    .defineInRange("searchIntervalTicks", 40, 5, 200);

            this.repathIntervalTicks = builder
                    .comment("已锁定目标、但目标没动过时，隔多少 tick 重新寻一次路。1 ~ 200，默认 20（1 秒）。",
                            "掉落物会被水流与爆炸推走，而导航路线一旦算歪不会自愈，所以要定期重算。",
                            "比 searchIntervalTicks 短得多是有意的：搜寻要遍历半径内所有掉落物（贵），",
                            "重算只是照着已选好的目标再寻一次路（便宜）。")
                    .defineInRange("repathIntervalTicks", 20, 1, 200);

            this.pickupDistance = builder
                    .comment("靠到多近才算捡起来（格）。1.0 ~ 8.0，默认 2.0。",
                            "取的是到掉落物的中心距离，不是判定箱接触——真菌不需要真的踩上去。")
                    .defineInRange("pickupDistance", 2.0D, 1.0D, 8.0D);

            this.moveSpeed = builder
                    .comment("走过去捡东西时的移动速度倍率。0.1 ~ 5.0，默认 1.0。",
                            "真菌的基础移速由它自己的属性决定，本项是走这条路时的倍率。",
                            "高于 1 会略显急切（像闻到味了），低到 0.5 以下会看起来像在闲逛。")
                    .defineInRange("moveSpeed", 1.0D, 0.1D, 5.0D);

            this.priority = builder
                    .comment("普通真菌那条拾荒目标的优先级。1 ~ 20，默认 8，数字越小越优先。",
                            "1 会盖过逃跑与战斗（它会为了捡东西而不打不跑），所以别调太小。",
                            "Spore 给普通感染体的常规目标最高到 10（其中吃尸体是 7），",
                            "8 = 比吃尸体让路、比跟着队友更有优先。",
                            "**拾荒者**那条用的是 scavenger 段里自己的 lootPriority，不是本项。")
                    .defineInRange("priority", 8, 1, 20);

            this.convertCooldownTicks = builder
                    .comment("两次转化之间至少间隔多少 tick。0 ~ 200，默认 10。",
                            "**这是限流闸门**：一场大战后的掉落物可能有几百件，",
                            "没有它的话一只真菌会在一瞬间把它们全部吞掉，资源数字直接跳一大截。",
                            "填 0 = 不限流。")
                    .defineInRange("convertCooldownTicks", 10, 0, 200);

            this.values = builder
                    .comment("掉落物的转化表，格式 \"物品id|资源值\" 或 \"#物品标签|资源值\"，可写多行。",
                            "例：[\"minecraft:iron_ingot|6\", \"#minecraft:logs|0.5\", \"#spore:biomass|8\"]",
                            "**带 # 的按物品标签匹配**，所以整合包可以一次给一整类物品定价。",
                            "同一件物品同时被标签条目和 id 条目命中时，**先出现的那条赢**——",
                            "把具体的 id 写在前面就能覆盖标签。",
                            "写坏的条目会被跳过并在日志里留一行警告，不会崩。")
                    .defineList("values", DEFAULT_LOOT_VALUES, o -> o instanceof String);

            this.defaultValue = builder
                    .comment("转化表里没列到的物品值多少资源。0.0 ~ 1000.0，**默认 0.0 = 不捡**。",
                            "默认给 0 是刻意的：让真菌只捡你定价过的东西，而不是把整片战场扫空。",
                            "想让它「什么都捡」就把这一项调大。")
                    .defineInRange("defaultValue", 0.0D, 0.0D, 1000.0D);

            this.despawnHarvestEnabled = builder
                    .comment("是否把 Despawning System 清理掉的真菌折算成资源。默认开。",
                            "只会统计**被那套系统清理的**真菌（Spore 每隔 1200 tick 清一次超编的部分），",
                            "玩家杀死的、自然消失的都不算——后者已经通过击杀恨意值体现过了。")
                    .define("despawnHarvestEnabled", true);

            this.despawnBaseValue = builder
                    .comment("被 Despawn 清理掉的一只真菌折算多少资源。0.0 ~ 10000.0，默认 5.0。",
                            "实际值 = 本项 × 该生物的**等级倍率**（就是 hatred 段里那一组 tier* 键），",
                            "于是灾厄级被清掉时给得远多于基础感染体，不必再单独配一套。",
                            "**注意这里没乘「类型 / 重要性 / 链接 / 发育」**：那些是「玩家杀了它有多可恨」的维度，",
                            "对「系统清理回收了多少生物质」没有意义。")
                    .defineInRange("despawnBaseValue", 5.0D, 0.0D, 10000.0D);

            builder.pop();
        }
    }

    /** 心智的通用机制：把造出来的生物收进存储、需要时投放。 */
    private static final class Hivemind {

        private final ForgeConfigSpec.BooleanValue storeEnabled;
        private final ForgeConfigSpec.IntValue storeMaxCount;
        private final ForgeConfigSpec.IntValue deployPerWave;
        private final ForgeConfigSpec.BooleanValue deployWhenThreatened;
        private final ForgeConfigSpec.IntValue threatDeployCount;
        private final ForgeConfigSpec.IntValue threatDeployCooldownSeconds;
        private final ForgeConfigSpec.IntValue spillCount;
        private final ForgeConfigSpec.DoubleValue spillChance;
        private final ForgeConfigSpec.IntValue spillCooldownSeconds;
        private final ForgeConfigSpec.DoubleValue domeRadius;
        private final ForgeConfigSpec.IntValue threatWindowSeconds;
        private final ForgeConfigSpec.IntValue storeCost;
        private final ForgeConfigSpec.DoubleValue deployScatterRadius;

        private Hivemind(ForgeConfigSpec.Builder builder) {
            builder.comment("心智的**存储**：把造出来的真菌收起来，需要时再投放。",
                            "存储里的生物**不以实体形式存在**（收起来时那个实体被丢弃），所以 Spore 的",
                            "消失管理系统根本看不到它们——这就是「避免被处理」的实现方式。",
                            "存的是生物的完整 NBT，随心智一起存盘；心智死了存储跟着消失。",
                            "投放是**复制**：原条目留在存储里，但同一次袭击里已经投放过的条目不会再被选中。")
                    .push("hivemind");

            this.storeEnabled = builder
                    .comment("是否让心智把造出来的生物收进存储（而不是放进世界）。默认开。",
                            "关掉就完全是 Spore 原样：造出来直接放进世界。")
                    .define("storeEnabled", true);

            this.storeMaxCount = builder
                    .comment("一只心智最多存多少生物。0 ~ 1000，默认 64。",
                            "**存满之后照旧放进世界**，而不是停止制造——那样会让心智的资源白花。",
                            "所以这个上限是「存储的容量」，不是「制造的闸门」。",
                            "注意存储只是「不在世界上」，并不省资源：收进存储一样要扣 Spore 那 2 点生物质。")
                    .defineInRange("storeMaxCount", 64, 0, 1000);

            this.deployPerWave = builder
                    .comment("我方围剿每一波开始时，从存储里投放几只。0 ~ 100，默认 4。",
                            "与我方原本的「传送一批」是两条独立的供给：传送调的是**世界里已有的**真菌，",
                            "投放放的是**存储里的**存量。两条一起用，波次才有厚度。")
                    .defineInRange("deployPerWave", 4, 0, 100);

            this.deployWhenThreatened = builder
                    .comment("心智受威胁时是否应急投放。默认开。",
                            "「受威胁」用的是 Spore 自己的状态：心智有攻击目标，或最近挨过打。")
                    .define("deployWhenThreatened", true);

            this.threatDeployCount = builder
                    .comment("应急投放一次放几只。0 ~ 100，默认 6。")
                    .defineInRange("threatDeployCount", 6, 0, 100);

            this.threatDeployCooldownSeconds = builder
                    .comment("应急投放的冷却秒数。1 ~ 600，默认 10。",
                            "被围殴时不该每 tick 都放——那会在一瞬间把存货掏空。")
                    .defineInRange("threatDeployCooldownSeconds", 10, 1, 600);

            this.spillCount = builder
                    .comment("存储被打散时最多漏出几只。0 ~ 200，默认 8。",
                            "需求：穹顶被破坏时，存储的生物有概率自行出现。",
                            "优先漏**本次袭击已经投放过的**那些——它们本来就在这场仗里露过面，",
                            "再漏一次不算额外收益；不够数时才轮到没用过的存货。")
                    .defineInRange("spillCount", 8, 0, 200);

            this.spillChance = builder
                    .comment("穹顶每被破坏一块方块，判一次「漏出」的概率。0.0 ~ 1.0，默认 0.25。",
                            "穹顶是 Spore 用 10% 概率**逐块**长出来的稀疏球壳（半径 32 一层、半径 16 一层），",
                            "玩家砸开它也是一块一块砸的，所以按「块」判定才跟手感一致。",
                            "与下面的冷却合起来看：冷却才是「一次事件只漏一次」的保证，",
                            "本项只决定「这次砸壳要不要漏」。填 0 = 不再漏出。")
                    .defineInRange("spillChance", 0.25D, 0.0D, 1.0D);

            this.spillCooldownSeconds = builder
                    .comment("两次「漏出」之间的最短间隔（秒）。1 ~ 600，默认 10。",
                            "没有它的话，一次爆炸掀掉几十块穹顶就是几十次独立掷骰，",
                            "按 0.25 的概率期望能连漏十几次——那不叫「有概率自行出现」，叫决堤。",
                            "冷却记在**心智自己**的持久数据里，所以多只心智各自独立计算。")
                    .defineInRange("spillCooldownSeconds", 10, 1, 600);

            this.domeRadius = builder
                    .comment("判定一块躯壳方块属于哪只心智：心智周围多大半径内算它的穹顶（格）。默认 32.0。",
                            "Spore 的穹顶是两层同心球壳，外层的半径**硬编码为 32**（见 CasingGenerator），",
                            "所以默认值跟它对齐；调小会让离得远的那层壳被砸时不算数。",
                            "躯壳方块取的是 Spore 生成穹顶时用的那份方块表：",
                            "biomass_block / rooted_biomass / calcified_biomass_block / sicken_biomass_block / gastric_biomass。")
                    .defineInRange("domeRadius", 32.0D, 4.0D, 128.0D);

            this.threatWindowSeconds = builder
                    .comment("「心智正受威胁」的判定窗口（秒）。1 ~ 60，默认 5。",
                            "威胁的判据沿用 Spore 自己的两个状态：它有攻击目标、或最近这么久内挨过打。",
                            "刻意不去自己扫附近敌人——那既省一次实体查询，也保证判定与 Spore 的 AI 一致。",
                            "调大 = 挨一下打之后更长时间里都算「被威胁」，应急投放会更积极。")
                    .defineInRange("threatWindowSeconds", 5, 1, 60);

            this.storeCost = builder
                    .comment("把一只生物收进存储要花多少资源。0 ~ 100，默认 2。",
                            "Spore 自己 summonMob 造一只是 2 点，默认值跟它对齐——这样「收起来」和「放出去」",
                            "是同一个价，存储不会变成免费造兵。",
                            "袭击期间这一项还会再吃 raid 的资源消耗折扣（走的是同一个 eatBiomass 入口）。",
                            "填 0 = 白收，那会让存储变成纯粹的净赚，慎用。")
                    .defineInRange("storeCost", 2, 0, 100);

            this.deployScatterRadius = builder
                    .comment("投放时，落点被占住的话最多在中心周围多少格内另找空位。0.0 ~ 16.0，默认 4.0。",
                            "投放的中心是「心智自己的坐标」，而心智**可能正卡在自己的穹顶壳里**",
                            "（壳只在它移动超过 10 格时才重新居中）——照着坐标硬塞会把它埋进生物质，",
                            "表现是生物卡在半空不动、而且因为隔着方块打不到。",
                            "所以落点先做空间检查，被占住就绕中心一圈圈往外找；",
                            "找到上限还没空位，这一次就**不投**（原条目留在存储里，下次还有机会），",
                            "而不是硬塞一个卡住的实体进去。")
                    .defineInRange("deployScatterRadius", 4.0D, 0.0D, 16.0D);

            builder.pop();
        }
    }
}
