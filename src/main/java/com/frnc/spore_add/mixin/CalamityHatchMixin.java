package com.frnc.spore_add.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.frnc.spore_add.fungus.WombHatch;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「灾厄孵化后传送到壳外」的挂点。
 *
 * <h2>为什么挂在 {@code setSearchArea} 上</h2>
 * {@code Womb#summon} 的顺序是：
 * {@code setPos(自己身上)} → {@code setSearchArea(重构体的 location)} → 套属性 →
 * {@code finalizeSpawn} → {@code addFreshEntity}。也就是说 {@code setSearchArea} 是
 * 「灾厄已经造好、但还没进世界」的那个时刻，在这里改坐标会连同 {@code addFreshEntity}
 * 一起生效。
 *
 * <p>为什么不直接在 {@code Womb#summon} 里改：那句落点用的是原版
 * {@code Entity#setPos} / {@code getX()}，要在 {@code @At} 里引用它们就必须 remap，
 * 而 {@code method} 写的是 Spore 的 {@code summon}、必须 {@code remap = false}——
 * 一个注解只有一个开关，覆盖不了两者（本项目那条最反复的约束）。
 * 换到 {@code Calamity} 这一侧就没有原版引用了。
 *
 * <h2>为什么用 {@code tickCount} 当闸门</h2>
 * {@code setSearchArea} 不只是孵化时会调（运行时也会），所以要认出「刚孵化」那一次。
 * 灾厄是 {@code entityType.create} 刚造出来的、还没 tick 过，所以那一刻
 * {@code tickCount == 0}；之后再被调用时它一定大于 0。不加这个闸门的话，
 * 一只站在重构体旁边的灾厄每次被设置搜索区域都会被凭空搬走。
 */
@Mixin(Calamity.class)
public abstract class CalamityHatchMixin {

    @Inject(method = "setSearchArea", at = @At("HEAD"), remap = false)
    private void sporeAdd$exitShellOnHatch(BlockPos pos, CallbackInfo ci) {
        Calamity self = (Calamity) (Object) this;
        if (self.tickCount != 0) {
            return;
        }
        WombHatch.exitShell(self);
    }
}
