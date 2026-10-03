package com.frnc.spore_add.hatred;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.SporeAddDebugConfig.Area;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.debug.SporeAddDebug;
import com.frnc.spore_add.fungus.FungusCombat;

import javax.annotation.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 恨意值的全部事件来源与去处。跑在 Forge 事件总线上。
 *
 * <table border="1">
 *   <caption>本类与需求条目的对应关系</caption>
 *   <tr><th>需求</th><th>方法</th></tr>
 *   <tr><td>2 击杀真菌涨恨意值</td><td>{@link #onDeath}</td></tr>
 *   <tr><td>3 世界恨意值 → 真菌减伤</td><td>{@link #onHurt}</td></tr>
 *   <tr><td>4 个人恨意值 → 玩家增益</td><td>{@link #onHurt}、{@link #onDamage}</td></tr>
 *   <tr><td>5 越档 50% 触发袭击</td><td>{@code HatredManager#add}（本类只负责提供改动）</td></tr>
 *   <tr><td>6 死于真菌 → −80% + 资源给心智</td><td>{@link #onDeath}</td></tr>
 *   <tr><td>7 击杀心智 −90%</td><td>{@link #onDeath}</td></tr>
 *   <tr><td>8 吃真菌类食物</td><td>{@link #onItemUseFinished}</td></tr>
 * </table>
 *
 * <h2>两条伤害通路为什么不放在同一个方法里</h2>
 * <ul>
 *   <li>{@link #onHurt} 处理 {@code LivingHurtEvent}——在护甲、抗性、吸收<b>之前</b>。
 *       真菌减伤与玩家减伤都挂在这儿，于是它们的作用是"减免之前的减免"，
 *       会照常与原版护甲叠乘，符合直觉。</li>
 *   <li>{@link #onDamage} 处理 {@code LivingDamageEvent}——那是<b>结算完之后</b>的最终值。
 *       "最终伤害加成"要的正是这个位置：在它上面加成才是真的"最终"，
 *       而不是又一次被护甲吃掉一部分。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID)
public final class HatredEvents {

    /**
     * 「真菌类食物」的物品标签，定义在 {@code data/spore_add/tags/items/fungal_food.json}。
     *
     * <p>用标签而不是硬编码一串物品 id：整合包能自己增删，而且 Spore 以后加新食物时
     * 只要它进的是同一个标签，这边不用改代码。
     */
    private static final TagKey<Item> FUNGAL_FOOD =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(SporeAdd.MOD_ID, "fungal_food"));

    private HatredEvents() {
    }

    // ------------------------------------------------------------------
    // 需求 2 / 6 / 7：死亡
    // ------------------------------------------------------------------

    /**
     * 死亡事件，三条需求共用。
     *
     * <p>三条互相独立，按"死者是谁"分流：
     * <ol>
     *   <li><b>死者是真菌</b>（需求 2）——凶手是玩家就给他涨恨意值，
     *       数额由 {@link HatredValues#killValue} 按等级/类型/重要性/链接/发育算；</li>
     *   <li><b>死者是玩家</b>（需求 6）——凶手是真菌就按比例削他的恨意值，
     *       并把损失换算成资源发给所有心智；</li>
     *   <li><b>死者是心智</b>（需求 7）——凶手是玩家就削他一大笔恨意值。</li>
     * </ol>
     *
     * <p>用 {@code getKiller()} 而不是自己看伤害源：它已经处理好了"玩家的宠物咬死的"
     * "玩家的箭射死的"这些情况（原版在 {@code lastHurtByPlayer} 里记的就是这个）。
     * 心智那一条也一样——心智被别的真菌打死不该算在玩家头上。
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }

        // 一、真菌被杀 → 凶手涨恨意值
        if (FungusCombat.isFungus(victim)) {
            if (responsiblePlayer(event.getSource()) instanceof ServerPlayer player) {
                double value = HatredValues.killValue(victim);
                HatredManager.add(player, value);
                SporeAddDebug.log(Area.HATRED, "击杀真菌：{} → {} 涨 {} 恨意值",
                        victim.getType(), player.getGameProfile().getName(), value);
            } else {
                // 「杀了真菌却没涨恨意值」的头号成因：凶手归不到任何玩家头上
                // （玩家的宠物没有主人、别的模组的投射物、自然死亡……）。
                SporeAddDebug.log(Area.HATRED, "击杀真菌：{} 死亡，但归不到玩家头上，不涨恨意值",
                        victim.getType());
            }
            return;   // 真菌不会同时是玩家或心智，不必再往下判
        }

        // 二、玩家死亡 → 死于真菌之手时重罚
        if (victim instanceof ServerPlayer player) {
            boolean byFungus = isFungusDamage(event.getSource());
            if (byFungus) {
                onPlayerKilledByFungus(player);
            }
            SporeAddDebug.log(Area.HATRED, "玩家死亡：{}，判定为死于真菌之手={}",
                    player.getGameProfile().getName(), byFungus);
            return;
        }

        // 三、心智被杀 → 凶手大幅降恨意值
        if (SporeCompat.isHivemind(victim)) {
            if (responsiblePlayer(event.getSource()) instanceof ServerPlayer player) {
                double ratio = SporeAddFungusConfig.hivemindKillLossRatio();
                HatredManager.reduceByRatio(player, ratio);
                SporeAddDebug.log(Area.HATRED, "击杀心智：{} → {} 按比例 {} 降恨意值",
                        victim.getType(), player.getGameProfile().getName(), ratio);
            } else {
                SporeAddDebug.log(Area.HATRED, "击杀心智：{} 死亡，但归不到玩家头上，不降恨意值",
                        victim.getType());
            }
        }
    }

    /**
     * 这次伤害该算在哪个玩家头上。
     *
     * <h2>为什么不用 {@code getKiller()}</h2>
     * 1.20.1 的 {@code LivingEntity} <b>没有</b>这个方法（它是 1.20.2 才加的；
     * 已用本工程的映射表核对过——整个 tsrg 里不存在 {@code getKiller}）。
     * 所以"谁干的"要自己判，好在三种情况已经覆盖了实际会遇到的绝大多数：
     * <ol>
     *   <li><b>玩家本人打的</b>——{@code getEntity()} 就是那个玩家；</li>
     *   <li><b>玩家的宠物咬死的</b>——{@code getEntity()} 是宠物，取它的主人；</li>
     *   <li><b>玩家射的箭</b>——{@code getEntity()} 已经是射手，兜底再看一眼 {@code getDirectEntity()}
     *       （有些弹射物没设责任实体）。</li>
     * </ol>
     *
     * <p><b>刻意不复刻</b>原版 {@code lastHurtByPlayer} 那种"很久以前被谁打过"的记忆语义：
     * 那会让"我打了一下、它后来摔死了"也算在我头上。这里只认"这一击是谁造成的"，
     * 语义更清楚，也更好解释。
     */
    @Nullable
    private static Player responsiblePlayer(DamageSource source) {
        Entity responsible = source.getEntity();
        if (responsible instanceof Player player) {
            return player;
        }
        if (responsible instanceof TamableAnimal pet && pet.getOwner() instanceof Player owner) {
            return owner;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Player player) {
            return player;
        }
        return direct instanceof TamableAnimal pet && pet.getOwner() instanceof Player owner ? owner : null;
    }

    /**
     * 需求 6：死于真菌之手。
     *
     * <p>具体的账（按 {@code death.fungusDeathLossRatio} 削恨意值、把损失
     * ×{@code death.resourceMultiplier} 的资源发给所有心智、没有心智就先存起来）
     * 都在 {@link HatredManager#punishPlayerDefeat} 里，本方法只是那条路上的一个触发点。
     *
     * <p><b>为什么账不写在这里</b>：袭击失败（竞技之须超时）也要走同一段代码——
     * 需求原话是"走玩家被真菌击杀的那条线"。抄第二遍的话，以后调了 {@code death} 段的任何一个数，
     * 两条路就会悄悄分叉。
     */
    private static void onPlayerKilledByFungus(ServerPlayer player) {
        HatredManager.punishPlayerDefeat(player);
    }

    /** 伤害来源算不算"真菌造成的"。取责任实体（弹射物会指向发射者），取不到再看直接实体。 */
    private static boolean isFungusDamage(DamageSource source) {
        Entity responsible = source.getEntity();
        if (responsible != null && FungusCombat.isFungus(responsible)) {
            return true;
        }
        Entity direct = source.getDirectEntity();
        return direct != null && FungusCombat.isFungus(direct);
    }

    // ------------------------------------------------------------------
    // 需求 3 / 4：减伤
    // ------------------------------------------------------------------

    /**
     * 受击时的两条减伤，都是需求里明确要求"减免之前"的那一层。
     *
     * <h2>一、真菌按世界恨意值减伤（需求 3）</h2>
     * 两处排除，正是需求括号里说的"不包括玩家造成的伤害和虚空等伤害"：
     * <ul>
     *   <li><b>玩家造成的</b>——{@code source.getEntity()} 是玩家（含玩家的宠物与弹射物，
     *       因为 {@code getEntity()} 取的是责任实体）。玩家永远是有效的输出手段；</li>
     *   <li><b>虚空等</b>——带 {@code #minecraft:bypasses_invulnerability} 标签的伤害。
     *       原版这个标签里就是 {@code out_of_world}（虚空）与 {@code generic_kill}（{@code /kill}），
     *       正好是"无视一切减伤"的那一类。用标签而不是硬编码那两个伤害类型，
     *       是为了让整合包加的同类伤害也自动生效。</li>
     * </ul>
     *
     * <h2>二、玩家按个人恨意值减伤（需求 4）</h2>
     * 玩家这一条<b>不</b>排除玩家造成的伤害——需求只对真菌那一条写了排除，
     * 而 PvP 里让恨意高的一方更耐打是这个机制的应有之义。
     */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }

        if (FungusCombat.isFungus(entity)) {
            if (isPlayerDamage(event.getSource()) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return;
            }
            if (!(entity.level() instanceof ServerLevel level)) {
                return;
            }
            double worldHatred = HatredData.get(level).total();
            applyReduction(event, HatredValues.fungusDamageReduction(worldHatred));
            return;
        }

        if (entity instanceof ServerPlayer player) {
            applyReduction(event, PlayerHatredBuffs.damageReduction(HatredManager.of(player)));
        }
    }

    /** 伤害是不是玩家造成的（含玩家的宠物与弹射物）。 */
    private static boolean isPlayerDamage(DamageSource source) {
        Entity responsible = source.getEntity();
        if (responsible instanceof Player) {
            return true;
        }
        Entity direct = source.getDirectEntity();
        return direct instanceof Player;
    }

    /** 按比例削减这次伤害。比例 <= 0 或伤害已经是 0 时原样不动。 */
    private static void applyReduction(LivingHurtEvent event, double reduction) {
        if (reduction <= 0.0D || event.getAmount() <= 0.0F) {
            return;
        }
        event.setAmount((float) (event.getAmount() * (1.0D - Math.min(1.0D, reduction))));
    }

    /**
     * 需求 4 的「最终伤害加成」。
     *
     * <p>挂在 {@code LivingDamageEvent} 上，也就是护甲、抗性、吸收都结算完之后的那一层——
     * 这是在它上面加成才是真的"最终"，而不是又一次被护甲吃掉一部分。
     *
     * <p>注意它<b>不是</b>"基础攻击力"的重复：那一项改的是攻击力属性，会被原版的一整套
     * 结算正常处理；这一项是最后再乘一次。两项叠乘，是需求把它们分列两条的本意。
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        double bonus = PlayerHatredBuffs.finalDamageBonus(HatredManager.of(player));
        if (bonus <= 0.0D || event.getAmount() <= 0.0F) {
            return;
        }
        event.setAmount((float) (event.getAmount() * (1.0D + bonus)));
    }

    // ------------------------------------------------------------------
    // 需求 8：食用真菌类食物
    // ------------------------------------------------------------------

    /**
     * 吃完东西之后掷两次独立的骰：先看降、再看涨。
     *
     * <p><b>两支独立</b>是刻意的（不是"二选一"）：需求把它们写成了两条各自带概率的规则，
     * 所以理论上存在"同一次既降又涨"的结果，默认概率下约 0.5%，可以忽略但不该被悄悄合并掉。
     *
     * <p>用 {@code Finish} 而不是 {@code Start}：食物类物品在开始使用时还没被消耗，
     * 玩家中途松手（比如被打断）就会白吃一次概率。{@code Finish} 只在真正吃完时触发。
     */
    @SubscribeEvent
    public static void onItemUseFinished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!stack.is(FUNGAL_FOOD)) {
            return;
        }
        if (player.getRandom().nextDouble() < SporeAddFungusConfig.foodReduceChance()) {
            HatredManager.reduceByRatio(player, SporeAddFungusConfig.foodReduceRatio());
        }
        if (player.getRandom().nextDouble() < SporeAddFungusConfig.foodIncreaseChance()) {
            double current = HatredManager.of(player);
            HatredManager.add(player, current * SporeAddFungusConfig.foodIncreaseRatio());
        }
    }

    // ------------------------------------------------------------------
    // 增益的刷新时机
    // ------------------------------------------------------------------

    /**
     * 登录 / 复活 / 换维度时补一次属性加成。
     *
     * <p>这三处恨意值本身没变，但玩家实体是新的（复活是换了一个实例，换维度会重建属性缓存），
     * 属性修饰符不会自己跟过去，所以要主动补。少了这一手，症状是"复活之后加成消失，
     * 直到下次恨意值变化才回来"。
     */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerHatredBuffs.refresh(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerHatredBuffs.refresh(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerHatredBuffs.refresh(player);
        }
    }

    /** 退场时摘掉加成。不带这一手的话，同一账号再进来时旧修饰符还在，会与新的一起叠上去。 */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerHatredBuffs.clear(player);
        }
    }
}
