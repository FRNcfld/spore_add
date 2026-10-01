package com.frnc.spore_add.event;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.advancement.ModTriggers;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.effect.BuffLevels;
import com.frnc.spore_add.effect.ModEffects;
import com.frnc.spore_add.enchantment.ModEnchantments;
import com.frnc.spore_add.enchantment.Warmth;
import com.frnc.spore_add.network.ModNetwork;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
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
     * 「烈阳」附魔：拦住冻伤的<b>施加</b>。
     *
     * <p>用 {@code MobEffectEvent.Applicable} 而不是自己轮询实体：它正好在
     * {@code LivingEntity#canBeAffected} 里触发，所以不论施加方是本 mod 的冷却液 / 液态寒冷，
     * 还是 Spore 自己的 CDU、冰霜肿瘤、PCI 武器，都会被这一道挡下。
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffect frostbite = SporeCompat.frostbite();
        if (frostbite == null || event.getEffectInstance().getEffect() != frostbite) {
            return;
        }
        if (Warmth.isWorn(event.getEntity())) {
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
        if (Warmth.isWorn(entity)) {
            MobEffect frostbite = SporeCompat.frostbite();
            if (frostbite != null && entity.hasEffect(frostbite)) {
                entity.removeEffect(frostbite);
            }
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
}
