package com.frnc.spore_add.sound;

import com.frnc.spore_add.SporeAdd;

import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本 mod 的声音事件。
 *
 * <h2>为什么需要自己注册，而不是直接用原版音效</h2>
 * 隐藏式字幕（辅助功能里的那个）显示的不是音频文件，而是<b>声音事件挂在
 * {@code sounds.json} 里的 {@code subtitle} 字段</b>：
 * {@code SoundManager} 读出它、包成 {@code Component.translatable}，
 * 再由 {@code SubtitleOverlay} 画到屏幕上。
 *
 * <p>所以"换个音效"和"换个字幕"是两件事：二次爆炸原先直接播原版的
 * {@code SoundEvents.GLASS_BREAK}，字幕就只能是它自带的 {@code subtitles.block.generic.break}
 * （"方块破坏声"）——一颗冰霜炸弹二次爆发却报"方块破坏声"，对不上。
 * 要给它自己的名字，就只能定义自己的声音事件。
 *
 * <h2>音频文件不必自己带</h2>
 * {@code sounds.json} 里可以用 {@code "type": "file"} 引用<b>任意命名空间</b>下的音频文件
 * （{@code Sound} 的构造器直接 {@code new ResourceLocation(path)}，不做命名空间校验），
 * 所以这里指向原版的 {@code minecraft:random/glass1..3}，沿用原本的碎裂声、只换字幕。
 * 这样既不用为一句字幕去塞几个 ogg，资源包替换了原版音效时也照常跟着走。
 *
 * <h2>顺带补上 Spore 自己漏掉的 7 个字幕键</h2>
 * Spore 的 {@code sounds.json} 里有 7 个 {@code subtitle} 值在它自己的语言文件里<b>没有对应条目</b>：
 * <pre>
 *   sounds.spore.engine / gast_ambient / nuke / saw_sound / spit / surgery / tumor
 * </pre>
 * 于是这几句字幕会把<b>原始键名</b>直接画到屏幕上（实测 {@code sounds.spore.spit}）。
 *
 * <p>补它们不需要碰 Spore 的文件：语言键是<b>全局</b>的——客户端会把所有命名空间下的
 * {@code lang/*.json} 合并成一张表再查，所以本 mod 的语言文件（{@code assets/spore_add/lang/}）
 * 直接定义这几个 {@code sounds.spore.*} 键就能生效。
 *
 * <p>代价是<b>可能盖住 Spore 未来的修正</b>：两边都定义同一个键时，后加载的那份生效。
 * 所以哪天 Spore 自己补上了这几句，就该把本 mod 语言文件里的这 7 行删掉。
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SporeAdd.MOD_ID);

    /** 发射。 */
    public static final RegistryObject<SoundEvent> FROST_NOVA_LAUNCH =
            SOUND_EVENTS.register("frost_nova_launch",
                    () -> SoundEvent.createVariableRangeEvent(SporeAdd.id("frost_nova_launch")));

    /** 蓄力中（每几 tick 一声，音调随蓄力升高）。字幕是"已就绪"。 */
    public static final RegistryObject<SoundEvent> FROST_NOVA_CHARGING =
            SOUND_EVENTS.register("frost_nova_charging",
                    () -> SoundEvent.createVariableRangeEvent(SporeAdd.id("frost_nova_charging")));

    /** 二次爆炸前的倒计时提示音。 */
    public static final RegistryObject<SoundEvent> FROST_NOVA_IMMINENT =
            SOUND_EVENTS.register("frost_nova_imminent",
                    () -> SoundEvent.createVariableRangeEvent(SporeAdd.id("frost_nova_imminent")));

    /** 二次爆炸。 */
    public static final RegistryObject<SoundEvent> FROST_NOVA_SECONDARY =
            SOUND_EVENTS.register("frost_nova_secondary",
                    () -> SoundEvent.createVariableRangeEvent(SporeAdd.id("frost_nova_secondary")));

    /** 「冰雪的叹息」被冰霜新星激活。低沉的嗡鸣——"核弹已就绪"的提示。 */
    public static final RegistryObject<SoundEvent> FROST_SIGH_ARMED =
            SOUND_EVENTS.register("frost_sigh_armed",
                    () -> SoundEvent.createVariableRangeEvent(SporeAdd.id("frost_sigh_armed")));

    /** 倒计时中的心跳。频率随倒计时加快。 */
    public static final RegistryObject<SoundEvent> FROST_SIGH_COUNTDOWN =
            SOUND_EVENTS.register("frost_sigh_countdown",
                    () -> SoundEvent.createVariableRangeEvent(SporeAdd.id("frost_sigh_countdown")));

    /** 即将爆发（最后 15 秒）。 */
    public static final RegistryObject<SoundEvent> FROST_SIGH_IMMINENT =
            SOUND_EVENTS.register("frost_sigh_imminent",
                    () -> SoundEvent.createVariableRangeEvent(SporeAdd.id("frost_sigh_imminent")));

    /** 已经爆发。 */
    public static final RegistryObject<SoundEvent> FROST_SIGH_DETONATED =
            SOUND_EVENTS.register("frost_sigh_detonated",
                    () -> SoundEvent.createVariableRangeEvent(SporeAdd.id("frost_sigh_detonated")));

    /** 冰封生物的冰壳被打碎。沿用的是原版的玻璃碎裂声，只换字幕。 */
    public static final RegistryObject<SoundEvent> FROST_SIGH_ICE_SHATTER =
            SOUND_EVENTS.register("frost_sigh_ice_shatter",
                    () -> SoundEvent.createVariableRangeEvent(SporeAdd.id("frost_sigh_ice_shatter")));

    private ModSounds() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
