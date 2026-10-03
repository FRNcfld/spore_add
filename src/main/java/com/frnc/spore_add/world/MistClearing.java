package com.frnc.spore_add.world;

import com.frnc.spore_add.SporeAddPlayerConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 冰雾在存续期间的持续清理：按 CDU 的规则，在雾的范围内反复清真菌方块。
 *
 * <h2>它与 {@link FungalClearing#clear} 的分工</h2>
 * 爆发那一刻的清是<b>一次性整片扫</b>（{@link FungalClearing#clear} 扫一个球），形状与雾一致。
 * 但雾会存在十几秒到十分钟，这期间被感染的地形还在长——只清一次的话，雾还在飘、
 * 底下却已经重新长满。所以这里负责"雾还在的时候一直清"。
 *
 * <h2>为什么核弹那边按「环」推进</h2>
 * 一次整片扫是 O(r³)：半径 128 时是 <b>880 万个方块</b>，一秒一遍根本不可能。
 * 所以核弹的雾把工作摊到时间上。
 *
 * <p><b>而摊的方式决定了它有没有用。</b>最初写的是行主序游标（{@code dx} 从 -r 到 r、
 * 每个 {@code dx} 里 {@code dz} 从 -r 到 r，每次推 {@code columnsPerTry} 列）——它确实
 * 最终会覆盖整个圆盘，但有两个致命问题：
 * <ul>
 *   <li><b>空间上不连贯。</b>任意时刻只在扫一条很窄的行带，玩家看到的是一片安静，
 *       完全不知道它在工作；</li>
 *   <li><b>追不上真菌再生。</b>默认预算 128 列/秒，而整盘有 51,433 列，
 *       走完一遍要 <b>402 秒</b>。冰雾默认只活 600 秒，所以整盘<b>只能覆盖约 1.5 遍</b>：
 *       你在圆盘某一侧放的菌，要 5~6 分钟才轮得到一次，而第二遍从 6.7 分钟才开始，
 *       走到中心已经是第 10.1 分钟——雾已经散了。净效果就是玩家报的那句
 *       「这团雾根本不清菌」。</li>
 * </ul>
 * 换成<b>按环推进</b>之后，同一份工作量被组织成一道道从中心推出去的环：空间连贯、
 * 看得见、而且<b>每一处在每遍之内必然轮到一次</b>（不再是"取决于它在行主序里的位置"）。
 *
 * <p><b>成本其实更低。</b>整盘 5 万列 × 每列扫 384 格高 ≈ 2000 万格；现有的 2 号冲击环
 * 本来就在 20 秒内扫完同样这 2000 万格（约 49k 格/tick）。把它摊到
 * {@code mistClear.pulseSeconds}（默认 60 秒）上只有约 16k 格/tick——比已经有先例的那条路更轻。
 *
 * <h2>环带是怎么枚举的</h2>
 * 遍历外圈的<b>外接方框</b>、按 {@code rFrom² ≤ dx²+dz² < rTo²} 筛出环带里的列。
 * 看着像浪费（半径 128 时每 tick 要过一遍 257×257 ≈ 6.6 万个格子），但那 6.6 万次只是
 * 整数乘加与比较，约 0.1 毫秒；真正贵的是被筛出来的那些<b>列</b>（每列扫 384 格）。
 * 而按环推进时环带很窄，筛出来的列数是被预算管住的——所以这个写法比"沿圆周逐个算角度"
 * 更简单，而且不可能漏格或重格。
 *
 * <h2>冰霜新星为什么不走这里</h2>
 * 那团雾是个半径十几格的小球（不到两万方块），每 {@code intervalTicks} 整球扫一遍反而更简单，
 * 所以它直接调 {@link FungalClearing#clear}。它走的是 {@code Rules} 同一份规则，两边不会各清各的。
 */
public final class MistClearing {

    /** 与 {@link FungalClearing} 取同一组更新标志：同步客户端 + 通知邻居。 */
    private static final int CLEAR_FLAGS = Block.UPDATE_ALL;

    private MistClearing() {
    }

    /**
     * 清掉环带 {@code [rFrom, rTo)} 里的所有真菌方块。
     *
     * <p>环带内每一列都<b>扫到底</b>（{@code minBuildHeight} 到 {@code maxBuildHeight}），
     * 与冰雪的叹息爆发时那一趟一致：孢子会感染地表以下，只看地表会漏掉一大半。
     *
     * @param outerRadius 雾的最终半径。用来夹住 {@code rTo}——环推到边缘时浮点误差
     *                    可能让 {@code rTo} 略微超过它，不该因此去动范围外的方块
     * @param rules       已经解析好的规则集。由调用方持有并复用，<b>不要</b>在这里调
     *                    {@link FungalClearing#rules()}：那是每 tick 一次重新解析 Spore 的配置表，
     *                    而规则集本身可以活一整个雾的寿命
     * @return 这次实际清了几个列（诊断与检查点用）
     */
    public static int clearRingBand(ServerLevel level, BlockPos center, double rFrom, double rTo,
                                    int outerRadius, FungalClearing.Rules rules) {
        if (rTo <= rFrom) {
            return 0;
        }
        int minX = Mth.floor(center.getX() - rTo);
        int maxX = Mth.ceil(center.getX() + rTo);
        int minZ = Mth.floor(center.getZ() - rTo);
        int maxZ = Mth.ceil(center.getZ() + rTo);
        double outerSqr = rTo * rTo;
        double innerSqr = Math.max(0.0D, rFrom) * Math.max(0.0D, rFrom);
        double capSqr = (double) outerRadius * outerRadius;

        int columns = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x - center.getX();
                double dz = z - center.getZ();
                double distSqr = dx * dx + dz * dz;
                if (distSqr >= outerSqr || distSqr < innerSqr || distSqr > capSqr) {
                    continue;   // 不在这一圈里，或超出雾的最终半径
                }
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;   // 未加载的区块不动（玩家走远时圆盘外圈就是这种状态）
                }
                clearColumn(level, x, z, rules);
                columns++;
            }
        }
        return columns;
    }

    /**
     * 清一列。
     *
     * <p>{@code hasChunkAt} 由调用方判过一次：整列都在同一个区块里，没必要每格都问一遍。
     */
    private static void clearColumn(ServerLevel level, int x, int z, FungalClearing.Rules rules) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y < maxY; y++) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            BlockState replacement = rules.replacementFor(state);
            if (replacement != null) {
                level.setBlock(cursor, replacement, CLEAR_FLAGS);
            }
        }
    }

    /** 冰雾当前是否应该清理。配置的单一读取点，免得三个调用方各判各的。 */
    public static boolean isEnabled() {
        return SporeAddPlayerConfig.mistClearEnabled();
    }
}
