package com.frnc.spore_add.fungus;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.Harbinger.Spore.Sentities.Organoids.Womb;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.world.SafeSpot;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 灾厄孵化后的「出壳」：把刚孵化出来的灾厄送出<b>心智的生物质穹顶</b>。
 *
 * <h2>「壳」到底指哪个</h2>
 * 指心智那两层生物质球壳：{@code Proto#generateCasing} 调了两次 {@code generateChasing}，
 * 半径 <b>32 厚 2</b> 一层、半径 <b>16 厚 1</b> 一层，中心是心智的 {@code NODE}
 * （= {@code getOnPos()}）。生成细节见 {@code DomeBreach} 的类注释。
 *
 * <p><b>注意不是重构体自己那圈方块</b>。最初的版本只把灾厄挪出重构体的包围盒，
 * 理由也说得通（{@code Womb#summon} 把灾厄放在重构体自己身上，而重构体是嵌在生物质里的土丘），
 * 但那不足以让它离开穹顶——它照样在穹顶内部把壳挖烂（游戏里实测到了，见 {@code wombHatchExitFromDome}
 * 的说明）。所以现在<b>优先</b>按穹顶算落点。
 *
 * <h2>为什么必须把它丢那么远</h2>
 * 落点是从穹顶中心往外推「穹顶半径 + 余量」，也就是离中心三十几格——这是刻意的：
 * 灾厄本来就不该待在巢里。水平推的距离等于半径就能保证在球壳之外（三维距离 ≥ 水平距离），
 * 所以只算水平方向是够的，不必再做一次球面求解。
 *
 * <p>落点不是实心方块才算数（用 {@code noCollision} 判），一次不行就换个角度再试；
 * 一圈都不行就退回「只送出重构体」那个较弱的落点——**宁可挪得不够远，也不把它塞进石头里**。
 */
public final class WombHatch {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 碰撞余量：两个包围盒刚好不重叠之外再让出半格。这是几何余量，不是玩法数值。 */
    private static final double CLEARANCE = 0.5D;

    /** 绕穹顶找落点时试几个方向（每 30°一个）。 */
    private static final int DOME_ANGLES = 12;

    private WombHatch() {
    }

    /**
     * 把刚孵化出来的灾厄送出壳外。
     *
     * <p>{@code womb} 由调用方（{@code WombSummonMixin}）直接给出——它就在孵化现场，
     * 比"再扫一遍周围找重叠的重构体"更准，也不会在重构体已经 {@code discard()} 之后扑空。
     */
    public static void exitShell(Calamity calamity, Womb womb) {
        if (!SporeAddFungusConfig.wombHatchExitEnabled()) {
            return;
        }
        if (calamity.level().isClientSide) {
            return;
        }
        Vec3 spot = null;
        if (SporeAddFungusConfig.wombHatchExitFromDome()) {
            spot = outsideDome(calamity, womb);
        }
        if (spot == null) {
            // 没有穹顶罩着它，或者穹顶外那一圈放不下 → 退回到「至少送出重构体自己」
            spot = outsideWomb(calamity, womb);
        }
        if (spot == null) {
            // 两条路都没找到落点：这一次不动它。留一条日志，否则"没传送"看起来会像 bug 而查不出原因
            LOGGER.info("[SporeAdd] 灾厄出壳失败：没找到落点，维持原位。重构体 {} @ {}",
                    womb.getUUID(), womb.blockPosition());
            return;
        }
        calamity.moveTo(spot.x, spot.y, spot.z, calamity.getYRot(), calamity.getXRot());
        // 带上喂到的属性种类数：那是「这只灾厄强不强」的唯一来源，
        // 而 hatchMinMutationTypes 那道闸门就是按它判的——排查时一眼能看出门槛有没有生效
        LOGGER.info("[SporeAdd] 灾厄出壳：{} @ {} → {}（喂到 {} 种突变属性）", calamity.getUUID(),
                womb.blockPosition(), spot, WombGate.distinctMutations(womb));
    }

    /**
     * 某一列的地表落点。
     *
     * <h2>为什么 Y 必须按地表算，不能沿用重构体的 Y</h2>
     * 重构体是<b>埋在地形里</b>的土丘——它的 Y 就是"在一个山包内部"的高度。
     * 照着这个高度往外推三十几格，那边通常是山体、树木或水面，
     * {@code SafeSpot.isFree} 会一个方向都判不过，于是整条穹顶路径空手而归、
     * 悄悄退回那个"只挪出包围盒"的弱落点——表现就是<b>灾厄几乎没动</b>。
     * （这就是实测报上来的那个"没有传送到壳外"。）
     *
     * <p>用 {@code MOTION_BLOCKING_NO_LEAVES} 是照抄 Spore 自己的 {@code Proto#teleportToSurface}：
     * 它给的是"脚下那块地之上"的高度，正是实体该站的位置；忽略树叶则避免被树冠顶到半空。
     *
     * <p>未加载的列直接返回 {@code null}：那时 {@code getHeight} 给的是无意义的值，
     * 而 {@link SafeSpot#isFree} 本来也要求区块已加载。
     */
    @Nullable
    private static Vec3 atSurface(Calamity calamity, double x, double z) {
        Level level = calamity.level();
        BlockPos column = BlockPos.containing(x, calamity.getY(), z);
        if (!level.hasChunkAt(column)) {
            return null;
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        return new Vec3(x, surfaceY, z);
    }

    /**
     * 落点放到罩住这只灾厄的那只心智的穹顶之外。
     *
     * <p>返回 null 表示"没有穹顶罩着它"或"穹顶外一圈都放不下"，调用方会退到较弱的落点。
     */
    @Nullable
    private static Vec3 outsideDome(Calamity calamity, Womb womb) {
        Proto hivemind = enclosingHivemind(calamity);
        if (hivemind == null) {
            // 没有心智罩着它 = 这里本来就没有「穹顶」，退回弱落点是正常行为而不是故障。
            // 但这一条必须留痕：用户报「没传到壳外」时，第一个要排除的就是它。
            LOGGER.info("[SporeAdd] 灾厄出壳：{} 附近 {} 格内没有心智，此处没有穹顶可出",
                    calamity.getUUID(), SporeAddFungusConfig.hivemindDomeRadius());
            return null;
        }
        Vec3 center = domeCenter(hivemind);
        double radius = SporeAddFungusConfig.hivemindDomeRadius()
                + SporeAddFungusConfig.wombHatchExitDomeMargin();

        // 从中心指向灾厄的方向往外推；灾厄正好压在中心时（距离为 0）方向没有意义，退成 +X
        Vec3 away = calamity.position().subtract(center);
        double baseAngle = away.lengthSqr() < 1.0E-4D
                ? 0.0D
                : Math.atan2(away.z, away.x);

        for (int i = 0; i < DOME_ANGLES; i++) {
            double angle = baseAngle + i * (2.0D * Math.PI / DOME_ANGLES);
            // **按地表取 Y**：穹顶中心在山包顶上，沿用它的 Y 会让落点埋进旁边的地里，
            // 十二个方向一个都过不了（见 atSurface 的说明）
            Vec3 spot = atSurface(calamity,
                    center.x + Math.cos(angle) * radius,
                    center.z + Math.sin(angle) * radius);
            if (spot != null && isFree(calamity, spot)) {
                return spot;
            }
        }
        LOGGER.info("[SporeAdd] 灾厄出壳：穹顶外一圈（{} 格，共 {} 个方向）都放不下，退回弱落点",
                radius, DOME_ANGLES);
        return null;
    }

    /** 哪只心智的穹顶罩住了这只灾厄（取最近的一只；都没有则返回 null）。 */
    @Nullable
    private static Proto enclosingHivemind(Calamity calamity) {
        double limit = SporeAddFungusConfig.hivemindDomeRadius();
        double best = limit * limit;
        Proto found = null;
        for (Proto hivemind : SporeCompat.hiveminds()) {
            if (hivemind.isRemoved() || hivemind.level() != calamity.level()) {
                continue;
            }
            double distance = domeCenter(hivemind).distanceToSqr(calamity.position());
            if (distance <= best) {
                best = distance;
                found = hivemind;
            }
        }
        return found;
    }

    /**
     * 穹顶的中心：心智那个 {@code NODE}（它不是心智的实时坐标——只在心智移动超过 10 格时才更新，
     * 见 {@code Proto#tick}）。用 {@code NODE} 而不是 {@code position()} 才和 Spore 生成壳时用的是同一个点。
     */
    private static Vec3 domeCenter(Proto hivemind) {
        BlockPos node = (BlockPos) hivemind.getEntityData().get(Proto.NODE);
        return new Vec3(node.getX() + 0.5D, node.getY(), node.getZ() + 0.5D);
    }

    /**
     * 较弱的落点：只把灾厄挪出重构体自己的包围盒。
     *
     * <p><b>先试同一高度的一圈</b>（重构体埋在土里时，四面八方的同一高度往往就是实心生物质或岩层），
     * <b>再试按地表高度的一圈</b>——后者才是真正大概率能站住的那一圈。
     * 最后才退回"抬到重构体正上方一个身位"。
     */
    @Nullable
    private static Vec3 outsideWomb(Calamity calamity, Womb womb) {
        double start = womb.getBbWidth() / 2.0D + calamity.getBbWidth() / 2.0D + CLEARANCE;
        double limit = Math.max(start, SporeAddFungusConfig.wombHatchExitSearchRadius());

        for (double r = start; r <= limit; r += 1.0D) {
            for (int i = 0; i < 8; i++) {
                double angle = i * (Math.PI / 4.0D);
                Vec3 spot = new Vec3(
                        womb.getX() + Math.cos(angle) * r,
                        womb.getY(),
                        womb.getZ() + Math.sin(angle) * r);
                if (isFree(calamity, spot)) {
                    return spot;
                }
            }
        }
        for (double r = start; r <= limit; r += 1.0D) {
            for (int i = 0; i < 8; i++) {
                double angle = i * (Math.PI / 4.0D);
                Vec3 spot = atSurface(calamity,
                        womb.getX() + Math.cos(angle) * r,
                        womb.getZ() + Math.sin(angle) * r);
                if (spot != null && isFree(calamity, spot)) {
                    return spot;
                }
            }
        }
        // 全都堵住：往上抬一个身位再试
        Vec3 up = new Vec3(womb.getX(),
                womb.getBoundingBox().maxY + calamity.getBbHeight() / 2.0D + CLEARANCE,
                womb.getZ());
        return isFree(calamity, up) ? up : null;
    }

    /** 挪到那里会不会卡进方块里（含"区块得已加载"这一条，理由见 {@link SafeSpot}）。 */
    private static boolean isFree(Calamity calamity, Vec3 spot) {
        return SafeSpot.isFree(calamity, spot);
    }
}
