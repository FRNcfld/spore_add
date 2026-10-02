package com.frnc.spore_add.entity;

import com.frnc.spore_add.particle.ModParticles;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 「冰雪的叹息」爆发后的蘑菇云。
 *
 * <h2>为什么是实体，而不是爆发时一次性撒完</h2>
 * 蘑菇云要长十几秒（茎往上蹿、伞盖张开、外缘往下卷），一次性撒完就只是一团不会动的粒子。
 * 而它又要跨区块、跨世界重载地活着，所以必须是个实体。结构照抄
 * {@link FrostNovaCloudEntity}：<b>绝对游戏时间记寿命</b>、服务端只判寿命、
 * <b>客户端撒粒子</b>——粒子完全在客户端生成，生成包之后零网络开销。
 *
 * <h2>形状</h2>
 * 按"底部涌浪 + 茎 + 伞盖"三段采样，尺寸整体随爆发半径缩放（系数见 {@link #scaleFor}）：
 * <ul>
 *   <li><b>底部涌浪</b>——贴着地面往外铺的一圈，前几秒最明显；</li>
 *   <li><b>茎</b>——从地面往上的一根圆柱，顶端随进度上升，于是看着像在"长高"；</li>
 *   <li><b>伞盖</b>——一个环面，外缘按半径平方往下卷，那就是蘑菇的"帽檐"。</li>
 * </ul>
 * 粒子数有硬上限并按尺寸缩放：半径 256 时若让粒子数跟着体积涨，一帧就是几万个。
 *
 * <h2>它不是"云"</h2>
 * 这个实体不施加任何效果、不判定任何实体、不加载任何区块——纯粹的观感。
 * 会伤人（冻伤）的是冲击环那一套，在 {@code FrostSighShockwaveEntity} 与
 * {@code FrostSighBlast} 里。
 */
public class FrostSighCloudEntity extends Entity {

    /**
     * 蘑菇云的参考高度（格）。配置里的半径 128 就对应这个尺寸，其余半径按比例缩放。
     * 下面的几个比例常数都是相对它取的。
     */
    private static final float REFERENCE_HEIGHT = 32.0F;

    /** 尺寸系数的上下限。太小看不见，太大挡视野。 */
    private static final float MIN_SCALE = 0.4F;
    private static final float MAX_SCALE = 1.6F;

    /** 茎的半径（占参考高度的比例，下同）。 */
    private static final float STEM_RADIUS = 0.10F;

    /** 茎能长到多高。 */
    private static final float STEM_HEIGHT = 0.70F;

    /** 伞盖的半径。 */
    private static final float CAP_RADIUS = 0.32F;

    /** 伞盖中心的高度。 */
    private static final float CAP_HEIGHT = 0.80F;

    /** 底部涌浪能铺多远。 */
    private static final float SURGE_RADIUS = 0.30F;

    /** 长成"完全体"要多少 tick。之后只维持，不再长大。 */
    private static final int BILLOW_TICKS = 200;

    /** 寿命最后这一段开始消散（0.75 = 最后 25%）。 */
    private static final float FADE_START = 0.75F;

    /** 每 tick 撒的粒子数上限。<b>这是主要的性能旋钮</b>——它有硬上限，不随半径的立方增长。 */
    private static final int MAX_PARTICLES_PER_TICK = 90;

    /** 开场这几 tick 额外撒一点最亮的粒子，做出"闪光"。 */
    private static final int FLARE_TICKS = 10;

    private static final int FLARE_PARTICLES = 12;

    /** 云的尺寸（= 爆发半径），客户端靠它决定撒多大，所以必须同步。 */
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(FrostSighCloudEntity.class, EntityDataSerializers.FLOAT);

    /**
     * 生成的时刻（绝对游戏时间）。
     *
     * <p>客户端靠它算"长到哪一步了"。用绝对时间而不是 {@code tickCount}：玩家中途飞过来时，
     * 客户端那份实体的 {@code tickCount} 是从它收到生成包那一刻开始数的，
     * 用它会让"晚到的玩家看到一朵刚出生的蘑菇云"。
     */
    private static final EntityDataAccessor<Integer> DATA_START =
            SynchedEntityData.defineId(FrostSighCloudEntity.class, EntityDataSerializers.INT);

    /** 寿命（tick）。客户端靠它算消散，所以也要同步。 */
    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
            SynchedEntityData.defineId(FrostSighCloudEntity.class, EntityDataSerializers.INT);

    /** 到期时刻（绝对游戏时间）。只在服务端用，只进存档。 */
    private long expiresAtGameTime;

    public FrostSighCloudEntity(EntityType<? extends FrostSighCloudEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /** 生成用构造器，由 {@code FrostSighShockwaveEntity} 调用。 */
    public FrostSighCloudEntity(Level level, double x, double y, double z, float radius, int lifetimeTicks) {
        this(ModEntities.FROST_SIGH_CLOUD.get(), level);
        setPos(x, y, z);
        setRadius(radius);
        int lifetime = Math.max(1, lifetimeTicks);
        entityData.set(DATA_START, (int) level.getGameTime());
        entityData.set(DATA_LIFETIME, lifetime);
        expiresAtGameTime = level.getGameTime() + lifetime;
    }

    public float getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    private void setRadius(float radius) {
        entityData.set(DATA_RADIUS, Mth.clamp(radius, 1.0F, 256.0F));
    }

    /** 蘑菇云的尺寸系数：半径 128 是 1.0。 */
    public static float scaleFor(float radius) {
        return Mth.clamp(radius / 128.0F, MIN_SCALE, MAX_SCALE);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_RADIUS, 128.0F);
        entityData.define(DATA_START, 0);
        entityData.define(DATA_LIFETIME, 900);
    }

    @Override
    public void tick() {
        super.tick();

        // 寿命只在服务端判。客户端那份实例走的是注册表构造器，expiresAtGameTime 一直是 0，
        // 两侧都判的话客户端会在第一帧就把自己删掉、云直接看不见（与 FrostNovaCloudEntity 同一个坑）。
        if (level().isClientSide()) {
            emitParticles();
            return;
        }
        if (level().getGameTime() >= expiresAtGameTime) {
            discard();
        }
    }

    // ------------------------------------------------------------------
    // 客户端：撒粒子
    // ------------------------------------------------------------------

    private void emitParticles() {
        int elapsed = (int) level().getGameTime() - entityData.get(DATA_START);
        if (elapsed < 0) {
            return;   // 客户端时钟还没追上生成时刻
        }
        int lifetime = Math.max(1, entityData.get(DATA_LIFETIME));
        float scale = scaleFor(getRadius());
        float height = REFERENCE_HEIGHT * scale;

        // 长成：前 BILLOW_TICKS 从 0 到 1
        float grow = Mth.clamp((float) elapsed / BILLOW_TICKS, 0.0F, 1.0F);
        // 消散：寿命最后这一段渐渐变稀，免得云"啪"地一下凭空消失
        float ratio = (float) elapsed / lifetime;
        float fade = Mth.clamp(1.0F - (ratio - FADE_START) / (1.0F - FADE_START), 0.0F, 1.0F);

        if (elapsed < FLARE_TICKS) {
            // 爆发瞬间的闪光：在伞盖高度附近撒一把最亮的粒子
            double flareRadius = CAP_RADIUS * height * 0.5D;
            for (int i = 0; i < FLARE_PARTICLES; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                double r = flareRadius * random.nextDouble();
                spawn(ModParticles.FROST_SIGH_FLARE.get(),
                        Math.cos(angle) * r, height * CAP_HEIGHT * 0.5D, Math.sin(angle) * r,
                        0.3D, 0.3D);
            }
        }

        int budget = Math.round(MAX_PARTICLES_PER_TICK * fade);
        if (budget <= 0) {
            return;
        }
        int surge = budget / 3;
        int stem = budget / 3;
        int cap = budget - surge - stem;

        // 底部涌浪：贴地往外铺的一圈
        double surgeRadius = SURGE_RADIUS * height * (0.2D + 0.8D * grow);
        for (int i = 0; i < surge; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double r = surgeRadius * Math.sqrt(random.nextDouble());
            spawn(ModParticles.FROST_SIGH_HAZE.get(),
                    Math.cos(angle) * r, random.nextDouble() * height * 0.12D, Math.sin(angle) * r,
                    0.02D, 0.02D);
        }

        // 茎：顶端随 grow 上升，所以看着像在长高
        double stemRadius = STEM_RADIUS * height;
        double stemTop = height * STEM_HEIGHT * (0.35D + 0.65D * grow);
        for (int i = 0; i < stem; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double r = stemRadius * Math.sqrt(random.nextDouble());
            spawn(ModParticles.FROST_SIGH_CLOUD.get(),
                    Math.cos(angle) * r, random.nextDouble() * stemTop, Math.sin(angle) * r,
                    0.05D, 0.05D);
        }

        // 伞盖：环面，外缘往下卷
        double capRadius = CAP_RADIUS * height;
        double capY = height * CAP_HEIGHT * (0.4D + 0.6D * grow);
        for (int i = 0; i < cap; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double u = 0.55D + 0.45D * random.nextDouble();   // 从内到外的相对半径
            double r = capRadius * u;
            // 外缘按 u² 往下卷，那就是蘑菇的"帽檐"
            double dy = -0.18D * capRadius * u * u;
            spawn(ModParticles.FROST_SIGH_HAZE.get(),
                    Math.cos(angle) * r, capY + dy, Math.sin(angle) * r,
                    0.04D, 0.04D);
        }

        // 中心最浓郁处：最暗的那个粒子，堆在伞盖正中最上面
        spawn(ModParticles.FROST_SIGH_CORE.get(), 0.0D, capY + capRadius * 0.15D, 0.0D, 0.15D, 0.15D);
    }

    /**
     * 在实体位置 + 给定偏移处撒一个粒子。
     *
     * <p>用 {@code level().addParticle} 而不是服务端的 {@code sendParticles}：
     * 这个方法只在客户端跑，粒子本来就不需要过一次网络。
     */
    private void spawn(ParticleOptions particle, double dx, double dy, double dz,
                       double driftXZ, double driftY) {
        level().addParticle(particle,
                getX() + dx, getY() + dy, getZ() + dz,
                (random.nextDouble() - 0.5D) * driftXZ, driftY, (random.nextDouble() - 0.5D) * driftXZ);
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Radius", getRadius());
        tag.putInt("Start", entityData.get(DATA_START));
        tag.putInt("Lifetime", entityData.get(DATA_LIFETIME));
        tag.putLong("ExpiresAt", expiresAtGameTime);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setRadius(tag.getFloat("Radius"));
        entityData.set(DATA_START, tag.getInt("Start"));
        entityData.set(DATA_LIFETIME, Math.max(1, tag.getInt("Lifetime")));
        expiresAtGameTime = tag.getLong("ExpiresAt");
    }
}
