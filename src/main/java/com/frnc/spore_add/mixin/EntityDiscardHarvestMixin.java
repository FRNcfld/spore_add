package com.frnc.spore_add.mixin;

import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.fungus.DespawnScope;
import com.frnc.spore_add.fungus.FungusCombat;
import com.frnc.spore_add.fungus.Resources;
import com.frnc.spore_add.hatred.HatredValues;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 新机制 2：被 Despawning System 清理掉的真菌转化为资源。
 *
 * <h2>为什么挂在 {@code Entity#discard} 这个全局热点上</h2>
 * 这是唯一能同时满足两件事的位置：既拿得到"被删掉的是哪个实体"，又不需要在注解里
 * 同时写 Spore 的方法名和原版的成员名（那个组合的 {@code remap} 是矛盾的，理由见 {@link DespawnScope}）。
 *
 * <p>代价是每次实体被删除都会跑一遍这个方法。所以第一行就是最便宜的筛子
 * ——一次静态布尔字段的读取。世界 tick 期间绝大多数 {@code discard()} 在这一行就返回了，
 * 不会往下走到 {@code instanceof} 与标签判断。
 *
 * <h2>{@code remap} 保持默认</h2>
 * 这里注入的 {@code discard} 是<b>原版</b>方法，生产环境里叫 SRG 名，所以必须走 refmap 重映射。
 * 这与 {@code SporeDespawnScopeMixin} 相反（那边是 {@code remap = false}），两个文件各管一半。
 *
 * <p>目标类属于原版，登记在 {@code spore_add.mixins.json} 的 {@code "mixins"}（通用）列表——
 * 服务端也要应用（清理逻辑只在服务端跑），不能放进 {@code "client"}。
 */
@Mixin(Entity.class)
public abstract class EntityDiscardHarvestMixin {

    @Inject(method = "discard", at = @At("HEAD"))
    private void sporeAdd$harvestDespawnedFungus(CallbackInfo ci) {
        // 最便宜的一道筛子，见类注释。放在最前面是有意的：这个方法是全局热点
        if (!DespawnScope.isActive()) {
            return;
        }
        if (!SporeAddFungusConfig.despawnHarvestEnabled()) {
            return;
        }

        Entity self = (Entity) (Object) this;
        // 只有生物才谈得上"等级"与价值；被清理的弹射物（Spore 也清）不算
        if (!(self instanceof LivingEntity living) || !FungusCombat.isFungus(living)) {
            return;
        }
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }

        // 值 0 就先走：等级表里 tierOther 默认就是 0（BOSS 分节实体、感染爪那类），
        // 而这里正处在"一次清理几百个实体"的循环里，没必要为它们去查一次存档数据
        double value = HatredValues.despawnValue(living);
        if (value <= 0.0D) {
            return;
        }

        // 均分给所有心智：这是"系统回收了多少生物质"的全阵营性质收入，
        // 与"某只小兵捡到一块铁"不同，不指向任何具体位置
        Resources.deliverToAll(level, value);
    }
}
