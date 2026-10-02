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
 * <p>本 mod 面向 Minecraft 1.20.1 / Forge，用于扩展与修改真菌类玩法（蘑菇、菌丝、孢子等相关机制）。
 *
 * <p>目前已接入的内容：
 * <ul>
 *   <li><b>三种流体</b>（冷却液 / 液态寒冷 / 高能燃料），见 {@code fluid}、{@code block}、{@code item}
 *       三个子包；三种流体都刻意不加进 {@code minecraft:fluids/water} 标签，原因写在 {@link ModFluids}
 *       的类注释里。</li>
 *   <li><b>高能燃料 → 可燃 → 爆燃</b> 这条机制链：泡在燃料里会积累 Spore 的「可燃」，可燃被触发时
 *       转化为本 mod 的「爆燃」buff 并逐次累加层数，每层放大火焰伤害。入口在 {@code ModEvents}，
 *       两个等级（可燃与爆燃）的存取见 {@code BuffLevels}。</li>
 *   <li><b>「冰霜新星」</b>：长按右键蓄力、松手发射一枚无重力直线飞行的弹体，落点处把球内的方块
 *       换成蓝冰/浮冰、给球内的生物叠冻伤。物品见 {@code FrostNovaItem}，实体见 {@code FrostNovaEntity}，
 *       爆发见 {@code FrostNovaBlast}。</li>
 *   <li><b>可配置项</b>：冰霜新星的四个威力参数与液态寒冷的影响半径都在
 *       {@code config/spore_add-player-common.toml} 里，见 {@link SporeAddPlayerConfig}。</li>
 * </ul>
 * 其余玩法尚未实现。后续约定：
 * <ul>
 *   <li>方块 / 物品 / 实体等注册：在本类里建 {@code DeferredRegister}，在构造函数中
 *       {@code register(modEventBus)}；内容分别放到 {@code block} / {@code item} / {@code entity}
 *       等子包。</li>
 *   <li>游戏事件：{@code MinecraftForge.EVENT_BUS.register(this)} 已就绪，需要时在本类或子包里
 *       用 {@code @SubscribeEvent} 补监听方法。</li>
 *   <li>需要改写原版行为时走 mixin：把类加进 {@code spore_add.mixins.json} 的 {@code mixins} 列表
 *       （构建脚本里的 MixinGradle 管线已经接好）。</li>
 * </ul>
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

        // 两份配置，**都显式给文件名**。
        //
        // 为什么不省事用默认名：Forge 默认按 "modId-类型" 拼文件名，两个 COMMON 会算出同一个
        // spore_add-common.toml，而 ConfigTracker 撞名会直接抛 "Config conflict detected!" 崩游戏。
        // 显式给名字之后，两份都落在 config/ 下、都是全局文件，也不会撞。
        //
        // 分两份是按「谁受益」：玩家那一份放让人更强的东西（冰霜武器、恨意值给玩家的增益、HUD），
        // 真菌那一份放让怪更强的东西（真菌加强、恨意值系统、减伤、资源、袭击）。
        // 于是「想调难度」与「想调手感」各看一份，不必来回对照。详见两个配置类的类注释。
        //
        // 注册必须在这里：Forge 会在模组加载期读文件（生成默认值），而所有取值都发生在游戏运行期，
        // 所以不存在"配置还没读就取值"的问题。
        context.registerConfig(ModConfig.Type.COMMON, SporeAddPlayerConfig.SPEC, SporeAddPlayerConfig.FILE_NAME);
        context.registerConfig(ModConfig.Type.COMMON, SporeAddFungusConfig.SPEC, SporeAddFungusConfig.FILE_NAME);

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
