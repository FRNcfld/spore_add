package com.frnc.spore_add.mixin;

import com.frnc.spore_add.client.DisplayedEffectLevel;

import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把鼠标悬停 buff 图标时那行 tooltip 名称里的等级也换成阿拉伯数字。
 *
 * <h2>为什么还需要这个 mixin</h2>
 * 图标本体与背包界面的名称行已经由 {@code NumeralEffectExtensions} 接管了，但 tooltip 是<b>另一条</b>
 * 独立路径：{@code EffectRenderingInventoryScreen} 自己拼 tooltip 文案时调用私有的
 * {@code getEffectName}，那里会为 amplifier 1~9 拼上罗马数字（"II"、"III"…）。
 * 这个私有方法不经过 Forge 的 {@code IClientMobEffectExtensions}，没有任何事件能插手，
 * 所以只能注入它。
 *
 * <p>注入点在 {@code RETURN} 并设为 cancellable：拿到原版算好的结果，如果这个 buff 归本 mod 显示
 * （{@link DisplayedEffectLevel#of} 不为 {@link DisplayedEffectLevel#NOT_OURS}）就换掉返回值，
 * 否则原样放行——别的 buff 的显示一点不受影响。
 *
 * <p>本类不声明任何字段：Mixin 会把 mixin 类的成员合并进目标类，往原版 GUI 里塞一个静态常量没有意义。
 * 因此语言键与文案拼接都放在 {@link DisplayedEffectLevel} 里。
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectNameLevelMixin {

    @Inject(method = "getEffectName", at = @At("RETURN"), cancellable = true)
    private void sporeAdd$arabicLevel(MobEffectInstance instance, CallbackInfoReturnable<Component> cir) {
        int level = DisplayedEffectLevel.of(instance);
        if (level <= 0) {
            // 不归我们管（NOT_OURS）或者当前没有数据：保持原版的返回
            return;
        }
        cir.setReturnValue(DisplayedEffectLevel.nameWithLevel(instance, level));
    }
}
