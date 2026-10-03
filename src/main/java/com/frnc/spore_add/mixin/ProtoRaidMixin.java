package com.frnc.spore_add.mixin;

import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.frnc.spore_add.raid.RaidManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * 袭击期间心智的三项加成（需求「准备」第 2 条）。
 *
 * <table border="1">
 *   <caption>三项与注入点</caption>
 *   <tr><th>需求</th><th>Spore 里的落点</th><th>怎么改</th></tr>
 *   <tr><td>资源消耗 −50%</td><td>{@code eatBiomass(int)}</td><td>改入参</td></tr>
 *   <tr><td>资源获取 +100%</td><td>{@code addBiomass(int)}</td><td>改入参</td></tr>
 *   <tr><td>发育速度 +50%</td><td>{@code tick()} 里守护 {@code generateCasing()} 的那个 200</td><td>改节拍常量</td></tr>
 * </table>
 *
 * <h2>为什么「改入参」比「取消并重写」好</h2>
 * 那三个量在 Spore 里都是简单方法（{@code addBiomass} 就是一句
 * {@code entityData.set(BIOMASS, get() + e)}）。想改乘数，最容易想到的是在 HEAD 取消、
 * 再用公开 API 重写一遍。但那有两个坑：{@code addBiomass} 重写成自己就是无限递归；
 * 而写 {@code entityData} 又要 {@code @Shadow} 那个私有的 {@code BIOMASS} 访问子。
 * {@code @ModifyVariable(argsOnly = true)} 只改传进去的那个数、方法体原样跑，
 * 既不递归也不碰私有字段。
 *
 * <h2>发育那一处为什么要写 slice</h2>
 * {@code Proto#tick} 里有<b>三个</b> 200：守护 {@code generateCasing()}、
 * 守护被动 {@code addBiomass(1)}、守护召唤。只按数值匹配会三个一起改，
 * 那样"资源获取"就会被「×2 的入参」和「×1.5 的频率」叠成 3 倍，与需求写的 +100% 对不上。
 *
 * <p>用 {@code @Slice(to = generateCasing 的调用)} 把匹配范围限死在方法开头到那次调用之间，
 * 那段里只有第一个 200。这样不依赖"第几个 200"这种序号假设——
 * 序号会随 Spore 调整分支顺序而错位，而"在 generateCasing 之前"这个关系是语义上的。
 *
 * <h2>哪几处要 {@code remap = false}、哪几处绝不能写</h2>
 * 判据只有一条：<b>那个方法名在生产环境会不会被重命名。</b>
 * <ul>
 *   <li>{@code eatBiomass} / {@code addBiomass} 是 <b>Spore 自己的</b>方法，不重命名
 *       → 必须 {@code remap = false}。开着 remap 反而要去找一个不存在的 searge 映射。</li>
 *   <li>{@code tick} 是<b>原版 {@code Entity#tick} 的覆写</b>，生产环境会被重混淆成 {@code m_8119_}
 *       → <b>绝不能写 {@code remap = false}</b>。写了的话 Mixin 就不查映射表、拿字面量
 *       {@code tick} 去找目标，而生产 jar 里根本没有叫 {@code tick} 的方法，启动直接
 *       {@code InvalidInjectionException: could not find any targets matching 'tick'}。
 *       <b>本工程已经在整合包里踩过这个坑</b>，两个 mixin 一起把游戏崩在启动阶段。</li>
 * </ul>
 * 这个坑在开发环境<b>永远看不出来</b>——Spore 被反混淆回 {@code tick}，怎么写都对；
 * 只有打成 jar 进整合包才会炸。所以"只跑过 runClient"不能算验证过 mixin。
 *
 * <p>至于 {@code @Slice} / {@code @At} 里那些指向 <b>Spore 成员</b>的 target
 * （{@code Proto;generateCasing()} 之类）：开着 remap 也不会动它们——映射表里没有 Spore 的东西，
 * 查不到就原样保留。refmap 里已有先例：{@code ScannerItemMixin.use} 被映射成
 * {@code ScannerItem;m_7203_}（原版覆写），同一个 mixin 里其它指向 Spore 的引用原样不动。
 */
@Mixin(Proto.class)
public abstract class ProtoRaidMixin {

    /** 资源消耗：袭击期间打折。 */
    @ModifyVariable(method = "eatBiomass", at = @At("HEAD"), argsOnly = true, remap = false)
    private int sporeAdd$cheaperUpkeep(int amount) {
        return RaidManager.adjustResourceCost(amount);
    }

    /** 资源获取：袭击期间翻倍。 */
    @ModifyVariable(method = "addBiomass", at = @At("HEAD"), argsOnly = true, remap = false)
    private int sporeAdd$richerIncome(int amount) {
        return RaidManager.adjustResourceGain(amount);
    }

    /**
     * 发育节拍：袭击期间缩短间隔。
     *
     * <p>{@code constant = @Constant(intValue = 200)} 匹配的是 {@code tickCount % 200} 里的那个 200，
     * slice 把它限制在 {@code generateCasing()} 那次调用之前，见类注释。
     */
    @ModifyConstant(
            method = "tick",
            slice = @Slice(to = @At(value = "INVOKE",
                    target = "Lcom/Harbinger/Spore/Sentities/Organoids/Proto;generateCasing()V")),
            constant = @Constant(intValue = 200))
    private int sporeAdd$fasterGrowth(int interval) {
        return RaidManager.adjustGrowthInterval(interval);
    }

    /**
     * <b>制造</b>节拍：袭击期间缩短间隔（需求：制造速度加快 500%）。
     *
     * <p>Spore 在 {@code tick} 里用三个<b>互相独立</b>的 {@code tickCount % 200 == 0} 分别管三件事：
     * 生成菌壳（{@code generateCasing}）、被动资源（{@code addBiomass(1)}）、以及
     * <b>召唤真菌生物</b>（{@code summonMob}）。「制造速度」要动的是最后那一个。
     *
     * <p>为什么用 slice 而不是序号：三个常量值都是 200，只按数值匹配会三个一起改。
     * 这里的 slice 从 {@code giveMadness} 那次调用起、到 {@code summonMob} 那次调用止——
     * 按字节码顺序，那一区间里只有召唤那一个 200（已用 {@code javap -c} 核对过偏移：
     * 107 = 菌壳、193 = 被动资源、315 = 召唤）。
     * 用"在哪两次调用之间"来定位，比数"第几个 200"稳：Spore 调整分支顺序时前者不会错位。
     */
    @ModifyConstant(
            method = "tick",
            slice = @Slice(
                    from = @At(value = "INVOKE",
                            target = "Lcom/Harbinger/Spore/Sentities/Organoids/Proto;giveMadness(Lcom/Harbinger/Spore/Sentities/Organoids/Proto;)V"),
                    to = @At(value = "INVOKE",
                            target = "Lcom/Harbinger/Spore/Sentities/Organoids/Proto;summonMob(ILnet/minecraft/core/BlockPos;)V")),
            constant = @Constant(intValue = 200))
    private int sporeAdd$fasterManufacture(int interval) {
        return RaidManager.adjustManufactureInterval(interval);
    }
}
