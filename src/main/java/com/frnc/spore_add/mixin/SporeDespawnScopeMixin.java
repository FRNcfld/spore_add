package com.frnc.spore_add.mixin;

import com.Harbinger.Spore.sEvents.HandlerEvents;
import com.frnc.spore_add.fungus.DespawnScope;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 Spore 的 Despawning System 清理过程两端开关 {@link DespawnScope}。
 *
 * <h2>目标方法</h2>
 * {@code HandlerEvents#cleanUpMobs(ServerLevel)} 是私有的<b>静态</b>方法，由 Spore 每 1200 tick
 * 对每个维度调一次；它先按类别把实体收进几个列表，再对每个列表调
 * {@code despawnExcess(level, list, cap)}，后者对超出上限的那些实体执行 {@code discard()}。
 *
 * <p>所以这个方法的执行期间，恰好就是"正在被 Despawning System 清理"的窗口：
 * 进出这个窗口的所有 {@code discard()} 都是清理行为，窗口之外的一律不是。
 *
 * <h2>为什么 {@code remap = false}</h2>
 * {@code cleanUpMobs} 是 <b>Spore 自己的</b>方法名，不是原版覆写，所以在生产环境里
 * 不会被改名，也就不该进 refmap。这一点与 {@code FungusColdMixin} 里那三处同理——
 * 不写的话注解处理器会因为查不到 searge 映射而直接编译失败。
 *
 * <p>本类刻意<b>没有</b>引用任何原版成员，所以 {@code remap = false} 在这里没有副作用。
 * 真正读原版 {@code Entity#discard()} 的那一半在 {@link EntityDiscardHarvestMixin} 里，
 * 那个 mixin 的 {@code remap} 保持默认（true）。两个文件合起来才是完整的一套，见 {@link DespawnScope}。
 */
@Mixin(HandlerEvents.class)
public abstract class SporeDespawnScopeMixin {

    /** 进入清理窗口。用 {@code HEAD} 而不是别的位置：窗口要从方法的第一行就开始算。 */
    @Inject(method = "cleanUpMobs", at = @At("HEAD"), remap = false)
    private static void sporeAdd$enterDespawnScope(ServerLevel level, CallbackInfo ci) {
        DespawnScope.enter();
    }

    /**
     * 离开清理窗口。
     *
     * <p>用 {@code RETURN} 而不是 {@code TAIL}：这个方法只有一个返回点，两者等价，
     * 但 {@code RETURN} 对"以后有人加了提前 return"更稳——每一个返回点都会执行，
     * 而 {@code TAIL} 只挂在最后一个返回点上，加了提前返回就会漏关标志。
     */
    @Inject(method = "cleanUpMobs", at = @At("RETURN"), remap = false)
    private static void sporeAdd$exitDespawnScope(ServerLevel level, CallbackInfo ci) {
        DespawnScope.exit();
    }
}
