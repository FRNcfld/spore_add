package com.frnc.spore_add.command;

import com.frnc.spore_add.SporeAdd;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /spore_add} 这个根命令的唯一注册点。
 *
 * <h2>为什么要有这个类</h2>
 * 根字面量只能有<b>一个</b>所有者。各个子命令各自订阅 {@code RegisterCommandsEvent}
 * 再各注册一次 {@code Commands.literal("spore_add")}，Brigadier 确实会把同名子节点合并
 * （{@code CommandNode#addChild} 对同名字面量做归并），所以那样写"能用"——
 * 但它把两个子树的挂载顺序变成了隐含约束，日后任何一处漏写或写错都表现为
 * "某个子命令时灵时不灵"，而不是编译错误。
 *
 * <p>所以拆成三层：本类管<b>根</b>，{@link HatredCommand} 与 {@link DebugCommand}
 * 各自只负责把自己那一个子树造出来（都返回 {@code hatred} / {@code debug} 节点，不含根）。
 * 加第三个子命令时只需要在这里多挂一行。
 *
 * <p>权限不在这里判：根谁都能敲，具体到某个子命令再各自 {@code requires}。
 * 这样 {@code /spore_add hatred}（看自己的恨意值）保持对普通玩家开放，
 * 而 {@code /spore_add hatred set} 与整个 {@code /spore_add debug} 仍要管理员。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID)
public final class SporeAddCommands {

    private SporeAddCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("spore_add")
                .then(HatredCommand.build())
                .then(DebugCommand.build());
        event.getDispatcher().register(root);
    }
}
