package com.frnc.spore_add.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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

    // ------------------------------------------------------------------
    // 冻伤抗性：每件 25%
    // ------------------------------------------------------------------

    /** 每件「烈阳」提供的免疫比例。四件叠满刚好 100%。 */
    private static final float RESISTANCE_PER_PIECE = 0.25F;

    /**
     * 这个生物靠「烈阳」能免疫掉<b>多大比例</b>的冻伤效果：每件 25%，上限 1.0。
     *
     * <h2>为什么从"任意一件全免"改成"每件 25%"</h2>
     * 原先的实现是"任意一件 → 完全免疫"，靠 {@code MobEffectEvent.Applicable} 直接拦下。
     * 但需求要求改成按件削弱，而那个事件<b>只能允许或拒绝，改不了效果实例本身</b>——
     * 想在"入体前把时长与层数按比例缩小"，必须拦在效果被传进 {@code addEffect} 的那一刻，
     * 所以改由 {@code WarmthFrostbiteScalingMixin} 来做，本方法只负责回答"削弱多少"。
     *
     * <p>这次改动<b>影响所有场景</b>，不只核弹：冷却液、液态寒冷、Spore 自己的冻伤来源，
     * 现在都只按件削弱。这是明确的取舍（统一行为优先于保留旧的全免）。
     */
    public static float resistanceFraction(LivingEntity entity) {
        int pieces = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (hasWarmth(entity, slot)) {
                pieces++;
            }
        }
        return Math.min(1.0F, pieces * RESISTANCE_PER_PIECE);
    }

    /**
     * 把贡献了免疫的那几件「烈阳」护甲当场销毁。
     *
     * <p>直接置空而不是扣耐久——需求的措辞是"以护甲碎裂为代价"，那就该是不可修复的损失。
     * 调用方负责决定什么时候付这个代价（见 {@code FrostSighShockwaveEntity}）。
     */
    public static void destroyWarmthArmor(LivingEntity entity) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (hasWarmth(entity, slot)) {
                entity.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }
}
