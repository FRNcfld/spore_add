package com.frnc.spore_add;

import java.util.List;

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
 * <p>还有第三份 {@code spore_add-debug-common.toml}（{@link SporeAddDebugConfig}），
 * 它<b>不参与</b>上面这条分类——那是默认全关的调试检查点，横跨玩家侧与真菌侧。
 * 本文件里任何一个数值都不该搬到那里去，反之亦然。
 *
 * <h2>段落顺序</h2>
 * 按「真菌整体多强 → 某一种特定真菌个体的机制 → 恨意值怎么涨、怎么降 →
 * 恨意值反过来怎么帮真菌 → 袭击 → 资源」排：
 * {@code fungus} → {@code womb} → {@code scavenger} → {@code hatred} → {@code food} →
 * {@code death} → {@code fungusResistance} → {@code raid} → {@code hivemind} → {@code loot}。
 *
 * <p>其中 {@code raid} 有 38 个键，平铺在一张表里得逐行扫，所以按<b>袭击自己的生命周期</b>
 * 拆成七张子表（{@code raid.trigger} / {@code prep} / {@code bonus} / {@code attack} /
 * {@code arena} / {@code ownWave} / {@code ownLoot}），查找从「扫 38 行」变成
 * 「先定位阶段、再定位键」。
 *
 * <p><b>三处顺序必须一致</b>，加段或改段名时三处一起改，否则玩家看到的索引就是错的：
 * 静态块里 {@code new} 的顺序（决定 toml 里的排布）、各内部 class 的声明顺序、
 * 以及下面访问器的排列顺序。静态块里那段注释是权威说明。
 *
 * <h2>为什么文件名是手写的</h2>
 * Forge 默认按 {@code modId-类型} 拼文件名（COMMON → {@code spore_add-common.toml}），
 * 多个 COMMON 会撞成同一个名字，而 {@code ConfigTracker} 撞名会直接抛
 * {@code "Config conflict detected!"} 把游戏崩掉。所以三份都显式给文件名。
 *
 * <p>选 COMMON 而不是 SERVER：SERVER 类型的配置文件会按存档分别生成、放在
 * {@code <存档>/serverconfig/} 下。这些数值是"整合包作者设一次、所有存档通用"的性质，
 * 不该跟着存档走——与 {@link SporeAddPlayerConfig} 同一个理由。
 *
 * <h2>哪些数值该进数据包、哪些留在这里</h2>
 * 项目里的数值分两类，界线是<b>「谁往里面加东西」</b>：
 * <ul>
 *   <li><b>表 / 名单</b>——由整合包作者往里加<em>条目</em>的集合。它们走<b>数据包</b>：
 *       能随整合包分发、能被别的数据包覆盖、能 {@code /reload} 热更。
 *       目前有三处：{@code LootValues}（掉落物定价）、{@code LootBlacklist}（掉落物黑名单）、
 *       {@code RaidDimensionBlacklist}（袭击维度黑名单）——都在
 *       {@code data/<命名空间>/<目录>/<任意名字>.json}，一个文件一条。</li>
 *   <li><b>标量旋钮</b>——一个数字或一个开关（倍率、上限、开关）。它们<b>留在这里</b>，
 *       因为配置能给每一格配上 {@code .comment()} 的说明和 {@code defineInRange} 的范围校验；
 *       而 JSON 两样都没有：注释没地方写，写错的值也不会被夹回来（一个 -5 会静默毁掉玩法）。</li>
 * </ul>
 *
 * <p>所以「把数值都搬进数据包」这件事<b>不该做</b>——不是做不到，是把注释和校验换掉了，
 * 而那份注释正是这些数值为什么长这样的唯一记录。要按存档区分或随存档分发，正确的杠杆是
 * 把类型换成 {@code ModConfig.Type.SERVER}（见上面「为什么文件名是手写的」一节），而不是搬 JSON。
 *
 * <p><b>玩家侧那一份永远不能搬</b>：{@link SporeAddPlayerConfig} 会被客户端读取
 * （冰霜新星的 tooltip、蓄力动画），而数据包只在服务端存在、也不同步给客户端。
 * 理由写在那一份的类注释里，这里不重复。
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
     * 攻击阶段默认给参战真菌的增益。
     *
     * <p>格式是 {@code 效果id|持续tick|amplifier}，与 Spore 自己配置里那几张 debuff 表同构
     * （它的 {@code levi_debuffs} 写的就是 {@code "minecraft:mining_fatigue|600|0"}）。
     *
     * <p>默认这一组偏保守：速度 I 让它们追得上人，力量 I 让攻击有分量，抗性 I 让它们不至于
     * 一照面就被清掉。刻意<b>没有</b>默认给生命提升或回复——那些会让袭击变成消耗战，
     * 而"袭击该有个头"这件事由波数（{@code raid.ownWave.max}）与竞技之须的超时（{@code raid.arena.timeoutSeconds}）
     * 两处兜底。
     *
     * <p>我方围剿阶段还会在这个等级之上<b>逐波加级</b>（见 {@code raid.ownWave.buffsPerWave}）。
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

    /**
     * 拾荒者死后把存量换成什么。
     *
     * <p>只放一项：这些是"它捡来的战利品折算成的生物质"，而 Spore 的生物质本身就是阵营的通用通货，
     * 语义最贴。整合包想让它掉钻石之类的，往这张表里加 id 即可——掉落实数在表里**随机取**，
     * 所以加项之后不会全掉同一种。
     */
    private static final List<? extends String> DEFAULT_SCAVENGER_DEATH_DROPS = List.of("spore:biomass");

    private static final Fungus FUNGUS;
    private static final Womb WOMB;
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
        // 按「真菌整体多强 → 某一种特定真菌个体的机制 → 恨意值怎么涨、怎么降 →
        // 恨意值反过来怎么帮真菌 → 袭击 → 资源」排。
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // 写进 toml 文件开头的总说明。类注释里那张表玩家看不到（那只在源码里），
        // 所以这里必须再写一遍——玩家打开文件的第一眼就该知道"这份管什么、另一份在哪"。
        // **加段或改段名时这里必须同步**：它是玩家唯一的索引，写漏了等于那一段不存在。
        builder.comment(
                "Spore Add —— 真菌侧配置。",
                "",
                "这一份管「让真菌更强」的东西。",
                "让玩家更强的那些在另一份文件里：spore_add-player-common.toml",
                "（冰霜武器、恨意值给玩家的增益、可燃/爆燃）",
                "还有一份调试用的 spore_add-debug-common.toml（默认全关的检查点），不属于上面那条分类。",
                "",
                "本文件的段落（按下面的顺序排列）：",
                "  fungus            真菌加强：进化更快、攻击一切生物、冰冻伤害倍率、感知范围、抗寒",
                "  womb              灾厄重构体：同化突变加速、孵化后放到哪里",
                "  scavenger         拾荒者：菌染人类的变种，专职捡掉落物、把收获供给同伙",
                "  hatred            恨意值怎么涨：击杀真菌能拿多少、各项权重",
                "  food              吃真菌类食物时的恨意值变化",
                "  death             死亡 / 击杀心智 / 打赢袭击时的恨意值削减",
                "  fungusResistance  世界恨意值给真菌的减伤（只对非玩家伤害生效）",
                "  raid              真菌袭击。它按生命周期拆成了七张子表：",
                "      raid.trigger    什么时候会打起来（跨档与触发概率）",
                "      raid.prep       准备阶段：拖多久、以及「够不够格发动」",
                "      raid.bonus      准备 / 攻击期间心智变强多少（袭击强度的总旋钮）",
                "      raid.attack     兵怎么送到玩家面前（传送与增益）",
                "      raid.arena      竞技之须",
                "      raid.ownWave    我方围剿波次",
                "      raid.ownLoot    我方围剿的战利品",
                "  hivemind          心智的通用机制：把造出来的生物收进存储、需要时投放",
                "  loot              真菌捡掉落物、以及被系统清理时回收成的资源",
                "",
                "改动在重启游戏、或执行 /reload 之后生效。",
                "另外：掉落物定价、掉落物黑名单、袭击维度黑名单**不在这里**，它们在数据包里，",
                "见随 jar 分发的 docs/values-and-formulas.md。");

        FUNGUS = new Fungus(builder);
        WOMB = new Womb(builder);
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
    //
    // **访问器的排列顺序 = 段落顺序**，与静态块里 new 的顺序、以及文件下半部分各个 class 的
    // 声明顺序三者一致。找某一项时按段落顺序翻即可，不必全局搜索。

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

    /**
     * 「猎杀一切生物」那条目标挂在目标选择器上的优先级。
     *
     * <p><b>必须大于 Spore 玩家 / 白名单目标的 1</b>——这条目标不做任何玩家判定，
     * 排在玩家目标前面的话，玩家站在旁边时它会先去咬别人。这也正是「判定不管玩家」
     * 不需要额外代码的原因。见 {@code HuntPreyGoal} 的类注释。
     */
    public static int huntPriority() {
        return FUNGUS.huntPriority.get();
    }

    /** 「打得过才打」的裕度，见 {@code FungusCombat#canWin}。 */
    public static double huntCautionRatio() {
        return FUNGUS.huntCautionRatio.get();
    }

    /**
     * 「打得过才打」判定里，每点护甲折算成多少有效生命倍率。
     *
     * <p>见 {@code FungusCombat#canWin}——那是"开打前的粗略掂量"，不是战斗模拟器。
     */
    public static double huntArmorEhpPerPoint() {
        return FUNGUS.huntArmorEhpPerPoint.get();
    }

    /** 猎杀目标多久搜一次（tick）。 */
    public static int huntSearchIntervalTicks() {
        return Math.max(1, FUNGUS.huntSearchIntervalTicks.get());
    }

    /** 真菌 {@code FOLLOW_RANGE} 的倍率（相对各实体自己的基础值）。 */
    public static double sensingMultiplier() {
        return FUNGUS.sensingMultiplier.get();
    }

    /** 真菌受到的冰冻伤害倍率。 */
    public static double freezeDamageMultiplier() {
        return FUNGUS.freezeDamageMultiplier.get();
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

    // ------------------------------------------------------------------
    // 灾厄重构体（Womb）
    // ------------------------------------------------------------------

    /**
     * 灾厄重构体同化喂食时，一次喂食算几条突变。Spore 原样是 1 条。
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
        return WOMB.mutationMultiplier.get();
    }

    /** 上面那条在真菌袭击期间的<b>额外</b>倍率（需求：在减半的基础上再减半）。 */
    public static double wombRaidMutationMultiplier() {
        return WOMB.raidMutationMultiplier.get();
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
        return WOMB.raidBonusesEnabled.get();
    }

    /**
     * 孵化前至少要喂到几种**不同**的突变属性。0 = 关掉门槛。
     *
     * <p>它管的是「孵化太快、属性太低」：生物质靠吃就能在几十秒内攒满，
     * 而属性只能靠喂特定进化体，所以不设门槛的话孵出来的多半是白板灾厄。
     */
    public static int wombHatchMinMutationTypes() {
        return Math.max(0, WOMB.hatchMinMutationTypes.get());
    }

    /** 基础条件满足后最多允许滞留多少秒再无条件孵化。0 = 不设上限。 */
    public static int wombHatchMaxStallSeconds() {
        return Math.max(0, WOMB.hatchMaxStallSeconds.get());
    }

    /** 心智是否出资替卡住的重构体补齐突变。 */
    public static boolean wombMindFundingEnabled() {
        return WOMB.mindFundingEnabled.get();
    }

    /** 补一种属性要花多少生物质。 */
    public static int wombMindFundingCost() {
        return Math.max(0, WOMB.mindFundingCost.get());
    }

    /** 多久尝试资助一次（tick）。至少 20，否则取模会退化成每 tick 都试。 */
    public static int wombMindFundingIntervalTicks() {
        return Math.max(20, WOMB.mindFundingIntervalSeconds.get() * 20);
    }

    /** 心智从多大范围内找需要资助的重构体（格）。 */
    public static double wombMindFundingRange() {
        return WOMB.mindFundingRange.get();
    }

    /**
     * 灾厄孵化后是否把它挪到重构体外面去（否则它会被塞在重构体自己那堆实心方块里窒息）。
     *
     * <p>见 {@code WombHatch} 的类注释：Spore 把孵化出来的灾厄放在<b>重构体自己身上</b>，
     * 而重构体是埋在生物质里的土丘、体型还会长到 3 档，于是大 hitbox 的灾厄一出来就卡在方块里。
     */
    public static boolean wombHatchExitEnabled() {
        return WOMB.hatchExitEnabled.get();
    }

    /** 找"壳外空位"时最多向外找多远（格）。找不到就维持原位。 */
    public static double wombHatchExitSearchRadius() {
        return WOMB.hatchExitSearchRadius.get();
    }

    /**
     * 孵化出的灾厄要不要被送出<b>心智的生物质穹顶</b>，而不仅仅是送出重构体本身。
     *
     * <p>默认开。「壳」在这条需求里指的是心智那两层生物质球壳（半径 32 与 16，见
     * {@code DomeBreach} 的类注释），不是重构体自己那圈方块——只是把灾厄挪出重构体的包围盒
     * 并不足以让它离开穹顶，它照样会在穹顶内部把壳挖烂。
     */
    public static boolean wombHatchExitFromDome() {
        return WOMB.hatchExitFromDome.get();
    }

    /** 送出穹顶时，在穹顶半径之外再留出多少格。 */
    public static double wombHatchExitDomeMargin() {
        return WOMB.hatchExitDomeMargin.get();
    }

    // ------------------------------------------------------------------
    // 拾荒者
    // ------------------------------------------------------------------
    //
    // 段内顺序：转变 → 存活成长 → 逃跑 → 交付/治疗 → 数量 → 目标优先级。
    // 与配置里那一段的键顺序一致（那一段刻意不拆子表：键名自带 transform*/flee*/heal* 语义，
    // 光看名字就能分组，拆表反而多一层要翻）。

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

    /**
     * 拾荒者随存活时间成长的四条曲线。四条都是「每分钟涨多少、涨到上限为止」，
     * 与 {@link #scavengerLootBonusPerMinute()} 用的是同一套算法（线性 + 封顶）。
     *
     * <p>时间基准是 {@code Scavenger#survivalMinutes()}——那只拾荒者自己维护<b>并存盘</b>的存活时长，
     * 所以区块卸载或重启服务器都不会把成长清零。
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

    /** 存活时间换算成拾荒加成的速率：每存活一分钟加多少倍率。 */
    public static double scavengerLootBonusPerMinute() {
        return SCAVENGER.lootBonusPerMinute.get();
    }

    /** 存活时间加成的上限（不含基础的 1.0）。 */
    public static double scavengerLootBonusMax() {
        return SCAVENGER.lootBonusMax.get();
    }

    /** 「拾荒者移动」那条字幕的提示音最短间隔（tick）。 */
    public static int scavengerMoveCueCooldownTicks() {
        return Math.max(1, SCAVENGER.moveCueCooldownSeconds.get() * 20);
    }

    // ---- 存储：捡到但送不出去的资源揣在自己身上，上限随存活成长 ----

    /**
     * 拾荒者的资源存储上限。
     *
     * <p><b>三个读者共用这一处</b>：封顶（{@code CollectLootGoal}）、判断"该去找队友了"
     * （{@code ScavengerSeekAllyGoal}）、以及死后按存量掉落（{@code Scavenger}）。
     * 各写一遍的话，"什么时候算满"会随着改配置而分叉。
     *
     * @param survivalMinutes 那只拾荒者的存活分钟数（{@code Scavenger#survivalMinutes()}）
     */
    public static double scavengerStorageCap(double survivalMinutes) {
        double growth = Math.max(0.0D, survivalMinutes) * SCAVENGER.storagePerMinute.get();
        return SCAVENGER.storageBase.get() + Math.min(SCAVENGER.storageMaxBonus.get(), growth);
    }

    // ---- 拾荒：捡东西时看多远、闲逛时朝哪走 ----

    /** 拾荒者搜寻掉落物的半径（格）。**拾荒者专用**，与普通真菌的 {@link #lootRadius()} 无关。 */
    public static double scavengerLootRadius() {
        return SCAVENGER.lootRadius.get();
    }

    /**
     * 闲逛时朝战利品偏的感知半径（格）。
     *
     * <p>与 {@link #scavengerLootRadius()} 相等就等于关掉「朝战利品游荡」这个行为——
     * 那样感知到的东西本来就在捡拾范围内，拾荒目标会直接去拿。
     */
    public static double scavengerRoamAwarenessRadius() {
        return SCAVENGER.roamAwarenessRadius.get();
    }

    /** 离目标掉落物多近时开始奔袭（格）。0 = 关掉奔袭。 */
    public static double scavengerLootSprintRadius() {
        return SCAVENGER.lootSprintRadius.get();
    }

    /** 奔袭时的移速倍率（乘在已含存活成长的移速之上）。 */
    public static double scavengerLootSprintSpeed() {
        return SCAVENGER.lootSprintSpeed.get();
    }

    // ---- 链接侦察 ----
    //
    // 链接关系不在这里存——它活在拾荒者自己的持久数据里（见 ScavengerLinks）。
    // 这一组只是那条关系的参数。

    /** 多久重建一次链接（校验旧的 + 补新的）。 */
    public static int scavengerLinkScanIntervalTicks() {
        return Math.max(1, SCAVENGER.linkScanIntervalTicks.get());
    }

    /** 直连时从多大范围内挑同伴（格）。 */
    public static double scavengerLinkScanRadius() {
        return SCAVENGER.linkScanRadius.get();
    }

    /** 链接对象离我超过多远就算走散。**不低于扫描半径**，否则刚链上就断。 */
    public static double scavengerLinkBreakRadius() {
        return Math.max(SCAVENGER.linkScanRadius.get(), SCAVENGER.linkBreakRadius.get());
    }

    /** 每个链接对象提供的搜索圆心半径（格）。 */
    public static double scavengerLinkProbeRadius() {
        return SCAVENGER.linkProbeRadius.get();
    }

    /** 爬到上限所需的存活分钟数。至少 0.01，否则除法会炸。 */
    public static double scavengerLinkRampMinutes() {
        return Math.max(0.01D, SCAVENGER.linkRampMinutes.get());
    }

    /**
     * 这只拾荒者能链几个同伴。**上限随存活时间从下限爬到上限**（线性 + 封顶）。
     *
     * <p>两个读者（建立链接、检查名额）共用这一处，免得"算不算满"两边分叉。
     *
     * @param viaHivemind 经心智间接链接吗（它的上限更高，2~6 对 1~3）
     */
    public static int scavengerLinkCap(double survivalMinutes, boolean viaHivemind) {
        int min = viaHivemind ? SCAVENGER.linkHivemindMinLinks.get() : SCAVENGER.linkMinLinks.get();
        int configuredMax = viaHivemind ? SCAVENGER.linkHivemindMaxLinks.get() : SCAVENGER.linkMaxLinks.get();
        int max = Math.max(min, configuredMax);
        if (max <= min) {
            return min;
        }
        double progress = Math.min(1.0D, survivalMinutes / scavengerLinkRampMinutes());
        return (int) Math.round(min + (max - min) * progress);
    }

    // ---- 掉落物综合评分 ----

    /** 【权重】距离。 */
    public static double lootWeightDistance() {
        return SCAVENGER.lootWeightDistance.get();
    }

    /** 【权重】到达时间。 */
    public static double lootWeightArrival() {
        return SCAVENGER.lootWeightArrival.get();
    }

    /** 【权重】掉落物数量。 */
    public static double lootWeightCount() {
        return SCAVENGER.lootWeightCount.get();
    }

    /** 【权重】掉落物类型（稀有度）。 */
    public static double lootWeightType() {
        return SCAVENGER.lootWeightType.get();
    }

    /** 【权重】转换资源效率（转化概率）。 */
    public static double lootWeightEfficiency() {
        return SCAVENGER.lootWeightEfficiency.get();
    }

    /**
     * 【权重】路径上的危险。**当前实现恒为 0**（见配置注释：算它要么不准、要么太贵）。
     *
     * <p>键留着是为了以后想开就开；这里照样透传，由 {@code LootScoring} 决定它乘的是什么。
     */
    public static double lootWeightDanger() {
        return SCAVENGER.lootWeightDanger.get();
    }

    /** 聚堆的三维格边长（格）。至少 1，否则除零。 */
    public static double lootClusterRadius() {
        return Math.max(1.0D, SCAVENGER.lootClusterRadius.get());
    }

    /** 价值与稀有度的半饱和点。至少 0.1，否则除零。 */
    public static double lootValueHalf() {
        return Math.max(0.1D, SCAVENGER.lootValueHalf.get());
    }

    /** 数量的半饱和点。至少 0.1，否则除零。 */
    public static double lootCountHalf() {
        return Math.max(0.1D, SCAVENGER.lootCountHalf.get());
    }

    /** 到达时间里，每向上 1 格按多少格水平距离计。 */
    public static double lootMaxVerticalClimbFactor() {
        return SCAVENGER.lootMaxVerticalClimbFactor.get();
    }

    /** 血量低于最大生命的这个比例时，无条件逃跑。 */
    public static double scavengerFleeHealthFraction() {
        return SCAVENGER.fleeHealthFraction.get();
    }

    /** 感知到多远的威胁就跑（格）。 */
    public static double scavengerFleeThreatRadius() {
        return SCAVENGER.fleeThreatRadius.get();
    }

    /** 「锁定它的生物能在这么多秒内打死它」就跑。调大 = 更胆小。 */
    public static double scavengerFleeSurviveSeconds() {
        return SCAVENGER.fleeSurviveSeconds.get();
    }

    /** 被谁打过之后，多少 tick 内仍把它算作「对我有仇恨」。 */
    public static int scavengerFleeAggroMemoryTicks() {
        return SCAVENGER.fleeAggroMemoryTicks.get();
    }

    /** 逃跑时的移动速度倍率。 */
    public static double scavengerFleeSpeed() {
        return SCAVENGER.fleeSpeed.get();
    }

    /** 选逃跑落点时向外搜的水平距离（格）。 */
    public static int scavengerFleeTargetHorizontalDistance() {
        return Math.max(1, SCAVENGER.fleeTargetHorizontalDistance.get());
    }

    /** 选逃跑落点时允许的高差（格）。调大会让它往矿洞或树上跑。 */
    public static int scavengerFleeTargetVerticalRange() {
        return Math.max(1, SCAVENGER.fleeTargetVerticalRange.get());
    }

    /** 逃跑目标的优先级（数字越小越优先）。 */
    public static int scavengerFleePriority() {
        return SCAVENGER.fleePriority.get();
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

    // ---- 存满了就去找队友 ----

    /** 存量达到上限的多少比例就算「即将蓄满」、该去找队友了。 */
    public static double scavengerSeekAllyThreshold() {
        return SCAVENGER.seekAllyThreshold.get();
    }

    /** 专程找接收者时的搜索半径（格）。比 {@link #scavengerDeliveryRadius()} 大得多。 */
    public static double scavengerSeekAllyRadius() {
        return SCAVENGER.seekAllyRadius.get();
    }

    /** 两次「去找队友」之间的冷却（tick）。 */
    public static int scavengerSeekAllyCooldownTicks() {
        return Math.max(1, SCAVENGER.seekAllyCooldownSeconds.get() * 20);
    }

    /** 世上同时存在的拾荒者上限。到顶后转换概率降到 0。 */
    public static int scavengerMaxCount() {
        return SCAVENGER.maxCount.get();
    }

    // ---- 死后把存量掉出来 ----

    /** 死后按汇率换成哪些物品（写坏的条目会被跳过并记日志）。 */
    public static List<? extends String> scavengerDeathDropItems() {
        return SCAVENGER.deathDropItems.get();
    }

    /** 多少资源换一个掉落物。 */
    public static int scavengerDeathDropResourcePerItem() {
        return Math.max(1, SCAVENGER.deathDropResourcePerItem.get());
    }

    /** 单次死亡最多掉几个掉落物。 */
    public static int scavengerDeathDropMaxItems() {
        return Math.max(0, SCAVENGER.deathDropMaxItems.get());
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
    // 真菌袭击
    // ------------------------------------------------------------------
    //
    // 顺序与 raid 段那七张子表一致：trigger → prep → bonus → attack → arena → ownWave → ownLoot。

    // ---- raid.trigger ----

    /** 个人恨意值每涨满这么多，就算跨过一个档位并掷一次触发骰。 */
    public static double raidThresholdStep() {
        return RAID.thresholdStep.get();
    }

    /** 每跨过一个档位时触发袭击的概率。 */
    public static double raidTriggerChance() {
        return RAID.triggerChance.get();
    }

    // ---- raid.prep ----

    /** 准备阶段持续多少 tick 后无条件转入攻击（够不够格是另说的）。 */
    public static int raidPrepTicks() {
        return Math.max(1, RAID.prepSeconds.get() * 20);
    }

    /** 准备阶段最多拖多久；到点还没凑够发动条件就取消这次袭击。 */
    public static int raidPrepTimeoutTicks() {
        return Math.max(raidPrepTicks(), RAID.prepMaxSeconds.get() * 20);
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

    /** 准备阶段每隔多少 tick 检查一次发动条件。 */
    public static int raidLaunchCheckIntervalTicks() {
        return Math.max(1, RAID.launchCheckIntervalTicks.get());
    }

    // ---- raid.bonus ----

    /** 准备期间心智的资源消耗倍率（需求：降低 50%）。 */
    public static double raidPrepResourceCostMultiplier() {
        return RAID.prepResourceCostMultiplier.get();
    }

    /** 准备期间心智的资源获取倍率（需求：提高 100%）。 */
    public static double raidPrepResourceGainMultiplier() {
        return RAID.prepResourceGainMultiplier.get();
    }

    /** 准备期间心智的发育速度倍率（需求：提高 50%）。 */
    public static double raidPrepGrowthSpeedMultiplier() {
        return RAID.prepGrowthSpeedMultiplier.get();
    }

    /** 攻击期间心智的制造（召唤）速度倍率（需求：加快 500%）。 */
    public static double raidAttackManufactureSpeedMultiplier() {
        return RAID.attackManufactureSpeedMultiplier.get();
    }

    /** 攻击期间 Despawning System 上限的倍率（需求：提高 100%）。 */
    public static double raidAttackDespawnCapMultiplier() {
        return RAID.attackDespawnCapMultiplier.get();
    }

    // ---- raid.attack ----

    /** 传染送落点距目标玩家的最近距离（格）。 */
    public static double raidTeleportMinDistance() {
        return RAID.teleportMinDistance.get();
    }

    /** 传染送落点距目标玩家的最远距离（格）。 */
    public static double raidTeleportMaxDistance() {
        return RAID.teleportMaxDistance.get();
    }

    /** 从多大范围内把真菌"调"过来（格）。 */
    public static double raidTeleportSearchRadius() {
        return RAID.teleportSearchRadius.get();
    }

    /** 攻击期间加在参战真菌身上的增益，格式 {@code 效果id|持续tick|amplifier}。 */
    public static List<? extends String> raidAttackBuffs() {
        return RAID.attackBuffs.get();
    }

    /** 玩家躲进黑名单维度后，最多允许缺席多少 tick 才判失败。 */
    public static int raidAbsenceTimeoutTicks() {
        return Math.max(1, RAID.absenceTimeoutSeconds.get() * 20);
    }

    // ---- raid.arena ----

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

    /** 「恐惧吧」比竞技之须出现提前多少 tick 发出。 */
    public static int raidWarnLeadTicks() {
        return Math.max(0, RAID.warnLeadSeconds.get() * 20);
    }

    // ---- raid.ownWave ----

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
     * 我方某一波该送来多少只。
     *
     * <p>需求改成"每波无上限、具体数量与波次有关"，所以这里是个随波次线性增长的公式，
     * 不再有固定的每波上限。真正的上限来自世界本身：{@code raid.attack.teleportSearchRadius}
     * 内一共只有那么多真菌可调，而它们的总数又受 Spore 的 Despawn 上限约束
     * （那个上限在攻击期间被 {@code raid.bonus.attackDespawnCapMultiplier} 翻倍了）。
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

    // ---- raid.ownLoot ----

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

    // ---- 清扫世界 ----

    /** 清扫的总开关。 */
    public static boolean hivemindSweepEnabled() {
        return HIVEMIND.sweepEnabled.get();
    }

    /** 一个维度里至少要有几只心智才可能发动清扫。 */
    public static int hivemindSweepMinHiveminds() {
        return Math.max(1, HIVEMIND.sweepMinHiveminds.get());
    }

    /** 清扫的判定周期（tick）。至少 20（1 秒），否则取模会退化成每 tick 都判。 */
    public static int hivemindSweepIntervalTicks() {
        return Math.max(20, HIVEMIND.sweepIntervalMinutes.get() * 60 * 20);
    }

    /** 到点时发动清扫的概率。 */
    public static double hivemindSweepChance() {
        return HIVEMIND.sweepChance.get();
    }

    /**
     * 这次清扫的转化倍率。
     *
     * <p><b>随心智数上浮</b>：实际倍率 = 基础倍率 + (心智数 - 下限) × 每多一只的增量。
     * 下限以上才计入，所以刚好凑够门槛时就是基础倍率。
     *
     * @param hivemindCount 这个维度里的心智数（调用方已确保它 >= 下限）
     */
    public static double hivemindSweepMultiplier(int hivemindCount) {
        int extra = Math.max(0, hivemindCount - hivemindSweepMinHiveminds());
        return HIVEMIND.sweepMultiplier.get() + extra * HIVEMIND.sweepPerExtraHivemind.get();
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

    /**
     * 转化表里没列到的物品值多少资源。
     *
     * <p>默认 1.0 而不是 0——需求是「所有掉落物全部纳入」，所以没列到的照收，
     * 想排除某类东西请用数据包里的 {@code loot_blacklist}。
     */
    public static double lootDefaultValue() {
        return LOOT.defaultValue.get();
    }

    /** 没被价值表列到的物品，捡起来之后的转化概率。 */
    public static double lootDefaultChance() {
        return LOOT.defaultChance.get();
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

    /**
     * 真菌加强那一段（第一批的内容，原样搬过来）。
     *
     * <p>段内顺序是「成长 → 猎杀 → 感知 → 抗寒」：先给读者「它变得多能打」，再给「它在什么环境下还撑得住」。
     * 灾厄重构体那几项原在本段，后来按「某种特定真菌个体的机制」拆去了 {@code womb} 段。
     */
    private static final class Fungus {

        private final ForgeConfigSpec.IntValue evolutionSpeedBonus;
        private final ForgeConfigSpec.BooleanValue huntEnabled;
        private final ForgeConfigSpec.IntValue huntPriority;
        private final ForgeConfigSpec.DoubleValue huntCautionRatio;
        private final ForgeConfigSpec.DoubleValue huntArmorEhpPerPoint;
        private final ForgeConfigSpec.IntValue huntSearchIntervalTicks;
        private final ForgeConfigSpec.DoubleValue sensingMultiplier;
        private final ForgeConfigSpec.DoubleValue freezeDamageMultiplier;
        private final ForgeConfigSpec.IntValue coldFrostbiteIntervalTicks;
        private final ForgeConfigSpec.DoubleValue coldHungerPenaltyFactor;
        private final ForgeConfigSpec.DoubleValue coldBiomeThreshold;

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

            this.huntPriority = builder
                    .comment("「猎杀一切生物」那条目标挂在目标选择器上的优先级。0 ~ 20，默认 3。",
                            "**必须大于 Spore 玩家 / 白名单目标的 1**：这条目标不做任何玩家判定，",
                            "排在玩家目标前面的话，玩家站在旁边时它会先去咬别人。",
                            "这也正是「判定不管玩家」不需要额外代码的原因——优先级本身就把它让开了。",
                            "调到 0 会让它盖过一切（包括逃跑类的目标），通常不该那么做。",
                            "**改动只对之后生成的实体生效**，已经在世界里的真菌要重载区块或重生才会用上新值。")
                    .defineInRange("huntPriority", 3, 0, 20);

            this.huntCautionRatio = builder
                    .comment("「打得过」的裕度。0.1 ~ 5.0，默认 1.0。",
                            "判据是比较「谁先死」：我打死它要多久 vs 它打死我要多久（见 FungusCombat#canWin）。",
                            "1.0 = 只有明显占优才打；调大更莽（愿意拿更长的击杀时间去赌），调小更怂。",
                            "要留意这个判据只看攻击力、护甲与最大生命：射程、药水、自爆、图腾都不计入，",
                            "它是开打前的粗略掂量，不是战斗模拟器。")
                    .defineInRange("huntCautionRatio", 1.0D, 0.1D, 5.0D);

            this.huntArmorEhpPerPoint = builder
                    .comment("「打得过才打」判定里，每 1 点护甲折算成多少有效生命倍率。0.0 ~ 1.0，默认 0.04。",
                            "原版的护甲减伤同时取决于护甲与这一击的伤害，而判定要做的是「还没交手，先估谁更硬」，",
                            "拿不到「这一击多大」，所以退化成「20 点护甲减伤 80%」这个上界 —— 也就是每点 4%。",
                            "它只影响判定，不改真实伤害。0.04 → 20 点护甲 = 2 倍有效生命；",
                            "调大 = 判定更忌惮护甲高的目标（更常放弃），调小则相反。")
                    .defineInRange("huntArmorEhpPerPoint", 0.04D, 0.0D, 1.0D);

            this.huntSearchIntervalTicks = builder
                    .comment("「攻击一切生物」那条目标多久搜一次目标（tick）。1 ~ 200，默认 10。",
                            "10 是原版 NearestAttackableTargetGoal 各简版构造器用的默认值，Spore 自己也用它。",
                            "改小的代价是空转成本：每 tick 搜一次全实体是没必要的。")
                    .defineInRange("huntSearchIntervalTicks", 10, 1, 200);

            this.sensingMultiplier = builder
                    .comment("真菌感知范围（FOLLOW_RANGE）的倍率。1.0 ~ 4.0，默认 2.0（16 格 → 32 格）。",
                            "同时放大两件事：**视觉**（目标搜索半径）与**声音**",
                            "（同伙被打时 alertOthers 的警报传播半径）——Spore 这两处读的是同一个属性。",
                            "上限说明：巢群广播（LocalTargettingGoal）在 Spore 里被硬编码封顶 32 格，",
                            "所以倍率超过 2.0 之后，继续增长的只有视觉与警报，巢群广播不再跟着变。",
                            "填 1.0 = 原样。")
                    .defineInRange("sensingMultiplier", 2.0D, 1.0D, 4.0D);

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

            builder.pop();
        }
    }

    /**
     * 灾厄重构体（Womb）那一段。
     *
     * <p>它原先混在 {@code fungus} 段里，但受益方不是「真菌整体」，而是<b>某一只特定生物</b>——
     * Spore 的 {@code Sentities.Organoids.Womb}。它自带独立的 BIOMASS 与配方体系，
     * 与心智之间没有任何数据通路，所以要单独一组旋钮。
     *
     * <p>段内顺序按「喂食 → 孵化出场」排：先改它攒了多少突变，再改它把灾厄放到哪里。
     */
    private static final class Womb {

        private final ForgeConfigSpec.DoubleValue mutationMultiplier;
        private final ForgeConfigSpec.DoubleValue raidMutationMultiplier;
        private final ForgeConfigSpec.BooleanValue raidBonusesEnabled;
        private final ForgeConfigSpec.IntValue hatchMinMutationTypes;
        private final ForgeConfigSpec.IntValue hatchMaxStallSeconds;
        private final ForgeConfigSpec.BooleanValue mindFundingEnabled;
        private final ForgeConfigSpec.IntValue mindFundingCost;
        private final ForgeConfigSpec.IntValue mindFundingIntervalSeconds;
        private final ForgeConfigSpec.DoubleValue mindFundingRange;
        private final ForgeConfigSpec.BooleanValue hatchExitEnabled;
        private final ForgeConfigSpec.DoubleValue hatchExitSearchRadius;
        private final ForgeConfigSpec.BooleanValue hatchExitFromDome;
        private final ForgeConfigSpec.DoubleValue hatchExitDomeMargin;

        private Womb(ForgeConfigSpec.Builder builder) {
            builder.comment("灾厄重构体（Womb）：同化突变加速、以及孵化出的灾厄放到哪里。",
                            "**它不是心智**：自带独立的生物质与配方体系，与心智之间没有数据通路，",
                            "所以 raid.bonus 里那些给心智的加成默认到不了它（见 raidBonusesEnabled）。")
                    .push("womb");

            this.mutationMultiplier = builder
                    .comment("同化喂食时，一次喂食算几条突变。1.0 ~ 10.0，默认 2.0。",
                            "需求：叠满属性所需的生物减少 50%，所以默认 2.0——喂一只等于原来的两只。",
                            "**机制说明（反编译 Spore 2.2.0j 确认）**：Womb 并没有「最多叠 N 条」的上限常量。",
                            "addMutation 只是把配方的属性 id 追加进一份不去重、不封顶的列表 attributeIDs，",
                            "孵化灾厄时 summon 再遍历整个列表、每条给对应属性 +1.0 基础值（重复的照样各算一次）。",
                            "所以「叠满」= 玩家自己喂出来的列表长度，本项就是那个长度的倍率。",
                            "填 1.0 = 与 Spore 原样。",
                            "注意它只影响喂食，不影响 Womb 攒生物质成型的快慢（那是另一套 BIOMASS）。")
                    .defineInRange("mutationMultiplier", 2.0D, 1.0D, 10.0D);

            this.raidMutationMultiplier = builder
                    .comment("上一条在真菌袭击期间的额外倍率。1.0 ~ 10.0，默认 2.0。",
                            "需求：袭击期间再减少 50%（剩余值的一半）。与上一条相乘，",
                            "所以默认在袭击期间喂一只等于原来的四只。",
                            "**倍率在喂食那一刻锁定**：追加进列表的是重复条目本身，",
                            "于是关掉袭击之后，先前喂进去的那些条目不会被追溯放大——",
                            "这也是不去改 summon 里那个 +1.0D 常量的原因，那个常量是所有条目共用的。",
                            "填 1.0 = 袭击期间与平时一样。")
                    .defineInRange("raidMutationMultiplier", 2.0D, 1.0D, 10.0D);

            this.raidBonusesEnabled = builder
                    .comment("袭击的制造速度 / 资源获取加成是否也作用到灾厄重构体。默认开。",
                            "需求原文说的是「心智制造速度加快 500%」，而灾厄重构体（Womb）不是心智：",
                            "它有自己的 BIOMASS、自己的配方体系，与心智之间没有任何数据通路",
                            "（它孵化灾厄是直接 create + addFreshEntity，不走心智的 summonMob）。",
                            "所以那项加成原本到不了它——这一项就是把袭击的加成单独接到它身上，属于扩展。",
                            "接的是两条已有的倍率，不新增数值：",
                            "  制造速度（raid.bonus.attackManufactureSpeedMultiplier）→ 孵化节拍（recontructor_clock 秒数）",
                            "  资源获取（raid.bonus.prepResourceGainMultiplier）→ 每次同化得到的生物质",
                            "关掉 = 灾厄重构体完全按 Spore 原速。")
                    .define("raidBonusesEnabled", true);

            this.hatchMinMutationTypes = builder
                    .comment("孵化前至少要喂到几种**不同**的突变属性。0 ~ 7，默认 4（过半）。",
                            "**这一项解决的是「灾厄孵化太快、属性太低」。** Spore 原版里孵化的触发条件是",
                            "生物质攒够，而生物质主要来自**吃掉**靠上来的真菌——每只给 5 点、每 2 秒能吃一只，",
                            "而被动节拍每 30 秒才给 1 点。所以一只蹲在菌群里的重构体**不到一分钟**就吃饱了，",
                            "根本等不到喂进去几种属性，孵出来的就是一只没有加成的白板灾厄。",
                            "**属性只来自喂食**：Spore 一共 7 个同化配方（ballistic / corrosives / grinding /",
                            "laceration / localization / rejuvenation / toxicity），每个要喂对应的特定进化体",
                            "（骑士、温迪戈、撕裂者、鸣蜂……）。本项就是「至少喂到几种才准孵化」。",
                            "没喂够时**它不会停止进食**——继续吃、继续攒，直到凑够为止。",
                            "填 0 = 关掉门槛，回到 Spore 原样（生物质一满就孵）。",
                            "**死亡孵化不受本项约束**：生物质过半的重构体被打死时照旧当场孵一只。")
                    .defineInRange("hatchMinMutationTypes", 4, 0, 7);

            this.hatchMaxStallSeconds = builder
                    .comment("基础诞生条件（生物质）满足后，最多允许滞留多少秒再无条件孵化。0 ~ 3600，默认 300（5 分钟）。",
                            "**没有它的话，凑不齐属性的重构体会永远卡在那里吃**——不孵、也不消失，",
                            "成为地图上一台一直运转的吞噬装置。到点无条件孵，孵出来的是它当时攒到的那点属性。",
                            "计时从**第一次被门槛拦下**那一刻算起（那一刻正好等于生物质攒够），",
                            "记在重构体自己的持久数据里，区块卸载与服务器重启都不清零。",
                            "填 0 = 不设上限，允许无限滞留。")
                    .defineInRange("hatchMaxStallSeconds", 300, 0, 3600);

            this.mindFundingEnabled = builder
                    .comment("心智是否**出资**替卡住的重构体补齐突变。默认开。",
                            "门槛（hatchMinMutationTypes）要求先喂够属性，而属性只能靠喂特定进化体",
                            "（骑士、温迪戈、撕裂者、鸣蜂……）。玩家不配合的话重构体就一直凑不齐——",
                            "这条能力让心智自己把这件事办掉：花生物质买缺的那几种属性。",
                            "**缺哪几种是读 Spore 的配方表得来的**，不在这里配：它自动跟着 Spore 的数据走。")
                    .define("mindFundingEnabled", true);

            this.mindFundingCost = builder
                    .comment("补一种属性要花多少生物质。0 ~ 10000，默认 20。",
                            "对比：吃一只真菌给 5 点生物质。所以它是「买」而不是「捡」，价码刻意贵一些。",
                            "出不起价的心智这一轮不办，等自己攒够（袭击期间它的收入本来就会翻倍）。",
                            "填 0 = 白送，那会让门槛形同虚设（心智瞬间把每只重构体补满）。")
                    .defineInRange("mindFundingCost", 20, 0, 10000);

            this.mindFundingIntervalSeconds = builder
                    .comment("多久尝试资助一次（秒）。1 ~ 600，默认 30。",
                            "一次只办一只重构体、只补**一种**属性，所以多只心智会自然分工，",
                            "也不会在一拍之内把一只心智掏空。调小 = 补得更快、也吃得更狠。")
                    .defineInRange("mindFundingIntervalSeconds", 30, 1, 600);

            this.mindFundingRange = builder
                    .comment("心智从多大范围内找需要资助的重构体（格）。8.0 ~ 256.0，默认 64.0。",
                            "超出这个范围的重构体只能靠自己攒或靠玩家喂。")
                    .defineInRange("mindFundingRange", 64.0D, 8.0D, 256.0D);

            this.hatchExitEnabled = builder
                    .comment("灾厄孵化后是否把它挪到重构体外面去。默认开。",
                            "Spore 原本把孵化出的灾厄放在**重构体自己身上**（setPos 用的就是它自己的坐标），",
                            "而重构体是埋在生物质里的土丘——它的 aiStep 在被方块卡住时会挖掉周围方块，",
                            "说明它本来就嵌在实心方块里，体型还会随生物质长到 3 档。",
                            "于是大 hitbox 的灾厄一孵化出来就卡在方块中窒息，进而把那圈壳挖烂。",
                            "开启后：向外找最近的空位再放它（见 WombHatch）；找不到空位就维持原位。")
                    .define("hatchExitEnabled", true);

            this.hatchExitSearchRadius = builder
                    .comment("找壳外空位时最多向外找多远（格）。1.0 ~ 32.0，默认 8.0。",
                            "起点由两个包围盒的半宽决定（刚好不重叠的距离），从这个半径起逐格向外找，",
                            "水平八个方向都试；水平全被堵住时再试着往上抬。",
                            "调大 = 更愿意把它丢远一点，代价是可能落到玩家没预料的地方；",
                            "找不到任何空位时不动它——宁可维持原位，也不扔进岩层或虚空。")
                    .defineInRange("hatchExitSearchRadius", 8.0D, 1.0D, 32.0D);

            this.hatchExitFromDome = builder
                    .comment("孵化出的灾厄是否要被送出心智的生物质穹顶（不只是送出重构体本身）。默认开。",
                            "**这里的「壳」指心智那两层生物质球壳**（半径 32 厚 2、半径 16 厚 1，",
                            "由 Spore 的 CasingGenerator 生成，见 DomeBreach 的类注释），",
                            "不是重构体自己那圈方块。需求要防的是「灾厄在穹顶里把壳挖烂」，",
                            "而只把它挪出重构体的包围盒根本不足以让它离开穹顶——所以默认走穹顶判定。",
                            "落点用的穹顶半径取自 hivemind 段的 domeRadius，两边共用同一个「穹顶有多大」。")
                    .define("hatchExitFromDome", true);

            this.hatchExitDomeMargin = builder
                    .comment("送出穹顶时，在穹顶半径之外再留出多少格。0.0 ~ 16.0，默认 2.0。",
                            "Spore 的外层壳是「半径 32、厚 2」，所以壳的外表面到 33；",
                            "默认在 32 之外再加 2，落点稳定在壳外。",
                            "**注意这个距离是从心智的位置算的**，也就是说灾厄会被丢到离心智",
                            "三十几格远的地方——它本来就不该待在巢里，这个距离是刻意的。",
                            "放不下（那一圈全是实心方块）时会依次试别的角度；一圈都不行就退回",
                            "「只送出重构体」那个较弱的落点，而不是硬塞进石头里。")
                    .defineInRange("hatchExitDomeMargin", 2.0D, 0.0D, 16.0D);

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
        private final ForgeConfigSpec.IntValue moveCueCooldownSeconds;
        private final ForgeConfigSpec.DoubleValue storageBase;
        private final ForgeConfigSpec.DoubleValue storagePerMinute;
        private final ForgeConfigSpec.DoubleValue storageMaxBonus;
        private final ForgeConfigSpec.IntValue lootRadius;
        private final ForgeConfigSpec.IntValue roamAwarenessRadius;
        private final ForgeConfigSpec.DoubleValue lootSprintRadius;
        private final ForgeConfigSpec.DoubleValue lootSprintSpeed;
        private final ForgeConfigSpec.IntValue linkScanIntervalTicks;
        private final ForgeConfigSpec.DoubleValue linkScanRadius;
        private final ForgeConfigSpec.DoubleValue linkBreakRadius;
        private final ForgeConfigSpec.DoubleValue linkProbeRadius;
        private final ForgeConfigSpec.IntValue linkMinLinks;
        private final ForgeConfigSpec.IntValue linkMaxLinks;
        private final ForgeConfigSpec.DoubleValue linkRampMinutes;
        private final ForgeConfigSpec.IntValue linkHivemindMinLinks;
        private final ForgeConfigSpec.IntValue linkHivemindMaxLinks;
        private final ForgeConfigSpec.DoubleValue lootWeightDistance;
        private final ForgeConfigSpec.DoubleValue lootWeightArrival;
        private final ForgeConfigSpec.DoubleValue lootWeightCount;
        private final ForgeConfigSpec.DoubleValue lootWeightType;
        private final ForgeConfigSpec.DoubleValue lootWeightEfficiency;
        private final ForgeConfigSpec.DoubleValue lootWeightDanger;
        private final ForgeConfigSpec.DoubleValue lootClusterRadius;
        private final ForgeConfigSpec.DoubleValue lootValueHalf;
        private final ForgeConfigSpec.DoubleValue lootCountHalf;
        private final ForgeConfigSpec.DoubleValue lootMaxVerticalClimbFactor;
        private final ForgeConfigSpec.DoubleValue fleeHealthFraction;
        private final ForgeConfigSpec.DoubleValue fleeThreatRadius;
        private final ForgeConfigSpec.DoubleValue fleeSurviveSeconds;
        private final ForgeConfigSpec.IntValue fleeAggroMemoryTicks;
        private final ForgeConfigSpec.DoubleValue fleeSpeed;
        private final ForgeConfigSpec.IntValue fleeTargetHorizontalDistance;
        private final ForgeConfigSpec.IntValue fleeTargetVerticalRange;
        private final ForgeConfigSpec.DoubleValue deliveryRadius;
        private final ForgeConfigSpec.IntValue healMinTargets;
        private final ForgeConfigSpec.IntValue healMaxTargets;
        private final ForgeConfigSpec.DoubleValue healRampMinutes;
        private final ForgeConfigSpec.IntValue healMinTier;
        private final ForgeConfigSpec.DoubleValue healWoundedFraction;
        private final ForgeConfigSpec.DoubleValue healthPerResource;
        private final ForgeConfigSpec.DoubleValue evoPointsPerResource;
        private final ForgeConfigSpec.DoubleValue seekAllyThreshold;
        private final ForgeConfigSpec.DoubleValue seekAllyRadius;
        private final ForgeConfigSpec.IntValue seekAllyCooldownSeconds;
        private final ForgeConfigSpec.IntValue maxCount;
        private final ForgeConfigSpec.IntValue fleePriority;
        private final ForgeConfigSpec.IntValue lootPriority;
        private final ForgeConfigSpec.ConfigValue<List<? extends String>> deathDropItems;
        private final ForgeConfigSpec.IntValue deathDropResourcePerItem;
        private final ForgeConfigSpec.IntValue deathDropMaxItems;

        private Scavenger(ForgeConfigSpec.Builder builder) {
            builder.comment("拾荒者：菌染人类的变体，不参战、专职捡掉落物、把收获供给同伙。",
                            "它的模型/贴图/音效与菌染人类完全一致（复用 Spore 那一套），所以只能靠行为辨认。",
                            "**它已经被完全排除在消失之外**（都在本 mod 的代码里拦的，不需要你去改 Spore 的配置）：",
                            "  · Spore 自己的 Despawning System —— 靠重定向它的黑名单判断；",
                            "  · 原版的「走远就消失」—— 靠覆写 removeWhenFarAway。",
                            "所以它会一直活到你打死它为止，存活成长也就真的能攒满。",
                            "**数量不会因此无限增长**：转变要同时过两道闸门——",
                            "  ① 基础概率：附近掉落物 0 件时 10%、32 件时 50%，之间线性插值；",
                            "  ② 数量系数：max(0, 1 - 现存/上限)，上限默认 20，到上限时概率正好归零。",
                            "**②才是决定最终只数的那个**——①只影响收敛快慢，改它不会改变最终数量。")
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
                    .comment("拾荒者每存活一分钟增加多少最大生命。0.0 ~ 100.0，默认 6.0。",
                            "需求：拾荒者的生命、防御、速度、生命恢复速度都随存活时间增长。",
                            "四条曲线都是「线性爬升、各自封顶」，与 lootBonusPerMinute 同一套算法；",
                            "时间基准是那只拾荒者的存活时长，**随实体存盘**：区块卸载或重启服务器都不会清零，",
                            "所以「活得越久越强」是真的按总存活时间算。",
                            "这条的封顶见 healthMaxBonus。填 0 = 生命不随存活时间成长。")
                    .defineInRange("healthPerMinute", 6.0D, 0.0D, 100.0D);

            this.healthMaxBonus = builder
                    .comment("最大生命成长的上限。0.0 ~ 500.0，默认 90.0。",
                            "以拾荒者自身的基础生命为基准往上加，所以最终生命 = 基础 + 本项（封顶后）。",
                            "菌染人类基础 15 点，所以默认满成长是 105 点。",
                            "换算：默认值下存活 15 分钟加满。",
                            "**调大它的意义是「更难被秒」，不是「打不死」**——它不还手，",
                            "所以这份血量只是把「顺手一刀带走」变成「得认真处理一下」。")
                    .defineInRange("healthMaxBonus", 90.0D, 0.0D, 500.0D);

            this.armorPerMinute = builder
                    .comment("每存活一分钟增加多少护甲。0.0 ~ 10.0，默认 1.5。",
                            "护甲是属性绝对值（原版每点护甲约减伤 4%，20 点封顶 = 80% 减伤）。",
                            "默认值下存活约 13 分钟加满 19 点——加上基础 1 点正好是原版满套装的 20 点。")
                    .defineInRange("armorPerMinute", 1.5D, 0.0D, 10.0D);

            this.armorMaxBonus = builder
                    .comment("护甲成长的上限。0.0 ~ 100.0，默认 19.0。",
                            "**原版对 20 点以上的护甲不再增加减伤**，所以默认值就是「穿满下界合金」的等效硬度。",
                            "继续调大只对别的模组改过护甲公式的情况有意义。")
                    .defineInRange("armorMaxBonus", 19.0D, 0.0D, 100.0D);

            this.speedPerMinute = builder
                    .comment("每存活一分钟增加多少移动速度。0.0 ~ 1.0，默认 0.015。",
                            "注意这是**属性的绝对值**，不是倍率：Spore 给菌染人类的移动速度基础值是 0.2",
                            "（javap 实测）。默认值下存活约 7 分钟加满 0.10，也就是比基础快 50%。")
                    .defineInRange("speedPerMinute", 0.015D, 0.0D, 1.0D);

            this.speedMaxBonus = builder
                    .comment("移动速度成长的上限（属性绝对值）。0.0 ~ 1.0，默认 0.10。",
                            "最终速度 = 0.2 + 本项，所以默认满成长是 0.30。",
                            "**参照系**：玩家的行走速度是 0.1、疾跑约 0.13。",
                            "所以默认下它比疾跑的玩家快一倍多——这正是「越活越难抓」的设计意图，",
                            "但要注意它同时持有逃跑用的 1.5 倍与捡东西时的奔袭倍率，",
                            "几个倍率是相乘的。想留出反制空间就调小这一项。")
                    .defineInRange("speedMaxBonus", 0.10D, 0.0D, 1.0D);

            this.regenHpsPerMinute = builder
                    .comment("每存活一分钟，每秒回复的生命点数涨多少。0.0 ~ 10.0，默认 1.0。",
                            "「生命恢复速度」做成每秒回复 N 点，N 随存活时间线性爬升、由 regenMaxHps 封顶。",
                            "默认值下存活 5 分钟到达每秒 5 点。填 0 = 不回复。")
                    .defineInRange("regenHpsPerMinute", 1.0D, 0.0D, 10.0D);

            this.regenMaxHps = builder
                    .comment("每秒回复生命的上限。0.0 ~ 50.0，默认 5.0。",
                            "实现上用一个小数累加器攒够 1 点才真的回一次血——",
                            "heal() 只收整数，0.5 点/秒这种速率直接取整会变成永不回复。",
                            "满成长时它是全 mod 里最硬的一只后勤：105 点生命、20 点护甲、每秒回 5 点——",
                            "**但它不还手**，所以这份硬度只延长「处理掉它」的时间，不构成战斗威胁。")
                    .defineInRange("regenMaxHps", 5.0D, 0.0D, 50.0D);

            this.moveCueCooldownSeconds = builder
                    .comment("「拾荒者移动」那条字幕的提示音最短间隔（秒）。1 ~ 600，默认 8。",
                            "它只负责「我开始走动了」这一声轻响，没有它的话行走会每 tick 触发一次，字幕会被刷屏。",
                            "这一条是给隐藏式字幕用的（拾荒者的模型/贴图/音效与菌染人类完全一致，",
                            "不看字幕分不出它），调大 = 更安静，调小 = 更容易注意到附近有拾荒者在活动。")
                    .defineInRange("moveCueCooldownSeconds", 8, 1, 600);

            this.storageBase = builder
                    .comment("它自己身上那个「资源存储」的上限**基数**。0.0 ~ 100000.0，默认 50.0。",
                            "捡到资源先试着送出去（心智 / 残血同伙），送不掉就先揣在自己身上。",
                            "本项是那个存储的上限起点，会随存活时间往上长，见下面两项。",
                            "**装不下就丢了**：超出上限的部分直接消失，不会退回掉落物。")
                    .defineInRange("storageBase", 50.0D, 0.0D, 100000.0D);

            this.storagePerMinute = builder
                    .comment("每存活一分钟，存储上限涨多少。0.0 ~ 10000.0，默认 10.0。",
                            "与 healthPerMinute 那一组同一套写法：线性爬升、由下一项封顶。",
                            "默认下 1 分钟 60、10 分钟 150、1 小时 650。")
                    .defineInRange("storagePerMinute", 10.0D, 0.0D, 10000.0D);

            this.storageMaxBonus = builder
                    .comment("上面的成长封顶。0.0 ~ 1000000.0，默认 2000.0。",
                            "默认下约 3.3 小时爬到顶（上限 2050）——「随存活时间**大幅**增加」就是靠它远大于基数体现的。",
                            "填 0 表示上限恒为 storageBase、不随存活增长。")
                    .defineInRange("storageMaxBonus", 2000.0D, 0.0D, 1000000.0D);

            this.lootRadius = builder
                    .comment("拾荒者搜寻掉落物的半径（格）。1 ~ 64，默认 32。",
                            "**它是拾荒者专用的**，与 loot 段那个给普通真菌用的 radius 是两回事：",
                            "那个默认 16，因为它同样作用于满地的普通真菌，半径翻倍 = 搜索盒体积翻四倍；",
                            "而拾荒者世上最多只有 maxCount 只，放宽到 32 的代价可以忽略。",
                            "它决定「捡东西时能看见多远」，下面那一项决定「闲逛时朝哪走」，两者分工不同。")
                    .defineInRange("lootRadius", 32, 1, 64);

            this.roamAwarenessRadius = builder
                    .comment("闲逛时朝战利品偏的感知半径（格）。16 ~ 128，默认 64。",
                            "拾荒者没东西可捡时是闲逛的。本项让它闲逛时先看看这个半径内有没有战利品，",
                            "有就朝那个方向偏着走——没有它的话，一只离战场很远的拾荒者会一直随机游荡、",
                            "永远碰不到任何东西，「活得越久越强」也就攒不出来。",
                            "**必须大于 lootRadius**：如果两者相等，它感知到的东西本来就在捡拾范围内、",
                            "拾荒目标会直接去拿，这一项就完全不起作用了，所以默认给了两倍。",
                            "调大 = 它更愿意长途跋涉去找战利品（代价是可能跑出你的视野）；",
                            "调成与 lootRadius 相等 = 关掉这个行为，退回原版的纯随机闲逛。")
                    .defineInRange("roamAwarenessRadius", 64, 16, 128);

            this.lootSprintRadius = builder
                    .comment("离目标掉落物多近时开始**奔袭**（格）。1.0 ~ 64.0，默认 8.0。",
                            "需求：靠近掉落物一定范围内后奔袭前去捡拾。",
                            "只影响「已经锁定目标、正在走过去」这一段——找东西、闲逛都不受影响。",
                            "填 0 = 关掉奔袭（一直用 loot.moveSpeed 那个速度）。")
                    .defineInRange("lootSprintRadius", 8.0D, 0.0D, 64.0D);

            this.lootSprintSpeed = builder
                    .comment("奔袭时的移速倍率。1.0 ~ 5.0，默认 1.5。",
                            "它是乘在**已经算上存活成长**的移速上的，所以两者是相乘的：",
                            "满成长的拾荒者（0.30）奔袭时实际是 0.45 格/tick——比疾跑的玩家快三倍多。",
                            "这个倍率只在最后那一段生效，所以看起来是「突然扑过去」而不是全程飞奔。")
                    .defineInRange("lootSprintSpeed", 1.5D, 1.0D, 5.0D);

            // ---- 链接侦察 ----
            // 链接关系存在拾荒者自己的持久数据里（一串 UUID），**不碰 Spore 的 linked**
            // ——那个是"被心智登记过"的全局标记、没有归属，而且全 Spore 没有一处把它置回 false。
            // 我们只**读**它来判断"我是否隶属于某心智"，从而决定走 1~3 档还是 2~6 档上限。

            this.linkScanIntervalTicks = builder
                    .comment("多久重建一次链接（校验旧的 + 补新的）。1 ~ 1200，默认 100（5 秒）。",
                            "链接对象是缓慢移动的生物，5 秒的位置变化对「提供一个搜索圆心」这个用途完全够，",
                            "而实体盒扫描是这一段唯一的成本（拾荒者最多 maxCount 只，可忽略）。",
                            "**断开不是靠这里**：对方死掉/走远时，下面那些「还认得出来吗」的判据会立刻失效，",
                            "这里只是周期性地把失效的清掉、把空出的名额补上。")
                    .defineInRange("linkScanIntervalTicks", 100, 1, 1200);

            this.linkScanRadius = builder
                    .comment("直连时从多大范围内挑同伴（格）。1.0 ~ 128.0，默认 48.0。",
                            "挑的是「离得近的、还不是拾荒者的真菌」——另一只拾荒者排最后：",
                            "它自己也在满地图跑，圆心会重复覆盖，而且它跟你抢同一批掉落物。")
                    .defineInRange("linkScanRadius", 48.0D, 1.0D, 128.0D);

            this.linkBreakRadius = builder
                    .comment("链接对象离我超过多远就算走散、断开（格）。1.0 ~ 256.0，默认 64.0。",
                            "必须大于 linkScanRadius，否则刚链上就断（访问器里会把它抬到扫描半径之上）。",
                            "断开的代价只是少一个搜索圆心，所以宁可给宽一点。")
                    .defineInRange("linkBreakRadius", 64.0D, 1.0D, 256.0D);

            this.linkProbeRadius = builder
                    .comment("每个链接对象向我提供一个多大的搜索圆心（格）。1.0 ~ 64.0，默认 24.0。",
                            "**这是这次改动最主要的一处开销**：一次搜索从「一个半径 lootRadius 的盒子」",
                            "变成「自身那个 + 每个链接对象各一个半径本项的盒子」。",
                            "所以它默认比 lootRadius（32）小——圆心多而小，比圆心少而大更可控。",
                            "搜索本身仍然受 loot.searchIntervalTicks 限流，不是每 tick 都扫。")
                    .defineInRange("linkProbeRadius", 24.0D, 1.0D, 64.0D);

            this.linkMinLinks = builder
                    .comment("直连数量的**下限**（刚转变时）。0 ~ 32，默认 1。")
                    .defineInRange("linkMinLinks", 1, 0, 32);

            this.linkMaxLinks = builder
                    .comment("直连数量的**上限**（活久了之后）。0 ~ 32，默认 3。",
                            "低于下限时会被自动抬到下限。上限随存活时间从下限爬上来，见 linkRampMinutes。")
                    .defineInRange("linkMaxLinks", 3, 0, 32);

            this.linkRampMinutes = builder
                    .comment("从下限爬到上限需要存活多少分钟。0.01 ~ 120.0，默认 10.0。",
                            "线性增长、到点封顶——与 healMinTargets/healMaxTargets 那一套写法相同。")
                    .defineInRange("linkRampMinutes", 10.0D, 0.01D, 120.0D);

            this.linkHivemindMinLinks = builder
                    .comment("**经心智**间接链接时的数量下限。0 ~ 32，默认 2。",
                            "前提是拾荒者自己已链接（Spore 的 linked）——那是心智扫描过它的标志。",
                            "经心智能链得更多，是因为它麾下的真菌本来就是一个成规模的群体。")
                    .defineInRange("linkHivemindMinLinks", 2, 0, 32);

            this.linkHivemindMaxLinks = builder
                    .comment("经心智间接链接时的数量上限。0 ~ 32，默认 6。",
                            "「心智麾下的真菌」取的是心智自己的扫描盒（Proto.seachbox()，受 Spore 的",
                            "proto_range 配置膨胀）里那些**已链接**的感染体——Spore 里没有成员名单，",
                            "那个盒子就是它每次登记时用的范围，所以语义正好对上。",
                            "低于下限时会被自动抬到下限。")
                    .defineInRange("linkHivemindMaxLinks", 6, 0, 32);

            // ---- 掉落物综合评分 ----
            // 拾荒者同时看到多处战利品时按下面六项加权求和选最优的一堆。
            // 所有权重都是「相对重要性」，只影响排名、不影响量纲——把它们同时乘 10 结果不变。

            this.lootWeightDistance = builder
                    .comment("【权重】距离。0.0 ~ 10.0，默认 0.35（最高的一项）。",
                            "归一化用自身的 lootRadius 当尺度：贴着脚边得 1 分、到边缘得 0 分。")
                    .defineInRange("lootWeightDistance", 0.35D, 0.0D, 10.0D);

            this.lootWeightArrival = builder
                    .comment("【权重】到达时间。0.0 ~ 10.0，默认 0.10。",
                            "到达时间 = 水平距离 ÷ 当前移速，再加上「每向上 1 格按若干格水平距离计」的爬升罚",
                            "（见 lootMaxVerticalClimbFactor）。",
                            "**它与距离在平地上是近似共线的**，两个都调高只是把「近」这件事看重两次；",
                            "通常只调其中一个。它唯一不可替代的作用是惩罚「在高处」的目标。")
                    .defineInRange("lootWeightArrival", 0.10D, 0.0D, 10.0D);

            this.lootWeightCount = builder
                    .comment("【权重】掉落物数量。0.0 ~ 10.0，默认 0.15。",
                            "一堆里件数越多分越高，用半饱和归一化（见 lootCountHalf）——",
                            "这样不存在「超过某个数就一律满分」，五十件永远比十件高。")
                    .defineInRange("lootWeightCount", 0.15D, 0.0D, 10.0D);

            this.lootWeightType = builder
                    .comment("【权重】掉落物类型（= 稀有度）。0.0 ~ 10.0，默认 0.25。",
                            "取那一堆里**单价最高**的那件来代表「这批东西贵不贵」——",
                            "一份里有一颗钻石，就该比五十块腐肉更值得跑一趟。",
                            "同样半饱和归一化（与价值共用一个半值 lootValueHalf）。")
                    .defineInRange("lootWeightType", 0.25D, 0.0D, 10.0D);

            this.lootWeightEfficiency = builder
                    .comment("【权重】转换资源效率（= 转化概率 chance）。0.0 ~ 10.0，默认 0.15。",
                            "取那一堆按价值加权的平均概率，本身就在 0~1 之间，不必再归一化。",
                            "概率低的堆「捡了多半白捡」，所以这一项是负向的——它给低概率的堆**减分**")
                    .defineInRange("lootWeightEfficiency", 0.15D, 0.0D, 10.0D);

            this.lootWeightDanger = builder
                    .comment("【权重】路径上的危险。0.0 ~ 10.0，默认 0.0（**不做**）。",
                            "键留在这里是为了以后想开就开，但当前实现**恒为 0**：",
                            "算它要么沿线采样（便宜但看不见绕路）、要么真跑一次寻路（每个候选一次，太贵）。",
                            "所以默认关掉，权重调多大都不会生效。")
                    .defineInRange("lootWeightDanger", 0.0D, 0.0D, 10.0D);

            this.lootClusterRadius = builder
                    .comment("聚堆的三维格边长（格）。1.0 ~ 64.0，默认 8.0。",
                            "地上的战利品天然成片，先把它们按这个大小的格聚成一堆再评分，",
                            "否则「五十件散在一处」会被当成五十个互不相干的目标。",
                            "调小 = 分得更细（可能把一处战场拆成好几堆）；调大 = 更容易合并。")
                    .defineInRange("lootClusterRadius", 8.0D, 1.0D, 64.0D);

            this.lootValueHalf = builder
                    .comment("价值与稀有度的半饱和点（资源）。0.1 ~ 10000.0，默认 32.0。",
                            "归一化用 x/(x+半值)：正好等于本项时得 0.5 分，再往上收益递减但**永不封顶**。",
                            "用它而不是「除以某个上限」是因为掉落物的价值没有上界——",
                            "整合包可以写一条价值 1000 的条目，硬除上限会让所有高价堆一律顶到满分、失去分辨力。")
                    .defineInRange("lootValueHalf", 32.0D, 0.1D, 10000.0D);

            this.lootCountHalf = builder
                    .comment("数量的半饱和点（件）。0.1 ~ 1000.0，默认 8.0。",
                            "与 lootValueHalf 同一个写法：8 件时得 0.5 分。")
                    .defineInRange("lootCountHalf", 8.0D, 0.1D, 1000.0D);

            this.lootMaxVerticalClimbFactor = builder
                    .comment("到达时间里，每向上 1 格按多少格水平距离计。0.0 ~ 10.0，默认 0.5。",
                            "只罚**向上**，下坡不罚（往下走不慢）。",
                            "填 0 = 到达时间与水平距离完全等价，那一项权重就退化成把距离算两遍。")
                    .defineInRange("lootMaxVerticalClimbFactor", 0.5D, 0.0D, 10.0D);

            this.fleeHealthFraction = builder
                    .comment("血量低于最大生命的这个比例时，**无条件**逃跑（即使面对玩家）。0.0 ~ 1.0，默认 0.5。",
                            "需求 5：躲避危险是第一要务，血量不足时逃跑。",
                            "填 0.0 = 只在察觉到威胁时才跑，不再因为残血而主动跑。")
                    .defineInRange("fleeHealthFraction", 0.5D, 0.0D, 1.0D);

            this.fleeThreatRadius = builder
                    .comment("感知到多远的威胁就跑（格）。1.0 ~ 64.0，默认 16.0。",
                            "「威胁」= 把它当敌人的生物（原版判定），所以躲在墙后、隔着一片水通常不会被当成威胁。")
                    .defineInRange("fleeThreatRadius", 16.0D, 1.0D, 64.0D);

            this.fleeSurviveSeconds = builder
                    .comment("「锁定它的生物能在这么多秒内打死它」就跑。1.0 ~ 60.0，默认 4.0。",
                            "判据是「我方有效生命 ÷ 锁定者的总输出」，用的是 huntArmorEhpPerPoint 那一套硬度估算。",
                            "**它只统计真的盯上它的生物**（见 fleeAggroMemoryTicks）：",
                            "原版僵尸骷髅这类根本不会把怪物当目标，所以一群僵尸围着它**不会**让它跑——",
                            "这正是「不要光顾着跑」，但也意味着它看起来变勇了。",
                            "**调大 = 更胆小**。标定参考：刚转变的拾荒者约 15.6 有效生命，",
                            "一只僵尸每秒 3 点，预计存活 5.2 秒 → 默认 4.0 下**一只不跑、两只才跑**；",
                            "而靠存活成长养到 40 有效生命的老拾荒者，要每秒十几点才跑得动它。")
                    .defineInRange("fleeSurviveSeconds", 4.0D, 1.0D, 60.0D);

            this.fleeAggroMemoryTicks = builder
                    .comment("被谁打过之后，多少 tick 内仍把它算作「对我有仇恨」。20 ~ 1200，默认 200（10 秒）。",
                            "**这是玩家唯一的入口**：玩家没有 getTarget()，所以「玩家在打我」只能靠这条记仇来认。",
                            "填 20 表示「只要 1 秒前打过我就算」，填 1200 是「一分钟内都算」。",
                            "它与 fleeHealthFraction 是两道独立的闸门：这条管「谁在打我」，",
                            "那条管「我已经很残了就无条件跑」。")
                    .defineInRange("fleeAggroMemoryTicks", 200, 20, 1200);

            this.fleeSpeed = builder
                    .comment("逃跑时的移动速度倍率。0.1 ~ 5.0，默认 1.5。",
                            "菌染人类的基础移速是 0.2，所以 1.5 倍约等于 0.3——比普通僵尸快一点，但追不上玩家。")
                    .defineInRange("fleeSpeed", 1.5D, 0.1D, 5.0D);

            this.fleeTargetHorizontalDistance = builder
                    .comment("选逃跑落点时向外搜的水平距离（格）。1 ~ 64，默认 16。",
                            "这个距离与 fleeThreatRadius 是两回事：那一项决定「多近算威胁、要不要跑」，",
                            "本项决定「往哪个方向、跑多远落脚」。",
                            "调小 = 绕着威胁小步退（看起来更犹豫、也更容易被追上）；",
                            "调大 = 更容易一步跑出视野，但它会顺带跑到更远的地方去。")
                    .defineInRange("fleeTargetHorizontalDistance", 16, 1, 64);

            this.fleeTargetVerticalRange = builder
                    .comment("上述搜索允许的高差（格）。1 ~ 64，默认 7。",
                            "原版默认给寻路用 7——大约是「能上下两级台阶加半个坡」的量级。",
                            "**调大会让它往矿洞或树上跑**：垂直范围一放开，落点就可能是脚下的洞里。")
                    .defineInRange("fleeTargetVerticalRange", 7, 1, 64);

            this.fleePriority = builder
                    .comment("逃跑目标的优先级。0 ~ 20，默认 0（数字越小越优先）。",
                            "**必须小于 lootPriority**，否则它会在该跑的时候停下来捡东西。",
                            "需求把「躲避危险」定为第一要务，所以默认给它 0——那是所有目标里的最优先。")
                    .defineInRange("fleePriority", 0, 0, 20);

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

            this.seekAllyThreshold = builder
                    .comment("存量达到上限的多少比例就算「即将蓄满」、该去找队友了。0.1 ~ 1.0，默认 0.8。",
                            "**没到这条线就不会主动找队友**——它会继续捡东西，这是需求要的「优先拾荒」。",
                            "到线之后，它会放下手头的活、专程走到最近的接收者那里把存量倒出去。",
                            "填 1.0 = 只有真的顶到上限才去找（更容易溢出丢弃）；调小会让它更早开始跑腿。")
                    .defineInRange("seekAllyThreshold", 0.8D, 0.1D, 1.0D);

            this.seekAllyRadius = builder
                    .comment("专程找接收者时的搜索半径（格）。8.0 ~ 256.0，默认 64.0。",
                            "比 deliveryRadius 大得多是有意的：那一项管「顺手给旁边的人」，",
                            "而这一项管「为了把存货倒出去，愿意走多远」。",
                            "找不到就继续捡（溢出丢弃），所以调得很大它可能一直在路上。")
                    .defineInRange("seekAllyRadius", 64.0D, 8.0D, 256.0D);

            this.seekAllyCooldownSeconds = builder
                    .comment("两次「去找队友」之间的冷却（秒）。1 ~ 600，默认 30。",
                            "没有它的话，存满又找不到人时它会每 tick 重扫一遍半径内的实体。",
                            "倒空之后也会起冷却，免得它刚给完东西又立刻判定该去找人。")
                    .defineInRange("seekAllyCooldownSeconds", 30, 1, 600);

            this.maxCount = builder
                    .comment("世上同时存在的拾荒者**上限**。0 ~ 500，默认 20。",
                            "**这一项才是决定「最终会有几只」的那个数**——不要去找转化概率：",
                            "  实际概率 = 基础概率 × max(0, 1 - 现存 / 上限)",
                            "现存为 0 时概率不打折，到上限时正好降到 0，所以**上限本身就是概率归零点**，",
                            "数量会朝它收敛。而 transformMinChance / transformMaxChance 那两个数只决定",
                            "「多快」收敛，不决定「收敛到多少」——把它们调大调小，最终的只数不变。",
                            "（实际会略微停在略低于上限的地方：拾荒者会被打死，而上限附近招募极慢。）",
                            "需求：数量上限 + 数量越多转换概率越低。这两件事由同一条公式实现，",
                            "不必再单独判一次上限，也就不会出现两条规则互相打架。",
                            "现存数量是增量维护的（实体进出世界时 ±1），不每次遍历世界去数。",
                            "调小可以限制拾荒者泛滥；调 0 = 完全不再出现（已经存在的不会消失）。")
                    .defineInRange("maxCount", 20, 0, 500);

            this.lootPriority = builder
                    .comment("拾荒目标挂在行动目标选择器上的优先级。0 ~ 20，默认 2。",
                            "**必须比逃跑目标低**（数字大），否则它会为了捡东西而不跑；",
                            "但要比闲逛、环顾那一批高得多，否则它根本轮不到捡东西。",
                            "逃跑目标固定在优先级 0，所以本项填 1 以上才有意义。")
                    .defineInRange("lootPriority", 2, 0, 20);

            this.deathDropItems = builder
                    .comment("它死的时候，身上存的资源按汇率换成哪些物品掉在原地。",
                            "元素是物品 id，例如 spore:biomass、minecraft:diamond。表里有多项时**随机取**，",
                            "所以不会一次全掉同一种。写坏的条目会被跳过并在日志里留一行警告。",
                            "**为什么是掉成物品而不是还给真菌**：还给真菌会让「玩家杀死拾荒者」等于",
                            "把资源送给真菌，与激励机制正好相反。掉在地上谁都能捡，打劫后勤才是奖励。")
                    .defineList("deathDropItems", DEFAULT_SCAVENGER_DEATH_DROPS, o -> o instanceof String);

            this.deathDropResourcePerItem = builder
                    .comment("多少资源换一个掉落物。1 ~ 10000，默认 20。",
                            "实际掉落实数 = min(deathDropMaxItems, 存量 ÷ 本项)。",
                            "存量是抽象数字，这里用「汇率 + 白名单」换算，而不是回查转化表做凑数找零——",
                            "后者既慢又难解释，而玩家只关心「打死它能捡到多少」。")
                    .defineInRange("deathDropResourcePerItem", 20, 1, 10000);

            this.deathDropMaxItems = builder
                    .comment("单次死亡最多掉几个。0 ~ 512，默认 32。",
                            "存满时按默认汇率会算出上百个，那样一死就是上百个实体，所以要封顶。",
                            "填 0 = 死了什么都不掉（存量直接消失）。")
                    .defineInRange("deathDropMaxItems", 32, 0, 512);

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

    /**
     * 真菌袭击。按生命周期拆成七张子表，生成的 toml 里就是 {@code [raid.trigger]}、{@code [raid.prep]}
     * 这样七个表——这一段共 38 个键，平铺在一张表里找东西得逐行扫，按阶段切开之后
     * 「先定位阶段、再定位键」就够了。
     *
     * <p>子表顺序就是袭击自己的顺序：会不会打起来（trigger）→ 打之前的窗口与资格（prep）→
     * 期间心智变强多少（bonus）→ 兵怎么送到面前（attack）→ 竞技之须（arena）→
     * 我方围剿（ownWave）→ 围剿奖励（ownLoot）。
     *
     * <p><b>子表内的键名不带子表前缀</b>（写 {@code raid.ownWave.base} 而不是
     * {@code raid.ownWave.ownWaveBase}），但<b>Java 字段名保留前缀</b>（{@code ownWaveBase}）——
     * 因为九个 {@code ownWave*} 字段与两个 {@code prep*} 字段同处这一个类里，去掉前缀会撞名
     * （{@code prepSeconds} 与 {@code ownWaveSeconds} 都会变成 {@code seconds}）。
     * 所以这个类的字段名与键名有几处**故意不一致**，改键名时别顺手把字段也改了。
     */
    private static final class Raid {

        // 字段按子表分组排列，与构造函数里的声明顺序一致（顺序无关正确性，只为人读）。

        // raid.trigger
        private final ForgeConfigSpec.DoubleValue thresholdStep;
        private final ForgeConfigSpec.DoubleValue triggerChance;

        // raid.prep
        private final ForgeConfigSpec.IntValue prepSeconds;
        private final ForgeConfigSpec.IntValue prepMaxSeconds;
        private final ForgeConfigSpec.IntValue launchMinBiomass;
        private final ForgeConfigSpec.IntValue launchMinFungusCount;
        private final ForgeConfigSpec.DoubleValue launchMinQuality;
        private final ForgeConfigSpec.DoubleValue gatherRadius;
        private final ForgeConfigSpec.IntValue launchCheckIntervalTicks;

        // raid.bonus
        private final ForgeConfigSpec.DoubleValue prepResourceCostMultiplier;
        private final ForgeConfigSpec.DoubleValue prepResourceGainMultiplier;
        private final ForgeConfigSpec.DoubleValue prepGrowthSpeedMultiplier;
        private final ForgeConfigSpec.DoubleValue attackManufactureSpeedMultiplier;
        private final ForgeConfigSpec.DoubleValue attackDespawnCapMultiplier;

        // raid.attack
        private final ForgeConfigSpec.DoubleValue teleportMinDistance;
        private final ForgeConfigSpec.DoubleValue teleportMaxDistance;
        private final ForgeConfigSpec.DoubleValue teleportSearchRadius;
        private final ForgeConfigSpec.ConfigValue<List<? extends String>> attackBuffs;
        private final ForgeConfigSpec.IntValue absenceTimeoutSeconds;

        // raid.arena
        private final ForgeConfigSpec.DoubleValue arenaChanceBase;
        private final ForgeConfigSpec.DoubleValue arenaChancePerTier;
        private final ForgeConfigSpec.DoubleValue arenaChanceMax;
        private final ForgeConfigSpec.IntValue arenaWaveSize;
        private final ForgeConfigSpec.IntValue arenaWaveLevel;
        private final ForgeConfigSpec.IntValue arenaTimeoutSeconds;
        private final ForgeConfigSpec.IntValue warnLeadSeconds;

        // raid.ownWave
        private final ForgeConfigSpec.IntValue ownWaveBase;
        private final ForgeConfigSpec.IntValue ownWavePerTier;
        private final ForgeConfigSpec.IntValue ownWaveMax;
        private final ForgeConfigSpec.IntValue ownWaveCountBase;
        private final ForgeConfigSpec.IntValue ownWaveCountPerWave;
        private final ForgeConfigSpec.IntValue ownWaveSeconds;
        private final ForgeConfigSpec.DoubleValue ownWaveClearRadius;
        private final ForgeConfigSpec.IntValue ownWaveClearCount;
        private final ForgeConfigSpec.IntValue ownWaveBuffsPerWave;

        // raid.ownLoot
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

            // ------------------------------------------------------------------
            // raid.trigger —— 什么时候会打起来
            // ------------------------------------------------------------------
            builder.comment("触发：个人恨意值跨档之后掷一次骰，中了才进入准备阶段。")
                    .push("trigger");

            this.thresholdStep = builder
                    .comment("个人恨意值每涨满这么多，就算跨过一个档位并掷一次触发骰。1.0 ~ 100000.0，默认 500.0。",
                            "判据是 floor(新值 / 本项) 是否比 floor(旧值 / 本项) 大，",
                            "所以每一次**跨档**才掷一次，同一档位内反复涨落不会重复触发。")
                    .defineInRange("thresholdStep", 500.0D, 1.0D, 100000.0D);

            this.triggerChance = builder
                    .comment("每跨过一个档位时触发袭击的概率。0.0 ~ 1.0，默认 0.5（需求写的就是 50%）。")
                    .defineInRange("chance", 0.5D, 0.0D, 1.0D);

            builder.pop();

            // ------------------------------------------------------------------
            // raid.prep —— 准备阶段：拖多久、以及「够不够格发动」
            // ------------------------------------------------------------------
            builder.comment("准备阶段：心智开始全力运转，但还没动手。",
                            "它有两重职责：给玩家一段反应时间，以及判断这次到底够不够格发动。")
                    .push("prep");

            this.prepSeconds = builder
                    .comment("准备阶段持续多少秒后转入攻击。1 ~ 600，默认 60。",
                            "**这一档决定玩家有多少反应时间**：准备阶段唯一的可见信号就是开场那句台词，",
                            "以及心智开始「全力运转」（资源、发育加成）。真打起来是攻击阶段的事。")
                    .defineInRange("seconds", 60, 1, 600);

            this.prepMaxSeconds = builder
                    .comment("准备阶段最多拖多少秒。1 ~ 3600，默认 180。",
                            "到点还没凑够发动条件（见下面三项）就**取消**这次袭击，而不是硬打。",
                            "取值必须 >= raid.prep.seconds，否则会被自动抬到 raid.prep.seconds——",
                            "不然「准备期一结束就超时」会让袭击永远发动不了。")
                    .defineInRange("maxSeconds", 180, 1, 3600);

            this.launchMinBiomass = builder
                    .comment("发动条件之一：至少有一只心智的资源达到这个数。0 ~ 100000，默认 200。",
                            "注意准备期间资源获取是翻倍的（见 raid.bonus），所以这个门槛通常几十秒就能攒到。")
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

            this.launchCheckIntervalTicks = builder
                    .comment("准备阶段每隔多少 tick 检查一次发动条件。1 ~ 200，默认 20（1 秒）。",
                            "检查要扫玩家周围的真菌并按等级表算质量，所以它同时是这一段的主要开销来源。",
                            "调小会让「条件一满足就发动」更灵敏，代价是每秒多扫几次。")
                    .defineInRange("launchCheckIntervalTicks", 20, 1, 200);

            builder.pop();

            // ------------------------------------------------------------------
            // raid.bonus —— 准备 / 攻击期间心智变强多少
            // ------------------------------------------------------------------
            builder.comment("袭击给心智的加成。全部作用在 Spore 自己的节拍与资源入口上，",
                            "所以关掉（填 1.0）就是完全原样——可以把这一整张表当成「袭击强度」的总旋钮。",
                            "**前缀区分阶段**：prep* 只在准备阶段生效，attack* 只在攻击阶段生效。")
                    .push("bonus");

            this.prepResourceCostMultiplier = builder
                    .comment("准备期间心智的资源**消耗**倍率。0.0 ~ 1.0，默认 0.5（需求：降低 50%）。",
                            "作用于 Proto.eatBiomass 的入参，也就是每次召唤、每次铺菌毯要花的量。")
                    .defineInRange("prepResourceCostMultiplier", 0.5D, 0.0D, 1.0D);

            this.prepResourceGainMultiplier = builder
                    .comment("准备期间心智的资源**获取**倍率。1.0 ~ 10.0，默认 2.0（需求：提高 100%）。",
                            "作用于 Proto.addBiomass 的入参。")
                    .defineInRange("prepResourceGainMultiplier", 2.0D, 1.0D, 10.0D);

            this.prepGrowthSpeedMultiplier = builder
                    .comment("准备期间心智的**发育速度**倍率。1.0 ~ 5.0，默认 1.5（需求：提高 50%）。",
                            "实现方式是缩短「生成菌壳」那个分支的节拍间隔（默认 200 tick ÷ 1.5 ≈ 133 tick），",
                            "于是同样的时间里它会铺开更多感染区。",
                            "**刻意只作用于发育那一支**，不动被动资源收入与召唤节拍——",
                            "否则会和上面的「资源获取 ×2」叠成一个说不清的倍数。")
                    .defineInRange("prepGrowthSpeedMultiplier", 1.5D, 1.0D, 5.0D);

            this.attackManufactureSpeedMultiplier = builder
                    .comment("攻击期间心智的**制造**（召唤真菌生物）速度倍率。1.0 ~ 20.0，默认 6.0。",
                            "需求：袭击全过程中制造速度加快 500%，也就是 6 倍。",
                            "作用的是 Proto 召唤那个分支的节拍常量 200 tick（默认 200 ÷ 6 ≈ 33 tick 一次）。",
                            "**与 prepGrowthSpeedMultiplier 是两回事**：那一项管的是生成菌壳（铺感染区），",
                            "这一项管的是造兵；Spore 在同一个 tick 里用三个不同的节拍分别管这两件事。",
                            "填 1.0 = 与 Spore 原样。")
                    .defineInRange("attackManufactureSpeedMultiplier", 6.0D, 1.0D, 20.0D);

            this.attackDespawnCapMultiplier = builder
                    .comment("攻击期间 Despawning System 上限的倍率。1.0 ~ 10.0，默认 2.0（需求：提高 100%）。",
                            "作用于 Spore 自己那几个 max_*_cap 配置项算出来的上限。",
                            "**只在攻击阶段生效**：准备阶段就把上限抬高会让袭击还没开始就先把怪堆满。")
                    .defineInRange("attackDespawnCapMultiplier", 2.0D, 1.0D, 10.0D);

            builder.pop();

            // ------------------------------------------------------------------
            // raid.attack —— 兵怎么送到玩家面前
            // ------------------------------------------------------------------
            builder.comment("攻击阶段：把真菌「调」到玩家身边，并给它们挂上增益。",
                            "注意这是**调动已有的**真菌，不是凭空生成——真正的上限来自世界本身。")
                    .push("attack");

            this.teleportMinDistance = builder
                    .comment("传送落点距目标玩家的**最近**距离（格）。1.0 ~ 128.0，默认 12.0。",
                            "需求里的「不会过于贴近」：太近会直接怼到脸上，没有反应时间。")
                    .defineInRange("teleportMinDistance", 12.0D, 1.0D, 128.0D);

            this.teleportMaxDistance = builder
                    .comment("传送落点距目标玩家的**最远**距离（格）。1.0 ~ 256.0，默认 32.0。",
                            "需求里的「不会过于远离」：太远等于没传送。",
                            "必须 >= teleportMinDistance，否则会被自动抬平。")
                    .defineInRange("teleportMaxDistance", 32.0D, 1.0D, 256.0D);

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
                    .defineList("buffs", DEFAULT_RAID_BUFFS, o -> o instanceof String);

            this.absenceTimeoutSeconds = builder
                    .comment("玩家躲进**黑名单维度**后，最多允许缺席多少秒才判失败。1 ~ 3600，默认 300。",
                            "躲进去不会立刻结束袭击：整场袭击（含阶段计时）会**冻结**，只累计缺席时间，",
                            "玩家回到允许的维度就归零继续打——所以出去一下再回来不算数。",
                            "逾期不归才判**玩家失败**，走「死于真菌之手」那条线（削恨意值 + 资源给心智）。",
                            "**冻结阶段计时是必须的**：不停的话，躲进下界的玩家几秒后就会被",
                            "raid.arena.timeoutSeconds 判失败，等于「躲一下就直接输」，",
                            "与这里的 300 秒窗口自相矛盾。")
                    .defineInRange("absenceTimeoutSeconds", 300, 1, 3600);

            builder.pop();

            // ------------------------------------------------------------------
            // raid.arena —— 竞技之须
            // ------------------------------------------------------------------
            builder.comment("竞技之须阶段：按概率在玩家附近种一根 spore:arena_tendril，出怪交给它自己。",
                            "它的玩法与下面的我方围剿是两块独立内容，可以单独关掉（概率填 0）。")
                    .push("arena");

            this.arenaChanceBase = builder
                    .comment("竞技之须出现概率的**基准**。0.0 ~ 1.0，默认 0.2。",
                            "需求：恨意值越高，竞技之须出现概率越大。",
                            "所以实际概率 = 基准 + 恨意档位 × raid.arena.chancePerTier，再被 raid.arena.chanceMax 夹住。",
                            "「档位」就是 raid.trigger.thresholdStep 切出来的那个序号。",
                            "填 0 且 raid.arena.chancePerTier 也填 0 = 完全不出现竞技之须，直接打我方围剿。")
                    .defineInRange("chanceBase", 0.2D, 0.0D, 1.0D);

            this.arenaChancePerTier = builder
                    .comment("恨意档位每高一级，竞技之须的出现概率加多少。0.0 ~ 1.0，默认 0.1。")
                    .defineInRange("chancePerTier", 0.1D, 0.0D, 1.0D);

            this.arenaChanceMax = builder
                    .comment("竞技之须出现概率的上限。0.0 ~ 1.0，默认 0.9。",
                            "刻意默认不给到 1.0：留一成「这次没有触须」，让高恨意值的袭击仍有变化。",
                            "低于 raid.arena.chanceBase 时会被自动抬到基准。")
                    .defineInRange("chanceMax", 0.9D, 0.0D, 1.0D);

            this.arenaWaveSize = builder
                    .comment("种给竞技之须的初始**波次规模**。0 ~ 100，默认 6。",
                            "它的规模决定每次出怪几只（>3 时随机 1~4 只），等级决定从哪张表出怪。",
                            "**注意它会自己往上加**：竞技之须每 40 tick 按附近玩家的护甲/生命/背包重新算一遍，",
                            "所以这里给的是「起步值」，不是上限——这正是它「竞技」的地方。")
                    .defineInRange("waveSize", 6, 0, 100);

            this.arenaWaveLevel = builder
                    .comment("种给竞技之须的初始**波次等级**。0 ~ 2，默认 1。",
                            "0/1/2 分别对应 Spore 自己的 Raid level 1/2/3 出怪表（在它自己的配置里）。",
                            "等级 ≥ 2 时它还会开始丢 FleshBomb。")
                    .defineInRange("waveLevel", 1, 0, 2);

            this.arenaTimeoutSeconds = builder
                    .comment("竞技之须阶段最长持续多少秒。1 ~ 3600，默认 300。",
                            "它自己会在「场上真菌少于 4 只」时缩回消失——那是**通过**，之后接着打我方围剿波次。",
                            "这一项管的是另一种收场：到点还没清场，就判**玩家失败**，整场袭击到此结束，",
                            "并按「死于真菌之手」那条线结算——削他的恨意值、把损失换算成资源给所有心智",
                            "（用的就是 death 段那两个数，不另开一套）。",
                            "**它同时堵了一个漏洞**：不判失败的话，玩家只要跑远（竞技之须扫不到宿主、",
                            "波次不再增长），就能白等到超时再继续打我方波次，等于整段挑战被跳过。")
                    .defineInRange("timeoutSeconds", 300, 1, 3600);

            this.warnLeadSeconds = builder
                    .comment("「恐惧吧」比**竞技之须出现**提前多少秒发出。0 ~ 120，默认 10（需求写的就是 10 秒）。",
                            "注意基准是「竞技之须出现」那一刻，不是「准备阶段开始」。",
                            "它属于竞技之须这一块，所以放在 arena 而不是 prep：不出现触须就没有这句话。",
                            "准备阶段本身也有一句台词（message.spore_add.raid_declare），",
                            "所以玩家的体感是：先被告知有人盯上他，再过一会儿听到第二句，然后触须钻出来。")
                    .defineInRange("warnLeadSeconds", 10, 0, 120);

            builder.pop();

            // ------------------------------------------------------------------
            // raid.ownWave —— 我方围剿波次
            // ------------------------------------------------------------------
            builder.comment("我方围剿：竞技之须收场之后，由心智直接派波次上来。",
                            "波数取决于恨意档位，每波的数量随波次线性增长（**没有人为上限**）。")
                    .push("ownWave");

            this.ownWaveBase = builder
                    .comment("我方围剿波次的**基础**数量。0 ~ 50，默认 1。",
                            "需求：最后几波是我们自己的袭击，波次数取决于恨意值大小。",
                            "实际波数 = 基础 + 恨意档位 × raid.ownWave.perTier，被 raid.ownWave.max 夹住。")
                    .defineInRange("base", 1, 0, 50);

            this.ownWavePerTier = builder
                    .comment("恨意档位每高一级，我方波次多几波。0 ~ 10，默认 2。")
                    .defineInRange("perTier", 2, 0, 10);

            this.ownWaveMax = builder
                    .comment("我方波次数量的上限。0 ~ 50，默认 10。",
                            "低于 raid.ownWave.base 时会被自动抬到基础。")
                    .defineInRange("max", 10, 0, 50);

            this.ownWaveCountBase = builder
                    .comment("我方第 1 波送过去几只。1 ~ 200，默认 3。",
                            "需求改成了「每波无上限、数量与波次有关」，所以这里与下一项一起构成一条公式：",
                            "  第 N 波的数量 = 本项 + (N-1) × raid.ownWave.countPerWave",
                            "**没有人为的每波上限**。真正的上限来自世界：能调动的真菌只限于",
                            "raid.attack.teleportSearchRadius 内已有的那些，而它们的总数又受 Spore 的 Despawn 上限约束",
                            "（那个上限在攻击期间被 raid.bonus.attackDespawnCapMultiplier 翻倍了）。")
                    .defineInRange("countBase", 3, 1, 200);

            this.ownWaveCountPerWave = builder
                    .comment("每往后一波多送几只。0 ~ 100，默认 2。",
                            "默认下：第 1 波 3 只、第 5 波 11 只、第 10 波 21 只（上限 10 波）。")
                    .defineInRange("countPerWave", 2, 0, 100);

            this.ownWaveSeconds = builder
                    .comment("我方每一波**最多**等多少秒。1 ~ 600，默认 60。",
                            "**这不是固定时长**：正常情况下这一波什么时候结束由「清完了没有」决定——",
                            "玩家周围 raid.ownWave.clearRadius 内的真菌少于 raid.ownWave.clearCount 只就提前进下一波。",
                            "本项只在清不掉时兜底：没有它，一波打不完的仗会让整场袭击永远停在那里。")
                    .defineInRange("seconds", 60, 1, 600);

            this.ownWaveClearRadius = builder
                    .comment("判断「这一波清完了没有」时统计真菌的半径（格）。4.0 ~ 128.0，默认 32.0。",
                            "**故意比 raid.prep.gatherRadius 小**：那一项管的是「附近有没有值得一战的菌群」，",
                            "而这一项管的是「我眼前这片打完了没有」。范围太大会把远处游荡的真菌也算进来，",
                            "于是玩家明明清完了却迟迟不进下一波。")
                    .defineInRange("clearRadius", 32.0D, 4.0D, 128.0D);

            this.ownWaveClearCount = builder
                    .comment("玩家周围 raid.ownWave.clearRadius 内的真菌少于这个数，就算这一波清完了。0 ~ 64，默认 4。",
                            "需求写的是「被清到 <4」，所以默认 4 表示剩 0~3 只时进入下一波。",
                            "填 0 表示必须一只不剩——那在小怪散开时很难达成，通常会让每波都走超时。")
                    .defineInRange("clearCount", 4, 0, 64);

            this.ownWaveBuffsPerWave = builder
                    .comment("每往后一波，参战增益的 amplifier 加多少。0 ~ 10，默认 1。",
                            "需求：波次越高，真菌越强。第 3 波时 raid.attack.buffs 里的每个效果都会 +2 级。",
                            "amplifier 有 127 的字节上限，所以配得很大时会自动封顶（见实现）。")
                    .defineInRange("buffsPerWave", 1, 0, 10);

            builder.pop();

            // ------------------------------------------------------------------
            // raid.ownLoot —— 我方围剿的战利品
            // ------------------------------------------------------------------
            builder.comment("打赢我方围剿波次之后的奖励。**只有这一块有奖励**——",
                            "竞技之须那一段的掉落走 Spore 自己的 drops 表，两者互不干扰。")
                    .push("ownLoot");

            this.ownLoot = builder
                    .comment("我方战利品表，格式 \"物品id|最少|最多\"，可写多行。",
                            "**只在我方围剿波次打完时掷**；竞技之须那一段的奖励走 Spore 自己的 drops 表，",
                            "两者互不干扰（这也是需求里说「各自用自己的表」的意思）。",
                            "例：[\"spore:living_core|1|1\", \"minecraft:diamond|1|2\"]",
                            "写坏的条目会被跳过并在日志里留一行警告。")
                    .defineList("table", DEFAULT_OWN_LOOT, o -> o instanceof String);

            this.ownLootRollsPerWave = builder
                    .comment("战利品表里每一条独立掷多少次（再乘以波次档位）。0 ~ 100，默认 1。",
                            "需求：波次越高，完成后奖励越丰厚。",
                            "实际掷骰次数 = 波数 × 本项，所以默认下 5 波就是每一条掷 5 次。")
                    .defineInRange("rollsPerWave", 1, 0, 100);

            this.ownLootRadius = builder
                    .comment("我方战利品掉在离玩家多远的范围内（格）。1.0 ~ 64.0，默认 6.0。",
                            "掉太远玩家会找不到，掉在脚下又会和已有的东西堆在一起。")
                    .defineInRange("radius", 6.0D, 1.0D, 64.0D);

            builder.pop();

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
        private final ForgeConfigSpec.DoubleValue defaultValue;
        private final ForgeConfigSpec.DoubleValue defaultChance;
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

            this.defaultValue = builder
                    .comment("**数据包里的转化表**没列到的物品值多少资源。0.0 ~ 1000.0，默认 1.0。",
                            "转化表已经搬到数据包了（data/<命名空间>/loot_values/*.json，一个文件一条），",
                            "这里两条只负责「没列到的物品」的兜底定价。",
                            "默认给 1.0 而不是 0 是刻意的：需求是「所有掉落物全部纳入价值单」，",
                            "而物品来自安装的所有模组、不可能逐个列完，所以用默认值兜底，让没有例外。",
                            "**想排除某类东西请在数据包里加一条 loot_blacklist，而不是把这一项调回 0**——",
                            "调回 0 会让所有没列到的物品统统不捡，等于把那张表变回白名单。")
                    .defineInRange("defaultValue", 1.0D, 0.0D, 1000.0D);

            this.defaultChance = builder
                    .comment("转化表里没列到的物品，捡起来之后的转化概率。0.0 ~ 1.0，默认 1.0。",
                            "与上面那条一起构成「没列到的物品」的定价：价值 1.0、必然转化。",
                            "写成 0.35 就是 35%。把这一项调小可以让未列物品变成「捡了也多半白捡」，",
                            "但通常更清楚的做法是直接给那类物品写一条带概率的表项。")
                    .defineInRange("defaultChance", 1.0D, 0.0D, 1.0D);

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
        private final ForgeConfigSpec.BooleanValue sweepEnabled;
        private final ForgeConfigSpec.IntValue sweepMinHiveminds;
        private final ForgeConfigSpec.IntValue sweepIntervalMinutes;
        private final ForgeConfigSpec.DoubleValue sweepChance;
        private final ForgeConfigSpec.DoubleValue sweepMultiplier;
        private final ForgeConfigSpec.DoubleValue sweepPerExtraHivemind;

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

            // ------------------------------------------------------------------
            // 清扫世界（心智的联合能力）
            // ------------------------------------------------------------------
            builder.comment("清扫世界：某个维度里心智够多时，它们会联合起来把整片地方扫一遍。",
                            "触发是三重的：**该维度里的心智数 >= sweepMinHiveminds**、周期到点、",
                            "且掷中 sweepChance。三条都满足才发动，所以它是一件稀有事。",
                            "发动时的效果是**把这个维度里所有掉落物收走、折算成资源**，",
                            "资源按心智数均分给它们（走与其它资源同一个出口）。")
                    .push("sweep");

            this.sweepEnabled = builder
                    .comment("总开关。默认开。")
                    .define("enabled", true);

            this.sweepMinHiveminds = builder
                    .comment("一个维度里至少要有几只心智才可能发动。1 ~ 64，默认 3（需求写的就是三只）。",
                            "**这条闸门的作用是限制它出现在心智成气候的地方**——",
                            "世界早期只有一两只心智时，这条能力根本不会触发。")
                    .defineInRange("minHiveminds", 3, 1, 64);

            this.sweepIntervalMinutes = builder
                    .comment("每隔多少分钟判定一次。1 ~ 1440，默认 20（需求写的就是 20 分钟）。",
                            "**这是判定周期、不是发动周期**：每次到点还要掷一次 sweepChance。",
                            "用游戏刻的取模实现，所以不存盘、也不怕重启——",
                            "重启后依 gameTime 继续走同一个节奏。")
                    .defineInRange("intervalMinutes", 20, 1, 1440);

            this.sweepChance = builder
                    .comment("到点时发动清扫的概率。0.0 ~ 1.0，默认 0.5（需求写的就是 50%）。",
                            "填 0 = 永不发动（等于关掉），填 1 = 到点必发动。")
                    .defineInRange("chance", 0.5D, 0.0D, 1.0D);

            this.sweepMultiplier = builder
                    .comment("清扫时的**基础**转化倍率。0.0 ~ 100.0，默认 2.0。",
                            "**清扫不掷转化概率**——它是这样一次性的收场，",
                            "所以总资源 = 掉落物满价值之和 × 本倍率（再按下面那条随心智数上浮）。",
                            "之所以说它「倍率更高」就是相对于逐件捡：那一路还要过一遍 chance 掷骰。")
                    .defineInRange("multiplier", 2.0D, 0.0D, 100.0D);

            this.sweepPerExtraHivemind = builder
                    .comment("每多一只（超出下限的）心智，倍率再加多少。0.0 ~ 100.0，默认 0.5。",
                            "实际倍率 = multiplier + (心智数 - minHiveminds) × 本项。",
                            "默认下：3 只 2.0 倍、5 只 3.0 倍、10 只 5.5 倍。",
                            "填 0 = 倍率不随心智数变化。")
                    .defineInRange("perExtraHivemind", 0.5D, 0.0D, 100.0D);

            builder.pop();

            builder.pop();
        }
    }
}
