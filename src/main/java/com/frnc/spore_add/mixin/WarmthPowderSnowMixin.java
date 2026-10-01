package com.frnc.spore_add.mixin;

import com.frnc.spore_add.enchantment.Warmth;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 靴子上有「烈阳」的生物不会在细雪里下陷。
 *
 * <h2>为什么拦这个静态方法</h2>
 * "能不能站在细雪上"最终由 {@code PowderSnowBlock#canEntityWalkOnPowderSnow(Entity)} 决定，
 * 而它内部去问"脚上那双靴子行不行"。那双靴子的判断是 {@code IForgeItem} 上的一个<b>默认方法</b>
 * （{@code canWalkOnPowderedSnow(ItemStack, LivingEntity)}，默认只认原版皮革靴），
 * 默认方法没有具体实现类可以注入，所以改从调用方入手——语义正好就是我们要的那一句。
 *
 * <p>返回 true 的效果是 {@code getCollisionShape} 交出真实碰撞箱，于是生物站在雪面上而不是陷进去；
 * 同时 {@code fallOn} 那套也随之按原版走。
 *
 * <p>注意判断的是<b>靴子那一格</b>（{@link Warmth#isOnBoots}），与免疫寒冷的"任意护甲格"不同——
 * 这条需求明确说的是"附魔于靴子上时"。
 */
@Mixin(PowderSnowBlock.class)
public abstract class WarmthPowderSnowMixin {

    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void sporeAdd$warmBootsStayOnTop(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity living && Warmth.isOnBoots(living)) {
            cir.setReturnValue(true);
        }
    }
}
