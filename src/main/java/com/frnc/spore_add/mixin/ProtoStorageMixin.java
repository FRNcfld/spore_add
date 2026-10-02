package com.frnc.spore_add.mixin;

import java.util.List;

import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.hivemind.HivemindStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让心智<b>造出来的生物直接进存储</b>，而不是放进世界。
 *
 * <h2>挂在哪一步</h2>
 * {@code Proto#summonMob} 的<b>方法入口</b>，取消掉整个方法。之所以不复刻它后半段：
 * 那个方法的产出是"一个实体 + 一次 {@code addFreshEntity}"，我们要的正是把那个实体收走、
 * 而不是放出去。在入口拦下之后，方法体里那些"给它摆位置、给它打来源标记、判断地面"
 * 全都不需要跑——那些是"放进世界"要做的事。
 *
 * <h2>为什么不用 {@code @Redirect} 重定向那句 {@code addFreshEntity}</h2>
 * 那本来更省事（一行就够），但它有一个绕不开的矛盾：{@code method} 写的是 Spore 自己的方法名
 * （必须 {@code remap = false}），而 {@code @At} 的 target 是原版的 {@code Level#addFreshEntity}
 * （生产环境是 SRG 名，必须 remap）。一个注解只有一个 {@code remap} 开关，覆盖不了两种情况。
 * 这和 {@code SporeDespawnScopeMixin} 那边遇到的是同一类问题，但那边能用"作用域标志 + 另一个
 * mixin"绕过去，这里绕不过去——所以要复刻的就只有"挑一个生物"这前半段。
 *
 * <h2>为什么不把 {@code eatBiomass} 也改掉</h2>
 * 收进存储与放进世界一样要花资源：需求里"孵化/制造消耗资源"是一条明确的平衡杠杆，
 * 存储不该变成免费造兵。所以这里照 Spore 的规矩收费，数额见
 * {@code SporeAddFungusConfig#hivemindStoreCost}（默认 2，与 Spore 自己造一只的价一致）。
 *
 * <p>全是 Spore 自己的成员（{@code getDecisionList} / {@code entityResourceLocation} /
 * {@code getRandom} / {@code eatBiomass}），所以整个注解 {@code remap = false} 成立，
 * 没有原版引用。
 */
@Mixin(Proto.class)
public abstract class ProtoStorageMixin {

    @Inject(method = "summonMob", at = @At("HEAD"), cancellable = true, remap = false)
    private void sporeAdd$storeInsteadOfSummon(int decision, BlockPos pos, CallbackInfo ci) {
        Proto self = (Proto) (Object) this;

        // 与 Spore 一致：BlockPos.ZERO 是"没有目标点"的哨兵值
        if (pos.equals(BlockPos.ZERO)) {
            return;
        }
        List<?> team = self.getDecisionList(decision);
        if (team == null || team.isEmpty()) {
            return;
        }

        // 挑一只：与 Spore 用的是同一个方法，所以"造出来的会是什么"完全一致
        Entity summoned = self.entityResourceLocation(self.getRandom().nextInt(team.size()), (List) team);
        if (summoned == null || !HivemindStorage.isStorable(summoned)) {
            return;   // 不认识的东西照旧走 Spore 原路
        }
        if (!HivemindStorage.tryStore(self, summoned)) {
            return;   // 存储满了 → 不拦，让它照常进世界
        }

        self.eatBiomass(SporeAddFungusConfig.hivemindStoreCost());
        // 挑出来的那个临时实例只是"用来取 NBT 的样板"，NBT 已经存进存储了。
        // 它从没进过世界，所以丢弃它不触发任何世界事件。
        summoned.discard();
        ci.cancel();
    }
}
