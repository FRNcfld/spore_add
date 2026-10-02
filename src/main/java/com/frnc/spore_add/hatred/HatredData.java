package com.frnc.spore_add.hatred;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 全服的恨意值账本。挂在主世界的 {@code DimensionDataStorage} 上。
 *
 * <h2>为什么不塞进玩家的持久数据</h2>
 * 需求是「<b>世界恨意值为所有玩家的恨意值之和</b>」。玩家离线时他的持久数据根本不会被加载，
 * 那份「和」就会随着谁在线而忽大忽小——真菌的减伤跟着一起抖。放在世界存档数据里，
 * 离线玩家的恨意值照样算数，也没有「玩家数据还没读出来」的时间窗。
 *
 * <h2>total 是缓存，但读盘时会重算</h2>
 * {@link #total} 每次改动都跟着维护，省的是一秒几十次的全表求和。
 * 但 {@link #load} 里它是<b>从明细重新加出来</b>的，不信任存档里那个数——
 * 万一存档被手改过或写坏过，宁可重新算一遍，也不要让一个错的总数一直放大真菌的减伤。
 *
 * <h2>恨意值不允许为负</h2>
 * 所有写入都过 {@link #clamp}。「降低 90%」这类按比例削减在数值很小时会反复乘出一个
 * 无限接近 0 的值，夹一下既省心，也让「0 就是干净了」这件事在小数上是真的。
 */
public final class HatredData extends SavedData {

    /** 存档里的数据名。改它等于让旧存档的恨意值全部作废。 */
    private static final String DATA_NAME = "spore_add_hatred";

    private static final String KEY_PLAYERS = "players";
    private static final String KEY_PENDING = "pending";

    /** 每个玩家的个人恨意值。键是玩家 UUID，所以离线玩家也在里面。 */
    private final Map<UUID, Double> hatred = new HashMap<>();

    /** {@link #hatred} 的求和缓存，见类注释。 */
    private double total;

    /**
     * 「暂时存储」的资源。
     *
     * <p>需求里有两处会产出资源却没有心智可收：死于真菌之手的那笔（{@code ×2} 的补偿），
     * 以及 Despawning System 清理掉的生物。一只心智都没有时先记在这里，
     * 等有心智了再补发（见 {@link #drainPending}）。
     */
    private double pending;

    // ------------------------------------------------------------------
    // 取用
    // ------------------------------------------------------------------

    /**
     * 取主世界那份账本。所有维度共用一份——恨意值是全局概念，不该按维度分裂。
     *
     * <p>1.20.1 的 {@code DimensionDataStorage#computeIfAbsent} 是
     * {@code (反序列化器, 构造器, 名字)} 这个顺序（1.20.2 之后才改成传一个 {@code SavedData.Factory}），
     * 两个方法引用别写反——写反了在编译期就会报错，所以还算安全，但顺序确实反直觉。
     */
    public static HatredData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(HatredData::load, HatredData::new, DATA_NAME);
    }

    /** 这个玩家的个人恨意值，没有记录时为 0。 */
    public double get(UUID player) {
        return hatred.getOrDefault(player, 0.0D);
    }

    /** 世界恨意值 = 所有玩家个人恨意值之和（含离线玩家）。 */
    public double total() {
        return total;
    }

    /** 直接设定某个玩家的恨意值（会夹到 0 以上）。 */
    public void set(UUID player, double value) {
        double next = clamp(value);
        if (next <= 0.0D) {
            // 0 就不留条目，免得长期下来攒一堆无意义的零记录
            if (hatred.remove(player) != null) {
                setDirty();
            }
        } else {
            Double previous = hatred.put(player, next);
            if (previous == null || previous != next) {
                setDirty();
            }
        }
        recomputeTotal();
    }

    /**
     * 增减某个玩家的恨意值，返回结算后的新值。
     *
     * <p>调用方通常需要「改之前」与「改之后」两个数：越档检测要比较它们，
     * 「降了多少」要拿去换算资源。所以这里返回新值，旧值由调用方自己先读一次。
     */
    public double add(UUID player, double delta) {
        set(player, get(player) + delta);
        return get(player);
    }

    /** 按比例削减，返回<b>被削掉的那部分</b>（正数）。需求里好几处要把这笔损失换算成资源。 */
    public double reduceByRatio(UUID player, double ratio) {
        double before = get(player);
        double lost = before * clampRatio(ratio);
        set(player, before - lost);
        return lost;
    }

    // ------------------------------------------------------------------
    // 暂存资源
    // ------------------------------------------------------------------

    /** 当前暂存了多少资源。 */
    public double pending() {
        return pending;
    }

    /** 往暂存里加一笔。 */
    public void addPending(double amount) {
        if (amount <= 0.0D) {
            return;
        }
        pending += amount;
        setDirty();
    }

    /**
     * 取出并清空暂存，交给调用方去发给心智。
     *
     * <p>先取后发而不是「发完再清」：发的过程里可能一只心智都没有（于是调用方又把钱存回来），
     * 那种来回不会丢数。反过来「发完再清」在发失败时会把钱吞掉。
     */
    public double drainPending() {
        double amount = pending;
        pending = 0.0D;
        if (amount > 0.0D) {
            setDirty();
        }
        return amount;
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Double> entry : hatred.entrySet()) {
            players.putDouble(entry.getKey().toString(), entry.getValue());
        }
        tag.put(KEY_PLAYERS, players);
        tag.putDouble(KEY_PENDING, pending);
        return tag;
    }

    private static HatredData load(CompoundTag tag) {
        HatredData data = new HatredData();
        CompoundTag players = tag.getCompound(KEY_PLAYERS);
        for (String key : players.getAllKeys()) {
            // UUID.fromString 对格式错误会抛异常，而存档里的键理论上都是我们自己写进去的。
            // 仍然吞掉单条的异常：一个坏键不该让整份恨意值归零。
            try {
                data.hatred.put(UUID.fromString(key), clamp(players.getDouble(key)));
            } catch (IllegalArgumentException ignored) {
                // 跳过这一条
            }
        }
        data.pending = Math.max(0.0D, tag.getDouble(KEY_PENDING));
        data.recomputeTotal();
        return data;
    }

    // ------------------------------------------------------------------

    private void recomputeTotal() {
        double sum = 0.0D;
        for (double value : hatred.values()) {
            sum += value;
        }
        total = sum;
    }

    private static double clamp(double value) {
        return value > 0.0D ? value : 0.0D;
    }

    private static double clampRatio(double ratio) {
        if (ratio <= 0.0D) {
            return 0.0D;
        }
        return Math.min(1.0D, ratio);
    }
}
