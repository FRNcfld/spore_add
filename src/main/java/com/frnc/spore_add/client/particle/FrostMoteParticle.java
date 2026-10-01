package com.frnc.spore_add.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 本 mod 三个霜冻粒子共用的实现：一个带贴图的四边形，缓慢飘动、逐渐淡出。
 *
 * <p>三个粒子（云雾 / 雪花 / 冰屑）的差别只是外观参数不同，所以共用这一个类，
 * 由 {@link #provider} 传入不同的取值——这样"新增一种霜冻粒子"只需注册一个类型 + 一张贴图，
 * 不用再写一个类。
 *
 * <h2>必须覆写 {@link #getRenderType()}</h2>
 * {@code TextureSheetParticle} 的父类 {@code SingleQuadParticle} 默认返回
 * {@code ParticleRenderType.TERRAIN_SHEET}，那是<b>方块图集</b>的着色器。我们的贴图在
 * {@code minecraft:particles} 图集里，用默认值会去方块图集里采样，结果是花屏或缺失贴图。
 * 改成 {@code PARTICLE_SHEET_TRANSLUCENT} 既换对了图集，也打开了 alpha 混合——
 * 本类靠 {@link #alpha} 淡出，没有混合就看不到效果。
 *
 * <p>{@code hasPhysics} 关掉：粒子穿墙飘，不会撞在方块上停住（爆炸点埋在方块里时尤其明显）。
 */
public final class FrostMoteParticle extends TextureSheetParticle {

    private final float baseAlpha;

    private FrostMoteParticle(ClientLevel level, double x, double y, double z,
                              double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites,
                              float quadSize, float baseAlpha, int minLifetime, int maxLifetime,
                              float gravity) {
        // 用三参数的那个父类构造器，速度自己赋值——六参数的那个会混进随机抖动，不好预期
        super(level, x, y, z);
        this.baseAlpha = baseAlpha;
        // 每个粒子随机一点大小，一片同尺寸的粒子看起来像贴纸
        this.quadSize = quadSize * (this.random.nextFloat() * 0.5F + 0.75F);
        this.lifetime = minLifetime + this.random.nextInt(Math.max(1, maxLifetime - minLifetime + 1));
        this.gravity = gravity;
        this.friction = 0.96F;
        this.hasPhysics = false;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.alpha = baseAlpha;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        // 生命越接近尽头越透明，避免粒子"啪"地消失
        this.alpha = this.baseAlpha * (1.0F - (float) this.age / (float) this.lifetime);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * 造一个给 {@code RegisterParticleProvidersEvent#registerSpriteSet} 用的 provider。
     *
     * @param quadSize    粒子边长（格）。参考：父类默认约 0.1~0.2
     * @param baseAlpha   出生时的不透明度（之后线性淡出到 0）
     * @param minLifetime 最短存活 tick 数
     * @param maxLifetime 最长存活 tick 数
     * @param gravity     重力：0 悬浮、正数下沉、负数上飘
     */
    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, float quadSize,
                                                               float baseAlpha, int minLifetime,
                                                               int maxLifetime, float gravity) {
        return (type, level, x, y, z, xSpeed, ySpeed, zSpeed) ->
                new FrostMoteParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites,
                        quadSize, baseAlpha, minLifetime, maxLifetime, gravity);
    }
}
