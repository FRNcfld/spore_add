package com.frnc.spore_add.event;

import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.advancement.ModTriggers;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.effect.BuffLevels;
import com.frnc.spore_add.effect.ModEffects;
import com.frnc.spore_add.enchantment.ModEnchantments;
import com.frnc.spore_add.enchantment.Warmth;
import com.frnc.spore_add.fungus.CollectLootGoal;
import com.frnc.spore_add.fungus.FungusCombat;
import com.frnc.spore_add.fungus.FungusEvolution;
import com.frnc.spore_add.fungus.FungusSenses;
import com.frnc.spore_add.fungus.HuntPreyGoal;
import com.frnc.spore_add.network.ModNetwork;
import com.frnc.spore_add.scavenger.Scavenger;
import com.frnc.spore_add.scavenger.ScavengerPopulation;
import com.frnc.spore_add.scavenger.ScavengerSpawn;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 本 mod 的游戏事件处理。跑在 Forge 事件总线上（{@code @Mod.EventBusSubscriber} 默认就是 FORGE 总线）。
 *
 * <p>可燃等级的归零有两条路，都在本类里：
 * <ol>
 *   <li>可燃被 Spore 消耗（触发）—— {@link #onIgnitableTriggered}，<b>无条件</b>清零，
 *       即使实体还泡在燃料里也照清；</li>
 *   <li>可燃自然到期 —— {@link #onIgnitableExpired}，说明已经离开燃料足够久。</li>
 * </ol>
 * 刻意<b>没有</b>"离开燃料就清零"这一路（曾经有，会让短暂离开再回来被打回 1 级），原因见
 * {@link BuffLevels} 的类注释。
 *
 * <p>另一半是本 mod 的「真菌加强」那四项（{@code fungus} 包）：
 * {@link #onEntityJoin}（补感知加成、挂猎杀目标）、{@link #onLivingTick}（加速进化）、
 * {@link #onChangeTarget}（「打得过才打」的判定闸门）、{@link #onFreezeDamage}（冰冻伤害倍率）。
 * 全部集中在 {@code Infected} 上，与可燃那一套互不相干。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID)
public final class ModEvents {

    private ModEvents() {
    }

    /**
     * 需求 2：「可燃被触发」→ 累加爆燃。
     *
     * <p><b>为什么用 {@code MobEffectEvent.Remove} 当触发信号</b>：Spore 没有为可燃提供任何事件，
     * 它自己的触发逻辑写在 {@code HandlerEvents.DefenseBypass(LivingDamageEvent)} 里，而且触发时会
     * <b>主动调用 {@code removeEffect(IGNITABLE)}</b>。所以"可燃被移除"就是"被触发"的精确信号。
     *
     * <p>这个信号不会和别的路径混淆：自然到期走的是 {@code MobEffectEvent.Expired}（不是 {@code Remove}），
     * 死亡的路径绕开了 {@code removeEffect}，而 Spore 的可燃 {@code getCurativeItems()} 返回空列表，
     * 所以牛奶桶也解不掉它。唯一能造成误报的是 {@code /effect clear} 这类命令，下面用火焰刻数过滤掉大部分。
     *
     * <p>比复刻 Spore 那张硬编码的伤害类型概率表（闪电 1.0 / 爆炸 0.8 / 玩家爆炸 0.6 / 烟花 0.3 /
     * in_fire 0.2 / on_fire 0.1，外加任何伤害 1%）稳得多——它以后改数值我们不用跟着改。
     */
    @SubscribeEvent
    public static void onIgnitableTriggered(MobEffectEvent.Remove event) {
        MobEffect ignitable = SporeCompat.ignitable();
        if (ignitable == null || event.getEffect() != ignitable) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        // Spore 的触发一定会先给受害者加火焰刻数再移除 buff；靠这一点排除 /effect clear 之类的误报
        if (entity.getRemainingFireTicks() <= 0) {
            return;
        }
        int level = BuffLevels.ignitable(entity);
        if (level <= 0) {
            return;
        }

        // 触发即清零：即使实体还泡在燃料里也照清（这是刻意的）。于是持续泡着反复触发时，
        // 每次触发都拿"这一刻累积了多少"结算，之后又从 1 级重新往上爬。
        // 先读 level 再清，所以下面结算用的仍是触发瞬间的等级。
        BuffLevels.setIgnitable(entity, 0);

        ModEffects.apply(entity, level);
    }

    /**
     * 第二路归零检测：可燃自然到期 → 说明已经离开燃料足够久，等级清零。
     *
     * <p>这一路不需要闸门，因为泡在燃料里时它每秒都会被刷新、而持续时长有 10 秒，所以不可能中途到期。
     * 反过来说：一旦到期，就足以证明人已经离开了。
     */
    @SubscribeEvent
    public static void onIgnitableExpired(MobEffectEvent.Expired event) {
        MobEffect ignitable = SporeCompat.ignitable();
        if (ignitable == null || event.getEffectInstance().getEffect() != ignitable) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        BuffLevels.setIgnitable(entity, 0);
        ModNetwork.syncLevels(entity);
    }

    /** 爆燃自己到期 → 层数一并清掉，免得留下一个没有 buff 却仍在加伤的幽灵计数。 */
    @SubscribeEvent
    public static void onDeflagrationExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance().getEffect() != ModEffects.DEFLAGRATION.get()) {
            return;
        }
        clearDeflagration(event.getEntity());
    }

    /** 爆燃被主动移除（牛奶桶等）→ 同样清掉层数。 */
    @SubscribeEvent
    public static void onDeflagrationRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect() != ModEffects.DEFLAGRATION.get()) {
            return;
        }
        clearDeflagration(event.getEntity());
    }

    private static void clearDeflagration(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        BuffLevels.clearDeflagration(entity);
        ModNetwork.syncLevels(entity);
    }

    /**
     * 「烈阳」附魔：<b>满四件</b>时完全免疫冻伤。
     *
     * <p>用 {@code MobEffectEvent.Applicable} 而不是自己轮询实体：它正好在
     * {@code LivingEntity#canBeAffected} 里触发，所以不论施加方是本 mod 的冷却液 / 液态寒冷，
     * 还是 Spore 自己的 CDU、冰霜肿瘤、PCI 武器，都会被这一道挡下。
     *
     * <p><b>不足四件的情况不在这里处理。</b>这个事件只能允许或拒绝，改不了效果实例，
     * 而"每件削弱 25%"必须改实例（时长与层数都要乘）。那一半交给
     * {@code WarmthFrostbiteScalingMixin}——它在效果入体前把实例缩放好。
     * 所以分工是：这里管"100% 直接不放行"，mixin 管"0~75% 放行但缩水"。
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffect frostbite = SporeCompat.frostbite();
        if (frostbite == null || event.getEffectInstance().getEffect() != frostbite) {
            return;
        }
        if (Warmth.resistanceFraction(event.getEntity()) >= 1.0F) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * 「烈阳」附魔：装备变化时触发「永恒炽阳」进度，并把已经挂在身上的冻伤清掉。
     *
     * <p>为什么还要清：上面那道只能拦住<b>将来</b>的施加，穿上护甲之前就已经存在的冻伤会继续按
     * 原时长生效（Spore 给的是 30~60 秒）。这里补一次移除。
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        // 只在"刚穿上的这一件是护甲、且带烈阳"时才算达成。
        // 必须判槽位：LivingEquipmentChangeEvent 对主手与副手也会触发，只看 getTo() 的话
        // 把附魔护甲拿在手上就会被当成装备（这是修掉的那个 bug）。
        if (entity instanceof ServerPlayer player
                && event.getSlot().getType() == EquipmentSlot.Type.ARMOR
                && EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.WARMTH.get(), event.getTo()) > 0) {
            ModTriggers.WARMTH_EQUIPPED.trigger(player);
        }
        // 只有"满四件、完全免疫"才把身上已有的冻伤清掉。
        // 不足四件时按新规则是"削弱"而不是"免疫"，那就不该清除已经挂着的那份。
        if (Warmth.resistanceFraction(entity) >= 1.0F) {
            MobEffect frostbite = SporeCompat.frostbite();
            if (frostbite != null && entity.hasEffect(frostbite)) {
                entity.removeEffect(frostbite);
            }
            // 光移除 buff 不够：屏幕结霜遮罩只看 getTicksFrozen() > 0，而冻结刻数是 Spore 的
            // 冻伤 tick 直接 setTicksFrozen(+100) 叠上去的（见 FrostbiteAllMobsMixin 第三条），
            // 移除 buff 之后那几百点还在，靠 aiStep 每 tick -2 要好几秒才退干净。
            // 既然此刻已经"完全免疫"，就一并抹掉——否则玩家的观感是"穿回护甲了屏幕还白着"。
            entity.setTicksFrozen(0);
        }
    }

    /**
     * 「烈阳」附魔：免疫冰冻类伤害。
     *
     * <p>细雪那一路的冻结伤害其实已经被 {@code WarmthFreezeMixin} 挡掉了（它依赖 {@code canFreeze}），
     * 但别的来源可以直接造成 {@code freeze} 伤害——Spore 的冻伤 tick 就是这么做的——所以这里再兜一层。
     */
    @SubscribeEvent
    public static void onFreezingDamage(LivingAttackEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_FREEZING) && Warmth.isWorn(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * 需求 2 + 6：爆燃放大携带者受到的火焰伤害。
     *
     * <p>用 {@code LivingHurtEvent} 而不是 {@code LivingDamageEvent}：前者在护甲、抗性、吸收<b>之前</b>触发，
     * 加进去的数值会照常走这些减免——这正是"并入同一次火焰伤害"想要的行为。后者是最终值，
     * 在它上面加伤等于绕过护甲。
     *
     * <p>「若实体不免疫火焰」这个条件基本是白送的：{@code Entity#fireImmune()} 与抗火药剂都会让原版的
     * {@code hurt} 提前 return，根本走不到这个事件。这里显式判一次是为了把意图写清楚。
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (!event.getSource().is(DamageTypeTags.IS_FIRE) || entity.fireImmune()) {
            return;
        }
        // 以 buff 在不在为准，而不是只看层数：万一有哪条路径没清干净，也不该继续加伤
        if (!entity.hasEffect(ModEffects.DEFLAGRATION.get())) {
            return;
        }
        int stacks = BuffLevels.deflagration(entity);
        if (stacks <= 0) {
            return;
        }
        event.setAmount(event.getAmount() + BuffLevels.fireDamageBonus(entity, stacks));
    }

    // ==================================================================
    // 真菌加强
    // ==================================================================

    /**
     * 真菌进入世界时补两样东西：感知加成，与「猎杀一切生物」的目标。
     *
     * <h2>为什么要用「加入世界」这个时机</h2>
     * 两者都只能在这时候做。目标要在<b>构造函数跑完之后</b>才能加进选择器，而 {@code registerGoals}
     * 是构造期调的、还被各子类各自覆写（有的调 {@code super}，有的不调）——从那里挂会漏掉一批。
     * 属性同理：{@code createAttributes} 是静态的，拿不到实例。
     *
     * <p>这个事件在实体被加进世界时触发（新生成、从存档读出来、区块重新加载都会触发），
     * 所以两处都必须<b>幂等</b>：属性那边靠自己先摘后加（见 {@code FungusSenses}），
     * 目标这边靠先扫一遍现有目标（见 {@link #hasHuntGoal}）。
     *
     * <p>只处理服务端：AI 与属性是服务端的事，客户端那份加了也没用。
     * 另外这个事件在实体真正入列之前就触发，所以这里<b>不能碰世界</b>（会撞上区块加载死锁），
     * 好在这两件事都只碰实体自己。
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // 拾荒者的现存数量是增量维护的（见 ScavengerPopulation）：这里记进、onEntityLeave 记出。
        // 只记服务端——实体真正存在的地方只有服务端，客户端那些是镜像。
        // 这一句必须放在下面"只处理 Infected"的判断之前，因为那些判断会提前 return。
        if (!event.getLevel().isClientSide()) {
            ScavengerPopulation.onJoin(event.getEntity());
        }
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Infected infected)) {
            return;
        }
        // 顺序有讲究：感知必须先补。目标在构造时就把搜索半径算好缓存进 TargetingConditions 了
        // （NearestAttackableTargetGoal 的构造器里取 getFollowDistance），先挂目标的话，
        // 这条目标的视觉半径会一直是加感知之前的值——直到实体重新入列为止。
        FungusSenses.apply(infected);

        // 拾荒者到此为止。它的 AI 目标全在 Scavenger#registerGoals 里配好了，这里一个都不要加：
        // 加了 HuntPreyGoal 会给它设攻击目标，而它的拾荒目标在"有攻击目标"时会主动让路
        // （见 CollectLootGoal#canUse）——于是它会放着满地掉落物不管、去追一个自己永远不会打的敌人。
        if (infected instanceof Scavenger) {
            return;
        }

        // 需求 2：菌染人类生成时按概率转变成拾荒者。
        // 排定了转变就不要再给这只配目标了——它下一拍就会被换成拾荒者，配了也是白配。
        if (ScavengerSpawn.scheduleTransform(infected)) {
            return;
        }

        // 优先级取 3（比 Spore 的玩家/白名单目标低），理由见 HuntPreyGoal 的类注释。
        // 这里不判 huntEnabled：开关交给目标自己的 canUse，于是改配置不需要让实体重新入列
        if (!hasGoal(infected.targetSelector, HuntPreyGoal.class)) {
            infected.targetSelector.addGoal(HuntPreyGoal.PRIORITY, new HuntPreyGoal(infected));
        }
        if (!hasGoal(infected.goalSelector, CollectLootGoal.class)) {
            infected.goalSelector.addGoal(SporeAddFungusConfig.lootPriority(), new CollectLootGoal(infected));
        }
    }

    /**
     * 实体离开世界：把拾荒者计数减掉。
     *
     * <p>用 {@code EntityLeaveLevelEvent} 而不是死亡事件：区块卸载时实体会离开世界但<b>不会死</b>，
     * 只挂死亡的话玩家走远一次计数就再也降不下来，最终卡在"满员"、拾荒者再也不出现。
     */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            ScavengerPopulation.onLeave(event.getEntity());
        }
    }

    /**
     * 这个真菌身上是不是已经挂过某一类目标了。
     *
     * <p>靠它保证反复进出区块不会越挂越多——{@link EntityJoinLevelEvent} 在实体每次被加进世界时
     * 都会触发一次（新生成、从存档读出来、区块重新加载），不查重的话一个真菌在几次区块往返之后
     * 会挂上十几个一模一样的目标，行为开始变得诡异（同一个目标被反复抢）。
     */
    private static boolean hasGoal(GoalSelector selector, Class<? extends Goal> type) {
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (type.isInstance(wrapped.getGoal())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 需求 1「更易进化」的入口。逐 tick 转交，真正的判断在 {@code FungusEvolution}。
     *
     * <p>先做 {@code instanceof} 再做别的：它在所有 LivingEntity 上每 tick 都会跑一次，
     * 类型检查是最便宜的那一道筛子。
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Infected infected)) {
            return;
        }
        if (infected.level().isClientSide()) {
            return;
        }
        FungusEvolution.tick(infected);
    }

    /**
     * 需求 2 的「打得过就攻击，否则放弃」——判定闸门。
     *
     * <h2>为什么拦在这里，而不是写进我们那条目标的谓词</h2>
     * Spore 自己那条「攻击所有生物」的目标（{@code at_mob} 控制的那条，优先级 1）
     * 会绕过我们的谓词，于是铁傀儡照样被锁定、判定形同虚设。
     * {@code LivingChangeTargetEvent} 是<b>所有</b>目标来源（Spore 的、我们的、受击反击的）的汇合点，
     * 在这里拦一次，判定才真的成立。取消它 = 目标保持不变。
     *
     * <h2>两处刻意不拦</h2>
     * <ul>
     *   <li><b>玩家</b>：需求里的判定明确不管玩家，玩家的锁定与其它行为与装本 mod 之前逐字一致；</li>
     *   <li><b>刚打过我的那个</b>：挨打一定要还手，否则真菌会站着被磨死。
     *       判据是"新目标就是 {@code getLastHurtByMob()}"，也正好是 Spore 的受击反击目标所设的那个。</li>
     * </ul>
     *
     * <h2>被拦下之后的代价</h2>
     * 拦下只是"这次不换目标"，并没有记下"这个打不过"。于是 Spore 那条目标下一拍还会再挑中它、
     * 再被拦一次——每秒几次属性读取，可以忽略；换来的是"猎物自己变强/变弱之后判定会自然重算"，
     * 不需要任何过期机制。
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!SporeAddFungusConfig.huntEnabled()) {
            return;
        }
        LivingEntity self = event.getEntity();
        if (self.level().isClientSide() || !(self instanceof Infected)) {
            return;
        }
        // 拾荒者不参战：判定是给"会打人的真菌"准备的，对它没有意义。
        // 它本来就没有锁敌目标，这里再挡一道只是为了让意图写在明处
        if (self instanceof Scavenger) {
            return;
        }
        LivingEntity target = event.getNewTarget();
        if (target == null || target instanceof Player || target == self.getLastHurtByMob()) {
            return;
        }
        if (!FungusCombat.canWin(self, target)) {
            event.setCanceled(true);
        }
    }

    /**
     * 需求 3：「真菌受到的冰冻伤害改为 2 倍」。
     *
     * <p>判据用伤害类型标签 {@code #minecraft:is_freezing} 而不是"哪个类造成的"，
     * 所以覆盖全部冰冻来源，而且 Spore 以后改伤害类型我们也不用跟：Spore 自己的冻伤 tick 用的是
     * {@code damageSources().freeze()}，本 mod 的冰霜新星 / 冰雪的叹息造成的冻伤也走同一条路
     * （见 {@code ModEvents#onFreezingDamage} 里对同一标签的用法）。
     *
     * <p>用 {@code LivingHurtEvent}（护甲/抗性/<b>烈阳</b>减免之前）而不是 {@code LivingDamageEvent}：
     * 倍率会照常走这些减免，于是"2 倍"是<b>减免之前</b>的 2 倍。
     * 满四件烈阳的实体本来就在 {@link #onFreezingDamage} 那里被整个挡下了，走不到这里；
     * 不足四件时按比例削弱，倍率与削弱相乘，语义保持一致。
     */
    @SubscribeEvent
    public static void onFreezeDamage(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !event.getSource().is(DamageTypeTags.IS_FREEZING)) {
            return;
        }
        if (!FungusCombat.isFungus(entity)) {
            return;
        }
        float multiplier = (float) SporeAddFungusConfig.freezeDamageMultiplier();
        if (multiplier == 1.0F) {
            return;
        }
        event.setAmount(event.getAmount() * multiplier);
    }
}
