package com.frnc.spore_add.entity;

import com.frnc.spore_add.SporeAddConfig;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.particle.ModParticles;
import com.frnc.spore_add.world.FrostSighChunks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「冰雪的叹息」打完之后留在爆心的东西：**降雪**与**冰雾**。
 *
 * <h2>为什么这两件事是同一个实体</h2>
 * 它们同源、同时生、同地生，寿命还是同一个量级（默认都是 10 分钟）。更要紧的是它们都需要
 * <b>把爆心那一个区块一直钉住</b>——实体只有在自己所在区块正在 tick 时才会被 tick，而玩家
 * 不可能在爆心守十分钟。而区块票是按「模组 + 所有者坐标 + 区块」去重的：两个实体各钉一次等于
 * 只钉了一张票，谁先到期谁把它放掉，另一个就跟着停摆。合成一个实体，票就只有一张，
 * 寿命也只有一个——干净。两种效果各自的时长仍然分着配（{@code snowMinutes} / {@code mistMinutes}）。
 *
 * <h2>冰雾的范围跟着 2 号环长</h2>
 * 需求是"2 号环扫过的区域出现冰雾"，所以雾的半径不是一上来就满，而是<b>跟着 2 号环的环半径长</b>：
 * 环推到哪，雾就铺到哪。环推完之后（{@code secondRingDelaySeconds + shockwaveSeconds}）
 * 雾停在满半径，一直到 {@code mistMinutes} 到期。
 *
 * <h2>雾里的生物持续吃冻伤</h2>
 * 每 {@link #FROSTBITE_INTERVAL_TICKS} tick 对雾范围内的生物施加一次冻伤，等级与时长与 2 号环
 * 施加的<b>完全一致</b>（同一个配置项）。所以后走进雾里的生物也会中招，而不是只在爆炸那一刻挨一下。
 *
 * <h2>粒子只在客户端撒</h2>
 * 生成包之后零网络开销。雾与雪都只发在玩家附近与圈内随机点——整个圆盘有 5 万列，
 * 均匀撒根本看不见，也没必要。
 */
public class FrostSighAftermathEntity extends Entity {

    /** 每几 tick 撒一次粒子。逐 tick 撒没必要，雪与雾本来就该是疏的。 */
    private static final int EMIT_INTERVAL_TICKS = 4;

    /** 玩家在圈内时，每次在他附近撒几粒雪 / 几粒雾。 */
    private static final int NEAR_PLAYER_SNOW = 6;
    private static final int NEAR_PLAYER_MIST = 5;

    /** 圈内随机点每次撒几粒。给站在圈外的人看的——不然他们完全不知道里面在下雪。 */
    private static final int DISC_SNOW = 3;
    private static final int DISC_MIST = 4;

    /** 玩家附近撒雪的水平半径（格）。 */
    private static final double NEAR_PLAYER_SPREAD = 12.0D;

    /** 在玩家头顶多高处生成雪，让它有"落下来"的过程。 */
    private static final double SNOW_ABOVE_PLAYER = 14.0D;

    /** 圈内随机点撒雪的高度（相对实体）。 */
    private static final double DISC_SNOW_HEIGHT = 28.0D;

    /** 雪的下落速度（格/tick）。 */
    private static final double SNOW_FALL_SPEED = -0.04D;

    /** 雾撒在多大一层里（相对实体位置的上下范围，格）。雾是贴地弥漫的，不该飘到天上去。 */
    private static final double MIST_VERTICAL_SPREAD = 8.0D;

    /** 每隔多少 tick 给雾里的生物补一次冻伤。 */
    private static final int FROSTBITE_INTERVAL_TICKS = 20;

    /** 爆炸的最终半径，客户端靠它决定撒多远的东西，所以必须同步。 */
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(FrostSighAftermathEntity.class, EntityDataSerializers.FLOAT);

    /** 生成的时刻（绝对游戏时间）。客户端靠它算"现在该铺多宽、还有没有到点"。 */
    private static final EntityDataAccessor<Integer> DATA_START =
            SynchedEntityData.defineId(FrostSighAftermathEntity.class, EntityDataSerializers.INT);

    /** 降雪的持续时长（tick）。 */
    private static final EntityDataAccessor<Integer> DATA_SNOW_TICKS =
            SynchedEntityData.defineId(FrostSighAftermathEntity.class, EntityDataSerializers.INT);

    /** 冰雾的持续时长（tick）。 */
    private static final EntityDataAccessor<Integer> DATA_MIST_TICKS =
            SynchedEntityData.defineId(FrostSighAftermathEntity.class, EntityDataSerializers.INT);

    /** 生成时刻与两个到期时刻。只在服务端用，只进存档。 */
    private long spawnGameTime;
    private long snowExpiresAt;
    private long mistExpiresAt;

    public FrostSighAftermathEntity(EntityType<? extends FrostSighAftermathEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** 生成用构造器，由 {@code FrostSighShockwaveEntity} 在引爆时调用。只应在服务端调用。 */
    public static void begin(ServerLevel level, BlockPos center, int radius) {
        FrostSighAftermathEntity aftermath =
                new FrostSighAftermathEntity(ModEntities.FROST_SIGH_AFTERMATH.get(), level);
        aftermath.setPos(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D);

        long now = level.getGameTime();
        int snowTicks = Math.max(1, SporeAddConfig.frostSighSnowTicks());
        int mistTicks = Math.max(1, SporeAddConfig.frostSighMistTicks());

        aftermath.spawnGameTime = now;
        aftermath.snowExpiresAt = now + snowTicks;
        aftermath.mistExpiresAt = now + mistTicks;
        aftermath.entityData.set(DATA_RADIUS, (float) radius);
        aftermath.entityData.set(DATA_START, (int) now);
        aftermath.entityData.set(DATA_SNOW_TICKS, snowTicks);
        aftermath.entityData.set(DATA_MIST_TICKS, mistTicks);

        level.addFreshEntity(aftermath);

        // 钉住爆心那一个区块，否则玩家一走开雪与雾就停了。票由本实体负责收
        // （降雪与冰雾已经合成一个实体了，所以不存在两张票互相踩的问题）。
        FrostSighChunks.forceSingle(level, center);
    }

    public float getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_RADIUS, 128.0F);
        entityData.define(DATA_START, 0);
        entityData.define(DATA_SNOW_TICKS, 12000);
        entityData.define(DATA_MIST_TICKS, 12000);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            long now = server.getGameTime();
            // 两边都到期才退场；只到期一边就只停那一边
            if (now >= snowExpiresAt && now >= mistExpiresAt) {
                stop(server);
                return;
            }
            if (now < mistExpiresAt && tickCount % FROSTBITE_INTERVAL_TICKS == 0) {
                applyMistFrostbite(server);
            }
            return;
        }
        emitParticles();
    }

    /** 放掉区块票然后退场。 */
    private void stop(ServerLevel level) {
        FrostSighChunks.releaseSingle(level, blockPosition());
        discard();
    }

    /**
     * 兜底释放。
     *
     * <p>正常路径是 {@link #stop}。但实体被移除的原因不止那一种（{@code /kill}、维度卸载、
     * 世界关闭……），而票会被 Forge 持久化到存档里——漏放一次，爆心那个区块就一直加载到重启。
     * 所以这里再放一次：{@code forceChunk(..., false)} 对不存在的票是安全的。
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && reason != RemovalReason.DISCARDED) {
            FrostSighChunks.releaseSingle((ServerLevel) level(), blockPosition());
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------
    // 服务端：雾里的生物持续吃冻伤
    // ------------------------------------------------------------------

    /**
     * 冰雾当前铺到多远。
     *
     * <p>跟着 2 号环长：2 号环起跑于 {@code secondRingDelaySeconds} 之后、用 {@code shockwaveSeconds}
     * 推到满半径，所以这里用同一个节奏算。
     */
    private double currentMistRadius(ServerLevel server) {
        long since = server.getGameTime() - spawnGameTime
                - SporeAddConfig.frostSighSecondRingDelayTicks();
        int ticks = SporeAddConfig.frostSighShockwaveTicks();
        double progress = Mth.clamp((double) since / ticks, 0.0D, 1.0D);
        return getRadius() * progress;
    }

    /**
     * 给雾里的生物补一次冻伤。
     *
     * <p>等级与时长直接取 2 号环用的那两个配置项，所以"雾里的冻伤"与"环扫过时那一下"是同一档。
     * 走 {@code addEffect}，所以「烈阳」的按件削弱照常生效。
     */
    private void applyMistFrostbite(ServerLevel server) {
        MobEffect frostbite = SporeCompat.frostbite();
        if (frostbite == null) {
            return;
        }
        double mistRadius = currentMistRadius(server);
        if (mistRadius <= 0.0D) {
            return;   // 2 号环还没起跑
        }
        int amplifier = Math.max(0, Math.min(126, SporeAddConfig.frostSighFrostbiteLevel() - 1));
        int duration = SporeAddConfig.frostSighFrostbiteTicks();

        Vec3 center = position();
        double limitSqr = mistRadius * mistRadius;
        for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(mistRadius))) {
            if (living.position().distanceToSqr(center) > limitSqr) {
                continue;
            }
            living.addEffect(new MobEffectInstance(frostbite, duration, amplifier));
        }
    }

    // ------------------------------------------------------------------
    // 客户端：撒雪与撒雾
    // ------------------------------------------------------------------

    private void emitParticles() {
        if (tickCount % EMIT_INTERVAL_TICKS != 0) {
            return;
        }
        int elapsed = (int) level().getGameTime() - entityData.get(DATA_START);
        if (elapsed < 0) {
            return;   // 客户端时钟还没追上生成时刻
        }
        boolean snowing = elapsed < entityData.get(DATA_SNOW_TICKS);
        boolean misting = elapsed < entityData.get(DATA_MIST_TICKS);
        if (!snowing && !misting) {
            return;
        }

        double mistRadius = misting ? currentMistRadiusClient(elapsed) : 0.0D;
        // 玩家放哪儿：用 Level#getNearestPlayer 而不是 Minecraft.getInstance().player，
        // 这个类就不用碰任何客户端专属的类（服务端加载它时不会因缺类而崩）。
        Player nearest = level().getNearestPlayer(getX(), getY(), getZ(), getRadius(), false);

        if (nearest != null) {
            if (snowing) {
                for (int i = 0; i < NEAR_PLAYER_SNOW; i++) {
                    addSnow(nearest.getX() + spread(), nearest.getY() + SNOW_ABOVE_PLAYER * random.nextDouble(),
                            nearest.getZ() + spread());
                }
            }
            if (misting) {
                for (int i = 0; i < NEAR_PLAYER_MIST; i++) {
                    addMist(nearest.getX() + spread(), nearest.getY() + mistOffset(), nearest.getZ() + spread());
                }
            }
        }

        if (snowing) {
            for (int i = 0; i < DISC_SNOW; i++) {
                double[] point = randomDiscPoint(getRadius());
                addSnow(getX() + point[0], getY() + DISC_SNOW_HEIGHT * (0.6D + 0.4D * random.nextDouble()),
                        getZ() + point[1]);
            }
        }
        if (misting) {
            // 雾只在"2 号环已经扫过"的那部分里撒——与它的效果范围保持一致
            for (int i = 0; i < DISC_MIST; i++) {
                double[] point = randomDiscPoint(mistRadius);
                addMist(getX() + point[0], getY() + mistOffset(), getZ() + point[1]);
            }
        }
    }

    /** 客户端的雾半径：与服务端 {@link #currentMistRadius} 同一个算式。 */
    private double currentMistRadiusClient(int elapsed) {
        long since = elapsed - SporeAddConfig.frostSighSecondRingDelayTicks();
        int ticks = SporeAddConfig.frostSighShockwaveTicks();
        return getRadius() * Mth.clamp((double) since / ticks, 0.0D, 1.0D);
    }

    /** 圆盘内均匀取一点（返回 {dx, dz}）。用 sqrt 才是面积上的均匀。 */
    private double[] randomDiscPoint(double radius) {
        double angle = random.nextDouble() * Mth.TWO_PI;
        double r = radius * Math.sqrt(random.nextDouble());
        return new double[] {Math.cos(angle) * r, Math.sin(angle) * r};
    }

    private double spread() {
        return (random.nextDouble() - 0.5D) * 2.0D * NEAR_PLAYER_SPREAD;
    }

    /** 雾是贴地弥漫的：上下都在地面附近，不该像雪那样从天上落下来。 */
    private double mistOffset() {
        return (random.nextDouble() - 0.5D) * 2.0D * MIST_VERTICAL_SPREAD;
    }

    private void addSnow(double x, double y, double z) {
        level().addParticle(ModParticles.FROST_SIGH_SNOW.get(), x, y, z,
                (random.nextDouble() - 0.5D) * 0.01D, SNOW_FALL_SPEED, (random.nextDouble() - 0.5D) * 0.01D);
    }

    /**
     * 冰雾撒一个粒子。
     *
     * <p>用的就是<b>冰霜新星那团霜雾的同一对粒子</b>：{@code FROST_MIST} 作主体、{@code FROST_SNOWFLAKE}
     * 点缀，初速也照抄 {@code FrostNovaCloudEntity}（雾静止悬浮、雪花缓慢下飘）。
     * 所以爆后这片领域看着就是"新星那团雾放大到半径 128、铺开十分钟"。
     */
    private void addMist(double x, double y, double z) {
        // 大约 5 个里掺 1 个雪花——新星那边是 18:4，比例接近
        boolean snowflake = random.nextInt(5) == 0;
        level().addParticle(
                (snowflake ? ModParticles.FROST_SNOWFLAKE : ModParticles.FROST_MIST).get(),
                x, y, z,
                (random.nextDouble() - 0.5D) * 0.02D,
                snowflake ? -0.01D : 0.0D,
                (random.nextDouble() - 0.5D) * 0.02D);
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Radius", getRadius());
        tag.putLong("SpawnAt", spawnGameTime);
        tag.putLong("SnowExpiresAt", snowExpiresAt);
        tag.putLong("MistExpiresAt", mistExpiresAt);
        tag.putInt("SnowTicks", entityData.get(DATA_SNOW_TICKS));
        tag.putInt("MistTicks", entityData.get(DATA_MIST_TICKS));
        tag.putInt("Start", entityData.get(DATA_START));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_RADIUS, tag.getFloat("Radius"));
        spawnGameTime = tag.getLong("SpawnAt");
        snowExpiresAt = tag.getLong("SnowExpiresAt");
        mistExpiresAt = tag.getLong("MistExpiresAt");
        entityData.set(DATA_SNOW_TICKS, Math.max(1, tag.getInt("SnowTicks")));
        entityData.set(DATA_MIST_TICKS, Math.max(1, tag.getInt("MistTicks")));
        entityData.set(DATA_START, tag.getInt("Start"));
    }
}
