package com.frnc.spore_add.world;

import javax.annotation.Nullable;

import com.frnc.spore_add.SporeAddConfig;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.entity.FrostNovaCloudEntity;
import com.frnc.spore_add.entity.FrostNovaIceCoreEntity;
import com.frnc.spore_add.particle.ModParticles;
import com.frnc.spore_add.sound.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 冰霜新星的两个爆炸阶段。所有爆发逻辑都集中在这一个类里，找起来只有一处。
 *
 * <h2>一次爆炸 —— {@link #detonate}</h2>
 * 按发生顺序做六件事：
 * <ol>
 *   <li><b>爆炸伤害</b>——原版爆炸，强度由 {@code frostNova.explosionPower} 决定（默认 8.0 = TNT 的两倍），
 *       不破坏方块；</li>
 *   <li><b>换方块</b>——球内换成蓝冰（内半）/ 浮冰（外半）；</li>
 *   <li><b>冻伤</b>——球内的生物立刻获得冻伤；</li>
 *   <li><b>粒子云</b>——留下一个持续 10 秒的霜雾球，碰到的生物也会叠冻伤；</li>
 *   <li><b>登记延时炸弹</b>——把那批冰交给 {@link FrostNovaIceCoreEntity} 看着，
 *       倒计时结束后由 {@link #detonateSecondary} 二次引爆；</li>
 *   <li><b>音效与粒子</b>——碎裂声与冰屑爆发。</li>
 * </ol>
 *
 * <h2>二次爆炸 —— {@link #detonateSecondary}</h2>
 * 倒计时结束后引爆，<b>只放大范围、不产生任何伤害</b>，并把那批冰清成空气。
 * 三个数值各有配置项：延时秒数、范围倍率、冻伤强度倍率。
 *
 * <h2>三个范围各管各的</h2>
 * 换方块的半径（{@code frostNova.blockRadius}，默认 4）与施加冻伤的半径
 * （{@code frostNova.entityRadius}，默认 10）是两个独立的配置项，都是<b>球形</b>判定。
 * 爆炸的波及范围不出自独立的配置项——它由 {@code frostNova.explosionPower} 推出来
 * （原版里伤害半径就是 power × 2）。二次爆炸的半径则是一次爆炸的冻伤半径乘范围倍率。
 * 粒子云用的始终是冻伤半径，所以"看到霜雾的范围"与"会被冻的范围"一致。
 *
 * <h2>威力按蓄力系数缩放</h2>
 * 两个半径、冻伤秒数、层数、爆炸强度<b>五项</b>都跟着蓄力变，走的是同一条曲线
 * （{@link SporeAddConfig#powerFactor}），所以不会出现"半径涨得比层数快"这种不一致。
 * 二次爆炸的三个倍率是独立的，不再乘蓄力系数——它们乘的是"一次爆炸的结果"。
 *
 * <h2>方块替换是彻底的，且不掉落</h2>
 * 需求是"球型范围内的<b>所有</b>方块被替换成蓝冰、浮冰"，所以这里只豁免不可破坏的方块
 * （基岩、屏障、命令方块等），其余一律替换——包括石头、泥土、箱子、矿石，也包括本 mod
 * 自己的三种流体。配合 {@link #REPLACE_FLAGS} 里刻意保留的 {@code UPDATE_SUPPRESS_DROPS}，
 * 被换掉的方块<b>不会掉落任何东西</b>。
 *
 * <p>不过这个破坏是<b>可逆的</b>：二次爆炸会把那批冰清掉（见
 * {@link #detonateSecondary}），所以地表不会永久留着一个冰球。代价是清成空气而非还原，
 * 原地因此会留下一个球形空洞。
 */
public final class FrostNovaBlast {

    /**
     * 判定半径比标称半径多出的余量（格）。这是把"每个面正中间凸出一格"抹平的关键。
     *
     * <h2>为什么需要这个余量</h2>
     * 朴素判据是"方块中心到球心的距离 ≤ r"。用格子堆出来的球，这个集合沿 6 个<b>轴向</b>恰好能伸到 r
     * （{@code (±r,0,0)} 这些点），但斜方向的"肩部"伸不到——以 r=4 为例只有 3.58。
     * 于是形状是"一个小一号的球，外加 6 个孤立凸点"，看起来就是每面正中鼓出一格。
     * 用支撑函数量它（沿大量方向取方块到球心的最大投影，连续球体该值恒为 r）：
     * 朴素判据在 <b>3.58 ~ 4.00</b> 之间波动，各向异性 12%。
     *
     * <h2>为什么是 0.25</h2>
     * 实测出来的最优点：加上它之后支撑变成 <b>4.00 ~ 4.24</b>，各向异性降到 6%，
     * 六个极点从"1 格孤点"变成"9 格的 3×3 面"，球面因此平滑。
     * <b>轴向仍然只伸到 r 格</b>——半径 r+1 的方块距离至少 r+1，远大于 r+0.25，永远不会被纳入，
     * 所以配置里那个半径的含义（球能伸多远）没变，只是把表面填圆了。
     *
     * <p>这个余量在冻冰（{@link #freezeBlocks}）与清冰（{@link #meltIce}）两边<b>必须一致</b>，
     * 否则清不干净、会留下最外层的一圈冰。
     */
    private static final double RADIUS_SLACK = 0.25D;

    /** 一次爆炸命中瞬间撒的冰屑数量。 */
    private static final int BURST_PARTICLES = 40;

    /** 二次爆炸的冰屑数量。范围大了一倍多，粒子也得跟上，不然看着很空。 */
    private static final int SECONDARY_BURST_PARTICLES = 90;

    /**
     * 冻伤显示层数的上限。它最终会写成 amplifier，而 amplifier 的网络同步与存档都是字节，
     * 超过 127 会静默损坏（详见 {@code FrostbiteLevels} 的类注释）。二次爆炸乘了倍率之后要再过一道。
     */
    private static final int FROSTBITE_LEVEL_CAP = 127;

    /**
     * 替换方块用的更新标志：同步客户端（2）、标记形状变化以重算剔除（16）、抑制掉落（32）。
     * 刻意不含 {@code UPDATE_NEIGHBORS}——避免连锁反应，与 {@code LiquidColdBlock} 取同一组。
     *
     * <p>代价要说清楚：不加邻居更新意味着悬空的火把/沙子不会掉落、红石不会更新，
     * 且 16 那个标志会让客户端一次性重建一大片区块网格——半径大时可能有一帧顿卡。
     */
    private static final int REPLACE_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private FrostNovaBlast() {
    }

    /**
     * 一次爆炸。只应在服务端调用——这里用 {@code ServerLevel} 的类型判断兜住，
     * 比 {@code isClientSide()} 更严（顺带拿到发送粒子包所需的 {@code ServerLevel}）。
     *
     * @param power  蓄力系数 0~1，见 {@link SporeAddConfig#scaledByPower}
     * @param source 爆炸的归属实体，通常是那枚弹体（它的 owner 会被原版解析成投掷者，用于结算击杀）
     */
    public static void detonate(Level level, BlockPos center, double power, @Nullable Entity source) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Vec3 centerVec = Vec3.atCenterOf(center);
        int effectRadius = SporeAddConfig.scaledByPower(SporeAddConfig.frostNovaEntityRadius(), power);
        int durationTicks = primaryFrostbiteDuration(power);
        int amplifier = frostbiteAmplifier(power);

        explode(server, centerVec, source, power);
        freezeBlocks(server, center, power);
        // 清真菌与冻伤共用同一个半径：玩家看到霜雾的地方就是会被清的地方。
        // 放在 freezeBlocks 之后无所谓——冻结只碰换方块半径内的方块，而冰不是真菌方块。
        FungalClearing.clear(server, center, effectRadius);
        frostbiteInRadius(server, centerVec, effectRadius, durationTicks, amplifier);
        spawnCloud(server, centerVec, effectRadius, SporeAddConfig.frostNovaPrimaryCloudTicks(),
                durationTicks, amplifier);
        spawnIceCore(server, centerVec, power);
        burstParticles(server, centerVec,
                SporeAddConfig.scaledByPower(SporeAddConfig.frostNovaBlockRadius(), power) * 0.6D,
                BURST_PARTICLES);
        playImpactSound(server, centerVec);
    }

    // ------------------------------------------------------------------
    // 1. 爆炸
    // ------------------------------------------------------------------

    /**
     * 走原版爆炸，但<b>不破坏方块</b>。
     *
     * <p>{@code ExplosionInteraction.NONE} 会被映射成 {@code BlockInteraction.KEEP}，
     * 于是方块、掉落物、着火都不发生；而伤害、击退、受击玩家记录、以及
     * {@code finalizeExplosion} 里那声 {@code GENERIC_EXPLODE} 与爆炸粒子都照常。
     *
     * <p>强度随蓄力缩放，而原版把伤害与范围绑在同一个值上（伤害作用半径 = 该值 × 2 格，
     * 中心满暴露伤害 ≈ 7 × 该值 × 2），所以<b>拉得越满，炸得越狠、也炸得越远</b>。
     * 配置成 0 时不炸——原版对 radius 0 是安全的（伤害判定 `dist/0 = ∞` 恒不满足，
     * 射线也不推进），只是拿不到爆炸音效与爆炸粒子。
     */
    private static void explode(ServerLevel level, Vec3 center, @Nullable Entity source, double power) {
        level.explode(source, center.x, center.y, center.z,
                SporeAddConfig.frostNovaExplosionPower(power), Level.ExplosionInteraction.NONE);
    }

    // ------------------------------------------------------------------
    // 2. 换方块 / 清冰
    // ------------------------------------------------------------------

    private static void freezeBlocks(Level level, BlockPos center, double power) {
        int radius = SporeAddConfig.scaledByPower(SporeAddConfig.frostNovaBlockRadius(), power);

        // 判定半径＝标称半径 + 余量，见 RADIUS_SLACK。循环上界仍是 radius：
        // radius+1 处的方块距离至少 radius+1 > radius+0.25，本来就不可能入选
        double limit = radius + RADIUS_SLACK;
        double limitSqr = limit * limit;
        // 蓝冰那一层的分界用同一个判定半径的一半，"内半"才是这颗球真正的一半
        double inner = limit / 2.0D;
        double innerSqr = inner * inner;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double distSqr = dx * dx + dy * dy + dz * dz;
                    if (distSqr > limitSqr) {
                        continue;   // 落在外接立方体的角上，球外
                    }
                    BlockPos target = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(target) || !isReplaceable(level, target)) {
                        continue;
                    }
                    level.setBlock(target, iceFor(distSqr <= innerSqr), REPLACE_FLAGS);
                }
            }
        }
    }

    /**
     * 清掉冰球留下的冰（变成空气）。
     *
     * <p>循环与判定跟 {@link #freezeBlocks} <b>逐字对应</b>——同一个中心、同一个半径、
     * 同一份 {@link #RADIUS_SLACK}。这是硬要求：两边判定只要差一点点，
     * 清冰就会漏掉最外层的一圈，留下一个冰壳。
     *
     * <p>只清<b>蓝冰与浮冰</b>：这两种是冻出来的。玩家自己放的同类方块会被误伤，
     * 但换个角度看，能接受——否则就得把几百个方块状态存进实体存档，代价不成比例。
     *
     * <p>更新标志用 {@code UPDATE_ALL}（同步客户端 + 通知邻居），与冻结那边刻意不同：
     * 冻结不需要连锁反应，而<b>移除</b>方块要让邻居知道（上方悬空的方块该掉、光照该重算）。
     */
    private static void meltIce(Level level, BlockPos center, int radius) {
        double limit = radius + RADIUS_SLACK;
        double limitSqr = limit * limit;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > limitSqr) {
                        continue;
                    }
                    BlockPos target = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(target)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(target);
                    if (!state.is(Blocks.BLUE_ICE) && !state.is(Blocks.PACKED_ICE)) {
                        continue;
                    }
                    level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /**
     * 内半半径<b>蓝冰</b>、外半半径<b>浮冰</b>。
     *
     * <p>两种冰都不融化，所以这里不涉及 {@code IceMeltMixin}（那个是给液态寒冷扩散时
     * 最外档的原版冰兜底的）；也刻意<b>不用</b>原版冰——它方块光照 ≥ 11 会化成水。
     *
     * @param inner 是否落在判定半径的内半（分界由调用方按同一个判定半径算出）
     */
    private static BlockState iceFor(boolean inner) {
        return inner ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
    }

    /** 只有不可破坏的方块被豁免；其余（含本 mod 自己的流体）一律替换，理由见类注释。 */
    private static boolean isReplaceable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().defaultDestroyTime() >= 0;
    }

    // ------------------------------------------------------------------
    // 3. 冻伤（一次与二次共用）
    // ------------------------------------------------------------------

    /**
     * 给球内的生物施加冻伤。一次爆炸与二次爆炸都走这里，所以两边的判定必然一致。
     *
     * <p>vanilla 在 amplifier 相同时取时长更长的一方，所以这里既不会被附近的液态寒冷
     * 把层数压低，也不会把目标身上已有的更高层数降下来。
     *
     * <p>「烈阳」附魔的免疫不用在这里判：{@code addEffect} 会走 {@code canBeAffected}，
     * 而 {@code ModEvents#onEffectApplicable} 已经拦在 {@code MobEffectEvent.Applicable} 上了。
     *
     * <p>投掷者自己也在查询范围内（需求是"球型范围内的所有实体"），所以贴脸引爆会冻到自己
     * ——穿烈阳护甲可以免疫，这是刻意留的互动。
     */
    private static void frostbiteInRadius(Level level, Vec3 center, double radius,
                                          int durationTicks, int amplifier) {
        MobEffect frostbite = SporeCompat.frostbite();
        if (frostbite == null) {
            return;
        }

        double limit = radius * radius;
        // 先整块查出来再按球面距离过滤。半径 10 的外接立方体是 21³ = 9261 格，
        // 逐格调 getEntitiesOfClass 会慢得离谱
        AABB area = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);

        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (living.position().distanceToSqr(center) > limit) {
                continue;   // 落在外接立方体的角上，球外
            }
            // 一次性给到目标等级，不走 FrostbiteLevels.add——那个是"每秒最多 +1"的累加模型，
            // 没有"直接设到某一级"的入口，硬凑要两百 tick 才会到位。
            living.addEffect(new MobEffectInstance(frostbite, durationTicks, amplifier));
        }
    }

    private static int primaryFrostbiteDuration(double power) {
        return SporeAddConfig.scaledByPower(SporeAddConfig.frostNovaFrostbiteSeconds(), power) * 20;
    }

    /** 配置填的是显示层数，这里换成 amplifier（层数 - 1），并夹到非负。 */
    private static int frostbiteAmplifier(double power) {
        return Math.max(0, SporeAddConfig.scaledByPower(SporeAddConfig.frostNovaFrostbiteLevel(), power) - 1);
    }

    // ------------------------------------------------------------------
    // 4. 粒子云（一次与二次共用）
    // ------------------------------------------------------------------

    /**
     * 铺一团霜雾。
     *
     * @param lifetimeTicks 这团雾存在多久，与"雾里冻伤持续多久"是两回事。
     *                      两个爆炸阶段各自的时长都来自配置：
     *                      一次用 {@code primaryCloudSeconds}，二次用 {@code secondaryCloudSeconds}
     */
    private static void spawnCloud(ServerLevel level, Vec3 center, float radius, int lifetimeTicks,
                                   int durationTicks, int amplifier) {
        level.addFreshEntity(new FrostNovaCloudEntity(level, center, radius, lifetimeTicks,
                durationTicks, amplifier));
    }

    // ------------------------------------------------------------------
    // 5. 延时炸弹
    // ------------------------------------------------------------------

    /**
     * 把冰球登记成一颗延时炸弹，倒计时结束后由 {@link #detonateSecondary} 引爆。
     *
     * <p>三个二次爆炸的参数都是"一次爆炸的结果再乘倍率"，不再乘蓄力系数——
     * 蓄力已经体现在一次爆炸的结果里了，再乘一次就是平方，满蓄力会离谱。
     */
    private static void spawnIceCore(ServerLevel level, Vec3 center, double power) {
        int iceRadius = SporeAddConfig.scaledByPower(SporeAddConfig.frostNovaBlockRadius(), power);

        float secondaryRadius = (float) Math.max(1.0D,
                SporeAddConfig.scaledByPower(SporeAddConfig.frostNovaEntityRadius(), power)
                        * SporeAddConfig.frostNovaSecondaryRangeMultiplier());
        int secondaryDuration = (int) Math.round(
                primaryFrostbiteDuration(power) * SporeAddConfig.frostNovaSecondaryPowerMultiplier());
        int secondaryLevel = (int) Math.min(FROSTBITE_LEVEL_CAP, Math.round(
                SporeAddConfig.scaledByPower(SporeAddConfig.frostNovaFrostbiteLevel(), power)
                        * SporeAddConfig.frostNovaSecondaryPowerMultiplier()));

        level.addFreshEntity(new FrostNovaIceCoreEntity(level, center, iceRadius,
                SporeAddConfig.frostNovaSecondaryDelayTicks(),
                secondaryRadius, secondaryDuration, Math.max(0, secondaryLevel - 1)));
    }

    /**
     * 二次爆炸：冰球倒计时结束后引爆。
     *
     * <h2>只有范围，没有伤害</h2>
     * 这里刻意<b>不调用 {@code level.explode}</b>——原版爆炸一定会造成伤害
     * （power 传 0 虽然伤害判定不成立，但仍然会做一遍射线与击退流程），
     * 而需求明确要求二次爆炸"仅影响范围，不包括伤害"。所以视觉与音效自己出：冰屑爆发 + 碎裂声。
     *
     * <h2>顺序</h2>
     * 先清冰再铺雾：这样"冰消失 → 霜雾炸开"的因果读起来是对的，
     * 而且冻伤判定不会把已经被清掉的位置当成遮挡。
     *
     * @param iceRadius      冰球半径（一次爆炸时的换方块半径），按它清冰
     * @param radius         二次爆炸的冰雾与冻伤半径（已经乘过范围倍率）
     * @param durationTicks  二次爆炸给的冻伤时长（已经乘过威力倍率）
     * @param amplifier      二次爆炸给的冻伤 amplifier（已经乘过威力倍率并夹过上限）
     */
    public static void detonateSecondary(ServerLevel level, BlockPos center, float iceRadius,
                                         float radius, int durationTicks, int amplifier) {
        Vec3 centerVec = Vec3.atCenterOf(center);

        meltIce(level, center, Math.round(iceRadius));
        // 二次爆炸的影响范围是一次爆炸的 1.5 倍，清真菌也用这个放大后的半径
        FungalClearing.clear(level, center, radius);
        frostbiteInRadius(level, centerVec, radius, durationTicks, amplifier);
        spawnCloud(level, centerVec, radius, SporeAddConfig.frostNovaSecondaryCloudTicks(),
                durationTicks, amplifier);
        burstParticles(level, centerVec, radius * 0.6D, SECONDARY_BURST_PARTICLES);
        // 用本 mod 自己的声音事件而不是原版的 GLASS_BREAK：音频文件还是那几个玻璃碎裂声，
        // 但字幕换成了自己的名字——否则隐藏式字幕会把它念成"方块破坏声"。
        level.playSound(null, centerVec.x, centerVec.y, centerVec.z,
                ModSounds.FROST_NOVA_SECONDARY.get(), SoundSource.BLOCKS, 1.4F, 0.6F);
    }

    // ------------------------------------------------------------------
    // 6. 音效与爆发粒子
    // ------------------------------------------------------------------

    /**
     * 命中瞬间的冰屑爆发。
     *
     * <p>用 {@code ServerLevel#sendParticles} 而不是 {@code Level#addParticle}：后者是纯客户端方法，
     * 在服务端调用什么都不做（粒子必须由服务端下发给范围内的玩家）。
     */
    private static void burstParticles(ServerLevel level, Vec3 center, double spread, int count) {
        double half = Math.max(1.0D, spread);
        level.sendParticles(ModParticles.FROST_SHARD.get(),
                center.x, center.y, center.z, count, half, half, half, 0.3D);
    }

    /**
     * 碎裂声。爆炸自己那声 {@code GENERIC_EXPLODE} 由原版在 {@code finalizeExplosion} 里放，
     * 这里补的是"冰"的那一层——两层叠起来才不像一发普通 TNT。
     *
     * <p>走 {@link SoundSource#BLOCKS}：玻璃碎裂本来就是方块音，让玩家能分别用
     * "方块"与"玩家"两个音量滑块调它，比塞进 PLAYERS 更合理。
     */
    private static void playImpactSound(ServerLevel level, Vec3 center) {
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.2F, 0.7F);
    }
}
