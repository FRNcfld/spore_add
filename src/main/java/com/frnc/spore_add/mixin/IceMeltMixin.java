package com.frnc.spore_add.mixin;

import com.frnc.spore_add.world.FrozenChunks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让液态寒冷影响范围内的原版冰不融化。
 *
 * <h2>为什么要拦原版冰</h2>
 * 三种冰里只有 {@code minecraft:ice} 带随机刻，并会在方块光照 ≥ 11 时融化成水
 * （见 {@code IceBlock#randomTick} → {@code melt} → {@code Blocks.WATER}）。浮冰与蓝冰不融。
 * 而冰扩散的中间层正是原版冰，所以不拦的话，亮处的中层会自己化成水坑。
 *
 * <p>判定很便宜：{@link FrozenChunks} 里只有源头方块每秒刷新的区块标记，
 * 这里 O(1) 查一次所在区块即可，不做距离扫描（原因见那个类的说明）。
 *
 * <p>标记带 2 秒有效期，由源头方块持续刷新。因此<b>把源头挖掉后约 2 秒，冰就恢复原版行为、会正常融化</b>
 * ——这符合"影响范围内才不融"的语义。
 */
@Mixin(IceBlock.class)
public abstract class IceMeltMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void sporeAdd$keepFrozen(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                     CallbackInfo ci) {
        if (FrozenChunks.isMarked(level, pos)) {
            ci.cancel();
        }
    }
}
