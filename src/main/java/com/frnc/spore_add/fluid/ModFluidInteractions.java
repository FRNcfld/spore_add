package com.frnc.spore_add.fluid;

import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidInteractionRegistry;

/**
 * 本 mod 的流体接触岩浆时的反应。
 *
 * <table border="1">
 *   <caption>反应表</caption>
 *   <tr><th>接触</th><th>岩浆源</th><th>流动的岩浆</th></tr>
 *   <tr><td>冷却液</td><td>黑曜石</td><td>方解石</td></tr>
 *   <tr><td>液态寒冷</td><td>黑曜石</td><td>末地石</td></tr>
 * </table>
 *
 * <h2>为什么用 Forge 的 FluidInteractionRegistry，而不是自己覆写 onPlace</h2>
 * 原版那套"岩浆遇水"的逻辑（{@code LiquidBlock#shouldSpreadLiquid}）在 Forge 里已经被标记弃用，
 * 换成了 {@link FluidInteractionRegistry}，而 {@code LiquidBlock} 的 {@code onPlace} 与
 * {@code neighborChanged} 现在都会调用它的 {@code canInteract}。也就是说这套机制<b>已经接在了
 * 我们需要的两个时机上</b>，自己再覆写一遍反而会与它重复触发。它还顺带处理了我们本来要手写的几件事：
 * 走 {@code ForgeEventFactory.fireFluidPlaceBlockEvent}（别的模组能拦这次方块变化）、
 * 以及放那声 1501 的嘶嘶声。
 *
 * <h2>为什么注册在岩浆类型上，而不是注册在自己的流体上</h2>
 * 这套注册表把"source"定义为<b>会被替换掉的那一方</b>：回调里的 {@code currentPos} 就是被 setBlock
 * 的位置，而 {@code relativePos} 是触发它的邻居。需求要的是"岩浆变成黑曜石/方解石/末地石、
 * 我们的流体留下"，所以岩浆才是那个 source——于是注册在 {@code ForgeMod.LAVA_TYPE} 上，
 * 与 Forge 自带的"岩浆+水→黑曜石/圆石"那条是同一个写法。
 *
 * <p>方向上也因此免费拿到两个方向：岩浆流到我们旁边时，是岩浆自己的 {@code onPlace} 触发；
 * 我们的流体流到岩浆旁边时，<b>岩浆</b>会收到 {@code neighborChanged}，同样触发。
 *
 * <h2>一个继承自原版的限制：只检查"上方 + 四个水平方向"</h2>
 * {@code canInteract} 遍历的是 {@code LiquidBlock.POSSIBLE_FLOW_DIRECTIONS}（下、北、东、南、西）
 * 并取反方向，所以实际检查的位置是<b>上方与四个水平邻居，不含正下方</b>。这与原版"岩浆遇水"完全一致，
 * 是这套机制明写在文档里的取舍。实际游戏里影响很小：流体都会扩散，互相挨上时总有一侧是水平或上方相邻，
 * 于是照样触发；真正不触发的是"岩浆柱正正落在流体上方且流体没有扩散"这种静态叠放。
 */
public final class ModFluidInteractions {

    private ModFluidInteractions() {
    }

    /**
     * 注册反应表。必须由 {@code FMLCommonSetupEvent} 调用——那时注册表已经冻结，
     * {@link ModFluids#COOLANT_TYPE} 之类的 {@code RegistryObject} 才取得到值。
     */
    public static void register() {
        // 冷却液：岩浆源 → 黑曜石，流动岩浆 → 方解石
        FluidInteractionRegistry.addInteraction(ForgeMod.LAVA_TYPE.get(),
                new FluidInteractionRegistry.InteractionInformation(ModFluids.COOLANT_TYPE.get(),
                        lava -> lava.isSource()
                                ? Blocks.OBSIDIAN.defaultBlockState()
                                : Blocks.CALCITE.defaultBlockState()));

        // 液态寒冷：岩浆源 → 黑曜石，流动岩浆 → 末地石
        FluidInteractionRegistry.addInteraction(ForgeMod.LAVA_TYPE.get(),
                new FluidInteractionRegistry.InteractionInformation(ModFluids.LIQUID_COLD_TYPE.get(),
                        lava -> lava.isSource()
                                ? Blocks.OBSIDIAN.defaultBlockState()
                                : Blocks.END_STONE.defaultBlockState()));
    }
}
