package com.frnc.spore_add.effect;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.network.ModNetwork;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本 mod 的 buff 注册表。目前只有「爆燃」。
 */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, SporeAdd.MOD_ID);

    /** 爆燃持续时间：10 秒（需求 3）。每次重新触发都会刷回这个值。 */
    public static final int DURATION_TICKS = 200;

    public static final RegistryObject<MobEffect> DEFLAGRATION =
            EFFECTS.register("deflagration", DeflagrationEffect::new);

    private ModEffects() {
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }

    /**
     * 施加或刷新爆燃，并把层数累加上去（需求 2 的"同等级"、需求 3 的"叠加并刷新时长"）。
     *
     * <p>duration 用同一个值刷两次是安全的：vanilla 的 {@code MobEffectInstance#update}
     * 在 amplifier 相同时会把 duration 取更长的一方，所以每次触发都会把时长顶回满值。
     *
     * <p>amplifier 固定传 0：层数不走 vanilla 那套（它有 127 的字节上限），而是存在 {@link BuffLevels} 里。
     *
     * @param extraStacks 本次要累加的层数，即触发瞬间的可燃等级
     */
    public static void apply(LivingEntity entity, int extraStacks) {
        if (extraStacks <= 0) {
            return;
        }
        BuffLevels.addDeflagration(entity, extraStacks);
        entity.addEffect(new MobEffectInstance(DEFLAGRATION.get(), DURATION_TICKS, 0, false, true));
        // 层数不是 vanilla 同步的一部分，立刻补发一次，免得客户端要等下一个每秒 tick
        ModNetwork.syncLevels(entity);
    }
}
