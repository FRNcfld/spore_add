package com.frnc.spore_add.world;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * 「哪里正处在液态寒冷的影响下」的登记处。两个用途：
 *
 * <ul>
 *   <li>{@link #isMarked} —— 区块粒度的粗筛，给"影响范围内的冰不融化"用（{@code IceMeltMixin}）；</li>
 *   <li>{@link #isWithinRange} —— 精确到半径的判定，给"影响范围内施加寒冷效果"用
 *       （{@code ModEvents#onLivingTick}）。</li>
 * </ul>
 *
 * <h2>为什么不用 SavedData 持久化</h2>
 * 登记由源头方块<b>每秒刷新</b>、有效期只有 {@link #TTL_TICKS}（2 秒）。既然活着的源头下一秒就会重新登记，
 * 存盘就没有意义——重启后源头还在世界里，最多 1 秒就补上了。所以这里用挂在类上的内存映射，
 * 省掉整套 {@code SavedData} 的读写代码。
 *
 * <h2>两级结构：区块粗筛 + 源头精判</h2>
 * 冰每融一次、实体每 tick 都要问一次"这里算不算范围内"，所以判定必须便宜：
 * <ol>
 *   <li>先查所在区块有没有被登记——O(1)，绝大多数位置在这里就被否掉了；</li>
 *   <li>只有第一个问题为"是"时，才去查该区块记录的那几个源头坐标、算精确距离。
 *       登记时只登记<b>源头半径能触及的那些区块</b>，所以粗筛是"宁可多算"而不会漏。</li>
 * </ol>
 * 冰不融化那一侧只用第 1 步（区块粒度），因为原版冰是随机刻、一次可能同时点到几十块，
 * 逐块算距离代价太大；代价是同一区块内略远的冰也会被保护，偏差方向是"倾向让冰保持冻结"。
 */
public final class FrozenChunks {

    /** 登记有效期（tick）。源头每秒刷新一次，2 秒足够宽松。 */
    private static final int TTL_TICKS = 40;

    /** 与液态寒冷的影响半径保持一致（见 {@code LiquidColdBlock#RADIUS}）。 */
    private static final int RADIUS = 6;

    /** 条目数超过它就顺手清一次过期项，避免长期游玩后映射无限增长。 */
    private static final int PRUNE_THRESHOLD = 4096;

    /** 区块 → 登记时间。粗筛用。 */
    private static final Map<ResourceKey<Level>, Map<Long, Long>> CHUNKS = new HashMap<>();

    /** 区块 → (源头坐标 → 登记时间)。精判用；一个区块里可能有多个源头。 */
    private static final Map<ResourceKey<Level>, Map<Long, Map<Long, Long>>> SOURCES = new HashMap<>();

    private FrozenChunks() {
    }

    /**
     * 源头方块每秒调用一次：登记自己、以及自己半径能触及的那些区块。
     */
    public static void mark(Level level, BlockPos source) {
        if (level.isClientSide()) {
            return;
        }
        long now = level.getGameTime();
        Map<Long, Long> chunks = CHUNKS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        Map<Long, Map<Long, Long>> sources = SOURCES.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        if (chunks.size() > PRUNE_THRESHOLD) {
            prune(chunks, now);
            pruneSources(sources, now);
        }

        long packedSource = source.asLong();
        int minX = (source.getX() - RADIUS) >> 4;
        int maxX = (source.getX() + RADIUS) >> 4;
        int minZ = (source.getZ() - RADIUS) >> 4;
        int maxZ = (source.getZ() + RADIUS) >> 4;
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                long key = ChunkPos.asLong(chunkX, chunkZ);
                chunks.put(key, now);
                sources.computeIfAbsent(key, k -> new HashMap<>()).put(packedSource, now);
            }
        }
    }

    /** 该位置所在区块是否仍在有效期内（区块粒度的粗筛）。 */
    public static boolean isMarked(Level level, BlockPos pos) {
        Map<Long, Long> chunks = CHUNKS.get(level.dimension());
        if (chunks == null) {
            return false;
        }
        Long stamped = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        return stamped != null && level.getGameTime() - stamped <= TTL_TICKS;
    }

    /**
     * 该位置是否真的落在某个源头方块的半径之内（精确到球）。
     *
     * <p>先用 {@link #isMarked} 粗筛，再对登记在附近区块里的源头逐个算距离——
     * 因为登记时只登记半径能触及的区块，所以粗筛不会漏掉真正的范围内位置。
     */
    public static boolean isWithinRange(Level level, BlockPos pos) {
        if (!isMarked(level, pos)) {
            return false;
        }
        Map<Long, Map<Long, Long>> sources = SOURCES.get(level.dimension());
        if (sources == null) {
            return false;
        }
        long now = level.getGameTime();
        int limit = RADIUS * RADIUS;
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        // 只看该位置周围 3×3 个区块里登记的源头，不必扫全世界的源头
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Map<Long, Long> inChunk = sources.get(ChunkPos.asLong(chunkX + dx, chunkZ + dz));
                if (inChunk == null) {
                    continue;
                }
                Iterator<Map.Entry<Long, Long>> it = inChunk.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Long, Long> entry = it.next();
                    if (now - entry.getValue() > TTL_TICKS) {
                        it.remove();     // 顺手清掉已经失效的源头，免得越积越多
                        continue;
                    }
                    BlockPos source = BlockPos.of(entry.getKey());
                    if (source.distSqr(pos) <= limit) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void prune(Map<Long, Long> stamps, long now) {
        stamps.entrySet().removeIf(entry -> now - entry.getValue() > TTL_TICKS);
    }

    private static void pruneSources(Map<Long, Map<Long, Long>> sources, long now) {
        Iterator<Map<Long, Long>> it = sources.values().iterator();
        while (it.hasNext()) {
            Map<Long, Long> inChunk = it.next();
            inChunk.entrySet().removeIf(entry -> now - entry.getValue() > TTL_TICKS);
            if (inChunk.isEmpty()) {
                it.remove();
            }
        }
    }
}
