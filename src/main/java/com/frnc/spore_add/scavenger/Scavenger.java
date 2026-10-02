package com.frnc.spore_add.scavenger;

import java.util.List;
import java.util.UUID;

import com.Harbinger.Spore.Sentities.AI.FloatDiveGoal;
import com.Harbinger.Spore.Sentities.AI.InfectedConsumeFromRemains;
import com.Harbinger.Spore.Sentities.AI.InfectedPanicGoal;
import com.Harbinger.Spore.Sentities.AI.LocHiv.LocalTargettingGoal;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.Harbinger.Spore.Sentities.BasicInfected.InfectedHuman;
import com.Harbinger.Spore.Sentities.Variants.ScamperVariants;
import com.frnc.spore_add.SporeAddFungusConfig;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.Level;

/**
 * 拾荒者：菌染人类的变体，阵营里的<b>后勤</b>。
 *
 * <h2>它做什么</h2>
 * 不参战，专职捡掉落物（{@link ScavengerLootGoal}），把收获按优先级供给同伙
 * （{@link ScavengerDelivery}），遇险就跑（{@link FleeDangerGoal}）。活得越久拾荒效率越高。
 *
 * <h2>为什么是「继承 InfectedHuman」而不是「照着写一遍」</h2>
 * 需求要求模型、贴图、音效与菌染人类完全一致。而 Spore 的两样东西只有继承 {@code InfectedHuman}
 * 才拿得到：
 * <ul>
 *   <li>{@code InfectedModel<T extends InfectedHuman>}——模型的泛型上界是它</li>
 *   <li>{@code InfectedHumanRenderer<Type extends InfectedHuman>}——渲染器同样</li>
 * </ul>
 * 继承下来之后，模型、动画、贴图、音效、掉落表全都自动一致，一行都不用重写。
 *
 * <h2>为什么构造器把类型参数丢掉了</h2>
 * {@code InfectedHuman} 只有 {@code InfectedHuman(Level)} 一个构造器，里面把实体类型<b>写死</b>成
 * {@code Sentities.INF_HUMAN.get()}。所以直接 {@code super(level)} 的话，我们这只会被登记成
 * {@code spore:inf_human}——<b>存盘之后就变回普通菌染人类了</b>（这是这个变体最本质的坑）。
 *
 * <p>解法是 {@code InfectedHumanTypeMixin}：把构造器里那次 {@code get()} 重定向成
 * 「接收者是 Scavenger 就返回我们的类型」。所以这里的 {@code type} 参数是**故意不用**的，
 * 留着只是为了满足 {@code EntityType.Builder.of} 要的工厂签名。
 *
 * <h2>两处刻意的「不调 super」</h2>
 * <ol>
 *   <li><b>{@code registerGoals} 不调 super</b>——Spore 那份会把近战、受击反击、主动锁敌
 *       一整套攻击目标都加上，而需求是「拾荒者不会进行攻击」。所以攻击目标一个都不能有，
 *       非攻击的那些由我们自己按原样补上。</li>
 *   <li><b>{@code tickEvolution} 覆写成空</b>——需求「无法进化」。这里没法靠"不实现那个接口"
 *       来达到：{@code InfectedHuman} 就实现了 {@code EvolvingInfected}，子类必然跟着实现。
 *       而 {@code InfectedHuman.baseTick} 每 tick 都会调 {@code this.tickEvolution(...)}，
 *       Java 又没法"跳过父类调祖父类"，所以最省事也最可靠的做法是把这个默认方法直接掐掉
 *       （虚分派会走到我们的覆写）。</li>
 * </ol>
 */
public class Scavenger extends InfectedHuman {

    /**
     * 「此刻正在构造的这只就是拾荒者」——只在构造那一瞬间有意义的一个标志。
     *
     * <h2>为什么需要它</h2>
     * {@code InfectedHuman} 的构造器把实体类型<b>写死</b>成 {@code Sentities.INF_HUMAN.get()}，
     * 我们必须把那次调用换成自己的类型（见 {@code InfectedHumanTypeMixin}）。但那一次调用发生在
     * {@code super()} <b>之前</b>——按 Mixin 的规矩，这种位置的处理器<b>必须是 static</b>，
     * 于是处理器里没有 {@code this}，也就没法用 {@code this instanceof Scavenger} 来判断到底是谁在构造。
     *
     * <p>所以改由子类主动报一声：{@code super(...)} 的实参<b>先于父类构造器求值</b>，
     * 正好赶在处理器读它之前把标志立起来。
     *
     * <p>用 {@code ThreadLocal} 而不是静态布尔：实体构造发生在线程各自的家务里（服务端线程、
     * 客户端的各个逻辑线程），一个全局布尔在多线程下会互相串味。
     */
    private static final ThreadLocal<Boolean> CONSTRUCTING = new ThreadLocal<>();

    public Scavenger(EntityType<Scavenger> type, Level level) {
        // type 参数故意不传下去，理由见类注释。留着它只为满足 EntityType.Builder.of 的工厂签名。
        // 但要先"报一声"，好让父类构造器里那次写死的类型换成我们的，见 CONSTRUCTING 的说明。
        super(announce(level));
    }

    /** 立标志，并把 {@code level} 原样传下去。只该出现在 {@code super(...)} 的实参里。 */
    private static Level announce(Level level) {
        CONSTRUCTING.set(Boolean.TRUE);
        return level;
    }

    /**
     * 取走并清除标志：这一次构造的是不是拾荒者。给 {@code InfectedHumanTypeMixin} 用。
     *
     * <p><b>读到就清</b>，所以哪怕某次父类构造器中途抛异常、没走到读取那一步，
     * 残留的标志也不会污染下一次构造（下一次读到的仍是"不是"）。
     */
    public static boolean claimConstruction() {
        boolean constructing = CONSTRUCTING.get() != null;
        CONSTRUCTING.remove();
        return constructing;
    }

    /** 属性与菌染人类完全一致，直接转交。 */
    public static AttributeSupplier.Builder createAttributes() {
        return InfectedHuman.createAttributes();
    }

    /**
     * 只挂非攻击目标。
     *
     * <p><b>没有 {@code super.registerGoals()}</b>：Spore 那份会加上近战与锁敌，而拾荒者不参战。
     * 下面这些是从 {@code Infected#addRegularGoals} 里挑出来的**非攻击**部分，按原优先级补上——
     * 「其他的参考 Spore」指的就是这些。
     */
    @Override
    protected void registerGoals() {
        // 第一要务：察觉危险就跑，残血无条件跑
        goalSelector.addGoal(SporeAddFungusConfig.scavengerFleePriority(), new FleeDangerGoal(this));

        // 拾荒。优先级来自配置，默认 2——必须比逃跑低（数字大），否则它会为了捡东西而不跑
        goalSelector.addGoal(SporeAddFungusConfig.scavengerLootPriority(), new ScavengerLootGoal(this));

        // 以下几项照抄 Spore 给普通感染体的配置，只是去掉了所有攻击相关的东西
        goalSelector.addGoal(3, new LocalTargettingGoal(this));
        goalSelector.addGoal(4, new RandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        goalSelector.addGoal(5, new InfectedPanicGoal(this, 1.5D));
        goalSelector.addGoal(6, new FloatDiveGoal(this));
        goalSelector.addGoal(7, new InfectedConsumeFromRemains(this));
    }

    /**
     * 拾荒者无法进化。见类注释第 2 条。
     *
     * <p>覆写成空实现而不是让 {@code FungusEvolution} 去判断类型：这样<b>无论谁</b>调用这条路
     * （Spore 自己的 {@code baseTick}、以后别人加的什么东西），进化都不会发生。
     * 单点防御比在每个调用方各判一次可靠。
     */
    @Override
    public void tickEvolution(Infected infected, List<? extends String> evolutionList, ScamperVariants variants) {
        // 刻意留空，见方法注释
    }

    /**
     * 存活了多少 tick。拾荒收益的加成、以及一次能治疗的同伴数量，都按它爬升。
     *
     * <p>用 {@code tickCount} 而不是自己再记一个字段：它就是"这只实体活了多久"，
     * 而且不需要存盘——重启之后从 0 重算，对一只生物来说是合理的（它本来就被重新放进了世界）。
     */
    public int survivalTicks() {
        return tickCount;
    }

    /** 存活了多少分钟。 */
    public double survivalMinutes() {
        return survivalTicks() / 1200.0D;
    }

    // ------------------------------------------------------------------
    // 随存活时间成长：生命 / 防御 / 速度 / 生命恢复速度
    // ------------------------------------------------------------------

    /**
     * 三条属性成长用的修饰符 id。
     *
     * <p>必须是<b>写死的 UUID</b>：刷新时靠它把上一次那个修饰符摘掉，才能做到幂等。
     * 这与 {@code PlayerHatredBuffs} 是同一手法（那边用它刷新玩家的恨意加成）。
     */
    private static final UUID GROWTH_HEALTH = UUID.fromString("0f9a7c31-5b2e-4d68-9a41-7c3e5f8b2d10");
    private static final UUID GROWTH_ARMOR = UUID.fromString("1a8b6d42-6c3f-4e79-8b52-8d4f6a9c3e21");
    private static final UUID GROWTH_SPEED = UUID.fromString("2b9c7e53-7d40-4f8a-9c63-9e5a7b0d4f32");

    /**
     * 生命回复的小数累加器。
     *
     * <p>为什么需要它：{@code heal()} 只收整数，而「每秒 0.5 点」这种速率直接取整就是 0
     * ——永远回不了血。攒够 1 点再回，速率才是准的。
     *
     * <p>不存盘：它和 {@code survivalTicks} 一样是"活着的时候才有意义"的临时量，
     * 重启后从 0 重算没有副作用。
     */
    private double regenBuffer;

    /**
     * 每 tick 推进成长。
     *
     * <p>属性<b>每秒刷一次</b>就够：四条曲线都是随存活时间平移的，看不出 1 秒内的差别，
     * 而每 tick 摘一次再加一次修饰符是无谓的开销。生命回复则要每 tick 累加。
     */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (tickCount % 20 == 0) {
            refreshGrowth();
        }
        applyRegeneration();
    }

    /** 按当前存活时长重算三条属性加成。 */
    private void refreshGrowth() {
        double minutes = survivalMinutes();
        grow(Attributes.MAX_HEALTH, GROWTH_HEALTH, "spore_add:scavenger_health",
                SporeAddFungusConfig.scavengerHealthPerMinute(),
                SporeAddFungusConfig.scavengerHealthMaxBonus(), minutes);
        grow(Attributes.ARMOR, GROWTH_ARMOR, "spore_add:scavenger_armor",
                SporeAddFungusConfig.scavengerArmorPerMinute(),
                SporeAddFungusConfig.scavengerArmorMaxBonus(), minutes);
        grow(Attributes.MOVEMENT_SPEED, GROWTH_SPEED, "spore_add:scavenger_speed",
                SporeAddFungusConfig.scavengerSpeedPerMinute(),
                SporeAddFungusConfig.scavengerSpeedMaxBonus(), minutes);
    }

    /**
     * 摘掉旧修饰符，再按「每分钟涨 {@code perMinute}、封顶 {@code max}」重加一个。
     *
     * <p><b>先摘后加是幂等的关键</b>：只加不摘的话，每秒都会叠一个新修饰符，
     * 几分钟后这只拾荒者就成了数值怪物（而且属性面板上一串一模一样的条目）。
     * 同理，改配置之后不必重启——下一次刷新自然按新值来。
     */
    private void grow(Attribute attribute, UUID id, String name,
                      double perMinute, double max, double minutes) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;   // 属性缺失（别的模组改过属性表）就跳过这一条，不抛异常
        }
        instance.removeModifier(id);
        double bonus = Math.min(max, minutes * perMinute);
        if (bonus > 0.0D) {
            instance.addPermanentModifier(
                    new AttributeModifier(id, name, bonus, AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * 生命回复：按存活时间爬升的「每秒回复点数」，攒够 1 点才真的回一次血。
     *
     * <p>满血时把累加器清零，而不是留着——否则"一直挂着不掉血"会攒下一大笔，
     * 受伤后立刻一次性回满。那是攒了十分钟的爆发治疗，不是回复速度。
     */
    private void applyRegeneration() {
        double hps = Math.min(SporeAddFungusConfig.scavengerRegenMaxHps(),
                survivalMinutes() * SporeAddFungusConfig.scavengerRegenHpsPerMinute());
        if (hps <= 0.0D || getHealth() >= getMaxHealth()) {
            regenBuffer = 0.0D;
            return;
        }
        regenBuffer += hps / 20.0D;
        int whole = (int) regenBuffer;
        if (whole > 0) {
            regenBuffer -= whole;
            heal(whole);
        }
    }
}
