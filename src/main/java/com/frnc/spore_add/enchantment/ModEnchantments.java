package com.frnc.spore_add.enchantment;

import com.frnc.spore_add.SporeAdd;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本 mod 的附魔注册表。目前只有「烈阳」。
 */
public final class ModEnchantments {

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, SporeAdd.MOD_ID);

    public static final RegistryObject<Enchantment> WARMTH =
            ENCHANTMENTS.register("warmth", WarmthEnchantment::new);

    private ModEnchantments() {
    }

    public static void register(IEventBus modEventBus) {
        ENCHANTMENTS.register(modEventBus);
    }
}
