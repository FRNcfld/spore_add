package com.frnc.spore_add.advancement;

import net.minecraft.advancements.CriteriaTriggers;

/**
 * 本 mod 的进度判据。
 *
 * <p>1.20.1 没有给自定义判据提供注册表，只能在 common setup 阶段调
 * {@link CriteriaTriggers#register} 注册，所以这里是普通静态字段 + 一个 {@code registerAll}，
 * 而不是 {@code DeferredRegister}。
 */
public final class ModTriggers {

    public static final WarmthEquippedTrigger WARMTH_EQUIPPED = new WarmthEquippedTrigger();

    private ModTriggers() {
    }

    public static void registerAll() {
        CriteriaTriggers.register(WARMTH_EQUIPPED);
    }
}
