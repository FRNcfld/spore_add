package com.frnc.spore_add.fungus;

import java.util.UUID;

import com.frnc.spore_add.SporeAddFungusConfig;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 需求的「真菌感知范围提高（视觉，声音等）」。
 *
 * <h2>一个属性，两条通路</h2>
 * Spore 的真菌从<b>一处</b>读感知半径：{@code Attributes.FOLLOW_RANGE}（基础 16）。
 * {@code TargetGoal#getFollowDistance} 就是它，而它同时喂给：
 * <ul>
 *   <li><b>视觉</b>——{@code NearestAttackableTargetGoal} 的搜索半径
 *       （{@code TargetingConditions.range(搜索半径)}），也就是"能看见多远的目标"；</li>
 *   <li><b>声音</b>——{@code HurtTargetGoal#alertOthers} 的警报半径。
 *       同伙被打时，被惊动的同类就在这个半径里，所以它就是真菌的"听觉传播距离"。</li>
 * </ul>
 * 所以加一个修饰符就够了，不必分别去动两条 AI。
 *
 * <h2>加不进去的地方，与为什么不为它写 mixin</h2>
 * 巢群广播 {@code LocalTargettingGoal} 把半径硬编码封顶在 32
 * （{@code min(FOLLOW_RANGE, 32)}）。默认倍率 2.0 刚好把 16 抬到 32、正好打满这个上限，
 * 所以默认配置下这一路也是跟着一起变大的。倍率调到 2 以上时，感知的继续增长只会体现在
 * 视觉与警报上、不再体现在巢群广播上——这是 Spore 的硬上限，为一个已经打满的旋钮去写
 * 字节码注入不划算，如实写在配置注释里即可。
 *
 * <h2>为什么用「永久」修饰符</h2>
 * {@code addPermanentModifier} 的加成会随实体存进存档。这不是必需的（每次实体进入世界
 * 我们都会重新补一遍），但它让"实体在被保存的那一刻是什么样"与"再次读出来时是什么样"一致，
 * 中间不存在一个"刚读出来、还没跑到加入事件"的、感知是原值的窗口。
 * 重新补的时候先 {@code removeModifier} 再 add，于是反复进出区块不会叠加。
 */
public final class FungusSenses {

    /**
     * 修饰符的固定 ID。
     *
     * <p>必须是常量：它就是"同一个加成"的身份。换个随机 UUID 的话，每次实体进入世界都会
     * 被当成一个新的、互不相干的加成叠上去，感知半径会随着进出区块一路翻倍。
     */
    private static final UUID SIGHT_MODIFIER_ID = UUID.fromString("3f2b9c14-7a5e-4d61-9b08-2c7e5a1d4f30");

    private static final String SIGHT_MODIFIER_NAME = "spore_add:fungus_sight";

    private FungusSenses() {
    }

    /**
     * 给一个真菌补上感知加成。可重复调用（先摘后加）。
     *
     * <p>倍率配成 1.0 时修饰符会被摘掉且不再加回，于是"关掉"这件事不需要另一个开关。
     */
    public static void apply(Mob mob) {
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange == null) {
            return;
        }
        followRange.removeModifier(SIGHT_MODIFIER_ID);

        // MULTIPLY_BASE 的语义是 base × (1 + Σamount)，所以"倍率 2.0"对应的增量是 1.0
        double bonus = SporeAddFungusConfig.sensingMultiplier() - 1.0D;
        if (bonus <= 0.0D) {
            return;
        }
        followRange.addPermanentModifier(new AttributeModifier(
                SIGHT_MODIFIER_ID, SIGHT_MODIFIER_NAME, bonus, AttributeModifier.Operation.MULTIPLY_BASE));
    }
}
