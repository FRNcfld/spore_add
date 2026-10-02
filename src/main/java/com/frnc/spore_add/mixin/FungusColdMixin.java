package com.frnc.spore_add.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.compat.SporeCompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 需求 5：「真菌受到寒冷带来的负面影响降低」。三处注入，各对应 Spore 的一处寒冷惩罚。
 *
 * <h2>Spore 原本有三处，都在 {@code Infected} 里</h2>
 * <ol>
 *   <li>{@code applyColdWeaknessEffects}——寒冷生物群系或细雪中，身上没冻伤就<b>立刻</b>补一个
 *       100 tick 的冻伤。于是真菌在寒冷里是<b>常驻</b>冻伤的：持续掉血、移动减速、屏幕结霜，
 *       还会让它因寒冷而惊慌（{@code InfectedPanicGoal} 看的是冻结刻数）。</li>
 *   <li>{@code handleStarvationProgress}——受冻时把饥饿增速从每秒 1 点提到 2 点。</li>
 *   <li>{@code isFreazing}——判断"我冷不冷"，三处惩罚（上两条 + 死亡时留冰冻残骸）共用它。</li>
 * </ol>
 *
 * <h2>做法：一律「削弱」，不「移除」</h2>
 * 三条注入分别对应三个配置项，且默认值都保留了原机制、只是幅度变小：
 * <ul>
 *   <li>冻伤改成<b>按间隔</b>补（默认 400 tick 对 100 tick 时长，占空比约 25%），不是不再补；</li>
 *   <li>寒冷加速饥饿的额外那 1 点只保留一半（默认 1.5/秒，不是 2/秒，也不是 1/秒）；</li>
 *   <li>「冷」的门槛从 0.2 收到 -0.2——雪原不再算冷，雪针叶林与冻峰照旧算。</li>
 * </ul>
 * 把任何一项配成 Spore 的原值（100 / 1.0 / 0.2）就完全回到原样。
 *
 * <h2>刻意没有动的一件事</h2>
 * {@code getEndurance}（抗寒等级）。它同时决定冻伤的伤害系数与"层数到几才生效"的门槛，
 * 改它会让真菌少挨冰冻伤害——那正好把需求 3 的冰冻伤害 ×2 抵消掉。
 * 需求 3 与 5 是两条互不相干的通路：<b>寒冷环境不再是威胁，冰霜武器才是</b>。
 *
 * <h2>三处注解为什么都写了 {@code remap = false}</h2>
 * 这里注入的四个方法名（{@code applyColdWeaknessEffects}、{@code handleStarvationProgress}、
 * {@code isFreazing}、{@code setHunger}）都是 <b>Spore 自己的</b>方法，不是原版覆写。
 * 原版成员在生产环境里会被重命名成 SRG 名（所以要写进 refmap、要 remap），
 * 而模组自己的成员不会——它们的名字在编译期与运行期完全一致。
 *
 * <p>不写这一句的话，注解处理器会去 searge 映射表里找这几个名字，找不到就<b>直接编译失败</b>
 * （{@code Unable to locate obfuscation mapping for @Inject target}）。
 * 本目录其它 mixin 之所以不用写，是因为它们注入的都是原版方法名
 * （{@code entityInside}、{@code applyEffectTick}、{@code aiStep}……），映射表里有。
 *
 * <p>目标类属于 Spore，登记在 {@code spore_add.mixins.json} 的 {@code "mixins"}（通用）列表。
 */
@Mixin(Infected.class)
public abstract class FungusColdMixin {

    /** 上次「寒冷自我施加冻伤」的时刻（绝对 tick）。见 {@link #sporeAdd$throttleAmbientFrostbite}。 */
    private static final String KEY_LAST_AMBIENT_FROSTBITE = "spore_add:cold_frostbite_tick";

    /** 饥饿小数累加器的余数。见 {@link #sporeAdd$weakenColdHunger}。 */
    private static final String KEY_HUNGER_CREDIT = "spore_add:cold_hunger_credit";

    /**
     * 一、寒冷自我冻伤：改成按间隔补，而不是"没有就立刻补"。
     *
     * <p>Spore 那段逻辑在 {@code aiStep} 里每 20 tick 跑一次：没有冻伤就补一个 100 tick 的。
     * 于是一旦进入寒冷，冻伤就是常驻的。这里在方法入口拦一道：距上次补不足
     * {@code coldFrostbiteIntervalTicks} 就直接取消——冻伤会自然到期，到期后要等够间隔才补下一个。
     *
     * <p><b>身上已经有冻伤时原样放行</b>：那说明是别处（玩家、冰霜新星、冷却液）施加的，
     * 或者是我们刚补上的。Spore 自己那段在有冻伤时本来就是空操作，放行等于什么都不做；
     * 更重要的是这时<b>不更新计时</b>——否则玩家一冻它，寒冷那边的间隔就被顺延了。
     *
     * <h2>为什么计时存 ForgeData 而不是加一个字段</h2>
     * 字段不会存盘，而 {@code tickCount} 也不存盘（载入时从 0 重计）。
     * {@code FrostbiteLevels} 里已经踩过这个坑：只比"距今多少 tick"的话，重进世界之后
     * 记录值比当前 tick 还大，差值是个大负数，"已过间隔"永远不成立，症状是"重进游戏后
     * 寒冷冻伤一直不施加，玩一阵忽然好了"。所以这里把"记录值大于当前 tick"当作过期。
     */
    @Inject(method = "applyColdWeaknessEffects", at = @At("HEAD"), cancellable = true, remap = false)
    private void sporeAdd$throttleAmbientFrostbite(CallbackInfo ci) {
        Infected self = (Infected) (Object) this;

        MobEffect frostbite = SporeCompat.frostbite();
        if (frostbite != null && self.hasEffect(frostbite)) {
            return;
        }

        CompoundTag data = self.getPersistentData();
        boolean recorded = data.contains(KEY_LAST_AMBIENT_FROSTBITE);
        int lastApply = data.getInt(KEY_LAST_AMBIENT_FROSTBITE);
        boolean stale = recorded && lastApply > self.tickCount;

        if (recorded && !stale
                && self.tickCount - lastApply < SporeAddFungusConfig.coldFrostbiteIntervalTicks()) {
            ci.cancel();
            return;
        }
        data.putInt(KEY_LAST_AMBIENT_FROSTBITE, self.tickCount);
    }

    /**
     * 二、寒冷加速饥饿：额外那一点只保留 {@code coldHungerPenaltyFactor} 的比例。
     *
     * <p>拦的是那一次 {@code setHunger}。传进来的值是"原饥饿 + 增量"，而此刻实体身上的饥饿
     * 还是原值，所以两者相减就还原出了 Spore 算的增量：<b>1 = 没吃到寒冷惩罚，2 = 吃到了</b>。
     * 只有 2 才需要动手，其余原样转交。
     *
     * <h2>为什么要一个小数累加器</h2>
     * 增量是整数，没法直接表达"保留一半"。默认 0.5 意味着两次里有一次不加速，
     * 但"随机挑一次"会让饥饿的节奏变得不可预期（玩家看到的是忽快忽慢）。
     * 这里改成把不足 1 的部分存下来累计：0.5 → 0.5 → 攒够 1 就这一次多加 0.5 的效果，
     * 于是每两次恰好有一次加速，长期均值精确是 1.5/秒，且节奏是固定的。
     *
     * <p>配成 1.0 时走的是"原样转交"那条路，一次浮点运算都不会多做。
     */
    @Redirect(
            method = "handleStarvationProgress",
            at = @At(value = "INVOKE",
                    target = "Lcom/Harbinger/Spore/Sentities/BaseEntities/Infected;setHunger(Ljava/lang/Integer;)V"),
            remap = false)
    private void sporeAdd$weakenColdHunger(Infected self, Integer value) {
        double factor = SporeAddFungusConfig.coldHungerPenaltyFactor();
        int increment = value - self.getHunger();
        if (increment <= 1 || factor >= 1.0D) {
            self.setHunger(value);
            return;
        }

        CompoundTag data = self.getPersistentData();
        double credit = data.getDouble(KEY_HUNGER_CREDIT) + factor;
        int whole = Mth.floor(credit);
        data.putDouble(KEY_HUNGER_CREDIT, credit - whole);

        // 基线那 1 点照给，只把"寒冷额外多给的那点"按比例折出来
        self.setHunger(self.getHunger() + 1 + whole);
    }

    /**
     * 三、「冷」的门槛收紧。
     *
     * <p>{@code isFreazing} 的原判据是 {@code weaktocold && 生物群系温度 <= 0.2}。
     * 这里只在它<b>返回真</b>的时候接一手（此时"weaktocold 为真且温度 <= 0.2"已经成立），
     * 再用更严的门槛复核一次，不满足就改判 false。雪原的温度是 0.0，在默认门槛 -0.2 下不再算冷；
     * 雪针叶林 / 冰刺之地（-0.5）与冻峰（-0.7）照旧算。
     *
     * <p>所以"削弱而非移除"是门槛这个形式自带的：越冷的地方真菌照样难受，只是范围小了。
     * 配置填回 0.2 时，{@code 温度 > 0.2} 在本分支里永不可能成立，等价于没装这一段。
     *
     * <p>连带效果（不需要额外代码）：{@code InfectedPanicGoal} 因寒冷惊慌、死亡时留冰冻残骸，
     * 都跟着这个判据一起变少。
     *
     * <p>客户端也会跑到这里：{@code BaseInfectedRenderer} 会调它来决定冻伤的外观表现。
     * 这是好事——外观与实际判定用的是同一个判据，不会出现"看着冻僵了但其实没冷"。
     */
    @Inject(method = "isFreazing", at = @At("RETURN"), cancellable = true, remap = false)
    private void sporeAdd$tightenColdThreshold(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }
        Infected self = (Infected) (Object) this;
        Biome biome = self.level().getBiome(self.blockPosition()).value();
        if (biome.getBaseTemperature() > SporeAddFungusConfig.coldBiomeThreshold()) {
            cir.setReturnValue(false);
        }
    }
}
