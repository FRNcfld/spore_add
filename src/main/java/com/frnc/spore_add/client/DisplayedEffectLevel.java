package com.frnc.spore_add.client;

import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.effect.ModEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 「某个 buff 实例的等级该显示成几」的唯一判断点。
 *
 * <p>两个 buff 的等级都不在 amplifier 里，而在服务端同步过来的 {@link ClientBuffLevelsCache} 里，
 * 所以显示代码不能直接读 {@code instance.getAmplifier()}。这里把"是不是我们管的 buff、去哪个数"收成一处，
 * 供两个地方共用：
 * <ul>
 *   <li>{@link NumeralEffectExtensions} —— HUD 与背包界面的图标、名称行；</li>
 *   <li>{@code EffectNameLevelMixin} —— 鼠标悬停时的 tooltip 名称行
 *       （原版在那里会拼上罗马数字等级，是另一处独立代码路径，必须单独接管）。</li>
 * </ul>
 */
public final class DisplayedEffectLevel {

    /** 不归本 mod 显示的效果。用 -1 而不是 0，是为了和"归我们管但等级为 0"区分开。 */
    public static final int NOT_OURS = -1;

    /**
     * 名称行里等级后缀的语言键，两个 lang 文件里各有一条（中文「%s 层」/ 英文「%s」）。
     *
     * <p>放在这里而不是各自的类里，是因为有两处代码要拼这行文案（图标渲染与 tooltip），
     * 而 Mixin 类里不该声明字段——那会被 Mixin 当成要合并进目标类的成员。
     */
    public static final String LEVEL_SUFFIX_KEY = "effect.spore_add.level_suffix";

    private DisplayedEffectLevel() {
    }

    /** 该效果是否由本 mod 接管显示。 */
    public static boolean isOurs(MobEffect effect) {
        return effect == ModEffects.DEFLAGRATION.get()
                || effect == SporeCompat.ignitable()
                || effect == SporeCompat.frostbite();
    }

    /**
     * 要显示的等级。
     *
     * @return {@link #NOT_OURS} 表示这个效果不归我们管，应当交还原版；
     *         否则是等级（可能为 0，即我们管但这个实体当前没有数据）
     */
    public static int of(MobEffectInstance instance) {
        MobEffect effect = instance.getEffect();
        if (!isOurs(effect)) {
            return NOT_OURS;
        }
        // 冻伤的层数就在它自己的 amplifier 里（Spore 读它、vanilla 也同步它），不需要我们的同步缓存
        if (effect == SporeCompat.frostbite()) {
            return instance.getAmplifier() + 1;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        // 爆燃与可燃的层数存在服务端的持久数据里，客户端只能查同步过来的缓存
        int owner = minecraft.player.getId();
        if (effect == ModEffects.DEFLAGRATION.get()) {
            return ClientBuffLevelsCache.deflagration(owner);
        }
        return ClientBuffLevelsCache.ignitable(owner);
    }

    /**
     * 拼出「可燃 12 层」这样的一行文案，供图标渲染与 tooltip 共用。
     *
     * @param level 小于等于 0 时只返回名称本身
     */
    public static Component nameWithLevel(MobEffectInstance instance, int level) {
        // 必须声明成 MutableComponent：append 只在可变实现上有，Component 接口本身没有。
        // 直接链式写 Component.translatable(...).append(...) 也能编过（返回值就是可变的），
        // 但这里要提前 return 名称本身，所以得先存进变量、因此类型不能写成 Component。
        MutableComponent name = Component.translatable(instance.getDescriptionId());
        if (level <= 0) {
            return name;
        }
        return name.append(CommonComponents.SPACE)
                .append(Component.translatable(LEVEL_SUFFIX_KEY, level));
    }
}
