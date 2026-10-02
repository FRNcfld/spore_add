package com.frnc.spore_add.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 「这只实体能站在哪」——把实体放到一个真正站得住的位置，而不是照着坐标硬塞。
 *
 * <h2>为什么需要它</h2>
 * 直接 {@code moveTo(x, y, z)} 是<b>不做任何检查</b>的：不看你有没有卡在方块里、
 * 也不看那个区块加载了没有。把一只生物塞进实心方块里的症状很隐蔽又很难受：
 * <ul>
 *   <li><b>卡住不动</b>——AI 寻路找不到出路，它就一直站在那儿；</li>
 *   <li><b>打不到</b>——玩家的攻击射线先命中方块，根本选不中里面的实体；</li>
 *   <li>而如果那堆方块本身悬在空中（比如心智穹顶的上半圈壳），看起来就是「悬浮的生物」。</li>
 * </ul>
 *
 * <p>本项目有两处落点曾经是裸 {@code moveTo}：心智存储的投放（{@code HivemindStorage#spawnOne}）
 * 与灾厄的出壳（{@code WombHatch}）。两处都是实测踩到之后才改的。
 *
 * <h2>为什么"区块已加载"必须先判</h2>
 * 未加载的区块查不到方块碰撞，{@code noCollision} 会回答「什么都没有」——
 * 于是未知区域看起来是完美空位，实体会被丢进一片还没加载的地形里。
 * 原版的 {@code Entity#randomTeleport} 正是先做这一步。
 */
public final class SafeSpot {

    /** 绕圈找的时候分几个方向。 */
    private static final int DIRECTIONS = 8;

    /** 由近到远的步长（格）。 */
    private static final double STEP = 1.0D;

    private SafeSpot() {
    }

    /** 这个位置能不能放下这只实体：区块已加载，且它的包围盒不撞方块。 */
    public static boolean isFree(Entity entity, Vec3 spot) {
        Level level = entity.level();
        if (!level.hasChunkAt(BlockPos.containing(spot.x, spot.y, spot.z))) {
            return false;
        }
        // 用「把包围盒平移过去」来试，而不是先改位置再检测——不真的动它，失败也没有副作用
        AABB moved = entity.getBoundingBox().move(spot.subtract(entity.position()));
        return level.noCollision(entity, moved);
    }

    /**
     * 先试 {@code center} 本身，不行就绕着它由近到远找一个能站的位置。
     *
     * <p>只在一个平面上找（保持 {@code center} 的 Y）：调用方要的是"在这附近出现"，
     * 不是"随便找个地方"——绕远了就失去意义了。找不到返回 null，由调用方决定怎么办
     * （本项目两处都是"宁可这次不放，也不硬塞")。
     */
    @Nullable
    public static Vec3 near(Entity entity, Vec3 center, double startRadius, double maxRadius) {
        if (isFree(entity, center)) {
            return center;
        }
        double limit = Math.max(startRadius, maxRadius);
        for (double radius = Math.max(STEP, startRadius); radius <= limit; radius += STEP) {
            for (int i = 0; i < DIRECTIONS; i++) {
                double angle = i * (2.0D * Math.PI / DIRECTIONS);
                Vec3 spot = new Vec3(
                        center.x + Math.cos(angle) * radius,
                        center.y,
                        center.z + Math.sin(angle) * radius);
                if (isFree(entity, spot)) {
                    return spot;
                }
            }
        }
        return null;
    }
}
