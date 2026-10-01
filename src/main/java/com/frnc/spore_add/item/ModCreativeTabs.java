package com.frnc.spore_add.item;

import com.frnc.spore_add.SporeAdd;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本 mod 自己的创造模式物品栏页签。
 *
 * <p>流体方块没有对应的 BlockItem，所以三种流体在本页签里表现为三个桶；这样它们才不需要靠命令
 * 取得。页签注册在<b>原版</b>的创造模式页签注册表 {@link Registries#CREATIVE_MODE_TAB} 上，
 * 而不是 Forge 的某个注册表。
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SporeAdd.MOD_ID);

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    public static final RegistryObject<CreativeModeTab> SPORE_ADD_TAB =
            CREATIVE_MODE_TABS.register("spore_add", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + SporeAdd.MOD_ID))
                    .icon(() -> new ItemStack(ModItems.COOLANT_BUCKET.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.COOLANT_BUCKET.get());
                        output.accept(ModItems.LIQUID_COLD_BUCKET.get());
                        output.accept(ModItems.HIGH_ENERGY_FUEL_BUCKET.get());
                    })
                    .build());
}
