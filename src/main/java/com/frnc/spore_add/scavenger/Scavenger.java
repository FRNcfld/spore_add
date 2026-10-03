package com.frnc.spore_add.scavenger;

import java.util.ArrayList;
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
import com.frnc.spore_add.fungus.CollectLootGoal;
import com.frnc.spore_add.sound.ModSounds;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

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

    // ------------------------------------------------------------------
    // 只属于拾荒者的隐藏式字幕
    // ------------------------------------------------------------------

    /**
     * 环境音。换成自己的声音事件之后，字幕写的就是「拾荒者低吼」而不是「菌染者低吼」。
     *
     * <p><b>音频一个字节都没换</b>：{@code sounds.json} 里指向的还是 Spore 那五个 ogg
     * （{@code spore:growl1..5}），所以听感与原来完全一样，变的只有字幕。
     * 这是刻意的——它的模型、贴图、音效三者本来就与菌染人类完全一致（见 {@code ScavengerRenderer}
     * 的类注释），光靠听分不出它们，字幕是唯一能分辨的途径。
     */
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SCAVENGER_AMBIENT.get();
    }

    /** 受伤。音频沿用 Spore 的受伤音，只换字幕。 */
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SCAVENGER_HURT.get();
    }

    /** 死亡。同上。 */
    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SCAVENGER_DEATH.get();
    }

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

        // 存满了就去交付。**夹在逃跑与拾荒之间**：存满时这一条优先于继续捡东西
        // （需求：存储即将蓄满时去寻找队友），但仍然让位于逃跑（命比货重要）。
        // 用 min 夹一下是为了兜住"使用者把 lootPriority 调到 1"的情况——那时两条同级，
        // 而同级里先注册的赢，所以这一条仍然排在前（见下面拾荒那行的注册顺序）。
        goalSelector.addGoal(Math.min(1, SporeAddFungusConfig.scavengerLootPriority()),
                new ScavengerSeekAllyGoal(this));

        // 拾荒。优先级来自配置，默认 2——必须比逃跑低（数字大），否则它会为了捡东西而不跑
        goalSelector.addGoal(SporeAddFungusConfig.scavengerLootPriority(), new ScavengerLootGoal(this));

        // 以下几项照抄 Spore 给普通感染体的配置（优先级 3/4/4/5/6/7、闲逛 0.8、受惊 1.5），
        // 只是去掉了所有攻击相关的东西。
        //
        // **这些优先级与移速刻意不开放配置**：它们不是玩法旋钮，而是"这只生物像 Spore 的普通感染体"
        // 这件事本身。开放出来只会诱人把 AI 顺序调坏（比如让吃残骸抢在逃跑前面）。
        // 真正该由使用者决定的两个优先级（逃跑、拾荒）已经在配置里，见上面两行。
        // 闲逛用自己那个"朝战利品偏"的版本（见 ScavengerWanderGoal），不是原版的纯随机闲逛。
        // 优先级与移速都不变（4 / 0.8），只是换了选落点的方式。
        goalSelector.addGoal(3, new LocalTargettingGoal(this));
        goalSelector.addGoal(4, new ScavengerWanderGoal(this, 0.8D));
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
     * 远离玩家也不消失。
     *
     * <h2>为什么光拦 Spore 的 Despawning System 不够</h2>
     * 「消失」其实有<b>两套</b>，{@code ScavengerDespawnProtectionMixin} 只挡住了其中一套：
     * <ol>
     *   <li>Spore 自己的 Despawning System（{@code cleanUpMobs} 按上限+黑名单清理）——靠那个 mixin；</li>
     *   <li><b>原版</b>的「走远就消失」（{@code Mob#checkDespawn} → {@code removeWhenFarAway}）——这里。</li>
     * </ol>
     * Spore 的 {@code Infected} 也覆写了 {@code removeWhenFarAway}，但它的判据是
     * {@code getEvoPoints()}（攒够 {@code min_kills} 才算"够格留下"），最后仍会落回原版默认值。
     * 而拾荒者<b>永远攒不到进化点</b>——它不参战（没有攻击目标）、进化也被掐掉了
     * （见 {@link #tickEvolution}），于是它会一直停在"不够格"那一档上，
     * 玩家一走远就被原版清掉。这与需求正相反：它的全部价值都建立在活得久上。
     *
     * <p>所以这里直接返回 false，与 Spore 给 {@code Womb} 的处理一致（那个也关掉了这一条）。
     *
     * <p><b>不必担心数量失控</b>：转变要同时过两道闸门——基础概率（附近掉落物 0 件 10%、
     * 32 件 50%）与数量系数 {@code max(0, 1 - 现存/上限)}（上限默认 20，到上限时概率正好归零，
     * 见 {@code ScavengerPopulation}）。所以"不会自己消失"不会变成"满地都是"。
     *
     * <p><b>最终会有几只由上限决定，不由基础概率决定</b>：那个系数在现存 = 上限时正好是 0，
     * 所以数量朝上限收敛；基础概率只影响收敛的快慢。想改"最终几只"就改
     * {@code scavenger.maxCount}。
     *
     * <p><b>和平难度是刻意的例外。</b>{@code Mob#checkDespawn()} 的第一条分支是
     * 「难度为和平 <b>且</b> {@code shouldDespawnInPeaceful()}」→ 直接删掉。
     * {@code Infected} 继承的是 {@code net.minecraft.world.entity.monster.Monster}
     * （已用 class 文件的 super_class 核实），那个类把该判据覆写成 {@code true}，
     * 所以<b>这里不覆写它</b>——拾荒者与其它真菌一样，在和平难度下正常消失。
     *
     * <p>这与 Spore 自己的设定一致：{@code Infected.checkMonsterInfectedRules} 里
     * 出生条件的第一句就是「难度是和平就返回 false」，也就是和平难度下真菌根本不刷。
     * 留一只免疫和平的拾荒者在这种世界里既没有同伙也没有意义，只显得不一致。
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /**
     * 死的时候把身上存的资源掉出来。
     *
     * <h2>为什么要有这一段</h2>
     * 存量存在实体自己的持久数据里，<b>随实体一起消失</b>。上限随存活时间能涨到几千，
     * 满仓时死一次就是一大笔无声损失，而且玩家看不到任何反馈。掉成物品之后，
     * 「打死一只囤了很久的拾荒者」才成为一个真正的收获。
     *
     * <h2>为什么是掉成物品，而不是转交给真菌</h2>
     * 转交（比如塞进世界暂存等心智补发）会让<b>玩家杀死拾荒者等于把资源送给真菌</b>，
     * 与激励机制正好相反。掉在地上谁都能捡，掠夺后勤才是奖励。
     *
     * <p>换算用「汇率 + 白名单」而不是回查掉落物转化表：存量是个抽象数字，
     * 反查需要遍历整张表做凑数找零，既慢又难解释，而玩家只关心打死它能捡到多少。
     */
    @Override
    public void die(DamageSource source) {
        dropStoredResources();
        super.die(source);
    }

    /** 把存量按汇率换成物品掉在原地，然后清空。 */
    private void dropStoredResources() {
        if (level().isClientSide()) {
            return;
        }
        double stored = CollectLootGoal.pendingResource(this);
        // **先清空再掉**：万一掉落实体那一步出问题，也不会留下"读档后把同一笔再掉一次"的隐患
        CollectLootGoal.setPendingResource(this, 0.0D);

        int perItem = SporeAddFungusConfig.scavengerDeathDropResourcePerItem();
        int limit = SporeAddFungusConfig.scavengerDeathDropMaxItems();
        int count = Math.min(limit, Mth.floor(stored / perItem));
        if (count <= 0) {
            return;
        }
        List<Item> pool = deathDropPool();
        if (pool.isEmpty()) {
            return;
        }
        for (int i = 0; i < count; i++) {
            // 随机取而不是轮转：表里有多项时不会一次全掉同一种
            spawnAtLocation(new ItemStack(pool.get(getRandom().nextInt(pool.size()))));
        }
    }

    /**
     * 把配置里那张掉落物 id 表解析成物品。
     *
     * <p>写坏的条目<b>跳过并记一行日志</b>，而不是抛异常——沿用工程里那条约定：
     * 配置是自由文本，一个拼错的 id 不该让一次死亡把服务器崩掉。
     *
     * <p>刻意不缓存：死亡是低频事件，而配置随时可能被改，缓存反而要处理失效。
     */
    private static List<Item> deathDropPool() {
        List<Item> pool = new ArrayList<>();
        for (String entry : SporeAddFungusConfig.scavengerDeathDropItems()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.trim());
            if (id == null) {
                LOGGER.warn("[SporeAdd] 拾荒者掉落表里这一条不是合法的 id，已跳过：{}", entry);
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) {
                LOGGER.warn("[SporeAdd] 拾荒者掉落表里这个物品不存在，已跳过：{}", id);
                continue;
            }
            pool.add(item);
        }
        return pool;
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 存活时长记在存盘数据里的键。
     *
     * <h2>为什么不能沿用实体的 {@code tickCount}</h2>
     * 最初就是用它——{@code tickCount} 看上去正好是"这只实体活了多久"，还不必额外存盘。
     * 但它<b>不写进存档</b>：玩家走远一次、区块卸载再加载，它就归零了。于是「活得越久越强」
     * 实际退化成「<b>在玩家视野里连续活得越久</b>越强」——一只活了半小时的拾荒者
     * 只要离开过视野一次，攒的成长就全没了，与需求的意思正好相反。
     *
     * <p>所以改成一个自己维护、随实体存盘的计数。这是这个类里唯一需要存盘的状态。
     */
    private static final String KEY_SURVIVAL = "spore_add:survival_ticks";

    /** 存活了多少 tick。只在服务端累加，见 {@link #tick()}。 */
    private int survivedTicks;

    /**
     * 存活了多少 tick。拾荒收益的加成、四条成长曲线、以及一次能治疗的同伴数量，都按它爬升。
     *
     * <p>只在服务端累加（见 {@link #tick}）：客户端那份不存盘，算了也没用，
     * 两边各算各的还会让显示值与实际值漂移。
     *
     * <p>存盘之前就在世界里的拾荒者没有这个字段，会从 0 重新开始——不会出错，只是没有历史积累。
     */
    public int survivalTicks() {
        return survivedTicks;
    }

    /** 存活了多少分钟。 */
    public double survivalMinutes() {
        return survivalTicks() / 1200.0D;
    }

    /**
     * 存档。**必须调 {@code super}**：Spore 的 {@code Infected} 也覆写了这一对方法
     * （饥饿、是否与心智链接等都在那里存），漏掉 super 会让那些状态一起丢失。
     */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(KEY_SURVIVAL, survivedTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // 夹到 0 以上：存档被改坏时也不该让成长曲线变成负数
        survivedTicks = Math.max(0, tag.getInt(KEY_SURVIVAL));
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
     * <p>不存盘：它只是"这一秒攒了多少零头"，重启后从 0 重算没有任何副作用
     * （与 {@code survivedTicks} 不同，那个是成长曲线的基准，必须存盘）。
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
        // 存活时长是成长曲线的基准，也是唯一存盘的状态
        survivedTicks++;
        if (tickCount % 20 == 0) {
            refreshGrowth();
        }
        // 链接名单的校验与补员。间隔由配置给（默认 5 秒）——同伴是缓慢移动的生物，
        // 而这是这一段唯一的成本（一次实体盒扫描），不该每 tick 做。
        // 用 survivedTicks 而不是 tickCount：后者读档后会从头计，与"多久重建一次"无关。
        if (survivedTicks % SporeAddFungusConfig.scavengerLinkScanIntervalTicks() == 0) {
            ScavengerLinks.refresh(this);
        }
        applyRegeneration();
        playMoveCue();
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
                    new AttributeModifier(id, name, round1(bonus), AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * 只保留 1 位小数。
     *
     * <p>{@code 分钟数 × 每分钟} 算出来是 {@code 14.600000000000001} 这种样子，
     * 它会原样出现在属性面板上（附魔/药水的 tooltip 那一栏会带一串小数）。
     * 每一条数值本身没什么精度意义——成长曲线是"活得越久越高"，
     * 差万分之一没人看得出来，但那一串小数很显眼。
     *
     * <p>注意它只作用于<b>我们加的修饰符</b>，不动 Spore 给的基础值，
     * 也不动其它模组加的修饰符。
     */
    private static double round1(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }

    /** 上一次播「移动」提示音的游戏刻。{@code Long.MIN_VALUE} 表示还没播过。 */
    private long lastMoveCueTick = Long.MIN_VALUE;

    /**
     * 走动时偶尔响一声，让字幕提示「附近有拾荒者在活动」。
     *
     * <p>用「这一 tick 实际挪了多远」判断在不在走：比去读行走动画的状态更直接，
     * 也不依赖任何动画字段的名字。站着不动时位移恰好是 0。
     *
     * <p><b>必须带冷却</b>，否则行走会每 tick 触发一次、字幕直接被刷屏
     * （间隔见 {@code scavenger.moveCueCooldownSeconds}，默认 8 秒）。
     */
    private void playMoveCue() {
        double dx = getX() - xOld;
        double dz = getZ() - zOld;
        if (dx * dx + dz * dz < 1.0E-6D) {
            return;   // 这一 tick 几乎没动
        }
        long now = level().getGameTime();
        if (now - lastMoveCueTick < SporeAddFungusConfig.scavengerMoveCueCooldownTicks()) {
            return;
        }
        lastMoveCueTick = now;
        playSound(ModSounds.SCAVENGER_STEP.get());
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
