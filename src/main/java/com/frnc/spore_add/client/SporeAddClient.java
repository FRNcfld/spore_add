package com.frnc.spore_add.client;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.fluid.ModFluids;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 本 mod 的客户端初始化。
 *
 * <p><b>这个类必须只在客户端加载</b>：它引用的 {@link ItemBlockRenderTypes} 是纯客户端类，
 * 服务端一旦加载到这个类就会因缺类而崩。{@code @Mod.EventBusSubscriber} 上的
 * {@code value = Dist.CLIENT} 就是干这个的——Forge 只会在客户端注册本类，服务端连类都不会碰，
 * 所以不需要在方法里手写 {@code if (dist.isClient())} 判断。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SporeAddClient {

    private SporeAddClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 流体默认走 RenderType.solid()，也就是不透明；不改成 translucent 的话，
        // 水状流体会渲染成一块实心方块（颜色仍然对，但完全没有液体的通透感）。
        // 静置与流动两个变体都要设：渲染时是按 FluidState 的 type 查的，而源头和流动
        // 在注册表里是两条独立条目，只设一个会出现"源头通透、流出去的部分变实心"。
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModFluids.COOLANT.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_COOLANT.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.LIQUID_COLD.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_LIQUID_COLD.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.HIGH_ENERGY_FUEL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_HIGH_ENERGY_FUEL.get(), RenderType.translucent());
        });
    }
}
