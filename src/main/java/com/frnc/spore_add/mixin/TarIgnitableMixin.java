package com.frnc.spore_add.mixin;

import com.Harbinger.Spore.Sblocks.Tar;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.effect.IgnitableContact;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 Spore 的焦油池（{@code spore:tar}）也能像高能燃料那样逐秒累积可燃等级。
 *
 * <h2>焦油本来就会给可燃，缺的是什么</h2>
 * Spore 的 {@code Tar#entityInside} 已经会给站在焦油里的生物施加可燃，但给的是<b>固定的 5 秒 1 级</b>，
 * 而且只在对方还没有该 buff 时施加一次——既不会累积，也不会刷新。所以「站着不动躺赢」是不成立的。
 * 本 mixin 在它跑完之后补上 {@link IgnitableContact}，焦油因此获得与高能燃料完全相同的累积行为。
 *
 * <h2>为什么注入在返回点，而不是开头</h2>
 * 这样能<b>白捡 Spore 自己的目标闸门</b>：它的方法体里有个 {@code Utilities.TARGET_SELECTOR} 判断，
 * 会把 Spore 自家的感染体排除在外。我们不去引那个内部类，而是反过来问一句"Spore 刚给这个生物加上可燃了吗"
 * ——它加了才说明这个生物通过了它的闸门，我们才跟进。既尊重了原设计，也少了一处对 Spore 内部的依赖。
 *
 * <p>顺带一个正确性收益：如果生物进焦油前就已经带着可燃（比如刚从高能燃料里爬出来），
 * Spore 那边会跳过施加（它只在"没有该 buff"时才加），但"有没有"这个判断仍然为真，我们照常累积 ✓。
 *
 * <h2>这是通用 mixin</h2>
 * 焦油是普通方块、逻辑也纯服务端，所以本 mixin 登记在 {@code spore_add.mixins.json} 的 {@code "mixins"} 列表
 * （另外两个是客户端专用的，在 {@code "client"} 列表里）。
 */
@Mixin(Tar.class)
public abstract class TarIgnitableMixin {

    @Inject(method = "entityInside", at = @At("RETURN"))
    private void sporeAdd$ignitableRamp(BlockState state, Level level, BlockPos pos, Entity entity,
                                        CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        // 只有当 Spore 自己认可这个目标（于是刚给它加上了可燃）时，我们才跟进累积
        if (!living.hasEffect(SporeCompat.ignitable())) {
            return;
        }
        IgnitableContact.apply(living);
    }
}
