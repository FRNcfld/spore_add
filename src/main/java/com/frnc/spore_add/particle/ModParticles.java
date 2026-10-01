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

    private ModParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
