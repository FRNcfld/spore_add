package com.frnc.spore_add;

import java.util.Locale;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * <b>调试侧</b>的配置，写进 {@code config/spore_add-debug-common.toml}。
 *
 * <h2>它是什么</h2>
 * 一份<b>默认全关</b>的检查点开关。打开之后，真菌袭击、心智存储、拾荒者、灾厄重构体这些
 * 子系统里原本静默的分支会开始往日志里打决策过程——"准备阶段差多少才发动""这次掷骰没中"
 * "存储投放为什么返回 0"这类问题，不必再读代码猜。
 *
 * <p>用法只有一条：<b>先开总闸，再按分组收窄</b>。总闸（{@code debug.enabled}）关着时
 * 下面九个分组开关一律不生效，所以默认生成的文件里除总闸外全是 {@code true}——
 * 把它们全设成 {@code false} 并不会让日志更安静，那件事只有总闸能做。
 *
 * <h2>为什么是第三份文件，而不是塞进现有两份</h2>
 * 现有两份是按<b>「谁受益」</b>分的（玩家侧 / 真菌侧）。检查点不属于任何一边——
 * 它既不让人更强也不让怪更强，它只是排查工具；而且它<b>横跨</b>两边（冰霜新星与袭击都要插桩）。
 * 硬塞进任意一份，都会让那一份的划分标准出现一个说不通的例外。
 * 单独一份还有个实际好处：交付给玩家时，把这一份的 {@code enabled} 保持默认即可，
 * 想彻底不看见它，删掉文件就行。
 *
 * <h2>为什么默认关</h2>
 * 这些日志是<b>给排查用的</b>，不是给玩家看的。一场袭击 + 一群拾荒者同时刷屏，
 * 正常游玩时会把真正重要的那几行淹没。默认关着还有一个硬要求：
 * <b>配置不动时，日志输出必须与加这套设施之前逐行一致</b>——这是"默认关闭"的验收标准。
 *
 * <h2>为什么文件名是手写的</h2>
 * 与另两份同一个理由：Forge 默认按 {@code modId-类型} 拼名，三份 {@code COMMON}
 * 会算出同一个 {@code spore_add-common.toml}，而 {@code ConfigTracker} 撞名会直接抛
 * {@code "Config conflict detected!"} 把游戏崩掉。
 *
 * <h2>为什么命令能改这份配置，而玩法数值没有这种入口</h2>
 * 排一个现场问题时，最忌"改文件 → 重启游戏 → 问题已经复现不出来了"。检查点要的正是
 * <b>当场就能开</b>，所以 {@code /spore_add debug} 会直接写这份配置（{@code set} + {@code save}，
 * 见 {@link #setArea}）。玩法数值不这么做的理由是相反的：那些值改一下就要重启才公平，
 * 而且不该在游戏里被随手改掉。
 *
 * <p>配置值一律不在静态初始化器里读取：{@link #SPEC} 的静态块只<b>声明</b>各项，
 * 真正的 {@code get()} 全部发生在访问器里（游戏运行期），这样与配置何时加载无关。
 * 与另两份同一个规矩。
 */
public final class SporeAddDebugConfig {

    /** 配置文件全名（含扩展名）。见类注释「为什么文件名是手写的」。 */
    public static final String FILE_NAME = "spore_add-debug-common.toml";

    /** 配置规格。主类构造时注册。 */
    public static final ForgeConfigSpec SPEC;

    /**
     * {@code debug} 段的全部键（见文件末尾的 {@link Debug}）。
     *
     * <p>在静态块里赋值。之所以不写成 {@code public}，也不给每个键单独开一个
     * {@code public static final BooleanValue} 字段：那十个字段是**实现细节**，
     * 对外只有 {@link #enabled()} / {@link #area} / {@link #setEnabled} / {@link #setArea}
     * 四个入口。少一层暴露，就少一种"绕过总闸直接读某个分组"的写法。
     */
    private static final Debug DEBUG;

    /**
     * 检查点的分组。
     *
     * <p>分组按<b>排查时你想一次看见多少</b>来切，不按代码目录切——两者的差别在多处都出现过：
     * {@code WOMB} 的代码一半在 {@code fungus/}、一半在 {@code mixin/}，而 {@code COLD}
     * 横跨 {@code fluid/}、{@code block/}、{@code entity/}、{@code world/} 四个包。
     * 按目录切的话，"我想看看重构体到底怎么了"就得同时开三四个开关。
     *
     * <p>{@link #key()} 是它在 toml 里的键名，也是 {@code /spore_add debug <分组>}
     * 接受的那个词——两者<b>必须</b>是同一个字符串，否则玩家照着配置改却敲不出命令。
     */
    public enum Area {

        /** 真菌加强：进化加速、猎杀判定、感知范围、抗寒、冰冻伤害倍率。 */
        FUNGUS("fungus"),

        /** 灾厄重构体：同化突变、孵化闸门、出壳搬移、心智出资补突变。 */
        WOMB("womb"),

        /** 拾荒者：生成转变、逃跑判据、拾荒评分、交付链、存量上限、死后掉落。 */
        SCAVENGER("scavenger"),

        /** 恨意值：击杀取值、越档掷骰、世界减伤、给玩家的增益。 */
        HATRED("hatred"),

        /** 真菌袭击：阶段机、发动条件、竞技之须、波次。 */
        RAID("raid"),

        /** 心智存储：收进存储、投放、清扫世界、穹顶漏出。 */
        HIVEMIND("hivemind"),

        /** 资源：转化表命中、捡起掷骰、回收与暂存。 */
        LOOT("loot"),

        /** 玩家侧的冰霜内容：冰霜新星、冰雪的叹息、液态寒冷与冷却液。 */
        COLD("cold"),

        /**
         * <b>只</b>打 mixin 注入点自己的日志。
         *
         * <p>单独一组的理由：本工程最常见的排查方向就是「注入到底有没有生效」——
         * 它的失效方式是不报错、不生效。把它与业务日志混在一起的话，
         * 想确认一次注入得先忍受整个子系统的刷屏。
         */
        MIXIN("mixin");

        private final String key;

        Area(String key) {
            this.key = key;
        }

        /** 本组在 toml 里的键名，也是命令里用的那个词。 */
        public String key() {
            return key;
        }

        /**
         * 按键名找分组，供命令解析用。
         *
         * @return 找不到时返回 {@code null}——命令那边要把"没这个分组"与"分组存在但没开"
         *         分开回话，所以这里不用默认值兜底
         */
        public static Area byKey(String key) {
            String normalized = key.toLowerCase(Locale.ROOT);
            for (Area area : values()) {
                if (area.key.equals(normalized)) {
                    return area;
                }
            }
            return null;
        }
    }

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // 写进 toml 开头的总说明。类注释里那些话玩家看不到，所以这里必须再说一遍。
        builder.comment(
                "Spore Add —— 调试检查点。",
                "",
                "这一份默认全关。打开之后，真菌袭击 / 心智存储 / 拾荒者 / 灾厄重构体这些子系统",
                "会往日志里打决策过程，用来排查「为什么没触发」「为什么没生效」。",
                "",
                "用法：先开 debug.enabled（总闸），再按下面的分组收窄。",
                "总闸关着时下面每一项都不生效——把它们全设成 false 并不会更安静，那件事只有总闸能做。",
                "",
                "也可以在游戏里改：/spore_add debug on|off、/spore_add debug <分组> on|off",
                "（需要权限等级 2）。命令会直接写回本文件，当场生效、重启后仍在。",
                "",
                "本文件的段落：",
                "  debug   检查点开关：一个总闸 + 九个分组（fungus / womb / scavenger / hatred /",
                "          raid / hivemind / loot / cold / mixin）");

        DEBUG = new Debug(builder);
        SPEC = builder.build();
    }

    private SporeAddDebugConfig() {
    }

    /**
     * 总闸：关着时 {@link #area} 一律返回 false。
     *
     * <p><b>未加载时返回 false，而不是去读配置</b>——这一个判断是整个设施唯一的兜底，
     * 理由是 Forge 的 {@code ConfigValue#get()} 在<b>开发环境</b>下配置尚未加载时会
     * 直接抛 {@code IllegalStateException}（生产环境才退回默认值）。本 mod 的开发与发布
     * 都在客户端跑，不兜的话踩中的表现是"启动即崩"而不是"日志没出来"。
     *
     * <p>兜在总闸这一处就够了：{@link #area} 只在总闸为 true 之后才会被读到，
     * 而总闸为 true 蕴含配置已加载。放在每一项里重复十遍没有意义。
     */
    public static boolean enabled() {
        return SPEC.isLoaded() && DEBUG.enabled.get();
    }

    /** 命令用：写总闸。会立即落盘，见 {@link #setArea} 的说明。 */
    public static void setEnabled(boolean value) {
        DEBUG.enabled.set(value);
        DEBUG.enabled.save();
    }

    /**
     * 某个分组当前是否输出。
     *
     * <p><b>不</b>含总闸判断——总闸在 {@code SporeAddDebug.on} 里与它合起来看。
     * 分开是有意的：命令要能把九个分组的开关状态<b>原样</b>念出来，
     * 那时若已经与总闸相与过，玩家会看到九个 false 却不知道是自己关的还是被总闸压着的。
     */
    public static boolean area(Area area) {
        // 走 handle 是为了让"枚举 → 配置项"的对应只写一处。
        // 分成两个 switch 的话，加一个分组时改了一处、漏了另一处，症状是
        // "命令能改但日志不跟着变"——很难看出是哪里漏了。
        return handle(area).get();
    }

    /**
     * 命令用：写某个分组的开关。
     *
     * <p>{@code set} 只改内存（Forge 的 {@code ConfigValue#get} 读的就是它，所以当场生效），
     * {@code save} 才落盘。两个都要做：少了 {@code save}，玩家在游戏里开的检查点重启就没了；
     * 少了 {@code set}，得重启才生效——而"当场就能开"正是这个命令存在的理由。
     *
     * <p>之所以敢让命令直接改这份文件：它改的是<b>日志开关</b>，不是玩法数值。
     * 玩法数值没有这种入口的理由写在类注释里。
     */
    public static void setArea(Area area, boolean value) {
        // 局部变量不叫 handle：它会与上面的 handle(area) 同名（Java 的变量与方法分属两个命名空间，
        // 编译得过，但读的人要在脑子里绕一圈才知道这行不是在递归）。
        ForgeConfigSpec.BooleanValue option = handle(area);
        option.set(value);
        option.save();
    }

    /** 取出某个分组背后的配置项，只给 {@link #setArea} 与 {@link #area} 用。 */
    private static ForgeConfigSpec.BooleanValue handle(Area area) {
        return switch (area) {
            case FUNGUS -> DEBUG.fungus;
            case WOMB -> DEBUG.womb;
            case SCAVENGER -> DEBUG.scavenger;
            case HATRED -> DEBUG.hatred;
            case RAID -> DEBUG.raid;
            case HIVEMIND -> DEBUG.hivemind;
            case LOOT -> DEBUG.loot;
            case COLD -> DEBUG.cold;
            case MIXIN -> DEBUG.mixin;
        };
    }

    /**
     * {@code debug} 段的全部键。
     *
     * <p>九个分组全是 {@code true}——它们只是<b>过滤器</b>，真正的闸门是总闸。
     * 这与另两份配置的直觉相反（那里 {@code false} 到处都是），所以特意写在每一项的注释里。
     */
    private static final class Debug {

        private final ForgeConfigSpec.BooleanValue enabled;

        private final ForgeConfigSpec.BooleanValue fungus;
        private final ForgeConfigSpec.BooleanValue womb;
        private final ForgeConfigSpec.BooleanValue scavenger;
        private final ForgeConfigSpec.BooleanValue hatred;
        private final ForgeConfigSpec.BooleanValue raid;
        private final ForgeConfigSpec.BooleanValue hivemind;
        private final ForgeConfigSpec.BooleanValue loot;
        private final ForgeConfigSpec.BooleanValue cold;
        private final ForgeConfigSpec.BooleanValue mixin;

        private Debug(ForgeConfigSpec.Builder builder) {
            builder.comment("检查点开关。默认全关——总闸关着时下面每一个分组都不生效。",
                            "这些日志是给排查用的。正常游玩时留着关闭，日志才不会被淹没。")
                    .push("debug");

            this.enabled = builder
                    .comment("总闸。**默认关**。",
                            "关着时下面九个分组一个都不输出，且判定是一次短路。",
                            "打开后默认九个分组全开（它们默认就是 true）——想只看其中一类，再往下关。",
                            "**不改这一项时，日志输出与没有这套设施之前逐行一致**，这是刻意的。")
                    .define("enabled", false);

            this.fungus = builder
                    .comment("真菌加强：进化加速、猎杀判定、感知范围、抗寒、冰冻伤害倍率。默认开（但受总闸约束）。")
                    .define("fungus", true);

            this.womb = builder
                    .comment("灾厄重构体：同化突变、孵化闸门、出壳搬移、心智出资补突变。默认开（但受总闸约束）。")
                    .define("womb", true);

            this.scavenger = builder
                    .comment("拾荒者：生成转变、逃跑判据、拾荒评分、交付链、存量上限、死后掉落。默认开（但受总闸约束）。")
                    .define("scavenger", true);

            this.hatred = builder
                    .comment("恨意值：击杀取值、越档掷骰、世界减伤、给玩家的增益。默认开（但受总闸约束）。")
                    .define("hatred", true);

            this.raid = builder
                    .comment("真菌袭击：阶段机、发动条件、竞技之须、波次。默认开（但受总闸约束）。")
                    .define("raid", true);

            this.hivemind = builder
                    .comment("心智存储：收进存储、投放、清扫世界、穹顶漏出。默认开（但受总闸约束）。")
                    .define("hivemind", true);

            this.loot = builder
                    .comment("资源：转化表命中、捡起掷骰、回收与暂存。默认开（但受总闸约束）。")
                    .define("loot", true);

            this.cold = builder
                    .comment("玩家侧的冰霜内容：冰霜新星、冰雪的叹息、液态寒冷与冷却液。默认开（但受总闸约束）。")
                    .define("cold", true);

            this.mixin = builder
                    .comment("只打 mixin 注入点自己的日志。默认开（但受总闸约束）。",
                            "单独一组是因为本工程最常见的排查方向就是「注入到底有没有生效」——",
                            "它的失效方式是不报错、不生效。混在业务日志里的话，",
                            "想确认一次注入得先忍受整个子系统的刷屏。")
                    .define("mixin", true);

            builder.pop();
        }
    }
}
