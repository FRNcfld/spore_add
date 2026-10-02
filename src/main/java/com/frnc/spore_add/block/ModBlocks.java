package com.frnc.spore_add.block;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.fluid.ModFluids;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本 mod 的方块注册表。目前只有三种流体各自的流体方块。
 *
 * <p>流体在世界里必须有一个对应的 {@link Block} 才能真正存在、也才能被流体的渲染器画出来；
 * 这三种方块不发光、不参与创造模式物品栏（没有 BlockItem，只能用桶放置）。
 *
 * <p>属性集直接镜像原版 {@code Blocks.WATER}，只有两点不同：地图颜色统一用水的颜色而没有细分，
 * 以及没有 {@code randomTicks()}——原版只有岩浆带那一项，水的流动靠 {@code FlowingFluid} 自己
 * 调度推进，不依赖方块的随机刻。
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SporeAdd.MOD_ID);

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    /**
     * 冷却液。
     *
     * <p>泡在里面会像细雪一样失温并累积冻伤（封顶 10 层），所以用 {@link CoolantBlock}。
     */
    public static final RegistryObject<LiquidBlock> COOLANT =
            BLOCKS.register("coolant", () -> new CoolantBlock(ModFluids.COOLANT, waterLikeBlock()));

    /**
     * 液态寒冷。
     *
     * <p>除了失温，它还负责区域冻伤与冰扩散，所以用 {@link LiquidColdBlock}。
     */
    public static final RegistryObject<LiquidBlock> LIQUID_COLD =
            BLOCKS.register("liquid_cold", () -> new LiquidColdBlock(ModFluids.LIQUID_COLD, waterLikeBlock()));

    /**
     * 高能燃料。
     *
     * <p>它比其他两种流体多一层交互（生物接触会积累 Spore 的「可燃」），所以用 {@link HighEnergyFuelBlock}
     * 而不是朴素的 {@link LiquidBlock}；方块属性仍然与另外两种共用 {@link #waterLikeBlock()}。
     */
    public static final RegistryObject<LiquidBlock> HIGH_ENERGY_FUEL =
            BLOCKS.register("high_energy_fuel",
                    () -> new HighEnergyFuelBlock(ModFluids.HIGH_ENERGY_FUEL, waterLikeBlock()));

    /**
     * 「冰雪的叹息」——本 mod 唯一的<b>非流体</b>方块，也是量级最大的一个。
     *
     * <p>属性刻意与三种流体完全不同：流体那套是"可替换、无碰撞、无掉落表"，而这个是一块实心的、
     * 抗爆的、会掉落的方块。{@code strength(50, 1200)} 的抗爆值取得比黑曜石还高，
     * 免得它被自己的核爆或别的爆炸掀掉。{@code lightLevel} 给 7 是为了在暗处也能看见它。
     */
    /**
     * 「冰雪的叹息」方块本体。
     *
     * <p><b>挖掘等级是钻石镐起</b>：{@code requiresCorrectToolForDrops()} 负责"工具不对就不掉落物"，
     * 具体到哪一档由数据包标签决定——本 mod 在 {@code data/minecraft/tags/blocks/} 下声明了
     * {@code mineable/pickaxe} 与 {@code needs_diamond_tool}。两者缺一不可：
     * 只加标签不加这个方法，任何工具都能挖下来；只加方法不加标签，则等同"随便什么镐都算对"。
     *
     * <p>硬度沿用 {@code 50.0}（与黑曜石同级），抗爆 1200。所以它是一块"要挖一会儿、
     * 而且得带对镐子"的方块——激活之后更是彻底挖不动（见 {@code FrostSighBlock}）。
     */
    public static final RegistryObject<Block> FROST_SIGH =
            BLOCKS.register("frost_sigh", () -> new FrostSighBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 7)));

    /**
     * 与原版水一致的流体方块属性。
     *
     * <p>每次调用都新建一个实例：{@code BlockBehaviour.Properties} 是可变的，各个方块应当各持一份，
     * 原版派生属性时也是走 {@code copy()} 而不是复用同一个对象。
     */
    private static BlockBehaviour.Properties waterLikeBlock() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WATER)
                .replaceable()
                .noCollission()
                .strength(100.0F)
                .pushReaction(PushReaction.DESTROY)
                .noLootTable()
                .liquid()
                .sound(SoundType.EMPTY);
    }
}
