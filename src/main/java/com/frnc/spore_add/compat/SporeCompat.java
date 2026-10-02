package com.frnc.spore_add.compat;

import java.util.List;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Core.Sentities;
import com.Harbinger.Spore.Core.Sblocks;
import com.Harbinger.Spore.Core.Seffects;
import com.Harbinger.Spore.Core.Ssounds;
import com.Harbinger.Spore.ExtremelySusThings.CustomJsonReader.SporeCduConversionData;
import com.Harbinger.Spore.ExtremelySusThings.SporeSavedData;
import com.Harbinger.Spore.ExtremelySusThings.Utilities;
import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.Harbinger.Spore.Sentities.Utility.ArenaEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Spore（真菌感染：孢子）的唯一引用点。
 *
 * <p><b>为什么集中在一个类里</b>：Spore 并没有为「可燃」提供公开 API——没有 {@code spore/api} 包、
 * 没有自定义事件，我们唯一能用的是它的 {@code Seffects.IGNITABLE} 这个 {@code public static} 字段。
 * 那不是一个承诺稳定的接口，Spore 更新后有改名/改结构的风险。把它收在这里，将来只需改这一个文件。
 *
 * <p><b>一个绕不开的例外</b>：mixin 的目标类必须是类字面量（{@code @Mixin(Tar.class)} 这种写法），
 * 没法藏在间接层后面。所以 Spore 的引用点实际有两处——本类（运行时取值）与各 mixin 的目标声明。
 * 这两处都写在了同一批文件里，找起来不难。
 *
 * <h2>关于「可燃被触发」为什么不用这里的方法判断</h2>
 * Spore 的触发逻辑硬编码在它自己的 {@code HandlerEvents.DefenseBypass(LivingDamageEvent)} 里
 * （六种伤害类型 + 1% 兜底概率），而且它触发时会<b>主动移除可燃 buff</b>。所以本 mod 用
 * {@code MobEffectEvent.Remove} 捕获那次移除当作触发信号，而不是去引 Spore 的伤害类型表——
 * 这样 Spore 以后改概率或改伤害类型，我们都不用跟着改。详见 {@code ModEvents}。
 */
public final class SporeCompat {

    private SporeCompat() {
    }

    /**
     * Spore 的「可燃」buff。
     *
     * <p>返回 {@code null} 表示取不到——正常加载顺序下不会发生（本 mod 在 {@code mods.toml} 里对
     * {@code spore} 声明了 {@code ordering="AFTER"}，所以它的注册项一定先就位），但调用方仍然按可空处理，
     * 免得因为一个第三方模组的内部变动而崩在事件里。
     */
    @Nullable
    public static MobEffect ignitable() {
        return Seffects.IGNITABLE.get();
    }

    /**
     * Spore 的「冻伤」buff（注册名 {@code frostbite}；类名是 {@code FrostBite}，注意大小写）。
     *
     * <p><b>它和可燃不是一类东西。</b>可燃是哑标记、amplifier 无人读；而冻伤的 amplifier <b>被 Spore
     * 真正使用</b>——算额外冻结伤害、判定能否越过自家进化体的抗性闸门、并缩放移动减速。
     * 所以本 mod 直接拿 amplifier 当层数，不另存一份，详见 {@code FrostbiteLevels}。
     */
    @Nullable
    public static MobEffect frostbite() {
        return Seffects.FROSTBITE.get();
    }

    // ==================================================================
    // 真菌阵营
    // ==================================================================

    /**
     * Spore 的「真菌实体」标签（{@code #spore:fungus_entities}，默认 83 项）。
     *
     * <p><b>这就是「哪些生物算真菌」的权威定义</b>，比 {@code instanceof Infected} 更准：
     * 它还包括 {@code hivetumor}、{@code claw}、{@code proto} 这些不走 {@code Infected} 继承链的
     * 功能实体。Spore 自己也用它——{@code data/minecraft/tags/entity_types/freeze_hurts_extra_types.json}
     * 里唯一的一项就是引用本标签，这正是 Spore 的冻伤只对真菌生效的原因。
     *
     * <p>它同时是数据包可扩展的：整合包往这个标签里加东西，本 mod 的「真菌」判定会跟着一起变。
     */
    public static TagKey<EntityType<?>> fungusEntities() {
        return entityTag("fungus_entities");
    }

    // ==================================================================
    // 心智与资源
    // ==================================================================
    //
    // 「心智」是 Spore 的 Proto（Organoids 包），它身上的 BIOMASS 就是需求里说的「资源」。
    // 这两个入口收在这里，理由与本节开头一样：它们是 Spore 的内部结构，不是为外部使用设计的接口。

    /**
     * 当前世上活着的所有心智。
     *
     * <p>返回的是 Spore 自己那份实时列表（{@code SporeSavedData.getHiveminds()}），
     * <b>不要修改它</b>，只读。
     */
    public static List<Proto> hiveminds() {
        return SporeSavedData.getHiveminds();
    }

    /** 这个实体是不是心智（{@code Proto}）。 */
    public static boolean isHivemind(Entity entity) {
        return entity instanceof Proto;
    }

    /**
     * 心智"发话"时用的那个音效（Spore 的 {@code REBIRTH}）。
     *
     * <p>Spore 自己在 {@code HiveSpawn} 里就是用这个音效配上「忍受吧...」那句话的，
     * 本 mod 的「恐惧吧...」照搬同一套，玩家的体感才对得上。
     */
    public static SoundEvent hivemindSummonSound() {
        return Ssounds.REBIRTH.get();
    }

    /**
     * 对某个玩家播一次心智的音效。
     *
     * <p>与 Spore 一样用 {@code playNotifySound} 而不是 {@code level.playSound}：
     * 前者只发给这一个玩家、且不受环境音效音量设置的影响，
     * 正好符合"心智对他一个人说话"这个场景。
     */
    public static void playHivemindSummonSound(Player player) {
        SoundEvent sound = hivemindSummonSound();
        if (sound != null) {
            player.playNotifySound(sound, SoundSource.AMBIENT, 1.0F, 1.0F);
        }
    }

    /**
     * 把一笔资源<b>均分</b>给所有心智，返回<b>没能发出去的部分</b>。
     *
     * <p>没有心智、或者钱少到每人连 1 点都分不到时，<b>整笔原样退回</b>——由调用方存进
     * 暂存区等下次有心智再发。不退的话，{@code 3 点资源 ÷ 5 个心智 = 0} 会把这 3 点凭空蒸发。
     *
     * <p>除不尽的零头给第一个心智。Spore 的 biomass 是整数，零头没法拆分，
     * 与其丢掉不如给一个——总共不到 {@code 心智数} 点，不会造成可感知的不均。
     */
    public static int grantResourcesToHiveminds(int amount) {
        if (amount <= 0) {
            return 0;
        }
        List<Proto> hiveminds = hiveminds();
        if (hiveminds.isEmpty()) {
            return amount;
        }
        int perHivemind = amount / hiveminds.size();
        if (perHivemind <= 0) {
            return amount;
        }
        int remainder = amount - perHivemind * hiveminds.size();
        for (int i = 0; i < hiveminds.size(); i++) {
            hiveminds.get(i).addBiomass(perHivemind + (i == 0 ? remainder : 0));
        }
        return 0;
    }

    /**
     * 把一笔资源交给<b>离该位置最近的</b>那个心智，返回没能发出去的部分。
     *
     * <p>用在「真菌捡到掉落物」这条路上：捡东西的是某一只真菌，交给离它最近的心智最自然，
     * 而不是像"玩家死亡补偿"那样撒给全体——那是全阵营的账，这是某只小兵捡到的零碎。
     *
     * <p>只认同一个维度的心智。跨维度"隔空投送"没有意义，而且 {@code Level} 不同时距离也算不出来。
     * 该死的心智（{@code isRemoved()}）跳过——Spore 的列表在实体真正移除后才清，
     * 中间那一小段里它还在列表上。
     */
    public static int grantResourcesToNearestHivemind(Level level, Vec3 pos, int amount) {
        if (amount <= 0) {
            return 0;
        }
        Proto nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Proto hivemind : hiveminds()) {
            if (hivemind.isRemoved() || hivemind.level() != level) {
                continue;
            }
            double distance = hivemind.position().distanceToSqr(pos);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = hivemind;
            }
        }
        if (nearest == null) {
            return amount;
        }
        nearest.addBiomass(amount);
        return 0;
    }

    // ==================================================================
    // 竞技之须（Spore 自己的波次挑战装置）
    // ==================================================================
    //
    // 「竞技之须」= `spore:arena_tendril`，实现类是 ArenaEntity。它是一个不可杀的环境装置：
    // 钻出地面 → 按附近玩家的强度算出波次规模与等级 → 周期性召唤 Verwa（肚子里装着具体生物）
    // → 场上真菌少于 4 只时缩回消失并掉奖励。
    //
    // 我们的袭击攻击阶段前半段就把出怪整个交给它，所以这几个入口收在这里。

    /** 这个实体是不是竞技之须。用来盯它有没有缩回消失。 */
    public static boolean isArenaTendril(Entity entity) {
        return entity instanceof ArenaEntity;
    }

    /**
     * 在指定位置种一个竞技之须并让它立刻开始钻出。
     *
     * <p><b>为什么必须显式给波次规模与等级</b>：它默认两个数都是 0，靠自己每 40 tick 扫附近玩家
     * 重算（{@code compareEntity}）。但那个"重算"只会往上<b>加</b>，0 起步的话第一次出怪
     * 要等到附近正好站着装备齐全的玩家才算得动。种下去的时候先给一个起步值，
     * 它之后照样会自己往上加——所以这里给的是下限而不是封顶。
     *
     * <p>{@code tickEmerging()} 也要手动调一次：那个计数器平时由它自己的战斗逻辑推进，
     * 而我们是在它还没入列时就把它设成"正在钻出"的，得替它迈出第一步。
     *
     * @return 种下去的那只，失败时为 {@code null}
     */
    @Nullable
    public static ArenaEntity spawnArenaTendril(ServerLevel level, Vec3 pos, int waveSize, int waveLevel) {
        ArenaEntity arena = Sentities.ARENA_TENDRIL.get().create(level);
        if (arena == null) {
            return null;
        }
        arena.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        arena.setWaveSize(Math.max(0, waveSize));
        arena.setWaveLevel(Mth.clamp(waveLevel, 0, 2));
        arena.startWave(true);
        arena.tickEmerging();
        level.addFreshEntity(arena);
        return arena;
    }

    /**
     * Spore 配置里的「不主动攻击的生物」名单（{@code blacklist}）。
     *
     * <p>默认是 {@code minecraft:creeper}、{@code minecraft:allay}、{@code minecraft:dolphin}、
     * {@code minecraft:squid}、{@code minecraft:bat}、{@code minecraft:armor_stand}，外加三条
     * <b>命名空间前缀</b>写法（{@code creeperoverhaul:}、{@code sculkhorde:}、{@code fromanotherworld:}，
     * 以冒号结尾表示"该模组的全部实体"）。解析规则见 {@code fungus/FungusCombat}。
     *
     * <p>本 mod 的「攻击一切生物」刻意<b>尊重</b>这份名单：那三条前缀是别的模组的实体，
     * 无视它们会平白引入跨模组行为（对方可能没做被真菌锁定的准备）。名单也是玩家唯一的调节旋钮。
     */
    public static List<? extends String> targetBlacklist() {
        return SConfig.SERVER.blacklist.get();
    }

    // ==================================================================
    // CDU：冰霜新星清理真菌方块时用到的那一套
    // ==================================================================
    //
    // 下面这些全部来自 Spore 的 CDU（一台在半径内清除真菌感染的机器）。本 mod 的冰霜新星
    // 要"清理掉影响范围内的所有真菌方块，机制与 CDU 一样"，于是把 CDU 用的那些判据与映射表
    // 原样搬了过来。规则本身写在 {@code world/FungalClearing} 里，这里只负责"把 Spore 的东西取出来"。
    //
    // ⚠️ 这一节是本类里最脆的部分：它直接依赖 Spore 的 6 个方块字段、2 个标签、1 个数据包读取器
    //    和 1 个配置项。这些都不是为外部使用设计的接口。Spore 改了内部结构，这里就会编译不过
    //    （好处是编译期就暴露，不会静默出错）。

    /**
     * Spore 的「真菌方块」标签（{@code #spore:fungal_blocks}）。
     *
     * <p>这就是需求里"所有真菌方块"的权威定义：生物质、各类感染方块、菌丝、菌柄、增生、蜂巢生成器……
     * {@code minecraft:mycelium} 也在里面。
     */
    public static TagKey<Block> fungalBlocks() {
        return blockTag("fungal_blocks");
    }

    /**
     * Spore 的「可清除植被」标签（{@code #spore:removable_foliage}）。
     *
     * <p>里面<b>全是 Spore 自己的方块</b>（增生、菌柄、菌丝脉、腐草、器官……），
     * 不涉及原版草木，所以照着 CDU 一并清除不会误伤。
     */
    public static TagKey<Block> removableFoliage() {
        return blockTag("removable_foliage");
    }

    /** Spore 的「生物质」标签，CDU 用它判定"该冻成冻伤生物质"。 */
    public static TagKey<Block> biomass() {
        return Utilities.biomass;
    }

    /** 残骸。CDU 把它冻成 {@link #frozenRemains()}。 */
    public static Block remains() {
        return Sblocks.REMAINS.get();
    }

    /** 冰冻残骸。 */
    public static Block frozenRemains() {
        return Sblocks.FROZEN_REMAINS.get();
    }

    /** 膜方块。CDU 把它与生物质一起冻成 {@link #frostBurnedBiomass()}。 */
    public static Block membraneBlock() {
        return Sblocks.MEMBRANE_BLOCK.get();
    }

    /** 冻伤生物质。名字里就带"冻"，正是冰霜新星该产出的东西。 */
    public static Block frostBurnedBiomass() {
        return Sblocks.FROST_BURNED_BIOMASS.get();
    }

    /** 胆汁（流体方块）。CDU 把它结壳成 {@link #crustedBile()}。 */
    public static Block bile() {
        return Sblocks.BILE.get();
    }

    /** 结壳胆汁。 */
    public static Block crustedBile() {
        return Sblocks.CRUSTED_BILE.get();
    }

    /**
     * 数据包定义的转换表（{@code data/&lt;命名空间&gt;/spore_cdu_conversion/*.json}）。
     *
     * <p>格式是 {@code {"方块或#标签": "结果方块"}}，Spore 自带的 {@code default_conversions.json}
     * 是空的 {@code {}}，要靠整合包/玩家自己填。取不到映射时返回 {@code null}。
     */
    @Nullable
    public static Block cduConversion(Block from) {
        return SporeCduConversionData.getResult(from);
    }

    /**
     * Spore 配置里那张「感染方块|干净方块」的表（{@code SConfig.DATAGEN.block_cleaning}）。
     *
     * <p><b>这张表才是 CDU 的主要机制</b>：它默认就有内容，把各种感染方块还原成原本的方块
     * （感染石头→石头、感染圆石→圆石、感染沙子→沙子、实验室方块→lab_block……）。
     * 所以清理真菌方块时不能只认数据包那张空表，否则感染石头会被"清"成空气而不是干净的石头。
     *
     * <p>每个元素形如 {@code "spore:infested_stone|minecraft:stone"}，按 {@code |} 分成两半。
     */
    public static List<? extends String> cduBlockCleaning() {
        return SConfig.DATAGEN.block_cleaning.get();
    }

    /** Spore 的标签都在 {@code spore} 命名空间下，这里省掉重复写名字空间。 */
    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("spore", path));
    }

    /** 同上，实体类型的那一份。 */
    private static TagKey<EntityType<?>> entityTag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("spore", path));
    }
}
