package com.frnc.spore_add.effect;

import com.frnc.spore_add.network.ModNetwork;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「可燃等级」与「爆燃层数」两个计数器的存取，以及火焰伤害加成的算式。
 *
 * <h2>为什么两个都自己存，都不放 amplifier</h2>
 * vanilla 的 amplifier 有两个硬限制，任何一个都足以让"层数不封顶"落空：
 * <ul>
 *   <li>网络同步用 {@code byte}（{@code ClientboundUpdateMobEffectPacket} 里
 *       {@code (byte)(amplifier & 255)}，客户端按有符号字节读）——超过 127 客户端显示就错乱；</li>
 *   <li>存档用 {@code CompoundTag.putByte}，读回来再 {@code Math.max(0, i)}——超过 127 存盘重载后掉回 1 级。</li>
 * </ul>
 * 所以两个计数器都存在 {@link net.minecraftforge.common.extensions.IForgeEntity#getPersistentData()} 里。
 * 那个 {@code CompoundTag} 由 Forge 自动写进实体 NBT 的 {@code "ForgeData"}、并自动读回，
 * 因此既不用注册 Capability，也不用处理 {@code PlayerEvent.Clone} 那类重生时的搬运问题。
 *
 * <p>代价是它不同步，客户端拿不到，所以两个等级都要靠 {@link ModNetwork} 发同步包，
 * 客户端只读缓存、不做权威计算。两个 buff 因此都把 amplifier 固定成 0——
 * 既绕开了字节上限，也顺带让原版不再画它自己的罗马数字等级（位置让给客户端自绘的阿拉伯数字）。
 *
 * <h2>可燃等级何时归零：只有两条路</h2>
 * <ol>
 *   <li><b>可燃被 Spore 消耗（触发）</b>（{@code MobEffectEvent.Remove}）：<b>无条件</b>清零，
 *       即使实体还泡在燃料里也照清。于是持续泡着反复触发时，每次触发都拿"这一刻累积了多少"结算，
 *       之后又从 1 级重新往上爬。</li>
 *   <li><b>可燃自然到期</b>（{@code MobEffectEvent.Expired}）：泡在燃料里时它每秒被刷新、十分钟也到不了期，
 *       所以一旦到期就说明已经离开燃料足够久 → 清零。这也是"离开后回来会从 1 级重新开始"的唯一原因：
 *       离开不足 10 秒（buff 还在）时回来，等级是接着原来的数继续涨的。</li>
 * </ol>
 *
 * <p>注意<b>刻意没有</b>把"实体是否还在燃料里"当成归零依据。曾经那样做过（隔一段时间没碰到燃料就清零），
 * 结果是短时间离开再回来会被打回 1 级，与预期不符。现在等级只被上面两条路清零，与接触与否无关。
 */
public final class BuffLevels {

    private static final String KEY_DEFLAGRATION = "spore_add:deflagration_stacks";
    private static final String KEY_IGNITABLE = "spore_add:ignitable_level";
    private static final String KEY_LAST_RAMP_TICK = "spore_add:last_ramp_tick";

    private BuffLevels() {
    }

    // ---------------- 爆燃层数 ----------------

    public static int deflagration(LivingEntity entity) {
        return entity.getPersistentData().getInt(KEY_DEFLAGRATION);
    }

    public static void setDeflagration(LivingEntity entity, int stacks) {
        put(entity, KEY_DEFLAGRATION, stacks);
    }

    public static void addDeflagration(LivingEntity entity, int extra) {
        if (extra > 0) {
            setDeflagration(entity, deflagration(entity) + extra);
        }
    }

    public static void clearDeflagration(LivingEntity entity) {
        entity.getPersistentData().remove(KEY_DEFLAGRATION);
    }

    // ---------------- 可燃等级 ----------------

    public static int ignitable(LivingEntity entity) {
        return entity.getPersistentData().getInt(KEY_IGNITABLE);
    }

    public static void setIgnitable(LivingEntity entity, int level) {
        put(entity, KEY_IGNITABLE, level);
    }

    /**
     * 实体这一 tick 接触着燃料（或其它同类危险液体）：在该涨级时涨一级。
     *
     * <p>本方法由方块的 {@code entityInside} 调用，而那个回调是<b>按覆盖到的每个方块各调一次</b>的，
     * 所以必须做<b>同 tick 去重</b>——否则泡在两格深的液体里，层数增速会随深度翻倍。
     *
     * <p>等级为 0 时直接给到 1，不等下一个整秒，这样刚踏进燃料就能立刻拿到可燃 buff。
     *
     * <p>这里<b>不做任何清零</b>：等级只被"被触发"与"自然到期"两条路清掉（见类注释），
     * 与这次接触之前离开了多久无关——离开不足 10 秒再回来，等级是接着涨的。
     *
     * @return 处理之后的可燃等级
     */
    public static int touchFuel(LivingEntity entity) {
        int level = ignitable(entity);
        if (level <= 0) {
            setIgnitable(entity, 1);
            return 1;
        }

        CompoundTag data = entity.getPersistentData();
        int tick = entity.tickCount;
        if (tick % 20 != 0 || tick == data.getInt(KEY_LAST_RAMP_TICK)) {
            return level;
        }
        data.putInt(KEY_LAST_RAMP_TICK, tick);
        int next = level + 1;
        setIgnitable(entity, next);
        return next;
    }

    // ---------------- 伤害算式 ----------------

    /**
     * 爆燃带来的火焰伤害加成。
     *
     * <p>需求 2：每有一层使受到的火焰伤害提高 1 点。
     * <br>需求 6：每满 10 层，额外附带目标最大生命值 1% 的火焰伤害（不足 10 层的零头不计）。
     *
     * <p>返回的是<b>要加进同一次伤害</b>的数值（调用方用 {@code LivingHurtEvent.setAmount}），
     * 所以护甲、抗性、吸收都会照常作用在这部分上，也不会产生第二次伤害或递归。
     */
    public static float fireDamageBonus(LivingEntity entity, int stacks) {
        return stacks + (stacks / 10) * 0.01F * entity.getMaxHealth();
    }

    private static void put(LivingEntity entity, String key, int value) {
        if (value <= 0) {
            entity.getPersistentData().remove(key);
        } else {
            entity.getPersistentData().putInt(key, value);
        }
    }
}
