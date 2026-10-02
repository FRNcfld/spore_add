package com.frnc.spore_add.client;

import com.frnc.spore_add.entity.FrozenCapsuleEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 冰封生物外面那层冰壳：把原版冰块的模型按体型拉伸出来。
 *
 * <h2>为什么直接画原版方块，而不是自建模型</h2>
 * 铁魔法（Iron's Spells）的做法是自带一套冰壳模型 + 两张贴图（{@code ice_tomb.png} 与
 * {@code ice_tomb_cull.png}，后者用来剔掉内侧的面）。这里不需要：<b>原版冰的模型渲染层本来就是
 * {@code translucent}</b>——它是半透明的，所以里面的生物透过冰看得见，效果一样，而本工程
 * 一张新贴图都不用加。
 *
 * <h2>画一个拉伸的立方体，而不是一堆单位方块</h2>
 * 另一种做法是铺一层 {@code ceil(w) × ceil(h) × ceil(w)} 的单位冰块，好处是贴图不拉伸。
 * <b>不行</b>：那些方块相邻的<b>内侧面</b>会被各画一遍，半透明叠加之后出现一圈明显的深色缝。
 * 一个拉伸的立方体没有内侧面，代价只是冰贴图被轻微非等比拉伸——那是噪声贴图，看不出来。
 *
 * <h2>为什么冰不会挡住里面的生物</h2>
 * 生物是冰壳的<b>乘客</b>，而乘客由原版渲染器独立绘制（{@code LevelRenderer} 会遍历
 * {@code level.entitiesForRendering()}，乘客也在其中）。冰壳画的是半透明批次，
 * 而半透明批次在实体绘制里排在后面——所以"先画生物、后铺一层半透明冰"，
 * 与末影人举着一个半透明方块是同一套机制。
 *
 * <p>注意 {@code renderSingleBlock} 这个调用是有原版先例的：{@code EndermanRenderer}
 * 画它举着的方块用的就是同一个方法、同一组参数。
 */
public class FrozenCapsuleRenderer extends EntityRenderer<FrozenCapsuleEntity> {

    /** 冰壳的方块状态。构造一次即可——它不会变。 */
    private static final BlockState SHELL = Blocks.ICE.defaultBlockState();

    /**
     * 画方块用。
     *
     * <p>在构造器里从 {@code Context} 取出来存着，而不是渲染时去
     * {@code Minecraft.getInstance().getBlockRenderer()}：1.20.1 的 {@code EntityRenderer}
     * <b>并没有保存那个 Context</b>（它只留了 {@code entityRenderDispatcher}），
     * 所以子类要用什么就得自己接住。原版 {@code EndermanRenderer} 画它举着的方块也是这个写法。
     */
    private final BlockRenderDispatcher blockRenderer;

    public FrozenCapsuleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(FrozenCapsuleEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float width = entity.getShellWidth();
        float height = entity.getShellHeight();

        poseStack.pushPose();
        // 方块模型是 0..1 的立方体，原点在最小那个角。先往水平方向挪半格让它与实体原点居中，
        // 再按 W×H×W 撑开——于是冰壳的底面贴着实体脚底，正好罩住站在同一位置的乘客。
        poseStack.translate(-width / 2.0F, 0.0F, -width / 2.0F);
        poseStack.scale(width, height, width);
        this.blockRenderer.renderSingleBlock(SHELL, poseStack, buffer,
                getPackedLightCoords(entity, partialTicks), OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * 这个方法画的是方块模型，贴图取自方块图集。
     *
     * <p>{@code EntityRenderer} 要求有个返回值，但真正被使用的是
     * {@code renderSingleBlock} 内部按方块状态解析出来的渲染层，与这里无关。
     */
    @Override
    public ResourceLocation getTextureLocation(FrozenCapsuleEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
