package com.frnc.spore_add.item;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.fluid.ModFluids;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本 mod 的物品注册表。目前只有三种流体的桶。
 *
 * <p>三个桶的物品属性与原版水桶逐项一致（{@code craftRemainder(Items.BUCKET)} + {@code stacksTo(1)}）：
 * 用掉之后退还一个空桶，且不可堆叠——后者不只是习惯，{@code BucketItem} 倒液体时要修改手上这一格，
 * 可堆叠的话逻辑会不正确。
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
