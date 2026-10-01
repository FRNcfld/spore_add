package com.frnc.spore_add.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * 「烈阳」附魔：让穿戴者免疫寒冷。
 *
 * <p>它本身**不做任何事**——免疫逻辑不在附魔里，而在两个地方：
 * <ul>
 *   <li>{@code Warmth}（{@code enchantment/Warmth.java}）负责回答"这个生物算不算穿着烈阳护甲"；</li>
 *   <li>{@code ModEvents} 与两个 mixin 负责按那个答案去拦截各种寒冷效果。</li>
 * </ul>
 * 这样附魔只管"能不能附上、叫什么"，判断与效果各归一处。
 */
public class WarmthEnchantment extends Enchantment {

    /** 四格护甲。类别用 {@link EnchantmentCategory#ARMOR} 时，这个数组决定具体能附在哪些槽位。 */
    private static final EquipmentSlot[] ARMOR_SLOTS =
            {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public WarmthEnchantment() {
        // 类别 ARMOR 就是"仅能作用于护甲"这条需求的实现：附魔台与铁砧都会按类别过滤
        super(Rarity.RARE, EnchantmentCategory.ARMOR, ARMOR_SLOTS);
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    /**
     * 宝藏附魔：附魔台刷不出来，只能从战利品箱、交易或创造模式获得。
     *
     * <p>这不只是稀有度的装饰——附魔台的候选列表会过滤掉 {@code isTreasureOnly()} 为真的附魔，
     * 所以这一句就是"附魔台拿不到"的实现。
     */
    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    /**
     * 与冰霜行者互斥：两者都作用于靴子，且语义相反（一个让你能踩冰面、一个让你不怕冷），
     * 同时存在会得到"既能在细雪上走、又免疫细雪"的怪状态。
     *
     * <p>注意父方法是 {@code protected}，覆写不能放宽成 {@code public}。
     */
    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other) && other != Enchantments.FROST_WALKER;
    }
}
