package com.frnc.spore_add.mixin;

import java.util.Optional;

import com.Harbinger.Spore.Core.Sparticles;
import com.Harbinger.Spore.Recipes.WombRecipe;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.Harbinger.Spore.Sentities.Organoids.Womb;
import com.frnc.spore_add.scavenger.Scavenger;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 灾厄重构体（Womb）的同化：<b>跳过拾荒者</b>。
 *
 * <p>需求：「拾荒者不会被真菌生物融合」。它是阵营里的后勤，攒了几小时的存活成长一旦被吃掉，
 * 归零的代价远大于一只普通感染体。
 *
 * <h2>Spore 原本怎么吃（反编译 2.2.0j，{@code Womb.java:218}）</h2>
 * <pre>
 *   private void AssimilateNearbyInfected() {
 *      if (!this.level().isClientSide) {
 *         for (Entity en : this.level().getEntities(this, this.getBoundingBox().inflate(0.1D),
 *                                                   EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
 *            if (en instanceof Infected infected) {
 *               this.setBiomass(this.getBiomass() + this.calculateAssimilation(infected)
 *                               + infected.getKills());
 *               this.getCurrentRecipe(infected).ifPresent(this::addMutation);
 *               infected.discard();
 *               ... 血粒子、GENERIC_EAT 音效、eatingTicks += 80 ...
 *               break;
 *            }
 *         }
 *      }
 *   }
 * </pre>
 * 由 {@code Womb.tick()} 每 tick 以 {@code random.nextInt(40) == 0} 触发（平均 2 秒一次），
 * 判定范围是 {@code inflate(0.1)}——也就是<b>贴着</b>。拾荒者继承
 * {@code InfectedHuman → Infected}，所以正中那条 {@code instanceof}。
 *
 * <h2>为什么整段复刻，而不是只挡掉 {@code discard()}</h2>
 * 因为那句 {@code discard()} <b>不是唯一有副作用的一步</b>。就算把移除挡下来，拾荒者仍然会
 * 每 2 秒被"吃"一次：Womb 白拿 {@code calculateAssimilation + getKills} 的生物质，
 * 而且如果哪个数据包给拾荒者写了 {@code WombRecipe}，突变会<b>无限叠加</b>——
 * 它会永远站在里面，因为根本不会被吃掉。复刻过滤才是干净的语义：它压根不进那个循环。
 *
 * <h2>为什么这一段可以只靠编译器保证正确</h2>
 * 复刻体里调用的全是 Spore 的 <b>public</b> 成员（{@code setBiomass} / {@code getBiomass} /
 * {@code calculateAssimilation} / {@code getKills} / {@code getCurrentRecipe} / {@code addMutation}），
 * 名字或签名写错会直接<b>编译失败</b>——不像 {@code @Redirect} 的 target 字符串那样要等到启动注入时
 * 才发现。全类只有两处是"线级"的：下面注解里的方法名，以及 {@link #eatingTicks} 那个 {@code @Shadow}。
 *
 * <h2>为什么 {@code remap = false}</h2>
 * 注入目标是 {@code AssimilateNearbyInfected}——<b>Spore 自己的</b>私有方法，生产环境不重命名，
 * 不该进 refmap。这与 {@code ProtoStorageMixin} / {@code WombMutationMixin} 同一个理由。
 * 本类刻意不引用任何原版成员作为<em>注入目标</em>（复刻体里调用原版方法是另一回事，
 * 那些由 ForgeGradle 正常重混淆），所以 {@code remap = false} 在这里没有副作用。
 *
 * <h2>维护提示</h2>
 * 复刻自 <b>Spore 2.2.0j</b>（{@code gradle.properties} 里锁的那个版本）。
 * 升级 Spore 之后要重新核对 {@code AssimilateNearbyInfected} 的实现——这与
 * {@code ProtoStorageMixin} 是同一类风险：Spore 那边改了，这边会静默地走旧逻辑。
 */
@Mixin(Womb.class)
public abstract class WombAssimilationMixin {

    /**
     * Spore 的进食计时器，{@code private int eatingTicks}。
     *
     * <p>必须 {@code remap = false}：它是 Spore 的字段，查不到 searge 映射。
     * 拼错的话是<b>启动期</b>的注入失败（报找不到字段），不会静默。
     */
    @Shadow(remap = false)
    private int eatingTicks;

    /** 与 Spore 原版一致的判定范围：包围盒外扩 0.1 格。 */
    private static final double ASSIMILATION_REACH = 0.1D;

    /**
     * 在 {@code AssimilateNearbyInfected} 入口拦下，用同样的判定重跑一遍、但跳过拾荒者。
     *
     * <p>{@code ci.cancel()} 放在最前面：原方法<b>一律不执行</b>，下面这段就是它的替代实现。
     * 这样不存在"原方法跑一半、我又跑一遍"的重复执行。
     */
    @Inject(method = "AssimilateNearbyInfected", at = @At("HEAD"), cancellable = true, remap = false)
    private void sporeAdd$skipScavengers(CallbackInfo ci) {
        ci.cancel();

        Womb self = (Womb) (Object) this;
        Level level = self.level();
        if (level.isClientSide()) {
            return;   // 原方法在客户端也是什么都不做
        }

        for (Entity candidate : level.getEntities(self, self.getBoundingBox().inflate(ASSIMILATION_REACH),
                EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
            // 这一行就是本次改动的全部：多了一个 instanceof Scavenger 的排除条件。
            // 其余每一行都与 Spore 原版逐句对应，顺序也一致。
            if (!(candidate instanceof Infected infected) || infected instanceof Scavenger) {
                continue;
            }

            self.setBiomass(self.getBiomass() + self.calculateAssimilation(infected) + infected.getKills());

            // Spore 那边是 `Optional recipe = ...; recipe.ifPresent(this::addMutation);`（裸类型）。
            // 直接照抄的话 `self::addMutation`（Consumer<WombRecipe>）对不上裸 Optional 的
            // Consumer<Object>，编译不过；所以这里把它收窄成带泛型的 Optional。
            // getCurrentRecipe 的签名本身没有泛型参数，这一句是**未检查转换**——
            // 运行时它返回的确实是 WombRecipe，与 Spore 自己的假设一致。
            @SuppressWarnings("unchecked")
            Optional<WombRecipe> recipe = self.getCurrentRecipe(infected);
            recipe.ifPresent(self::addMutation);

            infected.discard();

            if (level instanceof ServerLevel server) {
                // 与原版一样在自身附近撒一把血粒子；三个偏移量逐字照抄，别改。
                // 随机数走 getRandom() 而不是 Spore 源码里的 this.random——后者是 Entity 的
                // protected 字段，只有子类够得着，mixin 不在那条继承链上。
                double x = self.getX() - ((double) self.getRandom().nextFloat() - 0.1D) * 0.1D;
                double y = self.getY() + ((double) self.getRandom().nextFloat() - 0.25D) * 0.25D * 5.0D;
                double z = self.getZ() + ((double) self.getRandom().nextFloat() - 0.1D) * 0.1D;
                server.sendParticles((SimpleParticleType) Sparticles.BLOOD_PARTICLE.get(),
                        x, y, z, 8, 0.0D, 0.0D, 0.0D, 1.0D);
            }

            self.playSound(SoundEvents.GENERIC_EAT);
            this.eatingTicks += 80;
            break;   // 原版一次只吃一只
        }
    }
}
