package com.frnc.spore_add.mixin;

import java.util.function.Consumer;

import com.Harbinger.Spore.Effect.FrostBite;
import com.frnc.spore_add.client.NumeralEffectExtensions;

import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 给 Spore 的「冻伤」buff 补上客户端渲染扩展，把它的等级画成阿拉伯数字。
 *
 * <p>与 {@code IgnitableEffectMixin} 是同一套做法、同一个原因：Forge 解析 buff 客户端扩展是
 * {@code IClientMobEffectExtensions.of(instance)} → {@code of(instance.getEffect())} →
 * {@code effect.getEffectRendererInternal()}，也就是<b>按 effect 类型</b>取的；冻伤是 Spore 注册的效果，
 * 我们无法从外部替别人的类填那个槽位，只能 mixin。
 *
 * <p>冻伤这边的等级判断比可燃还简单：它的 amplifier <b>本来就是层数</b>（Spore 自己在读它），
 * 所以显示直接取 {@code amplifier + 1}，连同步都不需要——见 {@code DisplayedEffectLevel}。
 *
 * <p>Spore 的 {@code FrostBite} 没有覆写 {@code initializeClient}，所以下面这个方法是被 Mixin
 * <b>添加</b>到目标类上的，加进去之后正好覆写从 {@code MobEffect} 继承来的同名方法。
 * 因此不能写 {@code @Override}（本类并没有继承 {@code MobEffect}）。
 *
 * <p>登记在 {@code spore_add.mixins.json} 的 {@code "client"} 列表：它只挂客户端渲染，
 * 专用服务端不需要应用。
 */
@Mixin(FrostBite.class)
public abstract class FrostbiteEffectMixin {

    /** 注入入口，内容与在自家 {@code MobEffect} 子类里覆写 {@code initializeClient} 完全一致。 */
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new NumeralEffectExtensions());
    }
}
