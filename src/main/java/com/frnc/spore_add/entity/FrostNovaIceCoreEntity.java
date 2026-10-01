package com.frnc.spore_add.entity;

import com.frnc.spore_add.particle.ModParticles;
import com.frnc.spore_add.sound.ModSounds;
import com.frnc.spore_add.world.FrostNovaBlast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 冰球变成的<b>延时炸弹</b>：冰霜新星首次爆炸后留在原地，倒计时结束后引发二次爆炸。
 *
 * <p>本身不可见（见 {@code InvisibleEntityRenderer} 的说明），视觉与提示全靠它喷的粒子。
 *
 * <h2>倒计时用绝对游戏时间，不能用 tickCount</h2>
 * 一开始写的是"每 tick 把 tickCount 和总延时比一比"，那是错的：
 * <b>实体的 tickCount 不存盘</b>（原版里它就是个普通字段，载入时从 0 重新计），
 * 所以区块一旦卸载再加载、或存档重进，倒计时就会从头开始——玩家反复走开再回来，
 * 这颗炸弹可能永远不炸。
 *
 * <p>改成记录<b>引爆时刻的绝对游戏时间</b>：生成时算出 {@code getGameTime() + delay} 存进
 * 同步数据与存档，之后每次比较当前游戏时间。区块在倒计时期间没加载也不影响——
 * 加载回来时游戏时间早已越过引爆时刻，于是立刻引爆（这正是应有的行为）。
 *
 * <p>截断成 int 是安全的：游戏时间每 2^31 tick（约 3.4 年）才回绕一次，
 * 而延时最多 600 秒，两端用同样的方式截断，相减的结果在回绕前后都正确。
 *
 * <h2>预警：必须看得见、听得见</h2>
 * 一发无预警的延时爆炸会被玩家当成游戏出 bug——冰球静静躺在那里，20 秒后忽然把人冻住。
 * 所以这里从生成起就有提示，越接近引爆越强：
 * <ul>
 *   <li><b>视觉</b>：冰球表面持续往外涌霜雾与冰屑，数量在最后 {@link #WARNING_WINDOW_TICKS} 内
 *       从 {@link #MIN_MIST_PER_TICK} 涨到 {@link #MAX_MIST_PER_TICK}（冰屑同理）。
 *       让它"看得见"的是两点，都是踩过坑才定下来的：粒子取在<b>球面</b>上而不是球内
 *       （见 {@link #emitOnSurface}），且主力用大团云雾而不是小米粒大的冰屑。</li>
 *   <li><b>听觉</b>：提示音平时每 {@link #SLOW_CHIME_INTERVAL_TICKS} tick 一声，进入预警窗口后
 *       加密到 {@link #FAST_CHIME_INTERVAL_TICKS}，音量 {@link #CHIME_VOLUME}，
 *       音调从 0.7 一路升到 2.0 做出倒数感。</li>
 * </ul>
 *
 * <h2>二次爆炸只放大范围，不产生伤害</h2>
 * 具体做什么见 {@link FrostNovaBlast#detonateSecondary}。这里只负责计时与预警，
 * 到点把活交给那边——爆发逻辑全部集中在一个类里，找起来只有一处。
 */
public class FrostNovaIceCoreEntity extends Entity {

    /** 进入"加速预警"的窗口：最后 5 秒。 */
    private static final int WARNING_WINDOW_TICKS = 100;

    /**
     * 云雾粒子数（每 tick）：平时 / 引爆前。
     *
     * <p>用的是云雾而不是冰屑当主力。冰屑边长只有 0.4 格、只活 10~20 tick 还带重力，
     * 远远看去就是几个看不见的小点；云雾边长 2.2 格、活 50~90 tick，一团团往外涌才看得出"要炸了"。
     */
    private static final int MIN_MIST_PER_TICK = 2;
    private static final int MAX_MIST_PER_TICK = 14;

    /** 冰屑粒子数（每 tick）：平时 / 引爆前。冰屑是点缀，喷得比雾快。 */
    private static final int MIN_SHARD_PER_TICK = 1;
    private static final int MAX_SHARD_PER_TICK = 16;

    /** 平时的提示音间隔（1.5 秒一声）。 */
    private static final int SLOW_CHIME_INTERVAL_TICKS = 30;

    /** 预警窗口内的提示音间隔（0.3 秒一声）。 */
    private static final int FAST_CHIME_INTERVAL_TICKS = 6;

    /** 提示音音量。原来 0.7 混在环境音里基本听不出来。 */
    private static final float CHIME_VOLUME = 1.2F;

    /** 云雾向外喷的初速。 */
    private static final double MIST_OUTWARD_SPEED = 0.06D;

    /** 冰屑向外喷的初速。比雾快，像被内部压力挤出来。 */
    private static final double SHARD_OUTWARD_SPEED = 0.15D;

    /** 冰球的半径。清理冰时用同一个半径，客户端也靠它决定粒子撒在哪。 */
    private static final EntityDataAccessor<Float> DATA_ICE_RADIUS =
            SynchedEntityData.defineId(FrostNovaIceCoreEntity.class, EntityDataSerializers.FLOAT);

    /**
     * 引爆时刻（绝对游戏时间，截断成 int）。
     *
     * <p>同步给客户端是因为预警强度要按"还剩多少 tick"算，而客户端也得知道这个时刻。
     */
    private static final EntityDataAccessor<Integer> DATA_DETONATE_AT =
            SynchedEntityData.defineId(FrostNovaIceCoreEntity.class, EntityDataSerializers.INT);

    /** 二次爆炸的冰雾/冻伤半径。只在服务端用。 */
    private float secondaryRadius = 1.0F;

    /** 二次爆炸给的冻伤持续时长。只在服务端用。 */
    private int secondaryDurationTicks = 200;

    /** 二次爆炸给的冻伤 amplifier。只在服务端用。 */
    private int secondaryAmplifier;

    /** 注册表构造器，{@code EntityType} 用。 */
    public FrostNovaIceCoreEntity(EntityType<? extends FrostNovaIceCoreEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /**
     * 生成用的构造器，由 {@link FrostNovaBlast} 调用。
     *
     * @param iceRadius              冰球半径，二次爆炸时按它清冰
     * @param delayTicks             倒计时长度
     * @param secondaryRadius        二次爆炸的冰雾与冻伤半径（已经乘过范围倍率）
     * @param secondaryDurationTicks 二次爆炸给的冻伤时长（已经乘过威力倍率）
     * @param secondaryAmplifier     二次爆炸给的冻伤 amplifier（已经乘过威力倍率并夹过上限）
     */
    public FrostNovaIceCoreEntity(Level level, Vec3 center, float iceRadius, int delayTicks,
                                  float secondaryRadius, int secondaryDurationTicks, int secondaryAmplifier) {
        this(ModEntities.FROST_NOVA_ICE_CORE.get(), level);
        setPos(center.x, center.y, center.z);
        entityData.set(DATA_ICE_RADIUS, iceRadius);
        // 记的是"什么时候炸"，不是"还剩多久"，原因见类注释
        entityData.set(DATA_DETONATE_AT, (int) (level.getGameTime() + delayTicks));
        this.secondaryRadius = secondaryRadius;
        this.secondaryDurationTicks = secondaryDurationTicks;
        this.secondaryAmplifier = secondaryAmplifier;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_ICE_RADIUS, 4.0F);
        entityData.define(DATA_DETONATE_AT, 0);
    }

    public float getIceRadius() {
        return entityData.get(DATA_ICE_RADIUS);
    }

    /** 距引爆还剩多少 tick。已过时刻时返回 0 或负数。 */
    private int remainingTicks() {
        return entityData.get(DATA_DETONATE_AT) - (int) level().getGameTime();
    }

    @Override
    public void tick() {
        super.tick();
        int remaining = remainingTicks();

        if (level().isClientSide()) {
            emitWarningParticles(remaining);
            return;
        }
        if (remaining <= 0) {
            detonate();
            return;
        }
        playWarningChime(remaining);
    }

    private void detonate() {
        if (level() instanceof ServerLevel server) {
            FrostNovaBlast.detonateSecondary(server, BlockPos.containing(position()),
                    getIceRadius(), secondaryRadius, secondaryDurationTicks, secondaryAmplifier);
        }
        discard();
    }

    // ------------------------------------------------------------------
    // 预警（客户端与服务端各一半）
    // ------------------------------------------------------------------

    /** 倒计时进度：0 = 还早，1 = 即将引爆。 */
    private static float closeness(int remaining) {
        return 1.0F - Mth.clamp((float) remaining / WARNING_WINDOW_TICKS, 0.0F, 1.0F);
    }

    /** 冰球往外涌的霜雾与冰屑，数量随倒计时线性增加。 */
    private void emitWarningParticles(int remaining) {
        float progress = closeness(remaining);
        float radius = getIceRadius();

        int mists = MIN_MIST_PER_TICK + Math.round((MAX_MIST_PER_TICK - MIN_MIST_PER_TICK) * progress);
        for (int i = 0; i < mists; i++) {
            emitOnSurface(ModParticles.FROST_MIST.get(), radius, MIST_OUTWARD_SPEED);
        }

        int shards = MIN_SHARD_PER_TICK + Math.round((MAX_SHARD_PER_TICK - MIN_SHARD_PER_TICK) * progress);
        for (int i = 0; i < shards; i++) {
            emitOnSurface(ModParticles.FROST_SHARD.get(), radius, SHARD_OUTWARD_SPEED);
        }
    }

    /**
     * 在球<b>面</b>上取一点，沿法线往外喷一个粒子。
     *
     * <p><b>必须取在球面上，不能取在球内。</b>冰球是实心方块，粒子生成在球内会被那层冰整个遮住——
     * 之前用的就是"球体积内均匀取点"（{@code cbrt(random)}），所以预警几乎看不见：
     * 只有恰好落在表面的那几个能露出来。取在球面、再给一个向外的初速，粒子一出生成就越过冰面，才看得见。
     *
     * <p>半径取 0.95~1.15 倍而不是正好 1 倍：正好 1 倍时粒子有一半概率嵌在方块里，
     * 稍微往外偏一点才能保证它一开始就在冰面之外。
     *
     * <p>速度沿法线方向，再叠一点向上的偏置——朝下那一半球取出来的方向是朝下的，
     * 纯法线速度会让粒子往地里钻。
     */
    private void emitOnSurface(ParticleOptions particle, float radius, double speed) {
        double theta = random.nextDouble() * Math.PI * 2.0D;
        double cosPhi = random.nextDouble() * 2.0D - 1.0D;
        double sinPhi = Math.sqrt(1.0D - cosPhi * cosPhi);
        double distance = radius * (0.95D + random.nextDouble() * 0.20D);

        double nx = sinPhi * Math.cos(theta);
        double ny = cosPhi;
        double nz = sinPhi * Math.sin(theta);

        level().addParticle(particle,
                getX() + nx * distance,
                getY() + ny * distance,
                getZ() + nz * distance,
                nx * speed, ny * speed + 0.03D, nz * speed);
    }

    /** 提示音：进入预警窗口后加密、音调随倒计时升高。 */
    private void playWarningChime(int remaining) {
        boolean warning = remaining <= WARNING_WINDOW_TICKS;
        int interval = warning ? FAST_CHIME_INTERVAL_TICKS : SLOW_CHIME_INTERVAL_TICKS;
        // 节拍也走游戏时间，不用 tickCount：后者载入时从 0 重来，会让重进世界后的
        // 第一声提示偏掉。引爆时刻本身已经不受影响，这里只是让节拍也一致。
        if (level().getGameTime() % interval != 0) {
            return;
        }
        // 音调跨度拉到 0.7~2.0，倒数感更强
        float pitch = 0.7F + 1.3F * closeness(remaining);
        level().playSound(null, getX(), getY(), getZ(),
                ModSounds.FROST_NOVA_IMMINENT.get(), SoundSource.BLOCKS, CHIME_VOLUME, pitch);
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("IceRadius", getIceRadius());
        tag.putInt("DetonateAt", entityData.get(DATA_DETONATE_AT));
        tag.putFloat("SecondaryRadius", secondaryRadius);
        tag.putInt("SecondaryDuration", secondaryDurationTicks);
        tag.putInt("SecondaryAmplifier", secondaryAmplifier);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_ICE_RADIUS, tag.getFloat("IceRadius"));
        entityData.set(DATA_DETONATE_AT, tag.getInt("DetonateAt"));
        secondaryRadius = tag.getFloat("SecondaryRadius");
        secondaryDurationTicks = tag.getInt("SecondaryDuration");
        secondaryAmplifier = tag.getInt("SecondaryAmplifier");
    }
}
