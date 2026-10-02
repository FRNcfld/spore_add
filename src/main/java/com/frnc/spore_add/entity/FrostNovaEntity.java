package com.frnc.spore_add.entity;

import com.frnc.spore_add.SporeAddConfig;
import com.frnc.spore_add.item.ModItems;
import com.frnc.spore_add.particle.ModParticles;
import com.frnc.spore_add.world.FrostNovaBlast;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 冰霜新星的弹体：沿准星直线飞行，命中后由 {@link FrostNovaBlast} 在落点爆发。
 *
 * <h2>为什么继承 {@link ThrowableItemProjectile}</h2>
 * 它同时给出三样东西：{@code Projectile} 的命中检测与归属、{@code ItemSupplier} 的
 * {@link #getDefaultItem()}，以及最要紧的——原版渲染器
 * {@code ThrownItemRenderer<T extends Entity & ItemSupplier>} 可以直接用，
 * 不需要自己写渲染器类。弹体在画面上就是本物品的图标（与原版火焰弹被发射器射出时同理）。
 *
 * <h2>弹道：恒速直线</h2>
 * 三件事各自负责一段，合起来才是"恒速直线"：
 * <ul>
 *   <li><b>不下坠</b>靠 {@link #setNoGravity(boolean)}。父类 {@code ThrowableProjectile#tick}
 *       里那句 {@code if (!isNoGravity()) y -= getGravity()} 因此不会执行；覆写 {@code getGravity()}
 *       是没用的（它只被那一句读，成了死代码）。</li>
 *   <li><b>不偏移</b>靠发射时把 inaccuracy 传 0（见 {@link #FrostNovaEntity(Level, LivingEntity, double)}）。
 *       原版箭矢的"打不准"来自 {@code Projectile#shoot} 里那个
 *       {@code random.triangle(0, 0.017 * inaccuracy)}，传 0 就没有任何随机散布。</li>
 *   <li><b>不减速</b>靠覆写 {@link #tick()}：父类每 tick 会把速度乘 0.99（水中 0.8），
 *       详见那个方法的说明。</li>
 * </ul>
 * 三者都不是"差不多就行"：原版箭矢同时具备下坠、随机散布与减速，需求要的正是把这三样都去掉。
 *
 * <h2>引信：没命中就自动引爆</h2>
 * 原版的雪球、鸡蛋能自然结束，是因为它们有重力、最后总会落地。这个弹体没有重力，
 * 一旦打空（往天上射、射向空处）就<b>永远不会命中，也永远不会被移除</b>——父类不会自己
 * {@code discard()}。所以给它一根引信：投出后 {@code frostNova.autoDetonateSeconds} 秒
 * （默认 5 秒）仍未命中，就在当前位置引爆。
 *
 * <p>引信还顺带解决另一个问题：本 mod 的弹体继承 {@code ThrowableProjectile}，
 * 那个基类<b>没有</b>原版火焰弹（{@code AbstractHurtingProjectile}）里那句 {@code hasChunkAt} 守卫。
 * 所以弹体飞出已加载区块时不会消失，只是停在那里不再 tick（实体的 tick 依赖区块加载）——
 * 于是会在未加载的区块边缘慢慢堆积。有了引信，区块一旦重新加载、游戏时间早已越过引爆时刻，
 * 它<b>立刻</b>引爆，而不是变成一颗悬在那里的活弹。
 *
 * <p>引信记的是<b>引爆时刻的绝对游戏时间</b>而不是 tickCount，理由同
 * {@link FrostNovaIceCoreEntity} 的类注释：{@code tickCount} 不存盘，区块卸载再加载会从 0 重来。
 * 这里虽然只在服务端判超时，但存档重进后同样需要接着算，所以照样得用绝对时间。
 *
 * <p>（另一个会被引信兜住的情形：别的模组取消了 {@code ForgeEventFactory.onProjectileImpact}，
 * 命中回调被跳过 → 不会引爆，但引信到点照样收场。）
 */
public class FrostNovaEntity extends ThrowableItemProjectile {

    /** 生成点沿视线前移的距离，免得弹体一出生就贴在发射者的碰撞箱里。 */
    private static final double MUZZLE_OFFSET = 0.5D;

    /**
     * 每 tick 在弹体身后补几个拖尾粒子。
     *
     * <p>为什么不是"每 tick 一个"：速度是 1.5 格/tick，单点会在轨迹上留下 1.5 格一截的稀疏点阵。
     * 在上一 tick 到这一 tick 之间插值补点，间距才够密。
     */
    private static final int TRAIL_PARTICLES_PER_TICK = 4;

    private static final String KEY_POWER = "Power";
    private static final String KEY_DETONATE_AT = "DetonateAt";

    /** 蓄力系数 0~1（1 = 满蓄力），决定落点爆发的威力。见 {@code SporeAddConfig#scaledByPower}。 */
    private double power = 1.0D;

    /**
     * 引信：到这个游戏时刻（绝对值）仍未命中就自动引爆。
     *
     * <p>只有服务端那份实例有意义。客户端那份是走注册表构造器创建、再靠同步包补位置与速度的，
     * 这个字段一直是 0——所以 {@link #tick()} 里的超时判断必须只在服务端做，
     * 否则客户端会在第一帧就把自己删掉，弹体直接看不见。
     */
    private long detonateAtGameTime;

    /** 注册表构造器，{@code EntityType} 用。 */
    public FrostNovaEntity(EntityType<? extends FrostNovaEntity> type, Level level) {
        super(type, level);
        // 需求要的是直线，不是原版箭矢那种抛物线
        setNoGravity(true);
    }

    /**
     * 发射用的构造器。
     *
     * @param power 蓄力系数，0 = 刚够发射，1 = 满蓄力；超出范围会在取值时被夹住
     */
    public FrostNovaEntity(Level level, LivingEntity shooter, double power) {
        this(ModEntities.FROST_NOVA.get(), level);
        this.power = power;
        this.detonateAtGameTime = level.getGameTime() + SporeAddConfig.frostNovaAutoDetonateTicks();
        setOwner(shooter);

        Vec3 look = shooter.getLookAngle();
        Vec3 muzzle = shooter.getEyePosition().add(look.scale(MUZZLE_OFFSET));
        setPos(muzzle.x, muzzle.y, muzzle.z);

        // 用 shoot 而不是 shootFromRotation：后者会把发射者自身的移动速度叠加进弹道，
        // 一边跑一边射就会出现固定偏差——正是需求要排除的"偏移"。inaccuracy 传 0 则不产生随机散布。
        // 速度来自配置（{@code speedPerTick}，格/tick），不随蓄力变化。
        shoot(look.x, look.y, look.z, (float) SporeAddConfig.frostNovaSpeed(), 0.0F);
    }

    /** 弹体画面上就是这个物品的图标（原版 {@code ThrownItemRenderer} 会取它）。 */
    @Override
    protected Item getDefaultItem() {
        return ModItems.FROST_NOVA.get();
    }

    /**
     * 每 tick 把速度写回原值，从而<b>精确抵消</b>父类的减速。
     *
     * <p>父类 {@code ThrowableProjectile#tick} 里这几句的先后是关键：
     *
     * <pre>
     *   Vec3 vec3 = this.getDeltaMovement();   // 取出本 tick 的速度
     *   double d2 = this.getX() + vec3.x;      // ← 位移用的是这个「未缩放」的速度
     *   ...
     *   this.setDeltaMovement(vec3.scale(0.99F));  // ← 缩放只写回「下一 tick 用」的速度
     *   this.setPos(d2, d0, d1);
     * </pre>
     *
     * 也就是说 0.99 只影响下一 tick 的速度，不会回头修改本 tick 已经走完的位移。
     * 所以只要在 {@code super.tick()} 之后把速度还原，每 tick 就都走同样的距离——
     * 不这么做的话速度逐 tick 衰减（20 tick 后剩 82%，80 tick 后只剩 45%），
     * 方向虽然不变、轨迹仍是直线，但"越飞越慢"的手感与原版箭矢无异。
     *
     * <p>还原是无条件的，所以水中那档 0.8 的阻力也一并被抵消——弹体在水里也保持恒速。
     * 对"不要漂移"这个目标来说这是好事，且不必为水中单开一条分支。
     *
     * <p>顺带在这里判引信：{@link #discard()} 由父类的命中回调与这里的超时共同触发，
     * 超时那条还会先引爆一次（原因见类注释）。
     */
    @Override
    public void tick() {
        // 引信只在服务端判：客户端那份实例的 detonateAtGameTime 是 0，
        // 两侧都判的话客户端会在第一帧就把自己删掉、弹体看不见
        if (!level().isClientSide() && level().getGameTime() >= detonateAtGameTime) {
            FrostNovaBlast.detonate(level(), blockPosition(), power, this);
            discard();
            return;
        }
        if (level().isClientSide()) {
            emitTrail();
        }
        Vec3 velocity = getDeltaMovement();
        super.tick();
        if (isRemoved()) {
            // 父类这一 tick 里命中了，实体已经 discard，没必要再写速度
            return;
        }
        setDeltaMovement(velocity);
    }

    /** 拖尾纯客户端，走 {@code Level#addParticle} 就地生成，不经过网络。 */
    private void emitTrail() {
        Vec3 motion = getDeltaMovement();
        for (int i = 1; i <= TRAIL_PARTICLES_PER_TICK; i++) {
            double back = (double) i / (TRAIL_PARTICLES_PER_TICK + 1.0D);
            level().addParticle(ModParticles.FROST_SHARD.get(),
                    getX() - motion.x * back,
                    getY() - motion.y * back,
                    getZ() - motion.z * back,
                    0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * 命中方块或实体都算落点，两者的处理完全相同——所以覆写这一个方法就够了。
     *
     * <p>注意父类是<b>具体方法</b>，它负责把结果派发给 {@code onHitEntity} / {@code onHitBlock}；
     * 只覆写后两者中的任何一个都会漏掉另一路，而覆写 {@code onHitBlock} 不调 {@code super}
     * 还会丢掉钟、标靶、营火等方块各自的 {@code onProjectileHit}。这里先调 {@code super} 保留这些，
     * 再自己爆发。
     *
     * <p>爆发只在服务端做，{@code discard} 两侧都调——两端各自有一个该实体的副本，都得清掉。
     */
    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide()) {
            // 把自己当作爆炸的归属实体传进去：原版会顺着它的 owner 解析成投掷者，用于结算击杀。
            // 此刻实体尚未 discard，传引用是安全的。
            FrostNovaBlast.detonate(level(), BlockPos.containing(result.getLocation()), power, this);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble(KEY_POWER, power);
        tag.putLong(KEY_DETONATE_AT, detonateAtGameTime);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(KEY_POWER)) {
            power = tag.getDouble(KEY_POWER);
        }
        if (tag.contains(KEY_DETONATE_AT)) {
            detonateAtGameTime = tag.getLong(KEY_DETONATE_AT);
        }
    }
}
