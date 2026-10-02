package com.frnc.spore_add.command;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.hatred.HatredData;
import com.frnc.spore_add.hatred.HatredManager;
import com.frnc.spore_add.hatred.HatredValues;
import com.frnc.spore_add.hatred.PlayerHatredBuffs;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /spore_add hatred} —— 查看与调试恨意值。
 *
 * <h2>为什么需要它</h2>
 * 恨意值平时只体现在 HUD 上那个数字与几条看不见的加成里。要验证"杀一只超级体比基础体涨得多"
 * 这类事情，光看 HUD 是数不清的；而这一整套数值又是可配的，出问题时第一件事就是确认
 * "配置到底读进去了没有"。所以给一个能直接读数的入口，比只加一个 HUD 实用得多。
 *
 * <h2>为什么 set 也留着</h2>
 * 袭击触发是按档位来的（默认 500 一档），靠正常游玩去验证"越档掷骰"要杀很久。
 * {@code set} 让验收能一步跳到档位边界上。它需要权限等级 2（与 {@code /gamemode} 同级），
 * 所以不会变成普通玩家的作弊入口。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID)
public final class HatredCommand {

    private HatredCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("spore_add")
                .then(Commands.literal("hatred")
                        // 不带参数：看自己的
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            show(context.getSource(), player);
                            return 1;
                        })
                        // 带玩家：看别人的（需要权限，免得普通玩家去翻别人的数值）
                        .then(Commands.argument("player", EntityArgument.player())
                                // 权限等级 2 = /gamemode 那一档。用字面量而不是 Commands.LEVEL_* 常量：
                                // 那几个常量在 1.20.1 的映射里不叫这个名字（编译期已证伪），
                                // 而"2 = 游戏管理员"是稳定的原版约定。
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    show(context.getSource(), EntityArgument.getPlayer(context, "player"));
                                    return 1;
                                })
                                // set 子命令：调数值，用来把玩家推到档位边界上验证袭击
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D))
                                                .executes(context -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                                    double amount = DoubleArgumentType.getDouble(context, "amount");
                                                    HatredManager.set(target, amount);
                                                    context.getSource().sendSuccess(
                                                            () -> Component.translatable(
                                                                    "command.spore_add.hatred.set",
                                                                    target.getDisplayName(), format(amount)),
                                                            true);
                                                    return 1;
                                                })))));
    }

    /**
     * 把一个人的恨意值明细念出来。
     *
     * <p>一次念四个数：个人值、世界值、以及由个人值算出来的两项比例加成。
     * 后两项是玩家能直接感受到的那部分（"我变强了多少"），一起显示才好核对配置有没有生效。
     */
    private static void show(CommandSourceStack source, ServerPlayer player) {
        HatredData data = HatredData.get(player.serverLevel());
        double personal = data.get(player.getUUID());
        source.sendSuccess(() -> Component.translatable("command.spore_add.hatred.header",
                player.getDisplayName()), false);
        source.sendSuccess(() -> Component.translatable("command.spore_add.hatred.personal",
                format(personal)), false);
        source.sendSuccess(() -> Component.translatable("command.spore_add.hatred.world",
                format(data.total())), false);
        source.sendSuccess(() -> Component.translatable("command.spore_add.hatred.bonuses",
                percent(PlayerHatredBuffs.damageReduction(personal)),
                percent(PlayerHatredBuffs.finalDamageBonus(personal))), false);
        source.sendSuccess(() -> Component.translatable("command.spore_add.hatred.threshold",
                Long.toString(HatredValues.thresholdIndex(personal))), false);
    }

    /** 恨意值保留一位小数。它是浮点，直接打印会得到一串没意义的尾数。 */
    private static String format(double value) {
        return String.format("%.1f", value);
    }

    /** 把 0~1 的比例印成百分数，保留一位小数。 */
    private static String percent(double ratio) {
        return String.format("%.1f%%", ratio * 100.0D);
    }
}
