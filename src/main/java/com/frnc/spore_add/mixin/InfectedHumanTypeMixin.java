package com.frnc.spore_add.mixin;

import com.Harbinger.Spore.Sentities.BasicInfected.InfectedHuman;
import com.frnc.spore_add.entity.ModEntities;
import com.frnc.spore_add.scavenger.Scavenger;

import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让「拾荒者」这个变体真正拥有自己的实体类型。
 *
 * <h2>为什么非得动这一处</h2>
 * {@code InfectedHuman} 的构造器把实体类型<b>写死</b>了。它的字节码是：
 * <pre>
 *   getstatic  Sentities.INF_HUMAN : RegistryObject
 *   invokevirtual RegistryObject.get() : Object
 *   checkcast  EntityType
 *   invokespecial Infected.&lt;init&gt;(EntityType, Level)
 * </pre>
 * 所以只要 {@code Scavenger extends InfectedHuman}，它就会被登记成 {@code spore:inf_human}——
 * <b>存盘之后再读出来就变回普通菌染人类了</b>。而 {@code InfectedHuman} 没有接受
 * {@code EntityType} 的构造器，{@code super(level)} 是唯一的入口，子类无法从外面把类型塞进去。
 *
 * <p>把这个 {@code RegistryObject.get()} 重定向一下，就解决了：接收者是拾荒者时返回我们的类型。
 * 于是 {@code getType()} 从头到尾都是 {@code spore_add:scavenger}，存盘、读档、以及一切
 * 依赖实体类型的逻辑（标签、Despawn、刷怪蛋）全都正确。
 *
 * <h2>为什么这是全项目最脆的一处注入</h2>
 * 它依赖 Spore 的构造器里<b>恰好有这么一次调用</b>。Spore 哪天改了构造器（比如改成传字段、
 * 或者多了一次 {@code get()}），这里就会匹配失败——<b>表现是启动直接崩，不是静默失效</b>，
 * 所以一次 {@code runClient} 就能暴露。
 *
 * <p>不复刻整个构造器、也不改写父类字段，是因为那两者都依赖更多 Spore 内部结构；
 * 一次重定向是侵入面最小的做法。
 *
 * <h2>{@code remap = false}</h2>
 * 注入目标是 {@code RegistryObject.get()}——<b>Forge</b> 的类，不是原版类。
 * 生产环境里只有原版成员会被改成 SRG 名，Forge 与模组自己的成员都保持字面名，
 * 所以这里不需要（也不该）走 refmap。{@code method = "<init>"} 同理。
 */
@Mixin(InfectedHuman.class)
public abstract class InfectedHumanTypeMixin {

    /**
     * 把写死的 {@code Sentities.INF_HUMAN.get()} 换成"是拾荒者就用拾荒者的类型"。
     *
     * <h2>为什么处理器必须是 {@code static}（而且不能靠 {@code this} 判断）</h2>
     * 这一次 {@code get()} 调用是 {@code super(INF_HUMAN.get(), level)} 的<b>实参</b>，
     * 也就是发生在 {@code super()} <b>之前</b>。Mixin 对这种位置的处理器的要求是
     * <b>必须 static</b>——因为那时 {@code this} 还没初始化，实例方法根本不可靠。
     * 最初写成实例方法、并用 {@code this instanceof Scavenger} 判断，编译期完全看不出来，
     * 是启动注入时才炸的：
     * <pre>
     *   InvalidInjectionException: @Redirect handler before super() invocation must be static
     * </pre>
     *
     * <p>而改成 static 之后就<b>没有 {@code this} 了</b>，判断不了"正在构造的是不是拾荒者"。
     * 这个信息只能从对象外部来，所以改由 {@code Scavenger} 自己的构造器在 {@code super(...)}
     * 的实参里主动立一个标志（{@code Scavenger#announce} → {@code Scavenger#claimConstruction}）。
     * 实参先于父类构造器求值，时序上正好。
     *
     * <p>签名的其余部分不变：重定向的是<b>实例</b>方法调用，所以第一个参数是接收者
     * （那个 {@code RegistryObject}），返回类型是它擦除后的 {@code Object}。
     *
     * <p>{@code ModEntities.SCAVENGER.get()} 在这个时机取是安全的：实体只有等世界加载后才会被构造，
     * 那时注册早就完成了。（注册期构造会取到 {@code null}，但注册期不会构造实体。）
     */
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/registries/RegistryObject;get()Ljava/lang/Object;"),
            remap = false)
    private static Object sporeAdd$useScavengerType(RegistryObject<?> original) {
        return Scavenger.claimConstruction() ? ModEntities.SCAVENGER.get() : original.get();
    }
}
