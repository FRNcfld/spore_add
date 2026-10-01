package com.frnc.spore_add.fluid;

import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.ForgeFlowingFluid;

/**
 * 本 mod 所有流体的基类：把"静置"和"流动"这一对变体的公共部分收在这里。
 *
 * <p>Forge 1.20.1 没有 {@code BaseFlowingFluid}（那是 NeoForge 1.21 的类名），对应物是
 * {@link ForgeFlowingFluid}，它已经通过 {@code Properties} 读走了流动参数，所以这里不需要再覆盖
 * {@code getSlopeFindDistance} / {@code getDropOff} / {@code getTickDelay}——那三个值在
 * {@link ModFluids} 里按原版水的 4 / 1 / 5 配置。
 *
 * <p><b>关于"不可刷源"</b>：本 mod 的三种流体刻意不能像水一样两格相邻就生出新源头，
 * 这是 {@code gradle} 之外的一处玩法决定，由两个默认值共同保证，两处都不能被改动：
 * <ul>
 *   <li>{@link ForgeFlowingFluid#canConvertToSource(net.minecraft.world.level.Level)} 在 Forge 里硬编码
 *       返回 {@code false}；</li>
 *   <li>{@code FluidType.Properties.canConvertToSource} 默认也是 {@code false}（见 {@link ModFluids}）。</li>
 * </ul>
 * 因此这里不写任何覆盖代码就是想要的行为。将来若要放开无限源，上面两处都要改。
 */
public abstract class SporeFluid extends ForgeFlowingFluid {

    protected SporeFluid(Properties properties) {
        super(properties);
    }

    /** 静置变体，也就是用桶倒出来、以及"水源"那种形态。 */
    public static class Source extends SporeFluid {

        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }
    }

    /** 流动变体，即从源头扩散出去的部分，液面高度由 {@code LEVEL} 表示。 */
    public static class Flowing extends SporeFluid {

        public Flowing(Properties properties) {
            super(properties);
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }
    }
}
