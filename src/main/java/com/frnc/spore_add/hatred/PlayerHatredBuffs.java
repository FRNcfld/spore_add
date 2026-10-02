package com.frnc.spore_add.hatred;

import java.util.UUID;

import com.frnc.spore_add.SporeAddPlayerConfig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 个人恨意值给玩家的增益。五项里三项走原版属性、两项走事件。
 *
 * <h2>为什么三项走属性</h2>
 * 基础攻击力、防御力、幸运值在原版都已经是属性，做成属性修饰符就<b>自动</b>参与原版的一切结算
 * （附魔、药水、其它模组的加成全部照常叠加），不用我们自己在伤害事件里手算。
 *
 * <p>用<b>固定 UUID</b> 的修饰符，每次刷新先摘后加——于是"刷新"是幂等的，
 * 恨意值每秒变几次也不会叠出一堆修饰符。这与 {@code FungusSenses} 处理感知范围是同一手法。
 *
 * <h2>为什么减伤与最终伤害加成不走属性</h2>
 * 原版没有这两项属性，只能自己在事件里算（见 {@code HatredEvents}）：
 * 减伤挂在"受击"上、最终伤害挂在"已结算完"那一层，两者的时机不同，所以是两条独立通路。
 *
 * <p>三个提现值都按 {@code min(上限, 恨意值 × 每点系数)} 取，上限是必须的——
 * 没有上限的话，长局游戏里玩家的恨意值只会单调上涨（只有几种按比例削减的手段），
 * 迟早把攻击力堆到荒谬的量级。
 */
public final class PlayerHatredBuffs {

    /** 基础攻击力修饰符的固定 ID。 */
    private static final UUID ATTACK_ID = UUID.fromString("8d1f4a52-3c7b-4e19-a6f0-5b2d9c8e4a71");

    /** 防御力修饰符的固定 ID。 */
    private static final UUID ARMOR_ID = UUID.fromString("2a6c9e13-7d4f-4b85-8c31-9f0e6d2b7a54");

    /** 幸运值修饰符的固定 ID。 */
    private static final UUID LUCK_ID = UUID.fromString("c4e07b96-1a2d-4f38-b5e7-6d9013c8f2a5");

    private PlayerHatredBuffs() {
    }

    /**
     * 按当前恨意值刷新这个玩家身上的三项属性加成。
     *
     * <p>调用时机：恨意值每次变化、玩家登录、玩家复活、跨维度。少调一次不会出错——
     * 属性是"当前值"，下次刷新会追上；只是玩家会短暂看到一个偏旧的加成。
     */
    public static void refresh(ServerPlayer player) {
        double hatred = HatredData.get(player.serverLevel()).get(player.getUUID());
        apply(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, "spore_add:hatred_attack", attackBonus(hatred));
        apply(player, Attributes.ARMOR, ARMOR_ID, "spore_add:hatred_armor", armorBonus(hatred));
        apply(player, Attributes.LUCK, LUCK_ID, "spore_add:hatred_luck", luckBonus(hatred));
    }

    /** 玩家退场时把三项加成摘干净，免得修饰符留在实体上被别的逻辑读到。 */
    public static void clear(ServerPlayer player) {
        remove(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
        remove(player, Attributes.ARMOR, ARMOR_ID);
        remove(player, Attributes.LUCK, LUCK_ID);
    }

    // ------------------------------------------------------------------
    // 五项加成的数值
    // ------------------------------------------------------------------
    //
    // 这五个方法是<b>唯一</b>的计算处：既给 refresh/事件用，也给扫描仪显示用。
    // 分成两处写的话，扫描仪迟早会显示一个和实际生效不一样的数字——那种错最难发现，
    // 因为两边看起来都"对"。

    /** 按个人恨意值算出的基础攻击力加成（0 ~ 上限）。 */
    public static double attackBonus(double hatred) {
        return capped(hatred * SporeAddPlayerConfig.buffAttackDamagePerHatred(),
                SporeAddPlayerConfig.buffMaxAttackDamage());
    }

    /** 按个人恨意值算出的防御力加成（0 ~ 上限）。 */
    public static double armorBonus(double hatred) {
        return capped(hatred * SporeAddPlayerConfig.buffArmorPerHatred(),
                SporeAddPlayerConfig.buffMaxArmor());
    }

    /** 按个人恨意值算出的幸运值加成（0 ~ 上限）。 */
    public static double luckBonus(double hatred) {
        return capped(hatred * SporeAddPlayerConfig.buffLuckPerHatred(),
                SporeAddPlayerConfig.buffMaxLuck());
    }

    /** 按个人恨意值算出的减伤比例（0 ~ 上限）。 */
    public static double damageReduction(double hatred) {
        return capped(hatred * SporeAddPlayerConfig.buffDamageReductionPerHatred(),
                SporeAddPlayerConfig.buffMaxDamageReduction());
    }

    /** 按个人恨意值算出的最终伤害加成比例（0 ~ 上限）。 */
    public static double finalDamageBonus(double hatred) {
        return capped(hatred * SporeAddPlayerConfig.buffFinalDamagePerHatred(),
                SporeAddPlayerConfig.buffMaxFinalDamage());
    }

    /** 夹到 [0, max]。恨意值不可能为负，但配置可以配出负的系数，所以两头都夹。 */
    private static double capped(double value, double max) {
        return Math.min(Math.max(0.0D, max), Math.max(0.0D, value));
    }

    // ------------------------------------------------------------------

    /**
     * 加（或更新）一个修饰符。算出来是 0 时改成"摘掉"而不是"加一个 0"——
     * 留着一个 0 值修饰符没有意义，还会让 {@code getModifier} 的判空逻辑变得不可靠。
     */
    private static void apply(ServerPlayer player, Attribute attribute, UUID id, String name, double bonus) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        // 先摘后加，于是"刷新"是幂等的；bonus <= 0 时就停在摘掉这一步
        instance.removeModifier(id);
        if (bonus <= 0.0D) {
            return;
        }
        // ADDITION 而不是乘法：这些是"加多少点"，与基础值无关，改成百分比会让不同装备的玩家
        // 拿到的绝对量差出好几倍，与"恨意值越高越强"这个直觉不符
        instance.addPermanentModifier(new AttributeModifier(
                id, name, bonus, AttributeModifier.Operation.ADDITION));
    }

    private static void remove(ServerPlayer player, Attribute attribute, UUID id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }
}
