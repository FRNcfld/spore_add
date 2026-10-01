package com.frnc.spore_add.compat;

import com.Harbinger.Spore.Core.Seffects;

import net.minecraft.world.effect.MobEffect;
import org.jetbrains.annotations.Nullable;

/**
 * Spore（真菌感染：孢子）的唯一引用点。
 *
 * <p><b>为什么集中在一个类里</b>：Spore 并没有为「可燃」提供公开 API——没有 {@code spore/api} 包、
 * 没有自定义事件，我们唯一能用的是它的 {@code Seffects.IGNITABLE} 这个 {@code public static} 字段。
 * 那不是一个承诺稳定的接口，Spore 更新后有改名/改结构的风险。把它收在这里，将来只需改这一个文件。
 *
 * <p><b>一个绕不开的例外</b>：mixin 的目标类必须是类字面量（{@code @Mixin(Tar.class)} 这种写法），
 * 没法藏在间接层后面。所以 Spore 的引用点实际有两处——本类（运行时取值）与各 mixin 的目标声明。
 * 这两处都写在了同一批文件里，找起来不难。
 *
 * <h2>关于「可燃被触发」为什么不用这里的方法判断</h2>
 * Spore 的触发逻辑硬编码在它自己的 {@code HandlerEvents.DefenseBypass(LivingDamageEvent)} 里
 * （六种伤害类型 + 1% 兜底概率），而且它触发时会<b>主动移除可燃 buff</b>。所以本 mod 用
 * {@code MobEffectEvent.Remove} 捕获那次移除当作触发信号，而不是去引 Spore 的伤害类型表——
 * 这样 Spore 以后改概率或改伤害类型，我们都不用跟着改。详见 {@code ModEvents}。
 */
public final class SporeCompat {

    private SporeCompat() {
    }

    /**
     * Spore 的「可燃」buff。
     *
     * <p>返回 {@code null} 表示取不到——正常加载顺序下不会发生（本 mod 在 {@code mods.toml} 里对
     * {@code spore} 声明了 {@code ordering="AFTER"}，所以它的注册项一定先就位），但调用方仍然按可空处理，
     * 免得因为一个第三方模组的内部变动而崩在事件里。
     */
    @Nullable
    public static MobEffect ignitable() {
        return Seffects.IGNITABLE.get();
    }

    /**
     * Spore 的「冻伤」buff（注册名 {@code frostbite}；类名是 {@code FrostBite}，注意大小写）。
     *
     * <p><b>它和可燃不是一类东西。</b>可燃是哑标记、amplifier 无人读；而冻伤的 amplifier <b>被 Spore
     * 真正使用</b>——算额外冻结伤害、判定能否越过自家进化体的抗性闸门、并缩放移动减速。
     * 所以本 mod 直接拿 amplifier 当层数，不另存一份，详见 {@code FrostbiteLevels}。
     */
    @Nullable
    public static MobEffect frostbite() {
        return Seffects.FROSTBITE.get();
    }
}
