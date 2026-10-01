package com.frnc.spore_add.block;

import java.util.function.Supplier;

import com.frnc.spore_add.effect.ColdEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * 冷却液的流体方块：生物泡在里面会像陷在细雪里一样失温，并逐秒累积冻伤（<b>封顶 10 层</b>）。
 *
 * <h2>"和处于细雪中一样"只靠一句</h2>
 * 原版 {@code PowderSnowBlock#entityInside} 里真正产生冻结的只有
 * {@code entity.setIsInPowderSnow(true)} —— 累计到 140 tick 后每 40 tick 受冻伤伤害、离开后每 tick 衰减 2、
 * 按冻结比例减速，全部由 {@code Entity#baseTick} 与 {@code LivingEntity#aiStep} 自动完成。
 * 所以这里只设那个标志，<b>不要</b>自己调 {@code setTicksFrozen}。
 *
 * <p>刻意不调 {@code makeStuckInBlock}（那是细雪的"陷下去"）、也不覆写 {@code fallOn} /
 * {@code getCollisionShape}（那是免摔伤与踩在雪面上）——需求要的是"冻结计时 + 减速 + 冻伤伤害"这三项，
 * 正好就是设标志能自动带来的全部效果。
 */
public class CoolantBlock extends LiquidBlock {

    /** 冷却液能把冻伤推到的层数上限。只封顶"涨"，不会压低从液态寒冷带过来的更高层数。 */
    private static final int FROSTBITE_CAP = 10;

    public CoolantBlock(Supplier<? extends FlowingFluid> fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);

        if (level.isClientSide()) {
            return;
        }
        if (entity instanceof LivingEntity living) {
            // 本回调每 tick 都进来、且生物泡在两格深时按方块数各进来一次，
            // "每秒至多一层"与"同 tick 去重"都在 FrostbiteLevels 里统一兜底。
            // 走 ColdEffects 是为了与液态寒冷用同一条路径（那里还要额外叠细雪标志）
            ColdEffects.chill(living, FROSTBITE_CAP);
        }
    }
}
