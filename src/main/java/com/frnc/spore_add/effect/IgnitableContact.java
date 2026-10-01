package com.frnc.spore_add.effect;

import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.network.ModNetwork;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「接触可燃源」的统一入口：涨一级可燃、施加/刷新可燃 buff、必要时同步给客户端。
 *
 * <p>目前有两个可燃源，都调这里，所以两处的行为必然一致：
 * <ul>
 *   <li><b>本 mod 的高能燃料</b>（{@code HighEnergyFuelBlock#entityInside}）；</li>
 *   <li><b>Spore 的焦油池</b>（{@code spore:tar}）——它自己本来就会给可燃，但只给一个固定的
 *       5 秒 1 级 buff、不会累积；由 {@code TarIgnitableMixin} 在它的 {@code entityInside} 返回点
 *       补上这里的调用，焦油因此和高能燃料一样能逐秒累积。</li>
 * </ul>
 *
 * <p>两个来源都<b>由方块自己的"实体在其中"回调</b>驱动，而不是逐 tick 扫描实体——
 * 代价是调用方要自己处理"同一个 tick 被多个方块各调一次"的去重，那件事在
 * {@link BuffLevels#touchFuel} 里做掉了。
 */
public final class IgnitableContact {

    /**
     * 可燃的持续时间：10 秒。
     *
     * <p>两个来源都用这个值。注意它会覆盖 Spore 焦油原本的 100 tick（5 秒）——vanilla 在 amplifier
     * 相同时取更长的那个时长，所以泡在焦油里最终是 10 秒，与泡在高能燃料里一致。
     */
    private static final int DURATION_TICKS = 200;

    private IgnitableContact() {
    }

    /**
     * 实体这一 tick 正接触着某个可燃源。
     *
     * <p>只在等级真的变了的时候才发包：本方法每 tick 都会被调用，而等级每秒才涨一级。
     */
    public static void apply(LivingEntity entity) {
        MobEffect ignitable = SporeCompat.ignitable();
        if (ignitable == null) {
            return;
        }

        int before = BuffLevels.ignitable(entity);
        int level = BuffLevels.touchFuel(entity);
        if (level <= 0) {
            return;
        }

        // 只在等级真的变了（也就是每秒那一级）或者 buff 缺席/快到期时才施加。
        // 本方法每 tick 都会被调用，而 addEffect 会走一遍 canBeAffected 并在 Forge 总线上 post
        // MobEffectEvent.Applicable——一池燃料里泡着一群生物时，每 tick 无条件调用就是白白刷事件。
        MobEffectInstance current = entity.getEffect(ignitable);
        boolean needsRefresh = current == null || current.getDuration() < DURATION_TICKS - 20;
        if (level == before && !needsRefresh) {
            return;
        }

        // amplifier 固定 0：等级存在 BuffLevels 里，amplifier 有 127 的字节上限，扛不住无上限的等级。
        // 界面上那个数字由客户端从同步过来的数据自绘，与 amplifier 无关。
        entity.addEffect(new MobEffectInstance(ignitable, DURATION_TICKS, 0, false, true));

        if (level != before) {
            ModNetwork.syncLevels(entity);
        }
    }
}
