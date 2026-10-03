package com.frnc.spore_add.debug;

import com.frnc.spore_add.SporeAddDebugConfig;
import com.frnc.spore_add.SporeAddDebugConfig.Area;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/**
 * 检查点的统一出口。开关见 {@link SporeAddDebugConfig}。
 *
 * <h2>为什么要有这一层，而不是各处自己 {@code LOGGER.info}</h2>
 * 两个理由：
 * <ul>
 *   <li><b>共享一个 logger。</b>待插桩的二十多个类里，大部分连 {@code LogUtils.getLogger()}
 *       字段都没有。各自加一个字段意味着二十多处样板，而它们的日志其实属于同一类东西。</li>
 *   <li><b>前缀统一。</b>每条都带 {@code [SporeAdd][RAID]} 这样的标记，
 *       于是排查时可以 {@code grep "\[SporeAdd\]\[RAID\]"} 只看一组，
 *       也可以排除掉某一组。分组开关只能挡住"打不打"，挡不住"看哪一行"——
 *       而大日志里真正让人难受的是后者。</li>
 * </ul>
 *
 * <h2>热路径纪律（加日志前必读）</h2>
 * {@link #log(Area, String, Object...)} 的变长参数数组是在 <b>调用点</b> 就求值的，
 * 与关不关无关。也就是说
 *
 * <pre>{@code
 * SporeAddDebug.log(Area.RAID, "count={}", expensiveCount());   // expensiveCount() 照样会跑
 * }</pre>
 *
 * 所以分两种写法：
 * <table border="1">
 *   <caption>按调用点的热度选写法</caption>
 *   <tr><th>调用点</th><th>写法</th></tr>
 *   <tr><td><b>冷</b>：状态迁移、掷骰、加载、出生、死亡</td>
 *       <td>直接 {@code log(...)}——多一次方法调用与一个空数组，无所谓</td></tr>
 *   <tr><td><b>热</b>：每 tick、每实体、事件处理器里的高频分支</td>
 *       <td>先 {@code if (SporeAddDebug.on(area))}，再 {@code log(...)}</td></tr>
 * </table>
 *
 * <p>本类<b>只提供</b> {@link #on} / {@link #log} / {@link #warn} 三个入口，
 * 没有"传一个 {@code Supplier} 延迟拼串"的重载：那种重载每次调用都会分配一个 lambda，
 * 并不比 {@code on()} 判断便宜，却容易让人以为"用了它就自动是懒的"，
 * 于是在热的调用点误用。少一个入口就少一种误用。
 *
 * <p>与之对应，本工程<b>刻意不插桩</b>三处最热的 mixin 委托器——
 * {@code ProtoRaidMixin} / {@code DespawnCapMixin} / {@code WombRaidMixin} 的
 * 「当前没有袭击」快路径。它们每分钟被调用数百次，而那时能打出来的只有"什么都没有"。
 */
public final class SporeAddDebug {

    /**
     * 本设施唯一的 logger。
     *
     * <p>用 {@code LogUtils.getLogger()} 而不是按类取——这几条日志的归属是<b>分组</b>，
     * 而分组已经写在每行的 {@code [SporeAdd][XXX]} 前缀里了，再按类名分一次只是重复。
     */
    private static final Logger LOGGER = LogUtils.getLogger();

    private SporeAddDebug() {
    }

    /**
     * 这一组此刻是否输出。
     *
     * <p>总闸与分组开关相与，<b>总闸在前</b>——关着时短路，连分组那一项都不读。
     *
     * <p>读的是 Forge 的 {@code ConfigValue#get()}，它自带缓存（首次读之后就是读一个字段），
     * 所以这一层没有、也不需要自己的缓存。
     */
    public static boolean on(Area area) {
        return SporeAddDebugConfig.enabled() && SporeAddDebugConfig.area(area);
    }

    /**
     * 打一条检查点。
     *
     * <p>{@code format} 只支持 SLF4J 的 {@code {}} 占位符（不是 {@code String.format} 的 {@code %s}）。
     *
     * <p>关着时在<b>第一行</b>就返回，但因为变长参数在调用点已经求值，
     * 热的调用点仍应先判 {@link #on}，见类注释。
     */
    public static void log(Area area, String format, Object... args) {
        if (!on(area)) {
            return;
        }
        LOGGER.info("[SporeAdd][{}] " + format, prepend(area, args));
    }

    /**
     * 打一条"这里失败了"的检查点。
     *
     * <p>与 {@link #log} 的区别只在日志级别：失败分支（建不出实体、没落点、静默降级）
     * 用 WARN 才好在整屏 INFO 里被看见。它<b>不是</b>用来报错的——
     * 判据是"这句话描述的是没做到，而不是做到了什么"。
     */
    public static void warn(Area area, String format, Object... args) {
        if (!on(area)) {
            return;
        }
        LOGGER.warn("[SporeAdd][{}] " + format, prepend(area, args));
    }

    /**
     * 把分组名塞到参数数组最前面，供上面两个方法的格式串里那个 {@code {}} 用。
     *
     * <p>这样写是为了让日志前缀与 {@code format} 合成一个字符串——
     * SLF4J 只在真正要输出时才拼，而"真正要输出"已经在调用方确认过了。
     */
    private static Object[] prepend(Area area, Object[] args) {
        Object[] merged = new Object[args.length + 1];
        merged[0] = area.name();
        System.arraycopy(args, 0, merged, 1, args.length);
        return merged;
    }
}
