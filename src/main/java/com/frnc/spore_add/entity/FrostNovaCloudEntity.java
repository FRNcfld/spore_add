package com.frnc.spore_add.entity;

import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.particle.ModParticles;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 冰霜新星落点处那团持续 10 秒的粒子云：一个球形的、碰上去会叠冻伤的霜雾。
 *
 * <h2>为什么不用原版的 {@code AreaEffectCloud}</h2>
 * 那个类看着正合适（有半径、有持续时间、接触就给 buff），但两点对不上：
 * <ul>
 *   <li>它的粒子是<b>扁平的一圈</b>——每 tick 在固定的 Y 上按角度撒点（见它 {@code tick} 里的
 *       {@code cos/sin} 取点），而需求里的"影响范围"是个球；它判定实体时用的也是
 *       {@code dx²+dz² ≤ r²}（只算水平距离），于是只有跟云<b>同一层</b>（AABB 高 0.5 格）的生物会中招。</li>
 *   <li>它每 tick 撒 {@code ceil(π·r²)} 个粒子，半径 10 就是 314 个/tick、10 秒约 6 万个。
 *       那个密度是给半径 3 的龙息设计的，直接拿来会明显掉帧。</li>
 * </ul>
 * 所以这里自己写：粒子按<b>球体积</b>均匀撒、密度自己定（见 {@link #MIST_PER_TICK}），
 * 冻伤按<b>球面距离</b>判定。
 *
 * <h2>两个半径不在这里</h2>
 * 云半径 = 冻伤半径（{@code frostNova.entityRadius} 按蓄力折算），由
 * {@code FrostNovaBlast} 在生成时算好传进来。所以"看到云的范围"与"会被冻的范围"是同一个。
 *
 * <h2>本体是个点，不是一个大方块</h2>
 * 实体的碰撞箱固定 0.5 格（见 {@code ModEntities}），<b>不</b>随云半径变大。
 * 让 AABB 跟着半径走到 20 格的话，{@code Entity#baseTick} 里那些按包围盒遍历方块的逻辑
 * （流体推动等）每 tick 要扫两万格，纯属浪费。判定实体时自己拼一个精确的 AABB 就够了。
 */
public class FrostNovaCloudEntity extends Entity {

    /** 给球内生物刷新冻伤的间隔（tick）。比每次每 tick 都刷省事，也避免频繁 post {@code MobEffectEvent.Applicable}。 */
    private static final int FROSTBITE_INTERVAL_TICKS = 5;

    /** 每 tick 撒的云雾粒子数。这是主要的性能旋钮——调大更浓，但粒子总数按存活时长线性增长。 */
    private static final int MIST_PER_TICK = 18;

    /** 每 tick 撒的雪花的粒子数。只是点缀，不必多。 */
    private static final int SNOWFLAKE_PER_TICK = 4;

    /** 这团雾的球半径。客户端靠它决定粒子撒在哪，所以必须同步。 */
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(FrostNovaCloudEntity.class, EntityDataSerializers.FLOAT);

    /**
     * 到期时刻（绝对游戏时间）。
     *
     * <p>只在服务端有意义——客户端不需要知道这团雾还能活多久，服务端到期 {@code discard} 之后
     * 客户端跟着收移除包就行。所以它不进同步数据，只进存档。
     *
     * <p><b>为什么用绝对游戏时间而不是 tickCount：</b>{@code tickCount} 不存盘，区块卸载再加载会
     * 从 0 重来，配置里的时长就不作数了（重进世界、或反复走出加载范围，云会活得比配置的更久）。
     * 这与 {@link FrostNovaIceCoreEntity}、{@link FrostNovaEntity} 是同一类坑。
     * 这里的时长是可配置的、最长能配到 600 秒，偏差因此更容易撞上。
     */
    private long expiresAtGameTime;

    /** 冻伤的持续 tick 数。同样只在服务端用，只进存档。 */
    private int frostbiteDurationTicks = 200;

    /** 冻伤的 amplifier（= 显示层数 - 1）。同上。 */
    private int frostbiteAmplifier;

    /** 注册表构造器，{@code EntityType} 用。 */
    public FrostNovaCloudEntity(EntityType<? extends FrostNovaCloudEntity> type, Level level) {
        super(type, level);
        // 不参与物理：不被方块推、不撞方块、也不该被爆炸推动
        this.noPhysics = true;
        setNoGravity(true);
    }

    /**
     * 生成用的构造器，由 {@code FrostNovaBlast} 调用。
     *
     * @param radius                 云的球半径（格），同时也是施加冻伤的判定半径
     * @param lifetimeTicks          存活时长
     * @param frostbiteDurationTicks 接触者获得的冻伤持续时长
     * @param frostbiteAmplifier     接触者获得的冻伤 amplifier
     */
    public FrostNovaCloudEntity(Level level, Vec3 center, float radius, int lifetimeTicks,
                                int frostbiteDurationTicks, int frostbiteAmplifier) {
        this(ModEntities.FROST_NOVA_CLOUD.get(), level);
        setPos(center.x, center.y, center.z);
        setRadius(radius);
        // 记"什么时候散"，不是"还能活多久"，原因见 expiresAtGameTime
        this.expiresAtGameTime = level.getGameTime() + lifetimeTicks;
        this.frostbiteDurationTicks = frostbiteDurationTicks;
        this.frostbiteAmplifier = frostbiteAmplifier;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_RADIUS, 1.0F);
    }

    public float getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    /** 上下限与 {@code AreaEffectCloud} 取同一组，免得配置改出荒唐的值。 */
    public void setRadius(float radius) {
        entityData.set(DATA_RADIUS, Mth.clamp(radius, 0.5F, 32.0F));
    }

    @Override
    public void tick() {
        super.tick();

        // 寿命只在服务端判。客户端那份实例走的是注册表构造器，expiresAtGameTime 一直是 0，
        // 两侧都判的话客户端会在第一帧就把自己删掉、云直接看不见。
        // 客户端也不需要知道寿命：服务端到期 discard，客户端跟着收移除包。
        if (level().isClientSide()) {
            emitParticles();
            return;
        }
        if (level().getGameTime() >= expiresAtGameTime) {
            discard();
            return;
        }
        if (tickCount % FROSTBITE_INTERVAL_TICKS == 0) {
            applyFrostbite();
        }
    }

    // ------------------------------------------------------------------
    // 客户端：撒粒子
    // ------------------------------------------------------------------

    private void emitParticles() {
        float radius = getRadius();
        for (int i = 0; i < MIST_PER_TICK; i++) {
            spawnInSphere(ModParticles.FROST_MIST.get(), radius, 0.0D);
        }
        for (int i = 0; i < SNOWFLAKE_PER_TICK; i++) {
            spawnInSphere(ModParticles.FROST_SNOWFLAKE.get(), radius, -0.01D);
        }
    }

    /**
     * 在球体积内均匀取一点撒粒子。
     *
     * <p>半径用 {@code cbrt(random)} 而不是 {@code random}：球体积随半径三次方增长，
     * 直接对半径取均匀分布会让粒子全挤在球心附近。取立方根才是体积上的均匀。
     *
     * @param verticalDrift 竖直初速度：0 悬浮、负数上飘
     */
    private void spawnInSphere(ParticleOptions particle, float radius, double verticalDrift) {
        double theta = random.nextDouble() * Math.PI * 2.0D;
        double cosPhi = random.nextDouble() * 2.0D - 1.0D;
        double sinPhi = Math.sqrt(1.0D - cosPhi * cosPhi);
        double distance = radius * Math.cbrt(random.nextDouble());

        double dx = distance * sinPhi * Math.cos(theta);
        double dy = distance * cosPhi;
        double dz = distance * sinPhi * Math.sin(theta);

        level().addParticle(particle,
                getX() + dx, getY() + dy, getZ() + dz,
                (random.nextDouble() - 0.5D) * 0.02D, verticalDrift, (random.nextDouble() - 0.5D) * 0.02D);
    }

    // ------------------------------------------------------------------
    // 服务端：接触者叠冻伤
    // ------------------------------------------------------------------

    private void applyFrostbite() {
        MobEffect frostbite = SporeCompat.frostbite();
        if (frostbite == null) {
            return;
        }

        float radius = getRadius();
        Vec3 center = position();
        double limit = (double) radius * radius;

        // 按精确的球心拼 AABB，而不是用实体的碰撞箱（那个只有 0.5 格，与云半径无关）
        AABB area = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);

        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (living.position().distanceToSqr(center) > limit) {
                continue;   // 落在外接立方体的角上，球外
            }
            // 与落点那一次性施加用的是同一个持续时长，所以穿过云再走开，冻伤还会留一会儿。
            // 云在 10 秒内每 5 tick 刷一次，等于"待在云里就一直保持"。
            // 「烈阳」附魔的免疫依旧由 ModEvents#onEffectApplicable 拦下，这里不用管。
            living.addEffect(new MobEffectInstance(frostbite, frostbiteDurationTicks, frostbiteAmplifier));
        }
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Radius", getRadius());
        tag.putLong("ExpiresAt", expiresAtGameTime);
        tag.putInt("FrostbiteDuration", frostbiteDurationTicks);
        tag.putInt("FrostbiteAmplifier", frostbiteAmplifier);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setRadius(tag.getFloat("Radius"));
        expiresAtGameTime = tag.getLong("ExpiresAt");
        frostbiteDurationTicks = tag.getInt("FrostbiteDuration");
        frostbiteAmplifier = tag.getInt("FrostbiteAmplifier");
    }
}
