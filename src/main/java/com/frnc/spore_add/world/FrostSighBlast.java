package com.frnc.spore_add.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.frnc.spore_add.SporeAddPlayerConfig;
import com.frnc.spore_add.block.LiquidColdBlock;
import com.frnc.spore_add.block.ModBlocks;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.fluid.ModFluids;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * 「冰雪的叹息」爆发的效果逻辑。爆发是<b>分 tick 推进</b>的，这里放"每一步做什么"，
 * 推进的节奏由 {@link com.frnc.spore_add.entity.FrostSighShockwaveEntity} 掌握。
 *
 * <h2>为什么必须分 tick</h2>
 * 半径 128 的圆盘是 <b>51,433 列</b>。一次算完等于一个 tick 里做 5 万次方块查询加几万次 setBlock，
 * 服务器会直接卡死。所以冲击环按 `shockwaveSeconds` 匀速外扩，每 tick 只处理环带内的那些列
 * （20 秒扩完时最外圈约 257 列/tick，可接受）。
 */
public final class FrostSighBlast {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 铺冰用的更新标志。
     *
     * <p>不含 {@code UPDATE_NEIGHBORS}：这里要铺的是几万格冰，逐格触发邻居更新与光照重算是主要开销，
     * 而且这些位置本来就要被整片改掉、没有"邻居该连锁反应"的语义。
     */
    private static final int ICE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** 替换流体用的标志。这里要通知邻居：流体被抽掉之后，上方悬着的东西该掉、光照也该重算。 */
    private static final int FLUID_FLAGS = Block.UPDATE_ALL;

    /**
     * 清真菌用的标志。
     *
     * <p>与 {@code FungalClearing} 的 {@code CLEAR_FLAGS} <b>刻意取同一个值</b>：两处用的是同一套
     * CDU 规则，落笔方式也该一样，否则同一个方块在冰霜新星与冰雪的叹息下会被更新得不一样。
     */
    private static final int FUNGAL_FLAGS = Block.UPDATE_ALL;

    // 铺冰分层与冰刺形状的全部数值都来自配置（frostSigh 段与 [frostSigh.spike] 子表），
    // **只在方法体内读**，不做成静态常量——静态常量会在类初始化时定死，改配置就得重启。
    //
    // 算术约束（配置里的 defineInRange 已经锁住，这里记下原因）：
    //   · spike.maxRelativeRadius 是中心系数的除数 → 下限 0.01，填 0 会算出无穷高
    //   · spike.cellSize       是 floorDiv 的除数   → 下限 1
    //   · spike.rarity         是 floorMod 的模数   → 下限 1，**填 0 会直接抛 ArithmeticException 崩服**
    //   · spike.baseRadius     是高度公式的除数     → 下限 1

    private FrostSighBlast() {
    }

    /**
     * 爆发瞬间做的那几件一次性的事：中心放液态寒冷、改生物群系、放闪光。
     *
     * <p>逐列的铺冰与冻结不在这里——那些由冲击环推进时按列处理。
     */
    public static void detonate(ServerLevel level, BlockPos center) {
        int radius = SporeAddPlayerConfig.frostSighRadius();

        // 需求 8：爆发点处生成一格液态寒冷。
        // 放在最前面，免得后面铺冰把它盖掉（冲击环是从中心往外推的，中心那一列会被最先处理）。
        //
        // ⚠️ 必须用 placeQuietly 而不是裸 setBlock：LiquidColdBlock 放下时会把上下 + 四个水平
        // 各冻一块浮冰，那正好是一个<b>轴对齐的十字</b>（竖直三格高）。放在爆心，它就会把整片
        // 圆形冰面切成规规整整的四块——一发核弹的正中央顶着个大十字，非常扎眼。
        LiquidColdBlock.placeQuietly(level, center);

        changeBiome(level, center, radius);
    }

    // ------------------------------------------------------------------
    // 冲击环：逐列的处理
    // ------------------------------------------------------------------

    /**
     * 处理冲击环在 {@code [rFrom, rTo)} 这一圈里扫过的所有列。
     *
     * <p>用"上一 tick 的半径"到"这一 tick 的半径"之间的环带来定位列，所以每一列只会被处理一次
     * ——不会重复铺冰，也不会漏掉中间那些列。
     *
     * @param fungalRules CDU 清真菌的规则集，由 {@code FrostSighShockwaveEntity} 解析一次后传进来。
     *                    刻意<b>不在</b>这里调 {@code FungalClearing.rules()}：那是每 tick 一次，
     *                    而这个方法每 tick 要被 5 万列里的几百列共用。
     */
    public static void sweepRing(ServerLevel level, BlockPos center, double rFrom, double rTo, int radius,
                                 FungalClearing.Rules fungalRules) {
        int minX = Mth.floor(center.getX() - rTo);
        int maxX = Mth.ceil(center.getX() + rTo);
        int minZ = Mth.floor(center.getZ() - rTo);
        int maxZ = Mth.ceil(center.getZ() + rTo);
        double outerSqr = rTo * rTo;
        double innerSqr = rFrom * rFrom;
        double capSqr = (double) radius * radius;
        // 用爆心当盐：每一场爆发的冰刺布局都不同，而同一场内部处处一致（冰刺是 (x,z) 的纯函数）
        long spikeSalt = center.asLong() * 0x9E3779B97F4A7C15L;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x - center.getX();
                double dz = z - center.getZ();
                double distSqr = dx * dx + dz * dz;
                if (distSqr > outerSqr || distSqr < innerSqr || distSqr > capSqr) {
                    continue;   // 不在这一圈的环带里，或超出影响范围
                }
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;   // 未加载的区块不动（强制加载开着时不该发生，但这里仍然兜一道）
                }
                coverColumn(level, x, z, Math.sqrt(distSqr), radius, fungalRules, spikeSalt);
            }
        }
    }

    /**
     * 处理单独一列：找地面、铺 1~3 层冰、把流体换成冰、清掉真菌方块。
     *
     * <h2>为什么是一次扫到底</h2>
     * 从最高处往下逐格看，用整列扫描而不是高度图：高度图只给"最高的固体"，
     * 而需求 10 要的是"这一列里所有的流体"，流体可能藏在地表以下（地下水、岩浆湖）；
     * CDU 清真菌同理——孢子会感染地表以下，只看地表会漏掉一大半。
     *
     * <p><b>但流体的语义必须原样保住。</b>原来这里在碰到第一个实心方块时 {@code break}，
     * 那个 {@code break} 兼着"只冻海床以上的流体"的职责。既然循环现在要扫到底，
     * 就改用 {@link #fluidZone} 开关接替它：一旦碰到第一个非空气、非流体的方块就关掉，
     * 往下只剩真菌那一件事。直接删掉那个判断的话，圆盘内**所有**地下含水层与岩浆湖都会冻成冰，
     * 那是完全不同的一个量级的世界改动，不是需求要的。
     *
     * @param distance 这一列到爆发中心的水平距离
     */
    private static void coverColumn(ServerLevel level, int x, int z, double distance, int radius,
                                    FungalClearing.Rules fungalRules, long spikeSalt) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // 是否还在"地表以上"那一段——那一段才处理流体，见方法注释
        boolean fluidZone = true;

        // 这一列的水平系数（爆发中心 1 → 影响边缘 0），整列算一次就够，见 iceFor
        double horizontal = radius <= 0 ? 0.0D : 1.0D - Mth.clamp(distance / radius, 0.0D, 1.0D);

        for (int y = maxY - 1; y >= minY; y--) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }

            if (FrostProof.isProtected(state)) {
                // 名单里的方块（传送门、基岩……）：**绝不替换**，但照样算作"地层"——
                // 它下面的流体不该被冻，这一点与原版那句 `break` 的语义一致
                // （它也是"撞到第一个非空气非流体的方块就停"）。
                fluidZone = false;
                continue;
            }

            if (fluidZone) {
                FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty()) {
                    // 需求 10：所有流体（除液态寒冷）换成冰；每个流体源 1% 概率换成液态寒冷
                    if (state.is(ModBlocks.LIQUID_COLD.get())
                            || fluid.getFluidType() == ModFluids.LIQUID_COLD_TYPE.get()) {
                        continue;   // 液态寒冷豁免
                    }
                    if (fluid.isSource()
                            && level.random.nextDouble() < SporeAddPlayerConfig.frostSighSourceToLiquidColdChance()) {
                        // 同样走 quiet：不然整片冰面上会散布一堆小十字
                        LiquidColdBlock.placeQuietly(level, cursor);
                    } else {
                        // 流体没有"层"的概念，所以只按水平梯度分蓝冰/浮冰（见 iceFor）
                        level.setBlock(cursor, iceFor(level, horizontal, 0.0D), FLUID_FLAGS);
                    }
                    // 这一格已经按流体处理过了，不再走下面的真菌规则（否则会覆盖掉刚放下的冰）
                    continue;
                }
                // 第一个非空气、非流体的方块就是地表。往下不再动流体，但真菌那一趟继续扫到底。
                fluidZone = false;
            }

            // 与冰霜新星同款的 CDU 清真菌。用的是同一个规则集（FungalClearing.Rules），
            // 所以同一个感染方块在两边会变成同一个结果。空气在上面就短路了，这里必然是实心方块。
            BlockState replacement = fungalRules.replacementFor(state);
            if (replacement != null) {
                level.setBlock(cursor, replacement, FUNGAL_FLAGS);
            }
        }

        // 在地表之上铺 1~3 层
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int layers = layersFor(distance, radius);
        for (int i = 0; i < layers; i++) {
            cursor.set(x, surfaceY + i, z);
            if (cursor.getY() >= maxY) {
                break;
            }
            // ⚠️ 这一步是**无条件覆盖**——铺冰不挑地方，所以必须自己看一眼名单。
            //
            // 这正是传送门被埋掉的入口：传送门是不挡视线的非固体方块（noCollission），
            // 高度图看不见它，于是 surfaceY 会落在传送门所在的那几格上，冰就直接盖在它身上。
            // 前面那些"扫到实心方块就停"的逻辑一点忙都帮不上——这里根本没有扫描，只有 setBlock。
            if (FrostProof.isProtected(level.getBlockState(cursor))) {
                continue;
            }
            // i = 0 是最下面那一层（贴地），i = layers-1 是最上面那一层
            double layerT = layers <= 1 ? 0.0D : (double) i / (layers - 1);
            level.setBlock(cursor, iceFor(level, horizontal, layerT), ICE_FLAGS);
        }

        // 冰刺：长在冰层**之上**，所以起点是 surfaceY + layers。
        // 用同一个 horizontal 系数取色（传 layerT = 0），于是一根刺是**单一颜色**——
        // 靠中心的刺是整根蓝冰，靠外的整根浮冰，与地面上那套"中心蓝、外围浮"是同一套语言。
        double spikeCenter = radius <= 0 ? 0.0D
                : 1.0D - distance / ((double) radius * SporeAddPlayerConfig.frostSighSpikeMaxRelativeRadius());
        int spike = spikeHeight(x, z, Mth.clamp(spikeCenter, 0.0D, 1.0D), spikeSalt);
        if (spike < SporeAddPlayerConfig.frostSighSpikeMinHeight()) {
            // 太矮的不要，见 spike.minHeight。这一条就是"清除那些三格高的小柱子"。
            return;
        }
        for (int i = 0; i < spike; i++) {
            cursor.set(x, surfaceY + layers + i, z);
            if (cursor.getY() >= maxY) {
                break;
            }
            if (FrostProof.isProtected(level.getBlockState(cursor))) {
                break;   // 撞到受保护的方块就到此为止，别把传送门之类埋进冰刺里
            }
            level.setBlock(cursor, iceFor(level, horizontal, 0.0D), ICE_FLAGS);
        }
    }

    /**
     * 铺几层。
     *
     * <p>层数<b>只在靠近边缘时才下降</b>——中间那一大片一律三层。原来的阈值是 0.34 / 0.67，
     * 也就是从半径的三分之一处就开始变薄，看起来像"没铺满"；现在变薄只发生在最外面那一圈。
     */
    private static int layersFor(double distance, int radius) {
        double t = radius <= 0 ? 1.0D : distance / radius;
        // 两个分界都来自配置。**注意层数本身 3/2/1 是写死的**——这两项只改"在哪变薄"，
        // 不改"变到几层"。访问器里保证了两层分界不会小于三层分界。
        if (t < SporeAddPlayerConfig.frostSighFullLayersUntil()) {
            return 3;
        }
        return t < SporeAddPlayerConfig.frostSighTwoLayersUntil() ? 2 : 1;
    }

    /**
     * 这一格铺蓝冰还是浮冰。
     *
     * <h2>两个方向上的梯度，中间是混杂的过渡带</h2>
     * <ul>
     *   <li><b>水平</b>（{@code horizontal}）：越靠爆发中心越蓝，越靠影响边缘越是浮冰；</li>
     *   <li><b>竖直</b>（{@code layerT}）：同一摞冰里越靠下越蓝，越靠上越是浮冰。</li>
     * </ul>
     * 两个系数相乘＝这一格是蓝冰的概率。于是"中心 + 最下层"必定蓝冰、"边缘 + 最上层"必定浮冰，
     * 两者之间是两种冰混杂的过渡带——而不是原先那种一刀切（要么全蓝、要么全浮）。
     *
     * @param horizontal 水平系数：爆发中心 1 → 影响边缘 0
     * @param layerT     在这一摞冰里的位置：0 = 最下面一层，1 = 最上面一层。
     *                   <b>流体那一路没有"层"的概念</b>，按"只有水平梯度"处理，传 0
     */
    private static BlockState iceFor(Level level, double horizontal, double layerT) {
        double blueChance = horizontal * (1.0D - Mth.clamp(layerT, 0.0D, 1.0D));
        return level.random.nextDouble() < blueChance
                ? Blocks.BLUE_ICE.defaultBlockState()
                : Blocks.PACKED_ICE.defaultBlockState();
    }

    /**
     * 这一列该长多高的冰刺。返回 0 表示这一列不长。
     *
     * <h2>为什么是"纯函数"而不是一张生成表</h2>
     * 冰刺的形状完全由 {@code (x, z)} 决定（另外加一个由爆心算出的盐，让每一场爆发的布局都不同），
     * 所以冲击环扫到哪一列就现算哪一列，<b>不需要在实体里存一张"哪里该长刺"的表</b>——
     * 也就不存在"世界重载后那张表丢了、剩下的冰刺长不出来"这种问题。
     *
     * <h2>怎么长出一根根"锥体"而不是一片噪点</h2>
     * 把地图切成 {@code spike.cellSize} 见方的格子，其中 {@code spike.rarity} 分之一的格子里放一个山尖，
     * 山尖的高度沿半径线性收到 0，于是周围形成一个锥体。判断某一列时看的是<b>3×3 个格子</b>——
     * 山尖可能长在隔壁格子里、锥体伸进本格，只看自己那一格会把锥体削掉一角。
     *
     * <h2>"越靠近中心越雄伟"</h2>
     * 算出来的高度再乘一个中心系数：正中心满高，到 {@code spike.maxRelativeRadius} 处降到 0。
     *
     * <p><b>系数在这里开了平方根。</b>线性版本下，半径 128 的盘子里只有最里面那一小圈算得上高，
     * 三分之二半径处就只剩三成了（实测平均高度只有 4.6 格——满地的矮桩子）。
     * 开方之后衰减前重后轻，外侧也留得住高度。
     *
     * @param centerFactor <b>线性</b>的中心系数：0 = 太靠外、这一列不该有冰刺；1 = 正中心
     */
    private static int spikeHeight(int x, int z, double centerFactor, long salt) {
        if (centerFactor <= 0.0D) {
            return 0;
        }
        // 一次读出来用整轮：四个值分属同一套形状参数，中途被改的话至少这一列用的是同一组
        int cell = SporeAddPlayerConfig.frostSighSpikeCellSize();
        int rarity = SporeAddPlayerConfig.frostSighSpikeRarity();
        int baseRadius = SporeAddPlayerConfig.frostSighSpikeBaseRadius();
        int maxHeight = SporeAddPlayerConfig.frostSighSpikeMaxHeight();

        double falloff = Math.sqrt(Math.min(1.0D, centerFactor));
        int cellX = Math.floorDiv(x, cell);
        int cellZ = Math.floorDiv(z, cell);
        double best = 0.0D;

        for (int cx = cellX - 1; cx <= cellX + 1; cx++) {
            for (int cz = cellZ - 1; cz <= cellZ + 1; cz++) {
                long hash = spikeHash(cx, cz, salt);
                if (Math.floorMod(hash, rarity) != 0) {
                    continue;   // 这一格没有山尖
                }
                // 山尖在本格内的落点，以及它自身的高度系数（0.45~1.0，免得所有刺一样高）
                int peakX = cx * cell + Math.floorMod(hash >> 8, cell);
                int peakZ = cz * cell + Math.floorMod(hash >> 20, cell);
                double peakScale = 0.45D + 0.55D * (Math.floorMod(hash >> 32, 256) / 255.0D);

                double dx = x - peakX;
                double dz = z - peakZ;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d >= baseRadius) {
                    continue;
                }
                double height = maxHeight * peakScale * (1.0D - d / baseRadius);
                if (height > best) {
                    best = height;
                }
            }
        }
        return (int) Math.round(best * falloff);
    }

    /** 由格子坐标与爆心盐得到一个稳定的伪随机数：同一格永远是同一个值。 */
    private static long spikeHash(int cellX, int cellZ, long salt) {
        long h = salt + cellX * 341873128712L + cellZ * 132897987541L;
        h ^= h >>> 29;
        h *= 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return h;
    }

    // ------------------------------------------------------------------
    // 生物群系（需求 12）
    // ------------------------------------------------------------------

    /**
     * 把影响范围内的生物群系改成配置里指定的那个。
     *
     * <h2>为什么是 fillBiomesFromNoise 而不是 setBiome</h2>
     * 1.20.1 <b>没有任何 setBiome 方法</b>（`ChunkAccess` / `LevelChunk` / `ProtoChunk` 都没有）。
     * 官方改群系的唯一入口就是 {@code fillBiomesFromNoise}——`/fillbiome` 命令用的也是它，
     * 这里的调用顺序照抄那个命令：改完必须 {@code setUnsaved(true)}（否则不落盘）
     * 并 {@code resendBiomesForChunks}（否则客户端看不到变化）。
     *
     * <p>粒度是 <b>4 格</b>（quart）而不是逐格，这是原版群系数据的固有分辨率，改不了。
     *
     * <p>回调里对圆盘<b>外</b>的格子读旧值——那些格子永远不会被写，所以不存在"读到自己刚写的数据"。
     */
    private static void changeBiome(ServerLevel level, BlockPos center, int radius) {
        ResourceLocation id = ResourceLocation.tryParse(SporeAddPlayerConfig.frostSighColdBiome());
        if (id == null) {
            LOGGER.warn("[SporeAdd] frostSigh.coldBiome 不是合法的 id，跳过改生物群系: {}",
                    SporeAddPlayerConfig.frostSighColdBiome());
            return;
        }
        Holder<Biome> target = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(ResourceKey.create(Registries.BIOME, id))
                .orElse(null);
        if (target == null) {
            LOGGER.warn("[SporeAdd] 找不到生物群系 {}，跳过改生物群系", id);
            return;
        }

        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        double limitSqr = (double) radius * radius;
        List<ChunkAccess> touched = new ArrayList<>();

        for (long key : FrostSighChunks.chunkKeys(center, radius)) {
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            if (!level.hasChunk(chunkX, chunkZ)) {
                continue;
            }
            ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
            chunk.fillBiomesFromNoise((quartX, quartY, quartZ, s) -> {
                int blockX = QuartPos.toBlock(quartX);
                int blockZ = QuartPos.toBlock(quartZ);
                double dx = blockX - center.getX();
                double dz = blockZ - center.getZ();
                return dx * dx + dz * dz <= limitSqr ? target : chunk.getNoiseBiome(quartX, quartY, quartZ);
            }, sampler);
            chunk.setUnsaved(true);
            touched.add(chunk);
        }
        if (!touched.isEmpty()) {
            level.getChunkSource().chunkMap.resendBiomesForChunks(touched);
        }
    }

    // ------------------------------------------------------------------
    // 冻结与冻伤（需求 7、补充 1/2/3）
    // ------------------------------------------------------------------

    /**
     * 补充机制 2：给球内所有生物施加冻伤，层数与时长来自配置。
     *
     * <p>走 {@code addEffect}，所以会和「烈阳」的按件削弱自然衔接——
     * 那道削弱挂在 {@code LivingEntity#addEffect} 上（见 {@code WarmthFrostbiteScalingMixin}），
     * 这里不需要知道玩家穿了几件。
     *
     * <p>玩家被冰封那件事<b>不在这里</b>：它需要一个跨 tick 的计时器，
     * 而计时器归 {@code FrostSighShockwaveEntity} 管。见那个类的 {@code freezeEverythingInside}。
     *
     * <h2>为什么"只施加一次"要由调用方记</h2>
     * 冲击环每 tick 都会用<b>当前</b>半径重调本方法，所以同一个实体在环推完之前会被反复看到。
     * 如果每次都重新 {@code addEffect}，那么玩家的护甲一旦在爆发途中被收走（这正是"烈阳"的代价），
     * 下一 tick 就会按"抗性 0"重新施加一次——免疫凭空失效。所以这里只处理
     * {@code alreadyHit} 里还没有的实体，并把新的记进去；判断依据是"第一次被扫到的那一刻算什么"。
     *
     * @param alreadyHit 已经吃过这次爆发冻伤的实体 id。由调用方持有并跨 tick 保留
     */
    public static void applyFrostbite(ServerLevel level, BlockPos center, double radius,
                                      Set<Integer> alreadyHit) {
        MobEffect frostbite = SporeCompat.frostbite();
        if (frostbite == null) {
            return;
        }
        int level_ = SporeAddPlayerConfig.frostSighFrostbiteLevel();
        int amplifier = Math.max(0, Math.min(126, level_ - 1));
        int duration = SporeAddPlayerConfig.frostSighFrostbiteTicks();
        Vec3 centerVec = Vec3.atCenterOf(center);
        double limitSqr = radius * radius;

        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center).inflate(radius))) {
            if (living.position().distanceToSqr(centerVec) > limitSqr) {
                continue;
            }
            if (!alreadyHit.add(living.getId())) {
                continue;   // 这次爆发已经给过它了，理由见方法注释
            }
            living.addEffect(new MobEffectInstance(frostbite, duration, amplifier));
        }
    }
}
