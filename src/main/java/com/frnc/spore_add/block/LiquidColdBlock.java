package com.frnc.spore_add.block;

import java.util.function.Supplier;

import com.frnc.spore_add.SporeAddPlayerConfig;
import com.frnc.spore_add.effect.ColdEffects;
import com.frnc.spore_add.effect.FrostbiteLevels;
import com.frnc.spore_add.world.FrostProof;
import com.frnc.spore_add.world.FrozenChunks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * 液态寒冷的流体方块。它比另外两种流体都忙，身上挂了四件事：
 *
 * <ol>
 *   <li><b>泡在里面会失温</b>——和细雪一样，只靠 {@code setIsInPowderSnow(true)}（见 {@code CoolantBlock} 的说明）；</li>
 *   <li><b>区域寒冷效果</b>——以每个源头方块为中心、<b>球形</b>范围内的生物持续受到寒冷效果
 *       （细雪式冻结 + 无上限冻伤）。<b>注意它不由本类施加</b>：那必须在实体自己的 tick 里做，
 *       见 {@code ColdEffects} 的类注释与 {@code LiquidColdRangeMixin}；本类只负责把范围的登记
 *       （{@link FrozenChunks}）维护好。</li>
 *   <li><b>冰扩散</b>——放下时立刻冻住接触到的空气与流体，之后每秒在<b>同一个球形</b>内随机尝试替换；</li>
 *   <li><b>维持"影响范围"登记</b>——范围判定与"范围内的原版冰不融化"都靠它（{@code IceMeltMixin}）。</li>
 * </ol>
 *
 * <p><b>区域效果与冰扩散共用同一个范围和同一个半径</b>，所以"能看到冰的地方"与"会被冻的地方"
 * 始终是同一片区域。半径来自 {@link SporeAddPlayerConfig#liquidColdRadius()}——本类与 {@link FrozenChunks}
 * 都读同一个值，不存在"两处常量要记得一起改"的问题。
 *
 * <h2>为什么用调度刻而不是 randomTick</h2>
 * 原版的 {@code randomTick} 时机不可控（每区块每 tick 只随机挑几个方块），做不了"稳定推进"。
 * 调度刻 {@code level.scheduleTick(pos, this, N)} 是确定性的，在 {@link #tick} 里做完事再排下一次，
 * 这正是原版 {@code FrostedIceBlock} 的做法。
 *
 * <p><b>只有源头方块才当中心</b>：流动出来的每一格都排调度刻的话，扩散速率会随液体面积爆炸，
 * 与"每个源头方块各自一个中心"的约定不符。
 */
public class LiquidColdBlock extends LiquidBlock {

    // 影响半径不在这里——它来自 SporeAddPlayerConfig#liquidColdRadius()，本类与 FrozenChunks 共用同一个值。
    // 是球形而不是立方体：范围判定按欧氏距离（见 FrozenChunks#isWithinRange），冰分层也按同一套距离
    // （见 #iceFor）。

    /** 每个源头方块每秒的扩散尝试次数。 */
    private static final int ATTEMPTS_PER_SECOND = 16;

    /** 每次尝试的成功概率（各次独立）。 */
    private static final double SUCCESS_CHANCE = 0.5;

    /** 调度刻周期：每秒一拍，冰扩散与范围登记都在这一拍里做。 */
    private static final int TICK_DELAY = 20;

    /**
     * 替换方块用的更新标志：
     * 同步客户端（2）、抑制邻居反应避免连锁（16）、抑制邻居反应掉落的物品（32）。
     * 刻意不含 {@code UPDATE_NEIGHBORS}。
     */
    private static final int REPLACE_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    public LiquidColdBlock(Supplier<? extends FlowingFluid> fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide() || quietPlacement) {
            return;   // quietPlacement：核弹那条路不要这份"见面礼"，见 placeQuietly
        }
        // 放下时立刻把接触到的空气与流体冻成浮冰
        freezeNeighbours(level, pos);
        if (isSource(level, pos)) {
            level.scheduleTick(pos, this, TICK_DELAY);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (!isSource(level, pos)) {
            // 已经被替换/不再是源头，扩散自然停掉，不再续排
            return;
        }
        // 每秒一拍：登记影响范围（登记有效期 2 秒，够用），再推进一轮冰扩散。
        // 注意区域寒冷效果不在这里——它必须由实体自己的 tick 施加，原因见 ColdEffects 的类注释
        FrozenChunks.mark(level, pos);
        spreadIce(level, pos, random);
        level.scheduleTick(pos, this, TICK_DELAY);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);

        if (level.isClientSide()) {
            return;
        }
        if (entity instanceof LivingEntity living) {
            // 流动出来的那部分既不排调度刻、也不在"源头半径"的登记里，所以泡在里面这件事仍要在这里兜住。
            // 与区域效果重叠时，FrostbiteLevels 的间隔守卫会保证每秒只加一层
            ColdEffects.chill(living, FrostbiteLevels.UNLIMITED);
        }
    }

    /** 只有源头方块才配当扩散中心；流动出来的每一格都排调度刻会让速率随面积爆炸。 */
    private static boolean isSource(Level level, BlockPos pos) {
        return level.getFluidState(pos).isSource();
    }

    /** 放下时的立即冻结：只碰空气与流体。 */
    private static void freezeNeighbours(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos target = pos.relative(direction);
            if (isReplaceable(level, target, true)) {
                level.setBlock(target, Blocks.PACKED_ICE.defaultBlockState(), REPLACE_FLAGS);
            }
        }
    }

    /**
     * 当前这次放置要不要跳过 {@link #freezeNeighbours}。
     *
     * <p>只在 {@link #placeQuietly} 里短暂置起。{@code onPlace} 是在 {@code setBlock} 的调用栈里
     * 同步执行的，所以这个标志一定能被读到、也一定会被 {@code finally} 复位。
     * 世界的方块改动本来就只在服务端线程发生，不需要 volatile。
     */
    private static boolean quietPlacement;

    /**
     * 放下液态寒冷，但<b>不要</b>顺手把周围六格冻成浮冰。
     *
     * <h2>为什么需要它</h2>
     * {@link #freezeNeighbours} 对上下 + 四个水平各放一块浮冰，于是放下一个液态寒冷，
     * 俯视就是一个<b>轴对齐的十字</b>、竖直正好三格高。玩家自己拿桶倒一桶时这没什么，
     * 但「冰雪的叹息」会在<b>爆心</b>放一格液态寒冷——那个十字就正好落在圆心，
     * 把整片圆形冰面切成规规整整的四块，非常扎眼。
     *
     * <p>所以核弹那条路走这里：方块照放，只是不附带那份"见面礼"。
     * 玩家倒桶、以及别的模组放置液态寒冷时，行为一字未变。
     */
    public static void placeQuietly(Level level, BlockPos pos) {
        quietPlacement = true;
        try {
            level.setBlock(pos, ModBlocks.LIQUID_COLD.get().defaultBlockState(), Block.UPDATE_ALL);
        } finally {
            quietPlacement = false;
        }
    }

    /**
     * 一轮扩散：在影响半径的球形内随机取点尝试替换。
     *
     * <p>"空气替换最快、其次流体、最后方块"是刻意用<b>自然结果</b>实现的——三类成功概率都是
     * 你定的 50%，而空气最容易被探到、也最不阻挡，于是实际最先被换掉。若要硬性优先级
     * （例如空气 70% / 流体 50% / 方块 30%），只需在这里按类别给不同的概率。
     */
    private static void spreadIce(ServerLevel level, BlockPos center, RandomSource random) {
        // 一次读出来用整轮：半径配置中途被改的话，至少这一轮用的是同一个值
        int radius = SporeAddPlayerConfig.liquidColdRadius();
        for (int attempt = 0; attempt < ATTEMPTS_PER_SECOND; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dy = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > radius) {
                continue;   // 落在立方体的角上，球外
            }
            BlockPos target = center.offset(dx, dy, dz);
            if (!level.hasChunkAt(target)) {
                continue;
            }
            if (!isReplaceable(level, target, false)) {
                continue;
            }
            if (random.nextDouble() >= SUCCESS_CHANCE) {
                continue;
            }
            level.setBlock(target, iceFor(distance, radius), REPLACE_FLAGS);
        }
    }

    /**
     * 从内到外：内 1/3 <b>蓝冰</b>、中 1/3 <b>浮冰</b>、外 1/3 <b>冰</b>。
     *
     * <p>注意最外层是原版 {@code minecraft:ice}——三种冰里<b>只有它会融化</b>（方块光照 ≥ 11 化成水），
     * 所以这一层正是 {@code IceMeltMixin} 要保住的对象。浮冰与蓝冰本来就不融。
     */
    private static BlockState iceFor(double distance, int radius) {
        if (distance * 3 <= radius) {
            return Blocks.BLUE_ICE.defaultBlockState();
        }
        if (distance * 3 <= radius * 2) {
            return Blocks.PACKED_ICE.defaultBlockState();
        }
        return Blocks.ICE.defaultBlockState();
    }

    /**
     * 这个位置能不能被冻掉。
     *
     * @param airAndFluidOnly 放下时的立即冻结只看空气与流体；扩散时连固体方块也换
     */
    private static boolean isReplaceable(Level level, BlockPos pos, boolean airAndFluidOnly) {
        BlockState state = level.getBlockState(pos);
        if (FrostProof.isProtected(state)) {
            // 基岩、屏障、命令方块、末地传送门框架这类不可破坏的方块，以及传送门，
            // 永远不碰。名单见 FrostProof
            return false;
        }
        if (state.is(ModBlocks.LIQUID_COLD.get())) {
            // 本 mod 自己的液态寒冷不冻：否则池子会把自己冻成冰，扩散中心也随之消失
            return false;
        }
        if (state.isAir() && level.dimension().equals(Level.END)) {
            // 末地里空气永远不换。末地是"空岛 + 大片虚空"的地形，把空气冻成冰等于朝虚空里凭空长出一大块
            // 冰坨；那里的液态寒冷只该冻已有的方块（末地石等）。主世界/下界不受这条影响——
            // 那两处把空气冻住是想要的效果（挖开的地道会被冰封上）。
            return false;
        }
        if (airAndFluidOnly) {
            return state.isAir() || !state.getFluidState().isEmpty();
        }
        return true;
    }
}
