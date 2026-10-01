package com.frnc.spore_add.fluid;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.block.ModBlocks;
import com.frnc.spore_add.item.ModItems;

import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 三种流体的注册表：冷却液、液态寒冷、高能燃料。
 *
 * <p>每种流体在注册表里占四个位置——{@code FluidType}（它是什么液体）、静置 {@code Fluid}、
 * 流动 {@code Fluid}（这两个是"怎么流"）、以及配套的方块与桶（分别在 {@link ModBlocks} /
 * {@link ModItems} 里）。
 *
 * <h2>为什么这三种流体都"与水一致"</h2>
 * 它们的 {@code FluidType} 属性与流动参数都对齐原版水：密度 1000、粘稠度 1000、温度 300、
 * {@code motionScale} 0.014，流动参数 slopeFindDistance / levelDecreasePerBlock / tickRate 取
 * {@code WaterFluid} 的 4 / 1 / 5。因此游泳、浮力、溺水、划船、灭火、摔落免疫、湿润耕地这些行为
 * 都和水一样——<b>这一整套在 Forge 1.20.1 里是由 {@code FluidType} 驱动的，不依赖任何标签</b>
 * （{@code Entity.updateInWaterStateAndDoFluidPushing}、{@code LivingEntity.travel}、
 * {@code ForgeHooks.onLivingBreathe} 都遍历所有 {@code FluidType}）。
 *
 * <h2>刻意不加 {@code minecraft:fluids/water} 标签</h2>
 * 这是本文件最容易被人"顺手改坏"的地方，务必注意：<b>这三种流体不要加进 water 标签。</b>
 *
 * <p>原因与视野效果直接相关：本 mod 自己会给每种流体做<b>与流体同色</b>的两层效果——一层全屏滤镜
 * 和一层雾（都见 {@link SporeFluidType}）。而 {@code FluidTags.WATER} 会同时引入<b>原版</b>的两层水效果——
 * {@code FogRenderer} 的 {@code FogType.WATER} 分支（蓝色雾，且雾色取生物群系水色）和
 * {@code ScreenEffectRenderer} 里的 {@code underwater.png} 遮罩（蓝色，由 {@code isEyeInFluid(FluidTags.WATER)}
 * 触发）。那两层的颜色是原版写死的水蓝，一旦入标签就会叠在我们的滤镜和雾色上，
 * 把三种流体各自的颜色糊成一样的水蓝。
 *
 * <p>所以：<b>视野效果只由我们自己这两层提供，原版那两层必须保持不可达。</b>
 *
 * <p>注意这与一些教程的写法相反（例如教程站的 C-33 会把流体加进 water 标签，理由是"让玩家能浮起来"）。
 * 在 Forge 1.20.1 里那个理由不成立——浮力由 {@code FluidType} 提供，不需要标签，详见上面那节。
 *
 * <p>代价：{@code Entity#isInWater()} 对这三种流体返回 {@code false}，所以依赖该判断的原版 AI
 * （如溺尸）和模组代码不会认为玩家"在水里"。这属于预期行为。
 *
 * @see SporeFluidType 那里是"在流体里看到什么"的具体实现（滤镜 + 雾）
 */
public final class ModFluids {

    /** 流体类型注册表。注意键是 {@code ForgeRegistries.Keys.FLUID_TYPES}，不是 {@code ForgeRegistries.FLUID_TYPES}。 */
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, SporeAdd.MOD_ID);

    /** 流体注册表。 */
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, SporeAdd.MOD_ID);

    private ModFluids() {
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    // ------------------------------------------------------------------
    // 颜色（ARGB）。贴图用的是原版水贴图，而它是纯灰阶的，所以玩家看到的颜色就是这个值。
    // ------------------------------------------------------------------

    /** 冷却液：天蓝色，不透明。 */
    private static final int COOLANT_TINT = 0xFF4FC3F7;

    /**
     * 液态寒冷：冰青色。
     *
     * <p>刻意不取偏白/偏灰的浅蓝：实体颜色是 tint × 原版水贴图，而那张贴图是灰阶、平均亮度只有
     * 0.68，所以 tint 里 R 越高、实体渲染出来越"洗过"。原来的 {@code 0xFFB3E5FC}（R=179）渲染成
     * 发灰的 {@code ≈(120,153,169)}，看着不像冰；压掉 R 换成偏青的取值之后才是干净的冰色
     * （实体 {@code ≈(85,155,171)}），同时与冷却液（{@code ≈(54,133,168)}）拉开层次。
     *
     * <p>桶贴图 {@code liquid_cold_bucket.png} 用的是同一色系，改动这里时那张图要跟着调。
     */
    private static final int LIQUID_COLD_TINT = 0xFF7FE3FF;

    /**
     * 高能燃料：无色透明。
     *
     * <p>白色表示不带任何色偏，alpha 取 0x66 让它明显透光——想调通透度改这一个字节即可
     * （贴图本身还有 alpha 180，两者相乘才是最终透明度）。
     */
    private static final int HIGH_ENERGY_FUEL_TINT = 0x66FFFFFF;

    // ------------------------------------------------------------------
    // 滤镜强度：浸在流体里时那层全屏滤镜的不透明度。
    // 滤镜颜色不用单独给 —— SporeFluidType 直接取上面 tint 的 RGB，只把强度换掉，
    // 这样"滤镜颜色与流体一致"是结构上保证的。
    // ------------------------------------------------------------------

    /** 有色流体的滤镜强度。原版水的水下遮罩是 0.1，这里给得明显一些，让"泡在里面"有感。 */
    private static final float TINTED_FILTER_STRENGTH = 0.30F;

    /** 高能燃料是无色的，白色滤镜本身更亮，所以压低一档免得糊成一片白。 */
    private static final float COLORLESS_FILTER_STRENGTH = 0.15F;

    // ------------------------------------------------------------------
    // 冷却液
    // ------------------------------------------------------------------

    public static final RegistryObject<FluidType> COOLANT_TYPE =
            FLUID_TYPES.register("coolant",
                    () -> new SporeFluidType(COOLANT_TINT, TINTED_FILTER_STRENGTH, waterLike("coolant")));

    public static final RegistryObject<FlowingFluid> COOLANT =
            FLUIDS.register("coolant", () -> new SporeFluid.Source(coolantProperties()));

    public static final RegistryObject<FlowingFluid> FLOWING_COOLANT =
            FLUIDS.register("flowing_coolant", () -> new SporeFluid.Flowing(coolantProperties()));

    /**
     * 冷却液的流动配置。
     *
     * <p><b>为什么是方法而不是 {@code static final} 字段</b>：这里存在一个绕不开的环——流体实例需要配置，
     * 而配置需要流体实例（作为 {@code still} / {@code flowing} 两个 supplier）。写成字段的话，
     * 无论哪个在前都会撞上"非法前向引用"（JLS §8.3.3：类变量初始化器里用<b>简单名</b>读取文本上
     * 后声明的字段是编译错误）。注意这条规则是<b>编译期</b>的，把读取放进 lambda 也没用——
     * lambda 体仍然算外层初始化器的一部分；虽然委托到运行期确实能避开顺序问题，但编译器不让过。
     *
     * <p>改成方法即可：方法体不受该限制，且真正被调用的时机是注册期（{@code FLUIDS.register} 的
     * lambda 展开时），那时所有字段都已就位。
     *
     * <p>代价是 Source 与 Flowing 会各自拿到一份配置实例（各调一次本方法）。这是无害的：
     * {@code ForgeFlowingFluid.Properties} 只是个配置载体，两份的值与 supplier 完全相同。
     */
    private static ForgeFlowingFluid.Properties coolantProperties() {
        return waterFlow(COOLANT_TYPE, COOLANT, FLOWING_COOLANT)
                .block(() -> ModBlocks.COOLANT.get())
                .bucket(() -> ModItems.COOLANT_BUCKET.get());
    }

    // ------------------------------------------------------------------
    // 液态寒冷
    // ------------------------------------------------------------------

    public static final RegistryObject<FluidType> LIQUID_COLD_TYPE =
            FLUID_TYPES.register("liquid_cold",
                    () -> new SporeFluidType(LIQUID_COLD_TINT, TINTED_FILTER_STRENGTH, waterLike("liquid_cold")));

    public static final RegistryObject<FlowingFluid> LIQUID_COLD =
            FLUIDS.register("liquid_cold", () -> new SporeFluid.Source(liquidColdProperties()));

    public static final RegistryObject<FlowingFluid> FLOWING_LIQUID_COLD =
            FLUIDS.register("flowing_liquid_cold", () -> new SporeFluid.Flowing(liquidColdProperties()));

    /** 液态寒冷的流动配置；为什么写成方法，见 {@link #coolantProperties()}。 */
    private static ForgeFlowingFluid.Properties liquidColdProperties() {
        return waterFlow(LIQUID_COLD_TYPE, LIQUID_COLD, FLOWING_LIQUID_COLD)
                .block(() -> ModBlocks.LIQUID_COLD.get())
                .bucket(() -> ModItems.LIQUID_COLD_BUCKET.get());
    }

    // ------------------------------------------------------------------
    // 高能燃料
    // ------------------------------------------------------------------

    public static final RegistryObject<FluidType> HIGH_ENERGY_FUEL_TYPE =
            FLUID_TYPES.register("high_energy_fuel",
                    () -> new SporeFluidType(HIGH_ENERGY_FUEL_TINT, COLORLESS_FILTER_STRENGTH, waterLike("high_energy_fuel")));

    public static final RegistryObject<FlowingFluid> HIGH_ENERGY_FUEL =
            FLUIDS.register("high_energy_fuel", () -> new SporeFluid.Source(highEnergyFuelProperties()));

    public static final RegistryObject<FlowingFluid> FLOWING_HIGH_ENERGY_FUEL =
            FLUIDS.register("flowing_high_energy_fuel",
                    () -> new SporeFluid.Flowing(highEnergyFuelProperties()));

    /** 高能燃料的流动配置；为什么写成方法，见 {@link #coolantProperties()}。 */
    private static ForgeFlowingFluid.Properties highEnergyFuelProperties() {
        return waterFlow(HIGH_ENERGY_FUEL_TYPE, HIGH_ENERGY_FUEL, FLOWING_HIGH_ENERGY_FUEL)
                .block(() -> ModBlocks.HIGH_ENERGY_FUEL.get())
                .bucket(() -> ModItems.HIGH_ENERGY_FUEL_BUCKET.get());
    }

    // ------------------------------------------------------------------
    // 公共配置
    // ------------------------------------------------------------------

    /**
     * 三种流体共用的 {@code FluidType} 属性：取值全部对齐原版水。
     *
     * <p>只写出与原版水不同、必须显式指定的四项，其余保持 Forge 默认值——那些默认值恰好就是水的取值，
     * 重复写一遍只会让"哪里才是真正的水差异"变得难以看清。逐项对照：
     * <ul>
     *   <li>显式指定：{@code fallDistanceModifier(0F)}（默认 0.5F）、{@code canExtinguish(true)}（默认 false）、
     *       {@code supportsBoating(true)}（默认 false）、{@code canHydrate(true)}（默认 false）。</li>
     *   <li>靠默认值即为水：{@code motionScale} 0.014、{@code density} 1000、{@code viscosity} 1000、
     *       {@code temperature} 300、{@code canSwim}/{@code canDrown}/{@code canPushEntity} 均为 true、
     *       {@code pathType} WATER / {@code adjacentPathType} WATER_BORDER。</li>
     *   <li>刻意保持默认：{@code canConvertToSource} 为 false，即不可无限刷源（见 {@link SporeFluid}）。</li>
     * </ul>
     *
     * @param name 注册名，用来拼出 {@code block.spore_add.<name>} 这个描述 id；方块和桶的 lang
     *             条目用的是同一个键，所以不必再为流体类型单独准备一条语言文件
     */
    private static FluidType.Properties waterLike(String name) {
        return FluidType.Properties.create()
                .descriptionId("block." + SporeAdd.MOD_ID + "." + name)
                .fallDistanceModifier(0F)
                .canExtinguish(true)
                .supportsBoating(true)
                .canHydrate(true);
    }

    /**
     * 三种流体共用的流动参数：4 / 1 / 5，即 {@code WaterFluid} 的取值
     * （同时也是 {@code ForgeFlowingFluid.Properties} 的默认值，这里显式写出以表明"与水一致"是刻意的）。
     *
     * <p>{@code block} 与 {@code bucket} 不在这里指定，交由各流体自己补——它们要多绕一层
     * {@code () -> ModBlocks.X.get()} 的间接，原因见下。
     */
    private static ForgeFlowingFluid.Properties waterFlow(RegistryObject<FluidType> type,
                                                         RegistryObject<FlowingFluid> source,
                                                         RegistryObject<FlowingFluid> flowing) {
        return new ForgeFlowingFluid.Properties(type, source, flowing)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .tickRate(5);
    }

    // 说明上面那句"要多绕一层间接"：.block(...) / .bucket(...) 传的是
    // () -> ModBlocks.X.get() 而不是直接传 ModBlocks.X 这个 RegistryObject。
    // 原因是 ModBlocks / ModItems 的静态初始化会反过来引用本类的 COOLANT 等字段，
    // 于是两个类谁先被初始化就可能读到对方"还没赋值完毕"的字段——那是 null，
    // 而且不会报错，只会表现为流体在世界里没有方块。包成 lambda 后，本类的静态初始化
    // 完全不碰 ModBlocks / ModItems，谁先谁后都安全。
}
