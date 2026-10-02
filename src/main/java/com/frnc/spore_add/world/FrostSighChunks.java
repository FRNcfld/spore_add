package com.frnc.spore_add.world;

import java.util.HashSet;
import java.util.Set;

import com.frnc.spore_add.SporeAdd;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;

/**
 * 「冰雪的叹息」倒计时期间的区块强制加载。
 *
 * <h2>为什么需要它</h2>
 * BlockEntity 只有在自己的区块<b>正在 tick</b> 时才会被 tick。半径 128 的倒计时要跑 60 秒，
 * 玩家几乎不可能一直守在原地——一走开区块卸载，倒计时就停了。所以激活时把圆盘覆盖的区块钉住。
 *
 * <p>半径 128 时那大约是 <b>226 个区块</b>（区块数按圆盘算，不是外接正方形：那会是 289 个）。
 * 这是实打实的开销，所以配置里留了开关（{@code frostSigh.forceLoadChunks}）。
 *
 * <h2>刻意不注册 setForcedChunkLoadingCallback</h2>
 * Forge 提供了那个回调，用来在世界重载时清理"已经不该存在"的票。这里<b>不用</b>它，原因是它不安全：
 * 回调发生在区块被强制加载<b>之前</b>，那时 {@code level.getBlockEntity(owner)} 很可能返回 null
 * （区块还没加载），于是"这个位置没有已激活的方块"这个判断对<b>每一个合法票都成立</b>，
 * 会把它们全部误删——倒计时就此永久停摆。
 *
 * <p>不注册它也不会漏票：Forge 会把票存进 {@code ForcedChunksSavedData} 并在世界重载时自动恢复，
 * 而所有"票不该再留着"的路径都有对应处理——方块被挖掉走 {@code onRemove}，
 * 正常引爆走 {@code FrostSighBlockEntity#serverTick}，两条都会调 {@link #release}。
 */
public final class FrostSighChunks {

    /**
     * 是否要求完整 tick。
     *
     * <p>必须是 {@code true}：只要"加载不要 tick"的话，BlockEntity <b>不会</b>被 tick，
     * 倒计时照样停摆——这正好是这个类要解决的问题。
     */
    private static final boolean TICKING = true;

    private FrostSighChunks() {
    }

    /** 钉住圆盘覆盖的所有区块。重复调用是安全的（Forge 内部按票去重）。 */
    public static void force(ServerLevel level, BlockPos owner, int radius) {
        forEachChunk(level, owner, radius, true);
    }

    /** 放掉圆盘覆盖的所有区块。 */
    public static void release(ServerLevel level, BlockPos owner, int radius) {
        forEachChunk(level, owner, radius, false);
    }

    /**
     * 只钉住 {@code owner} 所在的<b>那一个</b>区块。
     *
     * <p>给降雪实体用：它最长要活 60 分钟，而它只在自己被 tick 的时候才撒得出雪。
     * 一个区块的票是完全可以忽略的开销，换来的是"玩家走远了雪还在下"。
     *
     * <p>与 {@link #force} 一样，票会被 Forge 存进 {@code ForcedChunksSavedData} 并在重启后恢复
     * ——正因如此<b>释放必须可靠</b>：降雪实体到期时释放，另外在 {@code remove} 里兜底再释放一次。
     */
    public static void forceSingle(ServerLevel level, BlockPos owner) {
        ForgeChunkManager.forceChunk(level, SporeAdd.MOD_ID, owner,
                owner.getX() >> 4, owner.getZ() >> 4, true, TICKING);
    }

    /** 放掉 {@link #forceSingle} 钉住的那一个区块。 */
    public static void releaseSingle(ServerLevel level, BlockPos owner) {
        ForgeChunkManager.forceChunk(level, SporeAdd.MOD_ID, owner,
                owner.getX() >> 4, owner.getZ() >> 4, false, TICKING);
    }

    private static void forEachChunk(ServerLevel level, BlockPos owner, int radius, boolean add) {
        for (long chunk : chunkKeys(owner, radius)) {
            ForgeChunkManager.forceChunk(level, SporeAdd.MOD_ID, owner,
                    ChunkPos.getX(chunk), ChunkPos.getZ(chunk), add, TICKING);
        }
    }

    /**
     * 圆盘覆盖的区块，打包成 {@code ChunkPos.asLong} 的形式。
     *
     * <p>按"区块与圆盘是否相交"筛，而不是直接取外接正方形的所有区块——
     * 半径 128 时前者 226 个、后者 289 个，差 28%。
     */
    public static Set<Long> chunkKeys(BlockPos center, int radius) {
        Set<Long> keys = new HashSet<>();
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;
        double limitSqr = (double) radius * radius;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                // 区块中心到圆心的距离，减去半个区块宽 = 到圆心的最近距离
                double dx = Math.abs((chunkX << 4) + 8 - center.getX()) - 8.0D;
                double dz = Math.abs((chunkZ << 4) + 8 - center.getZ()) - 8.0D;
                dx = Math.max(0.0D, dx);
                dz = Math.max(0.0D, dz);
                if (dx * dx + dz * dz <= limitSqr) {
                    keys.add(ChunkPos.asLong(chunkX, chunkZ));
                }
            }
        }
        return keys;
    }
}
