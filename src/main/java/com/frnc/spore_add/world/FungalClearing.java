package com.frnc.spore_add.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.frnc.spore_add.compat.SporeCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 冰霜新星的两个爆炸阶段都要做的"清除真菌方块"。
 *
 * <p>规则照搬 Spore 的 <b>CDU</b>（一台在半径内清除真菌感染的机器）。CDU 的那套逻辑是用字节码
 * 还原出来的（Spore 不发源码），下面是它的规则清单；每一处的取舍都写在对应位置。
 *
 * <h2>规则与优先级</h2>
 * 按顺序判断，<b>先匹配先赢</b>：
 * <ol>
 *   <li>残骸 → 冰冻残骸</li>
 *   <li>胆汁 → 结壳胆汁</li>
 *   <li>生物质 / 膜方块 → 冻伤生物质</li>
 *   <li>Spore 配置里的「感染方块|干净方块」表 → 表里那个方块</li>
 *   <li>数据包的 {@code spore_cdu_conversion} 表 → 表里那个方块</li>
 *   <li>{@code #spore:fungal_blocks} 或 {@code #spore:removable_foliage} → 空气</li>
 * </ol>
 *
 * <h2>三处刻意与 CDU 不同</h2>
 * <ul>
 *   <li><b>不用概率。</b>CDU 是每 tick 都在跑的机器，所以它的规则大多带概率
 *       （数据包表 20%、生物质 10%、落叶 20%、下雪 0.1%），靠反复尝试最终清完。
 *       新星是<b>一次性</b>爆发，照抄概率会留下 80% 的真菌方块，与"清理掉<b>所有</b>真菌方块"矛盾。
 *       所以这里一律 100% 执行。</li>
 *   <li><b>先匹配先赢，而不是后匹配覆盖。</b>CDU 把所有规则写成互不相干的 {@code if}，
 *       而且每个判断用的都是最初那个 {@code state}，于是<b>后面匹配的会覆盖前面的</b>
 *       （比如残骸先被冻成冰冻残骸、又被生物质那条覆盖成冻伤生物质）。这里把最具体的规则排在前面、
 *       命中即停，行为才可预期。</li>
 *   <li><b>不下雪。</b>CDU 有 0.1% 概率在实心方块上铺一层雪，那是它作为"冷冻机"的氛围效果。
 *       新星已经在落点铺了一整颗冰球，再撒雪只会打架。</li>
 * </ul>
 *
 * <h2>为什么"变空气"只兜底</h2>
 * 靠前的那几条会把感染方块<b>还原成干净的方块</b>（感染石头→石头），这才是 CDU 的主机制。
 * 只有既没被前几条命中、也没有任何映射表的真菌方块（菌丝、菌柄、增生……）才清成空气。
 * 顺序反过来的话，感染石头会被清成空气而不是干净的石头。
 *
 * <h2>映射表不按标签过滤</h2>
 * 第 4、5 条对<b>任何</b>在表里的方块生效，不限于真菌标签内——表本身就是 Spore 给出的
 * "这些方块该被清理成什么"的权威列表，而里面有些方块（如 {@code spore:infested_stone_bricks}）
 * 并不在 {@code #spore:fungal_blocks} 里。
 */
public final class FungalClearing {

    /** 与其他方块操作取同一组更新标志：同步客户端 + 通知邻居。 */
    private static final int CLEAR_FLAGS = Block.UPDATE_ALL;

    private FungalClearing() {
    }

    /**
     * 清除以 {@code center} 为心、{@code radius} 为半径的球内的真菌方块。
     *
     * <p>只应在服务端调用。半径用的是冻伤/冰雾半径，与冻伤判定是同一个球——
     * 玩家看到霜雾的地方就是会被清的地方。
     */
    public static void clear(Level level, BlockPos center, double radius) {
        if (level.isClientSide()) {
            return;
        }
        // 配置表解析一次就够，别放进循环里
        Rules rules = rules();

        int r = Mth.ceil(radius);
        double limitSqr = radius * radius;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > limitSqr) {
                        continue;   // 落在外接立方体的角上，球外
                    }
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    BlockState replacement = rules.replacementFor(state);
                    if (replacement != null) {
                        level.setBlock(pos, replacement, CLEAR_FLAGS);
                    }
                }
            }
        }
    }

    /**
     * 解析一次 Spore 的「感染方块|干净方块」表，得到一个可以反复使用的规则集。
     *
     * <p>给<b>球状</b>的 {@link #clear} 之外的调用方用的：冰雪的叹息要按列扫 5 万列，
     * 逐列去解析配置表是纯浪费，所以把"解析"和"判断"拆开——解析一次，判断几百万次。
     *
     * <p>规则本身只有这一处实现（{@link Rules#replacementFor} 直接转调下面那个私有方法），
     * 所以冰霜新星与冰雪的叹息不可能各清各的。
     */
    public static Rules rules() {
        return new Rules(parseConfiguredConversions());
    }

    /** 一份已经解析好的规则集。只做纯判断，不碰世界。 */
    public static final class Rules {

        private final List<BlockPair> configured;

        /**
         * 方块 → 结论的缓存。
         *
         * <p><b>为什么非要有它。</b>冰雪的叹息要按列扫 5 万列、约 <b>2000 万个方块</b>，
         * 而每个方块的判断在"没命中"那条路上要一路走完 {@code SporeCompat.cduConversion}
         * ——那是一次 map 查找，外加它内部对"标签转换表"的遍历。实世界里的方块种类其实很少
         * （石头、深板岩、泥土、水、空气……），按方块记下结论之后，一整场爆发只需要几百次真正的判断，
         * 而不是两千万次。这一步把 {@code FrostSighBlast} 那条整列扫描的开销从"要盯着看"
         * 拉回"可以忽略"。
         *
         * <p><b>按方块缓存是安全的</b>：命不命中只取决于方块本身。
         * 方块状态的那部分差异（朝向、半砖类型……）只影响"复制哪些属性"，
         * 而那一手在 {@link #replacementFor} 里对每个状态逐次做，不进缓存。
         *
         * <p>缓存不跨爆发复用，所以数据包中途重载最多影响当前这一次爆发。
         */
        private final Map<Block, Optional<Decision>> decisions = new HashMap<>();

        private Rules(List<BlockPair> configured) {
            this.configured = configured;
        }

        /** 决定这个方块该变成什么；返回 {@code null} 表示不该动它。 */
        @Nullable
        public BlockState replacementFor(BlockState state) {
            Decision decision = decisions
                    .computeIfAbsent(state.getBlock(), block -> Optional.ofNullable(decide(state, configured)))
                    .orElse(null);
            return decision == null ? null : apply(decision, state);
        }
    }

    /**
     * 一次判断的结论：该换成哪个方块，以及要不要按同名属性复制。
     *
     * <p>拆成"结论"与"应用"两步，就是为了让结论能按<b>方块</b>缓存——见 {@link Rules}。
     */
    private record Decision(Block target, boolean copyProperties) {
    }

    /**
     * 按方块判断该换成什么。
     *
     * <p>{@code state} 只用来做标签判断与取方块，所以同一个方块的不同状态会得到同一个结论。
     */
    @Nullable
    private static Decision decide(BlockState state, List<BlockPair> configured) {
        Block block = state.getBlock();

        // 名单里的方块（传送门等）一律不碰。放在最前面：数据包里完全可能有人把传送门写进
        // 转换表，或者给某个受保护的方块挂上真菌标签——那也不该由本 mod 把它清掉。
        // 这一步在 {@link Rules} 里是按方块缓存的，所以只会有一次判定成本。
        if (FrostProof.isProtected(state)) {
            return null;
        }

        if (block == Refs.REMAINS) {
            return new Decision(Refs.FROZEN_REMAINS, false);
        }
        if (block == Refs.BILE) {
            return new Decision(Refs.CRUSTED_BILE, false);
        }
        if (state.is(Refs.BIOMASS) || block == Refs.MEMBRANE) {
            return new Decision(Refs.FROST_BURNED_BIOMASS, false);
        }

        // 配置表优先于数据包表：那份配置默认就有内容，是 CDU 的主要机制
        for (BlockPair pair : configured) {
            if (block == pair.from()) {
                return new Decision(pair.to(), true);
            }
        }
        Block fromJson = SporeCompat.cduConversion(block);
        if (fromJson != null) {
            return new Decision(fromJson, true);
        }

        if (state.is(Refs.FUNGAL) || state.is(Refs.FOLIAGE)) {
            return new Decision(Blocks.AIR, false);
        }
        return null;
    }

    /** 把结论落成一个具体的方块状态（这一步才用到源状态，因为要复制属性）。 */
    private static BlockState apply(Decision decision, BlockState state) {
        BlockState target = decision.target().defaultBlockState();
        return decision.copyProperties() ? copyProperties(target, state) : target;
    }

    /**
     * 把源方块的属性按<b>同名</b>复制到目标状态上。
     *
     * <p>这是 CDU 的 {@code convertFromJson} 的做法（也是原版的通行做法）：感染石台阶 → 石台阶时
     * 朝向、半砖类型这些属性得跟着走，否则会变成默认朝向。同名属性取值域不同时（比如两边的枚举
     * 成员不一样）忽略该属性——CDU 也是这么吞掉异常的。
     */
    private static BlockState copyProperties(BlockState target, BlockState source) {
        BlockState result = target;
        for (Map.Entry<Property<?>, Comparable<?>> entry : source.getValues().entrySet()) {
            Property<?> property = result.getBlock().getStateDefinition().getProperty(entry.getKey().getName());
            if (property == null) {
                continue;
            }
            result = setUnchecked(result, property, entry.getValue());
        }
        return result;
    }

    /**
     * 属性类型在编译期对不上（源和目标的 {@code Property} 泛型参数不同），只能走裸类型。
     *
     * <p>那个 {@code (BlockState)} 转换不是多余的：裸类型会让 {@code setValue} 的类型参数被擦除，
     * 返回值的静态类型可能退化成 {@code Object}。写上它，两种情况都编译得过。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setUnchecked(BlockState state, Property property, Comparable value) {
        try {
            return (BlockState) state.setValue(property, value);
        } catch (IllegalArgumentException e) {
            return state;
        }
    }

    /**
     * 解析 Spore 配置里那张「感染方块|干净方块」表。
     *
     * <p>用 {@code tryParse} 而不是 {@code new ResourceLocation}：配置是自由文本，写错一个字符
     * 就会抛异常。CDU 那边是直接 new 的，也就是<b>配置写错会让它崩</b>；这里跳过非法项，
     * 一颗新星不该因为别人配置里的笔误而炸掉。
     */
    private static List<BlockPair> parseConfiguredConversions() {
        List<BlockPair> pairs = new ArrayList<>();
        for (String entry : SporeCompat.cduBlockCleaning()) {
            String[] parts = entry.split("\\|");
            if (parts.length != 2) {
                continue;
            }
            ResourceLocation fromId = ResourceLocation.tryParse(parts[0]);
            ResourceLocation toId = ResourceLocation.tryParse(parts[1]);
            if (fromId == null || toId == null) {
                continue;
            }
            Block from = ForgeRegistries.BLOCKS.getValue(fromId);
            Block to = ForgeRegistries.BLOCKS.getValue(toId);
            if (from != null && to != null) {
                pairs.add(new BlockPair(from, to));
            }
        }
        return pairs;
    }

    /** 一对「源方块 → 结果方块」。 */
    private record BlockPair(Block from, Block to) {
    }

    /**
     * Spore 的那些引用项，只解析一次。
     *
     * <p>放在内部类里是为了<b>延迟到首次使用才初始化</b>：注册表在游戏运行期是冻结的，
     * 值不会变，但如果在类加载期就去取，会撞上"注册还没完成"的时机问题。
     */
    private static final class Refs {

        private static final TagKey<Block> FUNGAL = SporeCompat.fungalBlocks();
        private static final TagKey<Block> FOLIAGE = SporeCompat.removableFoliage();
        private static final TagKey<Block> BIOMASS = SporeCompat.biomass();

        private static final Block REMAINS = SporeCompat.remains();
        private static final Block FROZEN_REMAINS = SporeCompat.frozenRemains();
        private static final Block MEMBRANE = SporeCompat.membraneBlock();
        private static final Block FROST_BURNED_BIOMASS = SporeCompat.frostBurnedBiomass();
        private static final Block BILE = SporeCompat.bile();
        private static final Block CRUSTED_BILE = SporeCompat.crustedBile();

        private Refs() {
        }
    }
}
