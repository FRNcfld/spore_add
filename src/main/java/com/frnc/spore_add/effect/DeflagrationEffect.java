package com.frnc.spore_add.effect;

import java.util.function.Consumer;

import com.frnc.spore_add.client.NumeralEffectExtensions;
import com.frnc.spore_add.network.ModNetwork;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

/**
 * 「爆燃」buff 本体。
 *
 * <p>它自己只负责两件事：把等级同步给客户端（供图标上的阿拉伯数字使用），以及在离开能灭火的环境后
 * 把携带者重新点着。伤害加成不在这里，而在 {@code ModEvents#onLivingHurt}——那里才能改到伤害数值本身。
 *
 * <h2>amplifier 恒为 0，这是有意的</h2>
 * 层数存在 {@link BuffLevels} 里而不是 amplifier（原因见那个类）。amplifier 保持 0 还有个附带好处：
 * 原版就不会在这条 buff 上画它自己的罗马数字等级，那个位置留给客户端自绘的阿拉伯数字
 * （见 {@link NumeralEffectExtensions}）。
 */
public class DeflagrationEffect extends MobEffect {

    /** buff 颜色，用于粒子与 buff 排序。取高温的琥珀色，与图标的白黄→青蓝火柱呼应。 */
    private static final int COLOR = 0xFFFFC24D;

    public DeflagrationEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /**
     * 注入客户端渲染扩展。只在物理客户端被调用（{@code MobEffect} 的 {@code initClient} 里有 dist 判断），
     * 所以这里可以安全地引用客户端类。
     */
    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new NumeralEffectExtensions());
    }

    /**
     * <b>必须覆写。</b>{@code MobEffect} 的默认实现只认原版那几个效果（再生、中毒、凋零……），
     * 对自定义效果一律返回 {@code false}；不覆写的话 {@link #applyEffectTick} 永远不会被调用，
     * 而且不报错、只是静默失效。
     *
     * <p>每秒一次（20 tick）就够了：重燃只需要跟上剩余时长，等级同步也不需要更高频率。
     */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    /**
     * 需求 4：爆燃只放大火焰伤害，本身不维持火焰——所以雨天、水里火焰照常被扑灭，
     * 但爆燃 buff 不受影响；一旦离开能灭火的环境，这里负责按<b>爆燃剩余的持续时间</b>把携带者重新点着。
     *
     * <p>{@code setSecondsOnFire} 只会抬高火焰刻数、不会压低，所以每秒调用一次就能让火焰时长
     * 一直跟随爆燃的剩余时长，而不会干扰更强或更久的既有火焰。
     */
    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) {
            // 客户端也会 tick buff，但状态以服务端为准，这里什么都不做
            return;
        }

        MobEffectInstance self = entity.getEffect(this);
        if (self == null) {
            return;
        }

        // 等级走自己的同步包，vanilla 那条只带 amplifier 的通道用不上
        ModNetwork.syncLevels(entity);

        if (entity.fireImmune() || entity.isInWaterRainOrBubble() || entity.isOnFire()) {
            return;
        }

        // 需求 4：重燃时长 = 爆燃剩余持续时间。至少要 1 秒，否则剩余不足 20 tick 时会被 setSecondsOnFire 当 0 忽略
        entity.setSecondsOnFire(Math.max(1, self.getDuration() / 20));
    }
}
