package com.frnc.spore_add;

import com.frnc.spore_add.advancement.ModTriggers;
import com.frnc.spore_add.block.ModBlockEntities;
import com.frnc.spore_add.block.ModBlocks;
import com.frnc.spore_add.effect.ModEffects;
import com.frnc.spore_add.enchantment.ModEnchantments;
import com.frnc.spore_add.entity.ModEntities;
import com.frnc.spore_add.fluid.ModFluidInteractions;
import com.frnc.spore_add.fluid.ModFluids;
import com.frnc.spore_add.item.ModCreativeTabs;
import com.frnc.spore_add.item.ModItems;
import com.frnc.spore_add.network.ModNetwork;
import com.frnc.spore_add.particle.ModParticles;
import com.frnc.spore_add.sound.ModSounds;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Spore Add 主入口。
 *
 * <p>本 mod 面向 Minecraft 1.20.1 / Forge，围绕 Spore（真菌感染：孢子）的真菌机制做修改与新增。
 * 内容按「谁受益」分成两条线：**加强真菌**，以及**给玩家反制手段**。
 *
 * <p>已接入的内容（按子包）：
 * <ul>
 *   <li><b>冰霜武器</b>（玩家侧）——{@code fluid} / {@code block} / {@code item} / {@code entity} /
 *       {@code world}：三种流体（冷却液 / 液态寒冷 / 高能燃料）、可蓄力的「冰霜新星」、
 *       核弹方块「冰雪的叹息」。详情与全部数值见随 jar 分发的 {@code docs/values-and-formulas.md}。</li>
 *   <li><b>高能燃料 → 可燃 → 爆燃</b>这条机制链：泡在燃料里积累 Spore 的「可燃」，可燃被触发时
 *       转化为本 mod 的「爆燃」并按层放大火焰伤害。入口在 {@code ModEvents}，层数存取见
 *       {@code effect/BuffLevels}。</li>
 *   <li><b>恨意值系统</b>（{@code hatred} / {@code command}）：击杀真菌累积个人恨意值，
 *       越档会掷骰触发袭击；个人恨意值给玩家增益，世界恨意值（含离线玩家）给真菌减伤。
 *       改值的唯一入口是 {@code HatredManager}。</li>
 *   <li><b>真菌袭击</b>（{@code raid}）：阶段机 {@code PREP →（可选）ARENA → WAVES → 收场}，
 *       {@code RaidManager} 是调度中心。状态只在内存里，不存盘。</li>
 *   <li><b>心智存储</b>（{@code hivemind}）：把心智造出来的生物收进存储而非放进世界，
 *       袭击期间按波投放；穹顶被砸时会漏出一部分。</li>
 *   <li><b>灾厄重构体</b>（{@code fungus/WombHatch} + {@code mixin/WombRaidMixin}）：
 *       同化突变加速、孵化后送出壳外。</li>
 *   <li><b>拾荒者</b>（{@code scavenger}）：菌染人类的变体，不参战、专职捡掉落物供给同伙，
 *       随存活时长成长，且被完全排除在消失之外。</li>
 *   <li><b>真菌加强</b>（{@code fungus}）：进化加速、攻击一切生物、冰冻伤害倍率、感知范围、抗寒。</li>
 * </ul>
 *
 * <h2>三份配置</h2>
 * 玩家侧 {@code config/spore_add-player-common.toml}（{@link SporeAddPlayerConfig}）、
 * 真菌侧 {@code config/spore_add-fungus-common.toml}（{@link SporeAddFungusConfig}）、
 * 以及调试侧的 {@code config/spore_add-debug-common.toml}（{@link SporeAddDebugConfig}）。
 * 前两份按「谁受益」分，第三份是**默认全关的检查点开关**，不参与那条分类——
 * 它既不让人更强也不让怪更强，只是排查工具，横跨两边。
 *
 * <p>表格类内容（掉落物定价、黑名单、袭击维度黑名单）走**数据包**而不是配置——
 * 分工的判据写在 {@link SporeAddFungusConfig} 的类注释里。
 *
 * <p>改写原版或 Spore 行为一律走 mixin：类加进 {@code spore_add.mixins.json} 的 {@code mixins}
 * 列表（MixinGradle 管线已接好）。
 *
 * <p>所有标识符都以 {@link #MOD_ID} 为命名空间，需与 {@code META-INF/mods.toml} 的 {@code modId} 一致。
 */
@Mod(SporeAdd.MOD_ID)
public class SporeAdd {

    /** 本 mod 的命名空间，需与 META-INF/mods.toml 中的 modId 一致。 */
    public static final String MOD_ID = "spore_add";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 快捷创建本 mod 的 ResourceLocation。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public SporeAdd(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // 模组加载期的初始化（注册表已可用，但世界尚未创建）
        modEventBus.addListener(this::commonSetup);

        // 三份配置，**都显式给文件名**。
        //
        // 为什么不省事用默认名：Forge 默认按 "modId-类型" 拼文件名，三个 COMMON 会算出同一个
        // spore_add-common.toml，而 ConfigTracker 撞名会直接抛 "Config conflict detected!" 崩游戏。
        // 显式给名字之后，三份都落在 config/ 下、都是全局文件，也不会撞。
        //
        // 前两份按「谁受益」分：玩家那一份放让人更强的东西（冰霜武器、恨意值给玩家的增益），
        // 真菌那一份放让怪更强的东西（真菌加强、恨意值系统、减伤、资源、袭击、心智存储）。
        // 于是「想调难度」与「想调手感」各看一份，不必来回对照。
        //
        // 第三份是调试检查点（默认全关），不参与那条分类：它横跨玩家侧与真菌侧，
        // 硬塞进任意一份都会让那份的划分标准出现一个说不通的例外。详见三个配置类的类注释。
        //
        // 注册必须在这里：Forge 会在模组加载期读文件（生成默认值），而所有取值都发生在游戏运行期，
        // 所以不存在"配置还没读就取值"的问题。注意这一条对检查点是**硬要求**——
        // Forge 的 ConfigValue#get() 在开发环境里配置未加载时会抛，见 SporeAddDebugConfig#enabled。
        context.registerConfig(ModConfig.Type.COMMON, SporeAddPlayerConfig.SPEC, SporeAddPlayerConfig.FILE_NAME);
        context.registerConfig(ModConfig.Type.COMMON, SporeAddFungusConfig.SPEC, SporeAddFungusConfig.FILE_NAME);
        context.registerConfig(ModConfig.Type.COMMON, SporeAddDebugConfig.SPEC, SporeAddDebugConfig.FILE_NAME);

        // 各部分内容各自的注册表。顺序其实无所谓（详见 ModFluids 里关于静态初始化顺序的说明），
        // 但把流体放在最前面更贴合阅读顺序：方块和桶都要引用流体的注册项。
        ModFluids.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModEffects.register(modEventBus);
        ModEnchantments.register(modEventBus);
        ModParticles.register(modEventBus);
        ModSounds.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        // 注册网络包。必须早于任何一次发包，放构造里最省心。
        ModNetwork.init();

        // 游戏运行时事件（服务端/客户端事件）走 Forge 事件总线
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("[SporeAdd] initialized");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 跨模组交互、能力注册等一次性初始化放这里。
        // 自定义进度判据：1.20.1 没有对应的注册表，只能在这里手动登记
        ModTriggers.registerAll();
        // 流体接触岩浆的反应：必须等到这一步，注册表冻结之后才取得到 FluidType 的值
        ModFluidInteractions.register();
        LOGGER.info("[SporeAdd] common setup done");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[SporeAdd] server starting");
    }
}
