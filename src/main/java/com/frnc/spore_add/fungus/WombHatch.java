package com.frnc.spore_add.fungus;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.Harbinger.Spore.Sentities.Organoids.Womb;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.world.SafeSpot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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

    /** 碰撞余量：两个包围盒刚好不重叠之外再让出半格。这是几何余量，不是玩法数值。 */
    private static final double CLEARANCE = 0.5D;

    /** 绕穹顶找落点时试几个方向（每 30°一个）。 */
    private static final int DOME_ANGLES = 12;

    private WombHatch() {
    }

    /** 把刚孵化出来的灾厄送出壳外。不是从重构体里出来的就什么都不做。 */
    public static void exitShell(Calamity calamity) {
        if (!SporeAddFungusConfig.wombHatchExitEnabled()) {
            return;
        }
        Level level = calamity.level();
        if (level.isClientSide) {
            return;
        }
        Womb womb = overlappingWomb(calamity);
        if (womb == null) {
            return;   // 不是从重构体里出来的（比如 Spore 自己刷的灾厄），别动它
        }
        Vec3 spot = null;
        if (SporeAddFungusConfig.wombHatchExitFromDome()) {
            spot = outsideDome(calamity, womb);
        }
        if (spot == null) {
            // 没有穹顶罩着它，或者穹顶外那一圈放不下 → 退回到「至少送出重构体自己」
            spot = outsideWomb(calamity, womb);
        }
        if (spot != null) {
            calamity.moveTo(spot.x, spot.y, spot.z, calamity.getYRot(), calamity.getXRot());
        }
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
            Vec3 spot = new Vec3(
                    center.x + Math.cos(angle) * radius,
                    womb.getY(),
                    center.z + Math.sin(angle) * radius);
            if (isFree(calamity, spot)) {
                return spot;
            }
        }
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

    /** 较弱的落点：只把灾厄挪出重构体自己的包围盒。 */
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
        // 水平八个方向全被堵住（比如四周都是实心生物质）：往上抬一个身位再试
        Vec3 up = new Vec3(womb.getX(),
                womb.getBoundingBox().maxY + calamity.getBbHeight() / 2.0D + CLEARANCE,
                womb.getZ());
        return isFree(calamity, up) ? up : null;
    }

    /** 与这只灾厄重叠的重构体。孵化那一刻两者必定重叠，所以这就是「它的壳」。 */
    @Nullable
    private static Womb overlappingWomb(Calamity calamity) {
        for (Womb womb : calamity.level().getEntitiesOfClass(Womb.class, calamity.getBoundingBox())) {
            if (!womb.isRemoved()) {
                return womb;
            }
        }
        return null;
    }

    /** 挪到那里会不会卡进方块里（含"区块得已加载"这一条，理由见 {@link SafeSpot}）。 */
    private static boolean isFree(Calamity calamity, Vec3 spot) {
        return SafeSpot.isFree(calamity, spot);
    }
}
