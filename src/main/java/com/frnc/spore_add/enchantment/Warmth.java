package com.frnc.spore_add.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * 「这个生物算不算穿着烈阳护甲」的唯一判断点。
 *
 * <p>之所以单独成类：免疫寒冷要拦的地方有好几个（细雪的冻结计时、冻结伤害、冻伤 buff、细雪下陷），
 * 它们分散在事件处理与 mixin 里，但判断依据必须是同一个，否则很容易出现"这个入口免疫、那个入口不免疫"。
 *
 * <h2>两个不同的粒度</h2>
 * <ul>
 *   <li>{@link #isWorn}：<b>四格护甲任意一件</b>带烈阳即可——用于免疫冻伤、冻结伤害、细雪冻结；</li>
 *   <li>{@link #isOnBoots}：<b>只有靴子那格</b>带烈阳才算——用于"不会在细雪中下陷"。</li>
 * </ul>
 */
public final class Warmth {

    /** 四格护甲。顺序上把靴子放前面：踩在细雪里的场合最常问的就是它，能少查几格。 */
    private static final EquipmentSlot[] ARMOR_SLOTS =
            {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private Warmth() {
    }

    /** 四格护甲里任意一件带有「烈阳」。 */
    public static boolean isWorn(LivingEntity entity) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (hasWarmth(entity, slot)) {
                return true;
            }
        }
        return false;
    }

    /** 只有靴子那格带有「烈阳」。 */
    public static boolean isOnBoots(LivingEntity entity) {
        return hasWarmth(entity, EquipmentSlot.FEET);
    }

    private static boolean hasWarmth(LivingEntity entity, EquipmentSlot slot) {
        return EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.WARMTH.get(), entity.getItemBySlot(slot)) > 0;
    }
}
