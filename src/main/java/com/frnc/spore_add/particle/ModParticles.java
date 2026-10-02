package com.frnc.spore_add.particle;

import com.frnc.spore_add.SporeAdd;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本 mod 的粒子类型注册表。三个都是"带贴图的普通粒子"，所以都用 {@link SimpleParticleType}
 * （它没有额外参数，只靠贴图区分）。
 *
 * <p>贴图来自「Iron 的法术与魔法书：艾尔登法环」的粒子库（MIT），见
 * {@code textures/particle/} 下的署名文件。
 *
 * <h2>光注册类型还不够，还要两样东西</h2>
 * <ol>
 *   <li><b>服务端的贴图定义</b>：{@code assets/spore_add/particles/<名字>.json}，内容是
 *       {@code {"textures": ["spore_add:<名字>"]}}。注意这里的贴图名<b>不带 {@code particle/} 前缀</b>——
 *       粒子的图集是 {@code minecraft:particles}，它的来源是一个不限命名空间的 directory 扫描，
 *       所以 {@code assets/spore_add/textures/particle/frost_mist.png} 在图集里的名字就是
 *       {@code spore_add:frost_mist}。写成 {@code spore_add:particle/frost_mist} 会静默变成缺失贴图。</li>
 *   <li><b>客户端的渲染器</b>：{@code SporeAddClient#onRegisterParticleProviders} 里注册，
 *       见 {@link com.frnc.spore_add.client.particle.FrostMoteParticle}。</li>
 * </ol>
 */
public final class ModParticles {

    /** 粒子类型注册表。 */
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, SporeAdd.MOD_ID);

    /** 云雾：冰霜新星落点那团持续 10 秒的粒子云的主体。 */
    public static final RegistryObject<SimpleParticleType> FROST_MIST =
            PARTICLES.register("frost_mist", () -> new SimpleParticleType(false));

    /** 雪花：粒子云里的点缀，颗粒更小、飘得更慢。 */
    public static final RegistryObject<SimpleParticleType> FROST_SNOWFLAKE =
            PARTICLES.register("frost_snowflake", () -> new SimpleParticleType(false));

    /** 冰屑：弹体拖尾与命中瞬间的爆发。 */
    public static final RegistryObject<SimpleParticleType> FROST_SHARD =
            PARTICLES.register("frost_shard", () -> new SimpleParticleType(false));

    // ------------------------------------------------------------------
    // 「冰雪的叹息」（核弹）用的一组
    // ------------------------------------------------------------------
    //
    // 与上面三个刻意区分开：上面是**浅青**（色相 192~196、明度 60~76），
    // 下面是**深靛蓝**（色相 229~256、明度 23~44）。色相与明度都有明确分界，
    // 玩家一眼就能分出"这是新星"还是"那是核弹"。素材同样来自那个 MIT 粒子库。

    /** 激活期的粒子云与冲击环。浓重紫蓝雾团。 */
    public static final RegistryObject<SimpleParticleType> FROST_SIGH_CLOUD =
            PARTICLES.register("frost_sigh_cloud", () -> new SimpleParticleType(false));

    /**
     * 激活期那团雾的<b>主体</b>：冰霜新星那团霜雾的同一个素材，只是重新上色成深蓝。
     *
     * <h2>为什么是"重上色"而不是另找一个素材</h2>
     * 需求要的是"和新星一样的冰雾特效，但要更浓烈、颜色深蓝"。素材库里没有现成的深蓝雾团，
     * 但把 {@code frost_mist} 的色相挪到 232°、饱和度顶到 0.85、明度压在 0.24~0.58，
     * 得到的就是**同一张图**的深蓝版本——形状、颗粒感一模一样，只有颜色不同。
     * 这正是"一样、只是深蓝"的字面实现。（这张是派生素材，署名见 THIRD-PARTY-NOTICES。）
     *
     * <p>"更浓烈"由两处给：这里的尺寸与不透明度都比新星那档大（见 {@code SporeAddClient}），
     * 以及倒计时每拍撒的数量。{@code FROST_SIGH_CLOUD} 仍然留给蘑菇云的茎用。
     */
    public static final RegistryObject<SimpleParticleType> FROST_SIGH_MIST =
            PARTICLES.register("frost_sigh_mist", () -> new SimpleParticleType(false));

    /**
     * 与 {@link #FROST_SIGH_MIST} 配对的雪花：{@code frost_snowflake} 的深蓝重上色版。
     *
     * <p>冰霜新星那团雾是"{@code FROST_MIST} 为主、{@code FROST_SNOWFLAKE} 点缀"的一对
     * （比例约 18:4）。核弹这边要"同款但更深"，所以两个都换成深蓝版本，组成也一样。
     */
    public static final RegistryObject<SimpleParticleType> FROST_SIGH_FLAKE =
            PARTICLES.register("frost_sigh_flake", () -> new SimpleParticleType(false));

    /** 蘑菇云主体。填充率最高（0.66）的一团。 */
    public static final RegistryObject<SimpleParticleType> FROST_SIGH_HAZE =
            PARTICLES.register("frost_sigh_haze", () -> new SimpleParticleType(false));

    /** 中心最浓郁处。近黑的深蓝（明度 23），用来做"核心"的观感。 */
    public static final RegistryObject<SimpleParticleType> FROST_SIGH_CORE =
            PARTICLES.register("frost_sigh_core", () -> new SimpleParticleType(false));

    /** 爆发瞬间的闪光。 */
    public static final RegistryObject<SimpleParticleType> FROST_SIGH_FLARE =
            PARTICLES.register("frost_sigh_flare", () -> new SimpleParticleType(false));

    /**
     * 降雪。
     *
     * <p>这一个<b>故意用浅色</b>——它是雪，不是核弹的烟。素材取自同一个库里最浅的一档雪粒。
     */
    public static final RegistryObject<SimpleParticleType> FROST_SIGH_SNOW =
            PARTICLES.register("frost_sigh_snow", () -> new SimpleParticleType(false));

    /**
     * 影响范围边界上的警示粒子。
     *
     * <p><b>这个是红橙色（色相约 13°），刻意不跟蓝色系走。</b>它要标的是"这条线以内都会被冻住"，
     * 而深蓝的核弹粒子已经铺满了整个范围——再给边界配一个近蓝的颜色就分不出来了。
     * 暖色在这个冷色系里跳出来，才有警示的意思。
     */
    public static final RegistryObject<SimpleParticleType> FROST_SIGH_WARNING =
            PARTICLES.register("frost_sigh_warning", () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
