package com.frnc.spore_add.mixin;

import com.Harbinger.Spore.Sentities.Organoids.Womb;
import com.frnc.spore_add.raid.RaidManager;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 袭击期间给灾厄重构体（Womb）的两项加成。
 *
 * <p>需求原文（袭击第 1 条）针对的是<b>心智</b>的制造速度，Womb 不是心智，
 * 那项加成到不了它——这是单独接上去的扩展，可用
 * {@code SporeAddFungusConfig#wombRaidBonusesEnabled} 整体关掉。接的是两条已有倍率，不新增数值。
 *
 * <table border="1">
 *   <caption>两项与注入点</caption>
 *   <tr><th>要动的东西</th><th>Spore 里的落点</th><th>怎么改</th></tr>
 *   <tr><td>孵化节拍</td><td>{@code tick()} 里读 {@code recontructor_clock} 的那次</td><td>改读到的秒数</td></tr>
 *   <tr><td>同化收益</td><td>{@code calculateAssimilation(Entity)} 的返回值</td><td>改返回值</td></tr>
 * </table>
 *
 * <h2>为什么孵化节拍要绕道 {@code @Redirect}，而不是直接改那个 20</h2>
 * {@code Womb#tick} 里有<b>两个</b> {@code bipush 20}：偏移 75 是 {@code recontructor_clock × 20}
 * （孵化节拍），偏移 200 是 {@code random.nextInt(20)}（播放吃东西音效的掷骰）。
 * 按理该用 {@code @ModifyConstant(intValue = 20)} + {@code @Slice} 把它俩区分开，但这条路走不通：
 * <ul>
 *   <li>Mixin 0.8.5 的 {@code @ModifyConstant} <b>没有 {@code ordinal}</b> 参数（已 javap 核对注解本身），
 *       所以没法"取第一个匹配"；</li>
 *   <li>{@code @Slice} 的 {@code to} 边界需要落在两个 20 之间（偏移 78~200），
 *       而那一段里全是原版调用（{@code SynchedEntityData.get/set}、{@code Integer.valueOf}），
 *       拿它们当 {@code @At} target 就必须 remap——而 {@code method} 写的是 Spore 的 {@code tick}，
 *       必须 remap=false。一个注解只有一个开关，覆盖不了两者（这就是本项目那条最反复的约束）。</li>
 * </ul>
 * 按数值匹配又会两个 20 一起改，顺带把音效播放频率也乘 6——那是没人要的副作用。
 *
 * <p>所以改的是<b>唯一</b>那个读取点：{@code recontructor_clock} 这个 Forge 配置项的 {@code get()}。
 * Forge 自己的成员不参与重命名（见 {@code FungusColdMixin} 类注释里说的那条），
 * 所以 {@code ConfigValue.get()} 可以安全地用 {@code remap = false} 重定向；
 * {@code @Slice(from = GETFIELD recontructor_clock)} 之后，整个 {@code tick} 里只剩这一次
 * {@code ConfigValue.get()}，定位是唯一的。
 *
 * <p>另一条更省事的路是「多点孵化节拍被算出来那一步」，但整个加成必须落在
 * {@code RaidManager} 里（与其余 {@code adjust*} 保持一致，配置类只管存值）——
 * 这也是这里绕一下的原因。
 */
@Mixin(Womb.class)
public abstract class WombRaidMixin {

    /**
     * 孵化节拍：袭击期间把 {@code recontructor_clock} 读到的秒数折算短。
     *
     * <p>{@code ConfigValue.get()} 的擦除返回类型是 {@code Object}，所以处理器也返回 {@code Object}
     * （Spore 那边紧跟一句 {@code checkcast Integer}，装箱值能过）。
     */
    @Redirect(
            method = "tick",
            slice = @Slice(from = @At(value = "FIELD",
                    target = "Lcom/Harbinger/Spore/Core/SConfig$Server;recontructor_clock:Lnet/minecraftforge/common/ForgeConfigSpec$ConfigValue;")),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/common/ForgeConfigSpec$ConfigValue;get()Ljava/lang/Object;"),
            remap = false)
    private Object sporeAdd$fasterHatchClock(ForgeConfigSpec.ConfigValue<?> value) {
        return RaidManager.adjustWombClock((Integer) value.get());
    }

    /**
     * 同化收益：袭击期间放大 {@code calculateAssimilation} 的返回值。
     *
     * <p>它有三个 return（融合体 ×4、进化体 ×2、普通 ×1），所以在 RETURN 处统一改，
     * 而不是去改 {@code reconstructor_assimilation} 那个基数——那样会漏掉两档倍率分支。
     */
    @Inject(method = "calculateAssimilation", at = @At("RETURN"), cancellable = true, remap = false)
    private void sporeAdd$richerAssimilation(Entity entity, CallbackInfoReturnable<Integer> cir) {
        // 用 getReturnValueI() 而不是 getReturnValue()：int 方法走原始类型访问器，
        // 免得在装箱/拆箱上留下歧义（两者都存在，前者是这个场景的规范用法）。
        cir.setReturnValue(RaidManager.adjustWombAssimilation(cir.getReturnValueI()));
    }
}
