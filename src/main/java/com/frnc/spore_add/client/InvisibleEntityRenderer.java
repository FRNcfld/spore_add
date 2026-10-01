package com.frnc.spore_add.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * "什么都不画"的渲染器：给那些视觉完全由粒子承担的实体用（粒子云、冰球里的延时炸弹）。
 *
 * <h2>为什么"什么都不画"也必须存在</h2>
 * 这些实体的观感全部来自它们自己每 tick 喷的粒子，实体本身没有任何要画的东西。
 * 但它们仍然必须有一个渲染器注册在案——因为 {@code EntityRenderDispatcher#shouldRender} 是这么写的：
 *
 * <pre>
 *   EntityRenderer&lt;? super E&gt; entityrenderer = this.getRenderer(pEntity);
 *   return entityrenderer.shouldRender(...);      // getRenderer 返回 null 就在这里 NPE
 * </pre>
 *
 * 也就是说，<b>世界里只要出现一个没注册渲染器的实体，客户端就会在渲染那一帧直接崩</b>，
 * 而且报的是个看不出实体的 NPE。这类"实体注册了、渲染器忘了"是本 mod 踩过的坑：
 * 弹体配了渲染器、粒子云漏了，于是第一次引爆就崩。所以新增实体类型时，
 * {@code ModEntities} 里加一条，{@link SporeAddClient#onRegisterRenderers} 里也要跟着加一条。
 *
 * <p>不需要覆写 {@code render}：基类实现只做"渲染自定义名牌"，
 * 而这些实体都没有名牌（{@code hasCustomName()} 为假），天然什么都不画。
 * 只有 {@link #getTextureLocation} 是抽象方法，必须给一个返回值——
 * 这个值只在别的调试路径上被用到，随便给个合法的图集路径即可。
 *
 * <p>做成泛型是为了让这类实体共用一份实现，而不是每加一个就抄一遍这个类。
 */
public class InvisibleEntityRenderer<T extends Entity> extends EntityRenderer<T> {

    public InvisibleEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TextureAtlas.LOCATION_PARTICLES;
    }
}
