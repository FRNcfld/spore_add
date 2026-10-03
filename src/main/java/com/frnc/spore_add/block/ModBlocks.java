package com.frnc.spore_add.block;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.fluid.ModFluids;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
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
     * 「冰雪的叹息（半成品）」——一个<b>占位</b>方块，没有任何机制。
     *
     * <h2>它为什么存在</h2>
     * 给<b>整合包当配方锚点</b>用：整合包作者要在自己的数据包里写「由什么合成什么」，
     * 就需要一个有稳定 id、能被 JEI 显示、能被标签引用的实体。
     * 它<b>刻意不带任何行为</b>——不带 BlockEntity、没有状态属性、不参与任何事件，
     * 所以整合包怎么用它都不会与 mod 的玩法打架。
     *
     * <h2>为什么另开一个 id 而不是复用 {@code frost_sigh}</h2>
     * 行为完全不同，所以不能共用 id：那颗核弹<b>不可堆叠</b>、激活后挖不动、
     * 还会被自己的核爆保护起来——当配方材料全是麻烦。这个占位方块走的是相反的路：
     * 普通方块、可堆叠 64、随便挖。
     *
     * <p><b>但外观是刻意做成一样的</b>：它没有自己的贴图与方块模型，方块状态直接引用
     * {@code spore_add:block/frost_sigh}（见 {@code blockstates/frost_sigh_0.json}）。
     * 于是玩家一眼就知道这两件东西是同一族——名字里那个「（半成品）」才是区分它们的地方。
     *
     * <h2>挖掘等级：铁镐起</h2>
     * 与 {@link #FROST_SIGH} 用的是同一套两段式写法，只是档位低一级：
     * 这里 {@code requiresCorrectToolForDrops()} 负责"工具不对就不掉落物"，
     * 具体到哪一档由数据包标签决定——本 mod 在 {@code data/minecraft/tags/blocks/} 下把它
     * 挂进了 {@code mineable/pickaxe} 与 {@code needs_iron_tool}。<b>两者缺一不可</b>：
     * 只加标签不加这个方法，任何工具都能挖下来；只加方法不加标签，则等同"随便什么镐都算对"。
     *
     * <p>注意 {@code needs_iron_tool} 是<b>原版的</b>标签，本 mod 只是往它里面追加一项
     * （{@code "replace": false} 就是"追加"），并没有自己新定义一套。
     *
     * <p>其余属性取最朴素的一组（{@code strength(2.0F)} 与原版石头同级）。
     * 掉落表在同名 loot_table 里，挖对了工具就是它自己。
     */
    public static final RegistryObject<Block> FROST_SIGH_0 =
            BLOCKS.register("frost_sigh_0", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(2.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));

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
