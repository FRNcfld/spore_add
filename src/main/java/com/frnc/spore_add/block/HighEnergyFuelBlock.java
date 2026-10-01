package com.frnc.spore_add.block;

import java.util.function.Supplier;

import com.frnc.spore_add.effect.IgnitableContact;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * 高能燃料的流体方块：生物接触它会逐秒累积可燃等级（需求 1）。
 *
 * <p>与另外两种流体不同，它不只是液体。三种流体共用同一份方块属性
 * （见 {@code ModBlocks#waterLikeBlock()}），区别只在于这里多覆写了一个回调。
 *
 * <h2>为什么是 {@code entityInside}</h2>
 * {@code Entity#checkInsideBlocks()} 会逐格调用所在方块的 {@code entityInside}，这是"接触方块"的原生落点，
 * 不需要自己扫 tick 去扫描实体。副作用是<b>它会按覆盖到的每个流体方块各调一次</b>——涨级、施加 buff、
 * 同步这几件事都交给 {@link IgnitableContact} 统一处理，那里已经做了同 tick 去重。
 *
 * <p>可燃料之外，Spore 的焦油池（{@code spore:tar}）走的是同一个 {@link IgnitableContact}，
 * 所以两个来源的行为完全一致。
 */
public class HighEnergyFuelBlock extends LiquidBlock {

    public HighEnergyFuelBlock(Supplier<? extends FlowingFluid> fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);

        if (level.isClientSide()) {
            return;
        }
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        IgnitableContact.apply(living);
    }
}
