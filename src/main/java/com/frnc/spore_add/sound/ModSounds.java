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

    private ModSounds() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
