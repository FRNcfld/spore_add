package com.frnc.spore_add.fungus;

/**
 * 「此刻正处在 Spore 的 Despawning System 清理过程中」这一个状态。
 *
 * <h2>为什么需要它</h2>
 * 需求要的是「<b>Despawning System 清理掉的</b>真菌转化为资源」，而不是「任何被 {@code discard()} 掉的真菌」。
 * 后者会把一大堆无关的消失也算进来：区块卸载、实体转换（进化时会 {@code discard()} 旧的）、
 * 玩家用命令清场、其它模组的清理逻辑……全都会重复给资源。
 *
 * <p>而被清理的那一刻没有任何事件可以挂钩：{@code HandlerEvents.despawnExcess} 是私有的静态方法，
 * 里面就是一句 {@code entity.discard()}。所以只能由两个 mixin 配合——
 * 一个在 {@code cleanUpMobs} 的两端开关这个标志，另一个在 {@code Entity#discard} 里读它。
 *
 * <h2>为什么不是一个 mixin 搞定</h2>
 * 想直接在 {@code despawnExcess} 里重定向那句 {@code discard()} 的话，
 * 那个注解需要<b>同时</b>做到两件互相矛盾的事：{@code method} 写的是 Spore 自己的方法名
 * （必须 {@code remap = false}），而 {@code @At} 的 target 是原版的 {@code Entity#discard}
 * （在生产环境被改成 SRG 名，必须 remap）。一个注解只有一个 {@code remap} 开关，覆盖不了两种情况。
 *
 * <h2>一个已知的边界情况</h2>
 * 如果 {@code cleanUpMobs} 中途抛异常，{@link #exit()} 就不会执行，标志会一直挂着，
 * 于是此后任何真菌被 {@code discard()} 都会被算作"被系统清理"。
 * 后果是<b>多给资源</b>，不是崩溃或丢数据；而触发它的前提（Spore 的清理循环抛异常）
 * 本身已经是一个坏掉的状态了。这个取舍是有意接受的，没有为它加一层 try/finally 式的包装。
 *
 * <h2>单线程假设</h2>
 * 标志是全局的，依赖"世界 tick 是单线程"这个原版前提。写成 {@code volatile}
 * 只是为了万一有别的线程调 {@code discard()} 时不会读到永久缓存的值，不是为了支持并发。
 */
public final class DespawnScope {

    private static volatile boolean active;

    private DespawnScope() {
    }

    /** 进入 Spore 的清理过程。 */
    public static void enter() {
        active = true;
    }

    /** 离开。 */
    public static void exit() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }
}
