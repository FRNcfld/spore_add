package com.frnc.spore_add.scavenger;

import javax.annotation.Nullable;

import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.fungus.LootBlacklist;
import com.frnc.spore_add.fungus.LootValues;

import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 拾荒者的闲逛：**朝战利品偏着走**，而不是纯随机游荡。
 *
 * <h2>为什么需要它</h2>
 * 拾荒者只在 {@code scavenger.lootRadius}（默认 32 格）内找掉落物，找不到就闲逛。
 * 原版的 {@link RandomStrollGoal} 是纯随机的，所以一只落在战场外围的拾荒者会一直随机游荡，
 * <b>可能永远走不进那 32 格</b>——而它的全部价值（存活成长、给同伙供资源）都建立在"能捡到东西"上。
 * 结果就是它活得很久、等级很高，却一件东西都没捡过。
 *
 * <h2>做法：只改"往哪走"，不改"走多快、多久走一次"</h2>
 * 覆写 {@link RandomStrollGoal#getPosition()}，在 {@code scavenger.roamAwarenessRadius}
 * （默认 64 格）内找最近的一件<b>值得捡的</b>掉落物，找到就用
 * {@link DefaultRandomPos#getPosTowards} 生成一个"朝它那边"的落点。
 *
 * <p><b>刻意不是直奔。</b>{@code getPosTowards} 的第 5 个参数是<b>允许偏离方向的弧度上限</b>
 * （不是距离），传 {@code π/2} 意味着落点必须落在"朝向战利品"的那个半平面里——
 * 方向对得上，但仍是随机散步的节奏。这样它看起来像"循着味儿慢慢摸过去"，
 * 而不是被磁铁吸过去；也避免了两只拾荒者为同一件东西直线对冲。
 *
 * <h2>它不负责"捡"</h2>
 * 捡拾是 {@link ScavengerLootGoal} 的事（走到半径内就由那个目标接管）。
 * 两者半径必须不同：如果感知半径 == 捡拾半径，那感知到的东西本来就在捡拾范围内、
 * 拾荒目标会直接去拿，这个目标就完全不起作用了。所以配置里
 * {@code roamAwarenessRadius} 默认是 {@code lootRadius} 的两倍，且注释里写明了两者的关系。
 */
public class ScavengerWanderGoal extends RandomStrollGoal {

    /**
     * 落点允许偏离"朝向战利品"方向的弧度上限。{@code π/2} = 半平面。
     *
     * <p>比它更窄（例如 {@code π/3}）会更像直奔，代价是附近被地形堵住时更容易选不出落点
     * （{@code RandomPos} 只试有限次就放弃，返回 null → 这一轮干脆不闲逛）。
     */
    private static final double TOWARDS_LOOT_ARC = Math.PI / 2.0D;

    /** 沿用的原版参数：水平 10 格、竖直 7 格——与 {@code RandomStrollGoal} 的默认值一致。 */
    private static final int STROLL_HORIZONTAL = 10;
    private static final int STROLL_VERTICAL = 7;

    public ScavengerWanderGoal(Scavenger scavenger, double speed) {
        super(scavenger, speed);
    }

    /**
     * 选一个闲逛落点：能感知到战利品就朝那边偏，否则退回原版的纯随机。
     *
     * <p>朝战利品那一路<b>可能返回 null</b>（那一带全是墙、或者水面），
     * 所以拿不到就落回 {@code super}——宁可随机走一步，也不要这一拍干脆不动。
     */
    @Override
    @Nullable
    protected Vec3 getPosition() {
        Vec3 towardsLoot = towardsNearestLoot();
        if (towardsLoot != null) {
            return towardsLoot;
        }
        return super.getPosition();
    }

    /** 朝最近的战利品偏一个落点；感知不到、或那一带选不出位置就返回 null。 */
    @Nullable
    private Vec3 towardsNearestLoot() {
        ItemEntity loot = findNearestLoot();
        if (loot == null) {
            return null;
        }
        return DefaultRandomPos.getPosTowards(mob, STROLL_HORIZONTAL, STROLL_VERTICAL,
                loot.position(), TOWARDS_LOOT_ARC);
    }

    /**
     * 感知半径内最近的、值得去捡的那件掉落物。
     *
     * <p>判据与 {@code CollectLootGoal#findNearestValuable} <b>刻意保持一致</b>：
     * 黑名单里的东西不捡、价值或概率为 0 的东西不可能转化出资源。
     * 两边不一致的话会出现"它大老远走过去、到了却不去捡，然后又开始朝它走"的鬼畜循环。
     *
     * <p>与那边一样<b>先算距离再看值不值得</b>：值不值得要查数据包名单（贵），
     * 而只要最近的那件值得就够了，没必要给感知半径里每一件都算一遍。
     */
    @Nullable
    private ItemEntity findNearestLoot() {
        double radius = SporeAddFungusConfig.scavengerRoamAwarenessRadius();
        AABB area = mob.getBoundingBox().inflate(radius);
        ItemEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ItemEntity item : mob.level().getEntitiesOfClass(ItemEntity.class, area)) {
            if (item.isRemoved() || item.getItem().isEmpty()) {
                continue;
            }
            double distance = mob.distanceToSqr(item);
            if (distance >= nearestDistance) {
                continue;
            }
            if (LootBlacklist.isBlacklisted(item.getItem())
                    || !LootValues.pricingOf(item.getItem()).canEverYield()) {
                continue;
            }
            nearestDistance = distance;
            nearest = item;
        }
        return nearest;
    }
}
