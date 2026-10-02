package com.frnc.spore_add.fungus;

import java.util.List;
import java.util.Objects;

import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.compat.SporeCompat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 「真菌该不该打这个生物」的全部判断。纯判断，不碰世界。
 *
 * <h2>为什么要有这个类</h2>
 * 需求是「真菌会尝试攻击一切生物获取进化点 / 杀戮点，<b>如果打得过就进行攻击，否则放弃</b>」。
 * 前一半靠 {@link HuntPreyGoal} 把候选面铺开，后一半就是本类的 {@link #canWin}。
 *
 * <h2>判定为什么放在事件里而不是目标谓词里</h2>
 * 光把判定写进我们自己的目标谓词是<b>不够</b>的：Spore 自己那条「攻击所有生物」的目标
 * （{@code Infected#addTargettingGoals} 里由 {@code at_mob} 控制的那条，优先级 1）会绕过我们的谓词，
 * 于是铁傀儡照样会被锁定，「打得过才打」形同虚设。所以判定落在
 * {@code LivingChangeTargetEvent} 这个<b>所有</b>目标来源的汇合点上——见 {@code ModEvents}。
 * 本类只回答"能不能赢"，由那边决定要不要拦。
 */
public final class FungusCombat {

    private FungusCombat() {
    }

    // ------------------------------------------------------------------
    // 谁算真菌、谁算猎物
    // ------------------------------------------------------------------

    /** 这个实体是不是真菌阵营（{@code #spore:fungus_entities}）。 */
    public static boolean isFungus(Entity entity) {
        return entity.getType().is(SporeCompat.fungusEntities());
    }

    /**
     * 这个生物在不在 Spore 的「不主动攻击」名单里。
     *
     * <p>逐字复刻 {@code Utilities.TARGET_SELECTOR_PREDICATE} 里的写法，包括那条容易漏的规则：
     * 以 <b>冒号结尾</b>的条目（{@code creeperoverhaul:}）表示"整个模组的实体"，
     * 按命名空间前缀比对，而不是当成一个普通的 id。
     *
     * <p>刻意不做缓存：判定每秒只发生几次，而缓存要跟着数据包重载失效，得不偿失。
     */
    public static boolean isBlacklisted(Entity entity) {
        List<? extends String> blacklist = SporeCompat.targetBlacklist();
        if (blacklist.isEmpty()) {
            return false;
        }
        String id = entity.getEncodeId();
        for (String entry : blacklist) {
            if (entry.endsWith(":") && id != null) {
                String[] namespacePrefix = entry.split(":");
                String[] ownId = id.split(":");
                if (Objects.equals(namespacePrefix[0], ownId[0])) {
                    return true;
                }
            }
        }
        return id != null && blacklist.contains(id);
    }

    /**
     * 这个生物能不能进「攻击一切」的候选池。
     *
     * <p>三条排除：
     * <ul>
     *   <li><b>真菌自己</b>——它们本来就同阵营，Spore 的 {@code TARGET_SELECTOR} 也排除了它们；</li>
     *   <li><b>Spore 的黑名单</b>——见 {@link #isBlacklisted}（这里有别的模组的实体，见 {@code SporeCompat}）；</li>
     *   <li><b>玩家</b>——玩家的锁定完全交给 Spore 原有逻辑。需求里的判定明确不管玩家，
     *       所以这里不是"排除玩家不攻击"，而是"不在这一条路径上重复处理玩家"，
     *       玩家的行为与装本 mod 之前逐字一致。</li>
     * </ul>
     *
     * <p>刻意<b>不</b>看 Spore 的 {@code at_an}（是否攻击动物）：需求是"攻击一切生物"，
     * 而动物恰恰是 Spore 默认放过的那一类。名单只认 {@code blacklist}。
     */
    public static boolean isHuntable(LivingEntity candidate) {
        if (candidate instanceof Player || isFungus(candidate)) {
            return false;
        }
        return !isBlacklisted(candidate);
    }

    // ------------------------------------------------------------------
    // 打得过吗
    // ------------------------------------------------------------------

    /**
     * 真菌能不能打赢这个目标。
     *
     * <h2>判据：谁先死</h2>
     * 把双方都折算成「每秒输出」与「有效生命」，比谁的击杀耗时更短：
     * <pre>
     *   我打死它要 selfDps 分之 targetEhp 秒
     *   它打死我要 targetDps 分之 selfEhp 秒
     * </pre>
     * 左边不大于右边的 {@code huntCautionRatio} 倍就打。倍率 1.0 是"稳赢才打"，
     * 调大就更莽（愿意拿时间换命），调小就更怂。
     *
     * <h2>两处刻意的简化</h2>
     * <ul>
     *   <li><b>不还手 = 稳赢。</b>{@code targetDps <= 0} 直接返回真：牛、羊、村民这类生物对真菌
     *       构不成任何威胁，判定它们纯属浪费。它们<b>没有</b> {@code ATTACK_DAMAGE} 属性，
     *       而"取不到"这件事由 {@link #attributeValue} 统一折算成 0，正好是这个语义。</li>
     *   <li><b>每秒输出就取攻击力。</b>原版生物的攻击间隔是 20 tick，也就是每秒一下，
     *       所以"每秒输出 = 攻击力"对它们是对的。玩家用剑时实际略高于此（挥砍冷却短于 1 秒），
     *       但玩家本来就不走这条判定，无所谓。</li>
     * </ul>
     *
     * <p>算不出来的东西（远程、药水、苦力怕自爆、图腾、附魔、队友……）一律不计——
     * 这是"开打前的粗略掂量"，不是战斗模拟器。它只需要把"明显打不过"的那批挡下来。
     *
     * <p>默认数值下（{@code inf_human} 15 血 / 6 攻击 / 1 护甲）：打普通僵尸会开打、
     * 打牛会开打、打铁傀儡会放弃、打监守者会放弃。
     */
    public static boolean canWin(LivingEntity self, LivingEntity target) {
        double targetDps = dps(target);
        if (targetDps <= 0.0D) {
            return true;
        }
        double selfDps = dps(self);
        if (selfDps <= 0.0D) {
            return false;
        }
        double ticksToKillTarget = effectiveHealth(target) / selfDps;
        double ticksToDie = effectiveHealth(self) / targetDps;
        return ticksToKillTarget <= ticksToDie * SporeAddFungusConfig.huntCautionRatio();
    }

    /** 每秒输出。没有 {@code ATTACK_DAMAGE} 属性的生物返回 0（= 不会还手）。 */
    private static double dps(LivingEntity entity) {
        return attributeValue(entity, Attributes.ATTACK_DAMAGE);
    }

    /**
     * 有效生命：血 × (1 + 护甲 × 每点加成)。
     *
     * <p>那个"每点加成"来自配置项 {@code fungus.huntArmorEhpPerPoint}，默认 0.04——
     * 原版的护甲减伤<b>同时取决于护甲与这一击的伤害</b>，而这里要做的是"还没交手，先估谁更硬"，
     * 拿不到"这一击多大"，所以退化成"20 点护甲减 80%"这个上界。它只在判定里用，不改真实伤害。
     */
    private static double effectiveHealth(LivingEntity entity) {
        return entity.getMaxHealth()
                * (1.0D + attributeValue(entity, Attributes.ARMOR) * SporeAddFungusConfig.huntArmorEhpPerPoint());
    }

    /**
     * 安全地读一个属性；这个生物没有该属性时返回 0。
     *
     * <h2>为什么不能直接用 {@code getAttributeValue}</h2>
     * 本方法的参数是<b>任意</b> {@code LivingEntity}——真菌会去攻击世上的一切生物。
     * 而 {@code LivingEntity#getAttributeValue} 在属性缺失时<b>不返回 0，而是抛异常</b>：
     * 它落到 {@code AttributeSupplier#getValue}，那里对取不到的属性直接
     * {@code throw new IllegalArgumentException("Can't find attribute " + ...)}。
     *
     * <p>这条很容易踩：{@code ATTACK_DAMAGE} 只有"会主动攻击"的生物才有，
     * 牛 / 鸡 / 羊 / 村民的属性表里根本没有它（它们的表只由
     * {@code createLivingAttributes()} 那几个通用属性组成）。所以拿
     * {@code getAttributeValue(ATTACK_DAMAGE)} 去掂量一头牛，会把整局游戏崩掉。
     * <b>本项目真的踩过一次这个坑，症状就是启动后崩在
     * {@code Can't find attribute minecraft:generic.attack_damage}。</b>
     *
     * <p>{@code getAttribute} 是安全的：属性表里没有就返回 {@code null}，
     * 这也正是 {@code FungusSenses}、{@code PlayerHatredBuffs} 那几处一直在用的写法。
     */
    private static double attributeValue(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }
}
