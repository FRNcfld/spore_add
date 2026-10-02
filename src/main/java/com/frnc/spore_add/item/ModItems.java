package com.frnc.spore_add.item;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.block.ModBlocks;
import com.frnc.spore_add.entity.ModEntities;
import com.frnc.spore_add.fluid.ModFluids;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本 mod 的物品注册表：三种流体的桶，以及「冰霜新星」。
 *
 * <p>三个桶的物品属性与原版水桶逐项一致（{@code craftRemainder(Items.BUCKET)} + {@code stacksTo(1)}）：
 * 用掉之后退还一个空桶，且不可堆叠——后者不只是习惯，{@code BucketItem} 倒液体时要修改手上这一格，
 * 可堆叠的话逻辑会不正确。
 *
 * <p>「冰霜新星」与它们性质完全不同（不是桶、要蓄力），所以不复用下面那个 {@link #bucket} 辅助方法。
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SporeAdd.MOD_ID);

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    /** 冷却液桶。 */
    public static final RegistryObject<Item> COOLANT_BUCKET =
            ITEMS.register("coolant_bucket", () -> bucket(ModFluids.COOLANT));

    /** 液态寒冷桶。 */
    public static final RegistryObject<Item> LIQUID_COLD_BUCKET =
            ITEMS.register("liquid_cold_bucket", () -> bucket(ModFluids.LIQUID_COLD));

    /** 高能燃料桶。 */
    public static final RegistryObject<Item> HIGH_ENERGY_FUEL_BUCKET =
            ITEMS.register("high_energy_fuel_bucket", () -> bucket(ModFluids.HIGH_ENERGY_FUEL));

    /**
     * 冰霜新星。
     *
     * <p>可堆叠到 16（与雪球、末影珍珠同族），每次发射消耗 1 个——蓄力与可堆叠并不冲突，
     * 蓄力状态记在玩家身上（{@code startUsingItem}），不在物品栈上。
     */
    public static final RegistryObject<Item> FROST_NOVA =
            ITEMS.register("frost_nova", () -> new FrostNovaItem(new Item.Properties().stacksTo(16)));

    /**
     * 「冰雪的叹息」的方块物品。
     *
     * <p>不可堆叠（需求 1）——它是一颗核弹，不是消耗品。
     *
     * <p>{@code ModBlocks.FROST_SIGH.get()} 放在 lambda 里：方块注册表先于物品注册表完成，
     * 所以等到这个 lambda 被求值时方块已经在注册表里了。直接写在字段初始化式里则会提前解引用。
     */
    public static final RegistryObject<Item> FROST_SIGH =
            ITEMS.register("frost_sigh", () -> new FrostSighItem(ModBlocks.FROST_SIGH.get(),
                    new Item.Properties().stacksTo(1)));

    /**
     * 拾荒者的刷怪蛋。
     *
     * <h2>外观由模型文件决定，与构造器里那两个颜色无关</h2>
     * Forge 1.20.1 的 {@link ForgeSpawnEggItem} <b>没有</b>客户端模型钩子——javap 核对过：
     * 它比原版只多了 {@code getDefaultType} 与发射器行为，<b>没有</b>
     * {@code initializeClient}/{@code getModel}。所以原版那套蛋的模板不会自动套上来，
     * 每个模组蛋都必须自带 {@code models/item/<id>.json}；缺了它就会渲染成紫黑格子的"缺失贴图"。
     *
     * <p>我们的模型是 {@code models/item/scavenger_spawn_egg.json}，它指向 Spore 自己的蛋模型
     * {@code spore:item/summon}（菌染人类那颗蛋用的也是它），所以外观与 Spore 原版那批感染体蛋一致。
     * 那份模型里<b>没有 tintindex</b>，因此下面两个颜色参数<b>不参与渲染</b>——
     * 它们只是构造器的必填项，这里按 Spore 自己的蛋色常量 {@code 0xFF00A8A6} 填一个合理的值。
     *
     * <p><b>刻意不用 {@code SporeSpawnEgg}</b>：那个构造器会把物品加进 Spore 的 {@code BIOLOGICAL_ITEMS}
     * 列表，于是它会同时出现在 Spore 的页签里。需求要的是"放在自己的标签页下"，
     * 用 Forge 的 {@link ForgeSpawnEggItem} 就只在我们自己的页签里。
     */
    public static final RegistryObject<Item> SCAVENGER_SPAWN_EGG =
            ITEMS.register("scavenger_spawn_egg", () -> new ForgeSpawnEggItem(
                    ModEntities.SCAVENGER, 0xFF00A8A6, -1, new Item.Properties()));

    /**
     * 按原版水桶的属性造一个桶。
     *
     * <p>装的是流体的"静置"变体（{@code Source}）而不是流动变体——桶倒出来的是源头。
     * 用接收 {@code Supplier} 的构造器，理由同 {@code ModBlocks}：另一个构造器已废弃，
     * 且会在构造时提前解引用流体。
     */
    private static Item bucket(RegistryObject<FlowingFluid> fluid) {
        return new BucketItem(fluid, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));
    }
}
