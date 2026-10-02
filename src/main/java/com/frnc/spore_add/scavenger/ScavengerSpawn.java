package com.frnc.spore_add.scavenger;

import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.Harbinger.Spore.Sentities.BasicInfected.InfectedHuman;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.entity.ModEntities;
import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * 需求 2：菌染人类生成时按概率转变成拾荒者，概率随附近掉落物数量提高。
 *
 * <h2>概率曲线</h2>
 * <pre>
 *   概率 = minChance + (maxChance - minChance) × min(1, 附近掉落物件数 / itemCountForMax)
 * </pre>
 * 默认就是需求写的 10% ~ 50%：一件战利品都没有时 10%，攒够 32 件时 50%，中间线性。
 * 于是拾荒者天然出现在"刚打完一架、满地掉落物"的战场附近——这既是需求，
 * 也是一条很自然的世界叙事：有东西可捡的地方才有拾荒者。
 *
 * <h2>为什么推迟一 tick 再换</h2>
 * 判定挂在 {@code EntityJoinLevelEvent} 上，而那个事件<b>在实体真正入列之前</b>触发，
 * 并且 Forge 明确警告过：此时底层区块可能还没到 {@code FULL} 状态，
 * 直接在处理器里做世界交互（比如再生成一个实体）会造成<b>区块加载死锁</b>。
 *
 * <p>所以这里只掷骰并<b>排一个下一拍执行的任务</b>，真正的新增/移除放到那时候做。
 * 代价是有一 tick 的窗口里那只菌染人类还活着——玩家完全看不出来，
 * 而换来的是不会因为一次生成就把服务器卡死。
 *
 * <p>返回 true 表示"已经排定转变"，调用方据此跳过给这只实体配 AI 目标（它反正要被替换掉）。
 */
public final class ScavengerSpawn {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ScavengerSpawn() {
    }

    /**
     * 掷一次转变骰；命中就排定替换。
     *
     * <p><b>只对普通菌染人类生效</b>：{@code Scavenger instanceof InfectedHuman} 为真，
     * 不显式排除的话拾荒者自己也会被反复"转变成拾荒者"，每次都白换一遍实体。
     */
    public static boolean scheduleTransform(Infected candidate) {
        if (!(candidate instanceof InfectedHuman human) || human instanceof Scavenger) {
            return false;
        }
        if (!(human.level() instanceof ServerLevel level)) {
            return false;
        }
        // 需求 3：现存拾荒者越多，转换概率越低；到上限时系数正好为 0
        double chance = transformChance(level, human.position()) * ScavengerPopulation.transformFactor();
        if (chance <= 0.0D || human.getRandom().nextDouble() >= chance) {
            return false;
        }

        // 推到下一拍再动世界，理由见类注释
        level.getServer().execute(() -> transform(level, human));
        return true;
    }

    /** 把这只菌染人类换成拾荒者。 */
    private static void transform(ServerLevel level, InfectedHuman human) {
        // 这一 tick 里它可能已经被别的东西清掉了（同伙的清理、Despawn、玩家击杀……）
        if (human.isRemoved() || !human.isAlive()) {
            return;
        }
        Scavenger scavenger = ModEntities.SCAVENGER.get().create(level);
        if (scavenger == null) {
            LOGGER.warn("[SporeAdd] 拾荒者实体创建失败，这只菌染人类不转变");
            return;
        }

        // 位置与朝向原样接手
        scavenger.moveTo(human.getX(), human.getY(), human.getZ(), human.getYRot(), human.getXRot());
        // 血量也接手：不接的话"把一只快死的菌染人类换成一只有满血的拾荒者"会变成刷血漏洞
        scavenger.setHealth(Math.min(human.getHealth(), scavenger.getMaxHealth()));

        // 名字、持久化、以及"是否已与心智链接"这三样是玩家/存档能感知到的，必须带过去。
        // 尤其是 linked：需求 4 的资源交付链第一条就看它，丢了的话它会从"直接给心智"退化成"喂同伙"。
        scavenger.setCustomName(human.getCustomName());
        scavenger.setCustomNameVisible(human.isCustomNameVisible());
        scavenger.setPersistent(human.isPersistenceRequired());
        scavenger.setLinked(human.getLinked());

        // 身上挂着的效果（冻伤、中毒、同伴给的增益……）一并继承
        for (MobEffectInstance effect : human.getActiveEffects()) {
            scavenger.addEffect(new MobEffectInstance(effect));
        }

        level.addFreshEntity(scavenger);
        human.discard();
    }

    /**
     * 这一次生成转变成拾荒者的概率。
     *
     * <p>统计范围内的 {@code ItemEntity}——是"掉落物实体"的件数，不是物品总数。
     * 一组 64 个铁锭算<b>一件</b>掉落物。用件数而不是总数，是因为玩家看到的就是"地上有几堆东西"，
     * 而且这样也不必为了算概率去遍历每一堆的内容。
     */
    private static double transformChance(ServerLevel level, Vec3 pos) {
        double min = SporeAddFungusConfig.scavengerTransformMinChance();
        // 上限低于下限时按下限算，否则曲线会反向（配置写反了也不该出现"掉落物越多概率越低"）
        double max = Math.max(min, SporeAddFungusConfig.scavengerTransformMaxChance());
        if (max <= min) {
            return min;
        }

        double radius = SporeAddFungusConfig.scavengerTransformItemRadius();
        int items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos, pos).inflate(radius)).size();
        double progress = Math.min(1.0D, items / (double) SporeAddFungusConfig.scavengerTransformItemCountForMax());
        return min + (max - min) * progress;
    }
}
