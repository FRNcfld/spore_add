package com.frnc.spore_add.block;

import com.frnc.spore_add.SporeAddPlayerConfig;
import com.frnc.spore_add.entity.FrostSighShockwaveEntity;
import com.frnc.spore_add.particle.ModParticles;
import com.frnc.spore_add.sound.ModSounds;
import com.frnc.spore_add.world.FrostSighChunks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 「冰雪的叹息」的状态：是否已激活，以及<b>什么时候爆发</b>。
 *
 * <p>本工程第一个 BlockEntity。它只负责倒计时，爆发本身交给
 * {@link FrostSighShockwaveEntity}——因为这个方块在爆发的那一刻就会被换成液态寒冷，
 * BlockEntity 随之消失，没法继续承担冲击环那种要跨几百 tick 的活。
 *
 * <h2>倒计时用绝对游戏时间</h2>
 * 记的是"什么时候炸"，不是"还剩多久"。理由是本工程已经踩过三次的坑：
 * <b>实体的 {@code tickCount} 与任何自减计数器都不存盘</b>，区块卸载再加载会从头开始。
 * 用绝对游戏时间之后，哪怕倒计时期间区块被卸载，回来时也会立刻按正确的时刻爆发
 * （见 {@code FrostNovaIceCoreEntity} 的类注释，那里记录了这个坑的完整经过）。
 *
 * <p>另一点：BlockEntity 只有在自己的区块<b>正在 tick</b> 时才会被 tick。
 * 所以激活时会用 {@link FrostSighChunks} 把圆盘覆盖的区块强制加载住，
 * 否则玩家一走开倒计时就停了。
 */
public class FrostSighBlockEntity extends BlockEntity {

    private static final String KEY_ACTIVATED = "Activated";
    private static final String KEY_DETONATE_AT = "DetonateAt";

    /** 是否已被冰霜新星激活。 */
    private boolean activated;

    /** 引爆时刻（绝对游戏时间）。未激活时无意义。 */
    private long detonateAtGameTime;

    /**
     * 是否已经引爆过。
     *
     * <p>只用来区分"方块被移除"的两种原因：玩家挖掉（要放票）与正常引爆（不能放票，见 {@link #onRemoved}）。
     * 引爆与移除发生在同一 tick，所以不需要进存档——重进世界时方块早就是空气了。
     */
    private boolean detonated;

    public FrostSighBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FROST_SIGH.get(), pos, state);
    }

    public boolean isActivated() {
        return activated;
    }

    /**
     * 用冰霜新星激活。
     *
     * @return 是否<b>刚刚</b>被激活（已经是激活态时返回 false，调用方据此决定要不要消耗物品）
     */
    public boolean activate(ServerLevel level, int radius) {
        if (activated) {
            return false;
        }
        activated = true;
        detonateAtGameTime = level.getGameTime() + SporeAddPlayerConfig.frostSighCountdownTicks();
        setChanged();
        // 同时写进方块状态：客户端靠它才知道"这个方块已经激活、挖不动了"（BE 的字段不过网）
        level.setBlock(getBlockPos(), getBlockState().setValue(FrostSighBlock.ACTIVATED, true), Block.UPDATE_ALL);

        // 把圆盘覆盖的区块钉住，否则玩家走开、区块卸载，倒计时就停了
        if (SporeAddPlayerConfig.frostSighForceLoadChunks()) {
            FrostSighChunks.force(level, getBlockPos(), radius);
        }
        return true;
    }

    /** 由方块注册的 ticker 每 tick 调用。 */
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!activated) {
            return;
        }
        long remaining = detonateAtGameTime - level.getGameTime();
        if (remaining <= 0) {
            // 引爆：把活交给冲击环实体。本方块自己马上会被移除，BlockEntity 随之消失
            // ——所以后续几百 tick 的事只能由那些实体接着做。
            //
            // ⚠️ **区块票绝不能在这里释放**：冲击环还要在半径 128 的范围里推十几秒，
            // 票一放，那片区域立刻卸载，`sweepRing` 会因为 hasChunk 守卫而整片跳过，
            // 效果就只剩中心一小圈。票由 2 号环在推完之后释放。
            //
            // 这里必须抢在 removeBlock **之前**把 detonated 立起来：removeBlock 会走
            // `onRemove` → `onRemoved` 那条路（原版是先调 onRemove、再移除 BlockEntity，
            // 所以那一刻 getBlockEntity 还拿得到本实例），没有这个标志它就会把票放掉。
            detonated = true;
            level.playSound(null, pos, ModSounds.FROST_SIGH_DETONATED.get(), SoundSource.BLOCKS, 4.0F, 0.7F);
            // 顺序：先把方块清掉，再交给冲击环。反过来的话 begin() 里的 detonate() 会把中心
            // 铺成液态寒冷，紧接着的 removeBlock 就可能把那一格又处理一遍——虽然最终状态一样，
            // 但那种"靠 no-op 兜住"的顺序不值得依赖。
            level.removeBlock(pos, false);
            FrostSighShockwaveEntity.begin(level, pos);
            return;
        }
        playCountdownSound(level, pos, remaining);
        emitCloud(level, pos, remaining);
        emitBoundaryWarning(level, pos);
    }

    /**
     * 倒计时那团粒子云的半径上限（格）。
     *
     * <p>云从方块往外扩散，但整个云始终关在这个球里——不随进度铺开到十几格。
     * 4 格大约是"一块方块的周围两圈"，玩家站在旁边看得出是一团罩着方块的雾。
     */
    private static final double CLOUD_RADIUS = 4.0D;

    /**
     * 每次爆发撒几个「雾」（分布在球内）。这是倒计时的主体。
     *
     * <p>用的是与新星同一张图（重上色成深蓝）的 {@code FROST_SIGH_MIST}。
     *
     * <h2>数量是跟着球体积走的，但跟不上</h2>
     * 半径从 2 提到 4，球体积是<b>八倍</b>；真要保持同样的浓度就得把数量也乘八，
     * 而最高频那一档（16 拍/秒）会变成每秒上千个粒子、稳态存活量六万——不可接受。
     * 所以这里取 6 → 12（两倍）：云明显更大更满，但比原来略稀一点。这是刻意的取舍。
     */
    private static final int MIST_PARTICLES_PER_BURST = 12;

    /**
     * 每次爆发撒几个雪花。
     *
     * <p>与新星那对粒子保持同一个组成（雾为主、雪花点缀，约 6:1）。
     */
    private static final int FLAKE_PARTICLES_PER_BURST = 2;

    /** 进度超过这个值开始补最亮的闪光（"快炸了"的提示）。 */
    private static final float FLARE_FROM_PROGRESS = 0.75F;

    /** 每 tick 在边界圆周上撒几个警示粒子。 */
    private static final int WARNING_PARTICLES_PER_TICK = 3;

    /**
     * 在影响范围的**边缘**撒一圈警示粒子。
     *
     * <p>与中心那团核弹粒子是两回事：中心那团是"它在充能"，这一圈是"这条线以内都会被冻住"。
     * 半径 128 意味着这个圆周长约 800 格，站在里面的人看不到边——所以它必须一直在，
     * 而且是<b>淡淡的</b>：密度低、不透明度压到 0.45。
     *
     * <p>逐玩家、并且<b>按该段圆弧到玩家的距离</b>筛选后才发：一圈 800 格，
     * 全发给所有玩家既浪费又没必要——每个人只该看到自己附近那一段。
     */
    private void emitBoundaryWarning(ServerLevel level, BlockPos pos) {
        int radius = SporeAddPlayerConfig.frostSighRadius();
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 1.0D;
        double cz = pos.getZ() + 0.5D;

        for (int i = 0; i < WARNING_PARTICLES_PER_TICK; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(x, cy, z) > 256.0D * 256.0D) {
                    continue;
                }
                level.sendParticles(player, ModParticles.FROST_SIGH_WARNING.get(), true,
                        x, cy, z, 1, 0.4D, 2.0D, 0.4D, 0.0D);
            }
        }
    }

    // ------------------------------------------------------------------
    // 倒计时的声音与粒子（需求 4）
    // ------------------------------------------------------------------

    /** 进入「即将爆发」提示的窗口：最后 15 秒。 */
    private static final int IMMINENT_WINDOW_TICKS = 15 * 20;

    /** 进入「最急促」的窗口：最后 5 秒。 */
    private static final int FRANTIC_WINDOW_TICKS = 5 * 20;

    /** 心跳的间隔（tick）：平时每 5 秒 / 即将爆发每秒 / 最急促每 0.2 秒。 */
    private static final int SLOW_SOUND_INTERVAL = 100;
    private static final int IMMINENT_SOUND_INTERVAL = 20;
    private static final int FRANTIC_SOUND_INTERVAL = 4;

    /** 粒子云的频率（次/秒）：平时每 2 秒一次 / 即将爆发 4 次 / 最急促 16 次（需求 4）。 */
    private static final double SLOW_EMISSION_RATE = 0.5D;
    private static final double IMMINENT_EMISSION_RATE = 4.0D;
    private static final double FRANTIC_EMISSION_RATE = 16.0D;

    /**
     * 粒子发射的累积器。
     *
     * <p>需求给的"16 次/秒"不是 20（每秒 tick 数）的整数因子，用 {@code tick % interval} 表达不出来
     * （只能近似成每 tick 一次 = 20/秒，或每 2 tick 一次 = 10/秒）。所以按"每 tick 累加 16/20"来算，
     * 攒够 1 就发一次，这样长期频率精确是 16/秒。
     */
    private double emissionAccumulator;

    /** 倒计时的心跳：越接近爆发越密、音调越高。 */
    private void playCountdownSound(ServerLevel level, BlockPos pos, long remaining) {
        int interval = remaining <= FRANTIC_WINDOW_TICKS ? FRANTIC_SOUND_INTERVAL
                : remaining <= IMMINENT_WINDOW_TICKS ? IMMINENT_SOUND_INTERVAL
                : SLOW_SOUND_INTERVAL;
        if (level.getGameTime() % interval != 0) {
            return;
        }
        float progress = countdownProgress(remaining);
        level.playSound(null, pos,
                (remaining <= IMMINENT_WINDOW_TICKS
                        ? ModSounds.FROST_SIGH_IMMINENT
                        : ModSounds.FROST_SIGH_COUNTDOWN).get(),
                SoundSource.BLOCKS, 1.6F, 0.8F + 0.5F * progress);
    }

    /** 倒计时进度：0 = 刚激活，1 = 即将爆发。 */
    private static float countdownProgress(long remaining) {
        int total = Math.max(1, SporeAddPlayerConfig.frostSighCountdownTicks());
        return 1.0F - Math.min(1.0F, (float) remaining / total);
    }

    /** 深靛蓝的粒子云：越接近爆发越密、铺得越开。 */
    private void emitCloud(ServerLevel level, BlockPos pos, long remaining) {
        double rate = remaining <= FRANTIC_WINDOW_TICKS ? FRANTIC_EMISSION_RATE
                : remaining <= IMMINENT_WINDOW_TICKS ? IMMINENT_EMISSION_RATE
                : SLOW_EMISSION_RATE;
        emissionAccumulator += rate / 20.0D;
        while (emissionAccumulator >= 1.0D) {
            emissionAccumulator -= 1.0D;
            emitBurst(level, pos, countdownProgress(remaining));
        }
    }

    /**
     * 发一次粒子。
     *
     * <h2>云被关在半径 2 的球里</h2>
     * 原先这里的散布是 {@code 2 + 12 × 进度}——爆发前会铺到半径 14 格，那是"一大团雾"，
     * 与"从方块往外扩散的一小团"不是一回事。现在：
     * <ul>
     *   <li>半径上限固定为 {@link #CLOUD_RADIUS}；</li>
     *   <li>"扩散"由<b>取点距离随进度增长</b>体现——刚激活时粒子几乎贴着方块，越接近爆发铺得越开，
     *       到爆发那一刻正好铺满整个球；</li>
     *   <li>每个粒子<b>单独定位</b>，所以是真正落在球里，而不是原先那种"撒进外接立方体、
     *       角落飞到 1.7 倍半径"的近似。</li>
     * </ul>
     * 代价是每次爆发的数据包数从 1 个变成一个粒子一个。频率最高的一档是 16 次/秒、
     * 每次几个粒子，几十个小包而已，可以接受。
     *
     * <p>逐玩家用<b>长距离</b>重载：普通重载只覆盖 32 格，而这是半径 128 的核弹，
     * 站在远处看的人正是最该看到它的人。
     */
    private void emitBurst(ServerLevel level, BlockPos pos, float progress) {
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 1.0D;
        double cz = pos.getZ() + 0.5D;
        // 这一拍云铺到多远：0（贴着方块）→ CLOUD_RADIUS（铺满整个球）
        double reach = CLOUD_RADIUS * progress;

        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) > 256.0D * 256.0D) {
                continue;
            }
            // 主体：雾。与冰霜新星那团雾是同一张图（重上色成深蓝），组成也一样。
            for (int i = 0; i < MIST_PARTICLES_PER_BURST; i++) {
                spawnInSphere(level, player, cx, cy, cz, reach, ModParticles.FROST_SIGH_MIST.get());
            }
            // 点缀：同一对里的雪花（深蓝版）
            for (int i = 0; i < FLAKE_PARTICLES_PER_BURST; i++) {
                spawnInSphere(level, player, cx, cy, cz, reach, ModParticles.FROST_SIGH_FLAKE.get());
            }

            if (progress > FLARE_FROM_PROGRESS) {
                double[] dir = randomDirection(level);
                level.sendParticles(player, ModParticles.FROST_SIGH_FLARE.get(), true,
                        cx + dir[0] * reach, cy + dir[1] * reach, cz + dir[2] * reach,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** 在球体积内均匀取一点撒一个粒子（{@code cbrt} 才是体积上的均匀，见上面那段注释）。 */
    private static void spawnInSphere(ServerLevel level, ServerPlayer player, double cx, double cy,
                                      double cz, double reach, ParticleOptions particle) {
        double distance = reach * Math.cbrt(level.random.nextDouble());
        double[] dir = randomDirection(level);
        level.sendParticles(player, particle, true,
                cx + dir[0] * distance, cy + dir[1] * distance, cz + dir[2] * distance,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /** 球面上均匀取一个方向。 */
    private static double[] randomDirection(ServerLevel level) {
        double theta = level.random.nextDouble() * Math.PI * 2.0D;
        double cosPhi = level.random.nextDouble() * 2.0D - 1.0D;
        double sinPhi = Math.sqrt(1.0D - cosPhi * cosPhi);
        return new double[] {Math.cos(theta) * sinPhi, cosPhi, Math.sin(theta) * sinPhi};
    }

    /**
     * 方块被破坏时收尾。
     *
     * <p>必须把强制加载的区块放掉——否则玩家把方块挖了、票还留着，
     * 那片区域会一直被加载着，直到重启服务器。
     *
     * <p><b>但引爆那条路要排除掉。</b>引爆时方块也会被移除（换成空气），走的是同一个
     * {@code onRemove}。要是这里一并放票，冲击环刚开始推的时候票就没了——
     * 而它还得推十几秒，中途卸载的区块会被跳过。所以看 {@link #detonated}：
     * 已经引爆的话，票交给 2 号环放。
     */
    public void onRemoved(ServerLevel level) {
        if (activated && !detonated) {
            FrostSighChunks.release(level, getBlockPos(), SporeAddPlayerConfig.frostSighRadius());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean(KEY_ACTIVATED, activated);
        tag.putLong(KEY_DETONATE_AT, detonateAtGameTime);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        activated = tag.getBoolean(KEY_ACTIVATED);
        detonateAtGameTime = tag.getLong(KEY_DETONATE_AT);
    }
}
