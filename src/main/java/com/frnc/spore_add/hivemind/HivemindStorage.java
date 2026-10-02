package com.frnc.spore_add.hivemind;

import java.util.ArrayList;
import java.util.List;

import com.Harbinger.Spore.Core.Sentities;
import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.world.SafeSpot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * 心智的<b>生物存储</b>：把制造出来的真菌收起来，需要时再投放。
 *
 * <h2>存的是什么</h2>
 * 每条记录是一个生物的完整 NBT（{@code Entity#save} 的结果，含 {@code id}），
 * 外加一个「本次袭击已投放」的标记。收起来时那个实体<b>被丢弃</b>——所以它不以实体形式存在，
 * Spore 的消失管理系统根本看不到它（这正是需求里"避免被处理"的目的）。
 *
 * <h2>存在哪</h2>
 * 存在<b>心智自己的持久数据</b>里（{@code Proto#getPersistentData}）。好处有三条：
 * <ul>
 *   <li>天然按心智分开，不同心智各存各的；</li>
 *   <li>随实体存盘，重启后还在；</li>
 *   <li>心智死了存储跟着消失——不需要额外的清理逻辑，也不会留下一份没人认领的孤儿数据。</li>
 * </ul>
 *
 * <h2>投放是「复制」，但每次袭击只投放一次</h2>
 * 需求的两句话合起来是：投放后<b>原条目仍然留着</b>（不是消耗掉），但同一次袭击里
 * <b>已经投放过的条目不能再投</b>。所以每条记录带一个 {@code deployed} 标记：
 * <ul>
 *   <li>{@link #deploy} 只挑没标记的，投完打上标记；</li>
 *   <li>{@link #resetDeployedMarks} 在一次袭击结束时把所有标记清掉——下次袭击它们又能用了。</li>
 * </ul>
 * 于是"存着的兵"是一份打不完的家底，但一场仗里不能靠同一批兵反复填线。
 */
public final class HivemindStorage {

    /** 存储在心智持久数据里的键。 */
    private static final String KEY_STORED = "spore_add:stored";

    /** 每条记录里"本次袭击已投放"的键。 */
    private static final String KEY_DEPLOYED = "spore_add:deployed";

    private HivemindStorage() {
    }

    // ------------------------------------------------------------------
    // 收进存储
    // ------------------------------------------------------------------

    /**
     * 试着把这只生物收进存储。收下了返回 true（调用方应当把它丢弃），没位置返回 false。
     *
     * <p>用实体自身的 {@code save} 而不是 {@code saveWithoutId}：后者不带 {@code id}，
     * 而 {@code EntityType.create} 要靠那个字段才知道该造出什么来。
     */
    public static boolean tryStore(Proto hivemind, Entity entity) {
        if (!SporeAddFungusConfig.hivemindStoreEnabled()) {
            return false;
        }
        ListTag stored = storedTag(hivemind);
        if (stored.size() >= SporeAddFungusConfig.hivemindStoreMaxCount()) {
            return false;
        }
        CompoundTag entry = new CompoundTag();
        entity.save(entry);
        stored.add(entry);
        return true;
    }

    // ------------------------------------------------------------------
    // 投放
    // ------------------------------------------------------------------

    /**
     * 从存储里投放最多 {@code limit} 只，投在 {@code center} 附近。
     *
     * <p>只挑<b>本次袭击还没投过</b>的条目；投出去的那些会打上标记，本次袭击不会再被选中。
     * 已经在世界里跑着的那一份是<b>副本</b>——原条目仍然留在存储里。
     *
     * @return 实际投放了几只
     */
    public static int deploy(Proto hivemind, ServerLevel level, Vec3 center, int limit) {
        if (limit <= 0) {
            return 0;
        }
        ListTag stored = storedTag(hivemind);
        int deployed = 0;
        for (int i = 0; i < stored.size() && deployed < limit; i++) {
            CompoundTag entry = stored.getCompound(i);
            if (entry.getBoolean(KEY_DEPLOYED)) {
                continue;
            }
            Mob mob = spawnOne(level, entry, center);
            if (mob == null) {
                continue;
            }
            entry.putBoolean(KEY_DEPLOYED, true);
            level.addFreshEntity(mob);
            deployed++;
        }
        return deployed;
    }

    /**
     * 从一条记录造出生物。造不出来（记录损坏、实体类型已不存在）时返回 null 并跳过。
     *
     * <p><b>落点必须真的站得住</b>，不能照着坐标硬塞：投放中心是「心智自己的坐标」，
     * 而心智可能正卡在自己的穹顶壳里（壳只在它移动超过 10 格时才重新居中），
     * 于是硬塞的实体会被埋进生物质方块——症状是卡在半空不动、而且因为隔着方块打不到。
     * 所以这里用 {@link SafeSpot#near} 先做空间与区块检查，被占住就绕中心找空位；
     * 上限内都找不到就<b>这一只不投</b>（返回 null，原条目留在存储里，下次还有机会），
     * 也不制造一个卡死的实体。
     */
    private static Mob spawnOne(ServerLevel level, CompoundTag entry, Vec3 center) {
        // EntityType.create 读记录里的 id 字段去查注册表，返回 Optional：
        // 查不到（Spore 版本变化导致某种实体不存在了）就是"少投一只"，而不是崩掉
        Entity entity = EntityType.create(entry, level).orElse(null);
        if (!(entity instanceof Mob mob)) {
            return null;
        }
        Vec3 spot = SafeSpot.near(mob, center, 0.0D, SporeAddFungusConfig.hivemindDeployScatterRadius());
        if (spot == null) {
            return null;
        }
        // 与 Spore 自己的 summonMob 一样打上来源标记，让它的 AI 知道自己从哪来
        CompoundTag data = mob.getPersistentData();
        data.putInt("hivemind", 0);
        data.putInt("decision", 0);
        mob.moveTo(spot.x, spot.y, spot.z, mob.getYRot(), mob.getXRot());
        return mob;
    }

    /** 把"本次袭击已投放"的标记全部清掉——一次袭击结束时调。 */
    public static void resetDeployedMarks(Proto hivemind) {
        ListTag stored = storedTag(hivemind);
        for (int i = 0; i < stored.size(); i++) {
            stored.getCompound(i).putBoolean(KEY_DEPLOYED, false);
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 一共存了多少只。 */
    public static int count(Proto hivemind) {
        return storedTag(hivemind).size();
    }

    /** 本次袭击还能投多少只（没打过标记的那些）。 */
    public static int availableCount(Proto hivemind) {
        ListTag stored = storedTag(hivemind);
        int available = 0;
        for (int i = 0; i < stored.size(); i++) {
            if (!stored.getCompound(i).getBoolean(KEY_DEPLOYED)) {
                available++;
            }
        }
        return available;
    }

    /**
     * 存储被破坏时的"漏出来"：把随机若干条已存生物直接放到世界里。
     *
     * <p>需求里"穹顶被破坏时，存储的生物有概率自行出现"。被打散的是<b>已经投放过的那些</b>
     * 优先——它们本来就在本次袭击里露过面，再漏一次不算额外收益；数量不足时才轮到没用过的。
     *
     * @return 实际漏出来几只
     */
    public static int spill(Proto hivemind, ServerLevel level, Vec3 center, int limit) {
        if (limit <= 0) {
            return 0;
        }
        ListTag stored = storedTag(hivemind);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < stored.size(); i++) {
            order.add(i);
        }
        // 已投放的排前面
        order.sort((a, b) -> Boolean.compare(
                !stored.getCompound(b).getBoolean(KEY_DEPLOYED),
                !stored.getCompound(a).getBoolean(KEY_DEPLOYED)));

        int spilled = 0;
        for (int index : order) {
            if (spilled >= limit) {
                break;
            }
            Mob mob = spawnOne(level, stored.getCompound(index), center);
            if (mob != null) {
                level.addFreshEntity(mob);
                spilled++;
            }
        }
        return spilled;
    }

    // ------------------------------------------------------------------

    /**
     * 取（必要时新建）那个列表。
     *
     * <p>存的是 {@code ListTag}，元素是 CompoundTag。用 {@code getList} 时要注意它只在
     * 类型匹配时返回原列表，所以这里统一先取再判空——写坏过一次也不该让整份存储消失。
     */
    private static ListTag storedTag(Proto hivemind) {
        CompoundTag data = hivemind.getPersistentData();
        if (!data.contains(KEY_STORED, Tag.TAG_LIST)) {
            ListTag fresh = new ListTag();
            data.put(KEY_STORED, fresh);
            return fresh;
        }
        return data.getList(KEY_STORED, Tag.TAG_COMPOUND);
    }

    /** 这个实体类型是不是可存储的（现在只排除那些不该被收起来的：心智自己与竞技之须）。 */
    public static boolean isStorable(Entity entity) {
        if (!(entity instanceof Mob)) {
            return false;
        }
        EntityType<?> type = entity.getType();
        // 心智、以及 Spore 的竞技之须都不该被塞进存储：
        // 前者是主人，后者是环境装置（它自己没有 AI 可跑）
        return type != Sentities.PROTO.get() && type != Sentities.ARENA_TENDRIL.get();
    }
}
