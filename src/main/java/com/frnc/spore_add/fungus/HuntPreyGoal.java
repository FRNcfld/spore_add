package com.frnc.spore_add.fungus;

import com.frnc.spore_add.SporeAddFungusConfig;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * 需求的「真菌会尝试攻击一切生物」——把候选面铺开到<b>除真菌自己以外的一切生物</b>。
 *
 * <h2>为什么还要另加一条目标，Spore 不是已经有了吗</h2>
 * Spore 在 {@code Infected#addTargettingGoals} 里确实有一条"攻击一切"的目标，
 * 但它被 {@code Utilities.TARGET_SELECTOR} 卡了两道：{@code at_an} 默认关着（动物全部放过），
 * 以及 {@code at_mob} 关掉时整条不成立。本目标绕开这两道，只保留 {@code blacklist}
 * ——名单的取舍见 {@link FungusCombat#isHuntable}。
 *
 * <h2>优先级为什么是 3</h2>
 * Spore 的玩家 / 白名单目标是优先级 1、动物与其它生物也是 1。本目标排在它们<b>后面</b>，
 * 于是"玩家在身边"时永远先由 Spore 那条接管，本目标只在玩家那条够不着时才出结果。
 * 这样"判定不管玩家"这条约定不需要靠代码去守，靠优先级就成立。
 *
 * <h2>这里没有"打得过才打"</h2>
 * 判定不写在本目标的谓词里，而是统一放在 {@code LivingChangeTargetEvent} 上（见 {@link FungusCombat} 的类注释）。
 * 写在这里的话，Spore 自己那条优先级 1 的目标会把判定的成果整个绕过去。
 *
 * <p>搜索半径跟着 {@code Attributes.FOLLOW_RANGE} 走（由 {@code NearestAttackableTargetGoal} 内部取
 * {@code TargetGoal#getFollowDistance}），所以需求 4 的感知加成对这条目标自动生效。
 */
public class HuntPreyGoal extends NearestAttackableTargetGoal<LivingEntity> {

    /**
     * 挂在目标选择器上的优先级。必须<b>大于</b> Spore 玩家/白名单目标的 1，见类注释。
     */
    public static final int PRIORITY = 3;

    public HuntPreyGoal(Mob mob) {
        // 搜索间隔来自配置，默认 10——那是原版各简版构造器与 Spore 自己用的值。
        // 它直接决定"没找到目标"时的空转成本：每 tick 搜一次全实体是没必要的。
        super(mob, LivingEntity.class, SporeAddFungusConfig.huntSearchIntervalTicks(),
                true, false, FungusCombat::isHuntable);
    }

    /**
     * 配置开关在这里判，而不是在挂目标的时候判。
     *
     * <p>目标是"实体进入世界"那一刻挂上去的。要是把 {@code huntEnabled} 判在挂的那一步，
     * 玩家改完配置就得让所有真菌重新入列（走出区块再回来、或重启）才生效；
     * 判在这里则是<b>当拍就生效</b>，而且"没挂过"与"挂了但关着"两种情况不会分叉。
     */
    @Override
    public boolean canUse() {
        return SporeAddFungusConfig.huntEnabled() && super.canUse();
    }
}
