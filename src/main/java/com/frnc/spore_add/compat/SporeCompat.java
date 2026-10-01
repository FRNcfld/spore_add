package com.frnc.spore_add.compat;

import java.util.List;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Core.Sblocks;
import com.Harbinger.Spore.Core.Seffects;
import com.Harbinger.Spore.ExtremelySusThings.CustomJsonReader.SporeCduConversionData;
import com.Harbinger.Spore.ExtremelySusThings.Utilities;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.Block;
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
}
