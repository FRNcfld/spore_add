package com.frnc.spore_add.mixin;

import java.util.List;

import com.Harbinger.Spore.sEvents.HandlerEvents;
import com.frnc.spore_add.entity.ModEntities;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 需求 3：把拾荒者加进消失管理系统的黑名单——它不会被那套系统清掉。
 *
 * <h2>Spore 是怎么判断黑名单的</h2>
 * {@code HandlerEvents#cleanUpMobs} 收集待清理实体时的第一道条件就是：
 * <pre>
 *   if (!despawn_blacklist.contains(entity.getEncodeId()) &amp;&amp; !entity.hasCustomName()) { 收进列表 }
 * </pre>
 * 名单来自 Spore 自己的配置（{@code SConfig.SERVER.despawn_blacklist}）。
 *
 * <h2>为什么改这次 {@code contains} 而不是往 Spore 的配置里加一行</h2>
 * 往那份配置里塞 {@code spore_add:scavenger} 也能达到目的，但那会<b>改写别的模组的配置文件</b>——
 * 玩家打开 Spore 的配置会看到一个不属于它的条目，而且 Spore 换版本、配置重置时那一行会消失。
 * 重定向这次判断则完全在本 mod 内部，不动任何外部文件。
 *
 * <p>拾荒者<b>故意不参与</b> Despawn 的清理：它的价值随存活时间增长（拾荒倍率、治疗个数都按
 * 存活时长爬升），被系统清掉等于把它攒的一切归零。它的数量由本 mod 自己的
 * {@code scavenger.maxCount} 管（见 {@code ScavengerPopulation}），不靠 Spore 那套按等级分的上限。
 *
 * <h2>{@code remap = false}</h2>
 * 注入目标是 {@code java.util.List#contains}——<b>JDK</b> 的方法。
 * 生产环境里只有原版与 Forge 的成员会被改名，JDK 与模组自己的成员都保持字面名，
 * 所以这里不需要走 refmap。{@code cleanUpMobs} 本身是 Spore 自己的方法，同理。
 *
 * <p>已核对过 {@code cleanUpMobs} 里只有<b>一处</b> {@code List.contains} 调用，
 * 所以不需要 {@code ordinal}。
 */
@Mixin(HandlerEvents.class)
public abstract class ScavengerDespawnProtectionMixin {

    /**
     * 把 {@code despawn_blacklist.contains(id)} 改成"拾荒者一律算在黑名单里，其余照旧"。
     *
     * <p>处理器签名按 Mixin 的规矩来：重定向的是<b>实例</b>方法调用 {@code List#contains}，
     * 所以第一个参数是接收者（那个 List），第二个是原参数（实体 id 字符串）。
     *
     * <h2>为什么处理器必须是 {@code static}</h2>
     * 注意这两件事是分开的：被重定向的 {@code contains} 是实例调用，而<b>它所在的方法</b>
     * {@code cleanUpMobs} 是 {@code private static}。Mixin 校验的是后者——处理器的静态性必须与
     * <b>目标方法</b>一致，不一致就直接抛 {@code InvalidInjectionException}：
     * <pre>
     *   'static' modifier of handler method does not match target in
     *   com/Harbinger/Spore/sEvents/HandlerEvents::sporeAdd$protectScavengers
     * </pre>
     * 这个错<b>编译期完全看不出来</b>，只在启动注入时炸——所以本类最初漏写 {@code static}
     * 时是一次 runClient 才暴露的。同一个目标类上的 {@code DespawnCapMixin} 一直是对的
     * （它注入同样静态的 {@code despawnExcess}），可以对照。
     *
     * <p>处理器本身不需要任何实例状态（只调静态的 {@link #isScavengerId} 与参数上的
     * {@code contains}），所以加 {@code static} 没有任何副作用。
     */
    @Redirect(
            method = "cleanUpMobs",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"),
            remap = false)
    private static boolean sporeAdd$protectScavengers(List<?> blacklist, Object id) {
        return isScavengerId(id) || blacklist.contains(id);
    }

    /**
     * 这个 id 是不是拾荒者的实体类型 id。
     *
     * <p>与 {@code ModEntities.SCAVENGER.getId().toString()} 比字符串，而不是去注册表里反查实体类型：
     * 这里手上只有一个 {@code Object}（{@code getEncodeId()} 的返回值），比字符串最直接。
     * 名字取不到时（注册表还没就绪等异常情况）返回 false，也就是"不保护"——
     * 宁可被清理，也不该因为一次取名字失败把整段清理逻辑带崩。
     */
    private static boolean isScavengerId(Object id) {
        var key = ModEntities.SCAVENGER.getId();
        return key != null && id instanceof String name && name.equals(key.toString());
    }
}
