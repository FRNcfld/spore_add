package com.frnc.spore_add.command;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.frnc.spore_add.SporeAddDebugConfig;
import com.frnc.spore_add.SporeAddDebugConfig.Area;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /spore_add debug} —— 在游戏里开关检查点。挂在 {@link SporeAddCommands} 的根上。
 *
 * <h2>为什么需要它</h2>
 * 检查点的用途是排现场问题：触发条件差多少、这次掷骰为什么没中、存储为什么没投出来。
 * 这些都要<b>在事情发生的那一刻</b>看到日志。而"改配置文件 → 重启游戏 → 再复现一次"
 * 这条路最大的问题是——问题往往已经复现不出来了。所以给一个当场能翻的开关。
 *
 * <h2>命令改的是配置文件，不是运行时状态</h2>
 * 三条命令做的都是 {@link SporeAddDebugConfig#setEnabled} / {@code setArea}，
 * 也就是 {@code set} + {@code save}：<b>当场生效，并且写回 toml</b>。
 *
 * <p>为什么不做成"只在内存里覆盖、重启即失效"：那样配置文件和实际行为会长期不一致——
 * 玩家重启后发现开关自己回去了，而 toml 上明明写着 false，只会以为命令坏了。
 * 让文件保持唯一事实来源，命令输出里那句「已写入配置文件」就是一句可核对的话。
 *
 * <h2>三个分支</h2>
 * <pre>
 * /spore_add debug                 看总闸与九个分组的当前状态
 * /spore_add debug on|off          总闸
 * /spore_add debug &lt;分组&gt; on|off   单个分组
 * </pre>
 *
 * <p>看状态不需要权限（与 {@code /spore_add hatred} 一致），改开关要权限等级 2。
 *
 * <p>状态里打印的是 {@link Area#name()} 的<b>原样大写</b>（{@code RAID}、{@code HIVEMIND}…），
 * 不是中文名。理由：日志前缀里就是同一个 token，玩家从这里复制出来可以直接
 * {@code grep "[SporeAdd][RAID]"}，不必再对照一次"raid 是哪个中文名"。
 */
public final class DebugCommand {

    private DebugCommand() {
    }

    /** 权限等级 2 = /gamemode 那一档。用字面量而不是 Commands.LEVEL_* 常量，理由见 HatredCommand。 */
    private static final int REQUIRED_PERMISSION = 2;

    /**
     * 命令树。
     *
     * <pre>
     * debug                看状态
     * debug on|off         总闸（字面量）
     * debug &lt;分组&gt; on|off  分组（先参数、后开关）
     * </pre>
     *
     * <p>分组名用 {@code word()} + 补全，而不是九个 literal：加一个分组时不必再手写九个节点。
     * 代价是补全<b>不能代替校验</b>，所以查不到分组时要自己报错并列出可用的（见 {@link #areaSwitch}）。
     *
     * <p>「{@code debug on}」与「{@code debug <分组>}」在解析上有歧义（{@code on} 也能当一个分组名），
     * 靠 Brigadier 的规矩化解：<b>同层里字面量优先于参数</b>。所以 {@code debug on} 一定走总闸，
     * 而 {@code debug raid on} 里的 {@code raid} 没有同名字面量，才会落到参数上。
     * 九个分组的键名因此不能叫 {@code on} / {@code off}。
     */
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug")
                // 不带参数：念状态。谁都能看。
                .executes(context -> {
                    show(context.getSource());
                    return 1;
                })
                .then(masterNode("on", true))
                .then(masterNode("off", false))
                .then(Commands.argument("area", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (Area area : Area.values()) {
                                builder.suggest(area.key());
                            }
                            return builder.buildFuture();
                        })
                        .then(areaNode("on", true))
                        .then(areaNode("off", false)));
    }

    /**
     * 造一个「总闸开/关」叶子。
     *
     * <p>开关值靠闭包传进去，不去反查"当前解析到的是 on 还是 off"——
     * 那种写法要遍历解析出来的节点名，命令树一改就悄悄指错方向，而且错得没有声音。
     */
    private static LiteralArgumentBuilder<CommandSourceStack> masterNode(String literal, boolean value) {
        return Commands.literal(literal)
                .requires(source -> source.hasPermission(REQUIRED_PERMISSION))
                .executes(context -> {
                    SporeAddDebugConfig.setEnabled(value);
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "command.spore_add.debug.set_master", state(value)), true);
                    return 1;
                });
    }

    /** 造一个「某个分组开/关」叶子，挂在 {@code area} 参数下面。开关值同样靠闭包（见 masterNode）。 */
    private static LiteralArgumentBuilder<CommandSourceStack> areaNode(String literal, boolean value) {
        return Commands.literal(literal)
                .requires(source -> source.hasPermission(REQUIRED_PERMISSION))
                .executes(areaSwitch(value));
    }

    /** 造一个「改某个分组」的执行器。开关值同样靠闭包，与 {@link #masterNode} 同一个理由。 */
    private static Command<CommandSourceStack> areaSwitch(boolean value) {
        return context -> {
            String raw = StringArgumentType.getString(context, "area");
            Area area = Area.byKey(raw);
            if (area == null) {
                // 报错要带上可用清单：补全只在玩家按 Tab 时出现，打错字时看不到。
                context.getSource().sendFailure(Component.translatable(
                        "command.spore_add.debug.unknown_area", raw, availableKeys()));
                return 0;
            }
            SporeAddDebugConfig.setArea(area, value);
            context.getSource().sendSuccess(() -> Component.translatable(
                    "command.spore_add.debug.set_area", area.name(), state(value)), true);
            return 1;
        };
    }

    /** 把总闸与九个分组念一遍。分组显示的是「配置里怎么写的」，不与总闸相与。 */
    private static void show(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.spore_add.debug.header",
                state(SporeAddDebugConfig.enabled())), false);
        for (Area area : Area.values()) {
            source.sendSuccess(() -> Component.translatable("command.spore_add.debug.area",
                    area.name(), state(SporeAddDebugConfig.area(area))), false);
        }
    }

    /** 九个分组的键名，逗号分隔。给打错字时看。 */
    private static String availableKeys() {
        return Stream.of(Area.values()).map(Area::key).collect(Collectors.joining(", "));
    }

    /** 开关状态的显示文本。走 lang，所以中英文各一份。 */
    private static Component state(boolean value) {
        return Component.translatable(value
                ? "command.spore_add.debug.state_on"
                : "command.spore_add.debug.state_off");
    }
}
