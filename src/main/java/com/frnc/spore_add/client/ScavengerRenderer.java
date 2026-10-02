package com.frnc.spore_add.client;

import com.Harbinger.Spore.Client.Renderers.InfectedHumanRenderer;
import com.frnc.spore_add.scavenger.Scavenger;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * 拾荒者的渲染器。需求 1 要的「模型、贴图与菌染人类完全一致」，这个类<b>一行都不用写</b>。
 *
 * <h2>为什么能这么省</h2>
 * Spore 的 {@code InfectedHumanRenderer} 是<b>泛型</b>的：
 * <pre>
 *   InfectedHumanRenderer&lt;Type extends InfectedHuman&gt; extends BaseInfectedRenderer&lt;Type, InfectedModel&lt;Type&gt;&gt;
 * </pre>
 * 而拾荒者继承自 {@code InfectedHuman}，正好落在那个上界里。所以直接用它、把类型参数填成
 * {@code Scavenger} 就行——贴图（{@code spore:textures/entity/infected.png}）、眼睛层
 * （{@code .../eyes/infected.png}）、模型（同一个 {@code LAYER_LOCATION}）、阴影大小（0.5F）
 * 全部继承下来，而且不是"抄得一样"，是<b>同一份常量</b>：Spore 哪天换贴图，这边跟着换，
 * 永远不会出现两边不一致。
 *
 * <p>这正是当初选「独立实体 + 构造器重定向」而不是"自研模型"的原因：那条路要重写动画，
 * 只能做到"看起来差不多"，而这条是逐像素相同。
 *
 * <h2>为什么必须单独注册</h2>
 * 实体类型是我们自己的（{@code spore_add:scavenger}），类型不同就要有自己的渲染器注册项，
 * 哪怕实现是全空的——客户端遇到没有渲染器的实体会在那一帧崩掉。
 */
public class ScavengerRenderer extends InfectedHumanRenderer<Scavenger> {

    public ScavengerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}
