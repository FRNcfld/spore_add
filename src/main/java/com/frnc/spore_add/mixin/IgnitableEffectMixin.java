package com.frnc.spore_add.mixin;

import java.util.function.Consumer;

import com.Harbinger.Spore.Effect.Ignitable;
import com.frnc.spore_add.client.NumeralEffectExtensions;

import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 本 mod 的第一个 mixin：给 Spore 的「可燃」buff 补一个客户端渲染扩展，把它的等级画成阿拉伯数字。
 *
 * <h2>为什么非要用 mixin</h2>
 * Forge 解析 buff 的客户端扩展是 {@code IClientMobEffectExtensions.of(instance)} →
 * {@code of(instance.getEffect())} → {@code effect.getEffectRendererInternal()}，也就是<b>按 effect 类型</b>
 * 取的。可燃是 Spore 注册的效果，它的这个槽位是空的，而我们无法从外部替别人的类填上——
 * 唯一能碰到那个类的办法就是 mixin。原版那边还有第二层限制：等级只在 amplifier 1~9 时显示、且写成罗马数字，
 * 所以覆写 {@code initializeClient} 换成自己的渲染器是唯一出路。
 *
 * <h2>这是"新增一个覆写方法"，不是改写已有方法</h2>
 * Spore 的 {@code Ignitable} 只声明了构造函数和 {@code getCurativeItems}，<b>没有</b>覆写
 * {@link net.minecraft.world.effect.MobEffect#initializeClient}。所以下面这个方法是被 Mixin
 * <b>添加</b>到目标类上的，加进去之后它正好覆写从 {@code MobEffect} 继承来的同名方法。
 * 因此这里不能写 {@code @Override}（本类并没有继承 {@code MobEffect}），也不需要 {@code @Overwrite}
 * （那个注解要求目标类自己声明了该方法）。
 *
 * <p>被调用是无误的：{@code MobEffect} 的构造函数会调用 {@code initClient()}，间接虚调用
 * {@code initializeClient}，此时的运行期类型已经是打上本 mixin 的 {@code Ignitable}。
 *
 * <h2>只挂在客户端</h2>
 * 本 mixin 登记在 {@code spore_add.mixins.json} 的 {@code "client"} 列表里，专用服务端不会应用它——
 * 反正 {@code initializeClient} 也只在物理客户端被调用，服务端保持原样即可。
 */
@Mixin(Ignitable.class)
public abstract class IgnitableEffectMixin {

    /** 注入入口，内容与在自家 {@code MobEffect} 子类里覆写 {@code initializeClient} 完全一致。 */
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new NumeralEffectExtensions());
    }
}
