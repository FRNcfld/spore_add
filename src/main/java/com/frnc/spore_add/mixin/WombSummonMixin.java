package com.frnc.spore_add.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.Organoids.Womb;
import com.frnc.spore_add.fungus.WombGate;
import com.frnc.spore_add.fungus.WombHatch;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code Womb#summon} 上的两个挂点——孵化这件事的两端都在这一个方法里。
 *
 * <table border="1">
 *   <caption>两处注入各管什么</caption>
 *   <tr><th>位置</th><th>管什么</th></tr>
 *   <tr><td>{@code HEAD}（可取消）</td><td><b>够不够格孵</b>：没喂够种类的突变属性就先不孵，
 *       见 {@link #sporeAdd$requireEnoughMutations}</td></tr>
 *   <tr><td>{@code RETURN}</td><td><b>孵出来放哪</b>：把灾厄送出壳外，
 *       见 {@link #sporeAdd$exitShellAfterSummon}</td></tr>
 * </table>
 *
 * <h2>为什么挂在 {@code summon} 上，而不是挂在 {@code Calamity#setSearchArea} 上</h2>
 * 最初的写法是挂在灾厄的 {@code setSearchArea} 上，用 {@code tickCount == 0} 认出"刚孵化"那一次。
 * 那个判据有两个问题，第二个是实测踩到的：
 * <ol>
 *   <li><b>它认不出"读档"</b>：{@code Calamity#readAdditionalSaveData} 里也会调 {@code setSearchArea}，
 *       而那时 {@code tickCount} <b>同样是 0</b>（刚构造出来还没 tick 过）。于是每次区块重载，
 *       站在重构体旁边的灾厄都会被<b>再传送一次</b>——一只本来就在外面的灾厄会莫名其妙跳走。</li>
 *   <li><b>它太早</b>：那一刻 {@code summon} 还没走完。后面还有套属性、
 *       {@code finalizeSpawn}、{@code addFreshEntity} 三步——任何一步动了坐标，
 *       我们的落点就被悄悄覆盖掉，而那种失效是无声的。</li>
 * </ol>
 *
 * <p>挂在 {@code summon} 的末尾就没有这两个问题：那时灾厄<b>已经定型并进入世界</b>，
 * 也不会在读档时被调到。而"这一次是不是孵化"根本不需要判据——<b>这个方法本身就是孵化</b>。
 *
 * <h2>为什么是 {@code RETURN} 而不是 {@code TAIL}</h2>
 * {@code summon} 里有多个提前 {@code return}（变体表为空、实体 id 查不到、{@code create} 返回 null）。
 * {@code TAIL} 只挂在最后一个返回点上，那些提前返回就漏掉了；{@code RETURN} 每个返回点都会执行。
 * 漏掉的那些情况本来也没有灾厄要挪，但用 {@code RETURN} 省掉了"要不要补"的疑问。
 *
 * <h2>为什么 {@code remap = false}</h2>
 * 注入目标是 {@code Womb#summon}——<b>Spore 自己的</b>私有方法，生产环境不重命名。
 * 处理器体里调用的也全是 Spore 成员（{@code Calamity}、{@code Womb}），
 * 所以这个开关在这里没有副作用。理由与 {@code ProtoStorageMixin} / {@code WombMutationMixin} 相同。
 */
@Mixin(Womb.class)
public abstract class WombSummonMixin {

    /**
     * 孵化<b>入口</b>的闸门：没喂够属性就先别孵。
     *
     * <h2>为什么需要它</h2>
     * Spore 原版里孵化的触发条件是「生物质攒够」，而生物质主要来自<b>吃掉</b>靠上来的真菌
     * （每只 5 点、每 2 秒能吃一只），被动节拍每 30 秒才给 1 点——两条路差 75 倍。
     * 于是蹲在菌群里的重构体不到一分钟就吃饱了，根本等不到喂进去几种属性，
     * 孵出来的就是一只没有加成的白板灾厄。
     *
     * <p>属性只能靠喂食：Spore 一共 7 个同化配方，每个要喂对应的特定进化体
     * （骑士 / 温迪戈 / 撕裂者 / 鸣蜂……）。本闸门要求<b>至少喂到 N 种不同属性</b>才准孵化，
     * 而没喂够时<b>它不会停止进食</b>——继续吃、继续攒，直到凑够。
     *
     * <h2>为什么不拦死亡孵化</h2>
     * {@code die()} 那条路传的 {@code dying = true}，本闸门放行。那是 Spore 的原设
     * （生物质过半的重构体被打死时当场孵一只、且那只只有一半血），保留它还有个作用：
     * <b>玩家不能靠"提前把重构体打死"来彻底抹掉灾厄</b>。
     *
     * <p>取消发生在 {@code HEAD}，所以原方法一行都没跑（它第一件事是掷骰换地形变体，
     * 那一步也不该被浪费掉）。
     */
    @Inject(method = "summon", at = @At("HEAD"), cancellable = true, remap = false)
    private void sporeAdd$requireEnoughMutations(Entity summoned, boolean dying, CallbackInfo ci) {
        if (dying) {
            return;   // 死亡孵化不受门槛约束，见上面的说明
        }
        // 判断全在 WombGate 里：门槛、滞留计时、超时放行。
        // 挡在这里的只有"生物质够了但属性还没够、且还没等够时间"这一种情况。
        if (WombGate.allowHatch((Womb) (Object) this)) {
            return;
        }
        ci.cancel();
    }

    /**
     * 孵化收尾：把刚刚出来的灾厄送出壳外。
     *
     * <p>处理器是<b>实例</b>方法——{@code summon} 是实例方法，处理器必须与它同类。
     * 参数顺序照抄 {@code summon(Entity, boolean)}，末尾追加 {@code CallbackInfo}。
     */
    @Inject(method = "summon", at = @At("RETURN"), remap = false)
    private void sporeAdd$exitShellAfterSummon(net.minecraft.world.entity.Entity summoned, boolean dying,
                                               CallbackInfo ci) {
        Womb womb = (Womb) (Object) this;
        Level level = womb.level();
        if (level.isClientSide()) {
            return;
        }
        // 刚刚孵化出来的那只灾厄：它被 setPos 到了重构体自己的坐标上，所以与重构体重叠。
        // 走的是上面那条"离 location 太远就直接挪走 100 格"的分支时它不重叠——
        // 那时它本来就已经在很远的地方了，不需要再挪，这里扫不到它正好。
        Calamity calamity = level
                .getEntitiesOfClass(Calamity.class, womb.getBoundingBox().inflate(1.0D))
                .stream()
                .filter(candidate -> !candidate.isRemoved())
                .findFirst()
                .orElse(null);
        if (calamity == null) {
            return;   // 这次孵出来的不是灾厄（或者已经被挪走了），不用管
        }
        WombHatch.exitShell(calamity, womb);
    }
}
