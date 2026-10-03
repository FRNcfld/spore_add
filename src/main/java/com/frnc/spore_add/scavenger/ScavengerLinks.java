package com.frnc.spore_add.scavenger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.fungus.FungusCombat;

import net.minecraft.nbt.LongArrayTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 拾荒者的「链接」关系：它链了哪些同伴，那些同伴就是它额外的<b>搜索圆心</b>。
 *
 * <h2>为什么不复用 Spore 的 {@code linked}</h2>
 * 已核实（反编译 2.2.0j）：{@code Infected.getLinked()} 是一个<b>同步布尔</b>，
 * 由心智 {@code Proto#scanForHosts} 每 1200 tick 给范围内的感染体置 {@code true}，
 * 而<b>全 Spore 没有任何一处把它置回 false</b>。也就是说：
 * <ul>
 *   <li>它是"被心智登记过"的<b>全局标记</b>，<b>没有 owner 指针</b>——不区分是哪个心智链的；</li>
 *   <li>一旦为真就永远为真，表达不了"走散就断"；</li>
 *   <li>它还被别处读：恨意值的 {@code linkedMultiplier}、心智死亡时给已链接者上凋零。
 *       我们去改它等于污染这些语义。</li>
 * </ul>
 * 所以链接关系<b>完全活在我们自己的持久数据里</b>，只<b>读</b> Spore 那个布尔来判断
 * 「我是否隶属于某个心智」，从而决定走 1~3 档还是 2~6 档上限。
 *
 * <h2>存哪、怎么存</h2>
 * 存在拾荒者自己的 {@code getPersistentData()} 里，键 {@code spore_add:scavenger_links}，
 * 内容是一串 UUID 打成的 {@code long[]}（每个 UUID 两个 long）。
 *
 * <p><b>为什么不每 tick 现扫</b>：现扫没有陈旧状态，但（1）每 tick 扫实体盒有成本；
 * （2）关系会抖动——两只真菌恰好错开一个区块就会"断链—重链"；
 * （3）"上限随存活爬升后把空出的名额补上"本质上要求一个<b>跨 tick 稳定</b>的集合。
 * 存下来的代价只是"UUID 可能指向已死/已卸载的实体"，而那正好由 {@link #resolve} 处理掉。
 *
 * <h2>走散与死亡</h2>
 * {@link #resolve} 每次都用 {@code level.getEntity(uuid)} 解析并校验，解析不到（死亡、卸载、
 * 换维度）就丢掉。距离判据分两种：
 * <ul>
 *   <li><b>直连</b>的同伴：必须还在 {@code linkBreakRadius} 之内；</li>
 *   <li><b>经心智</b>链上的：判据落在<b>心智</b>身上（它得在断裂半径内），
 *       而同伴只要还在那个心智的扫描盒里就算数——它们本来就是围着我方心智的，
 *       不该因为离拾荒者远而被判成走散。</li>
 * </ul>
 */
public final class ScavengerLinks {

    /** 链接名单存在实体持久数据里的键。 */
    private static final String KEY_LINKS = "spore_add:scavenger_links";

    private ScavengerLinks() {
    }

    /**
     * 重建一次链接：先校验旧的，再把空出的名额补上，最后写回。
     *
     * <p>由 {@code Scavenger#tick} 按 {@code linkScanIntervalTicks} 驱动。
     * 顺序是固定的「先校验、后补员」——先补后校验的话，刚补进来的可能当拍就被判走散。
     */
    public static void refresh(Scavenger scavenger) {
        List<LivingEntity> alive = resolve(scavenger);
        int cap = capFor(scavenger);
        if (alive.size() < cap) {
            topUp(scavenger, alive, cap);
        }
        write(scavenger, alive);
    }

    /** 当前还认得出来的链接对象。每次调用都重新解析校验，所以不会给出已经死掉的目标。 */
    public static List<LivingEntity> linked(Scavenger scavenger) {
        return resolve(scavenger);
    }

    /** 这只拾荒者现在能链几个。 */
    public static int capFor(Scavenger scavenger) {
        return SporeAddFungusConfig.scavengerLinkCap(scavenger.survivalMinutes(), scavenger.getLinked());
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    /** 解析并校验名单，丢掉失效的那些。返回的是还活着的。 */
    private static List<LivingEntity> resolve(Scavenger scavenger) {
        List<LivingEntity> alive = new ArrayList<>();
        if (!(scavenger.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return alive;
        }
        double breakSqr = Math.pow(SporeAddFungusConfig.scavengerLinkBreakRadius(), 2.0D);
        Proto hivemind = nearbyHivemind(scavenger);

        for (UUID id : read(scavenger)) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof LivingEntity candidate) || candidate.isRemoved() || !candidate.isAlive()) {
                continue;
            }
            // 另一只拾荒者不做圆心：它自己也在满地图跑，而且跟你抢同一批掉落物
            if (candidate instanceof Scavenger) {
                continue;
            }
            if (scavenger.distanceToSqr(candidate) <= breakSqr) {
                alive.add(candidate);   // 直连：离我不远
            } else if (hivemind != null && hivemind.seachbox().contains(candidate.position())) {
                alive.add(candidate);   // 经心智：它在我方心智的扫描盒里，离我远也算数
            }
        }
        return alive;
    }

    /** 断裂半径内最近的心智；没有就返回 null。 */
    private static Proto nearbyHivemind(Scavenger scavenger) {
        if (!scavenger.getLinked()) {
            return null;   // 没被心智登记过，也就谈不上"麾下"
        }
        double breakSqr = Math.pow(SporeAddFungusConfig.scavengerLinkBreakRadius(), 2.0D);
        Proto nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Proto hivemind : SporeCompat.hiveminds()) {
            if (hivemind.level() != scavenger.level() || hivemind.isRemoved() || !hivemind.isAlive()) {
                continue;
            }
            double distance = scavenger.distanceToSqr(hivemind);
            if (distance <= breakSqr && distance < nearestDistance) {
                nearestDistance = distance;
                nearest = hivemind;
            }
        }
        return nearest;
    }

    // ------------------------------------------------------------------
    // 补员
    // ------------------------------------------------------------------

    /** 把空出的名额补满。优先经心智那一批，不够再用直连的填。 */
    private static void topUp(Scavenger scavenger, List<LivingEntity> alive, int cap) {
        Set<UUID> taken = new HashSet<>();
        for (LivingEntity linked : alive) {
            taken.add(linked.getUUID());
        }
        Proto hivemind = nearbyHivemind(scavenger);
        if (hivemind != null) {
            // 心智麾下的真菌 = 它扫描盒里那些**已链接**的感染体。Spore 里没有成员名单，
            // 而 seachbox() 就是它每次登记时用的那个盒子（受 Spore 的 proto_range 膨胀），
            // 所以"在盒子里 + 已链接"正是"麾下"的定义。
            fill(scavenger, hivemind.seachbox(), taken, alive, cap);
        }
        if (alive.size() < cap) {
            double radius = SporeAddFungusConfig.scavengerLinkScanRadius();
            AABB area = scavenger.getBoundingBox().inflate(radius);
            fill(scavenger, area, taken, alive, cap);
        }
    }

    /** 在给定范围里按距离由近到远挑同伴，直到名额满。 */
    private static void fill(Scavenger scavenger, AABB area, Set<UUID> taken,
                             List<LivingEntity> alive, int cap) {
        if (alive.size() >= cap) {
            return;
        }
        List<LivingEntity> candidates = scavenger.level().getEntitiesOfClass(LivingEntity.class, area).stream()
                .filter(candidate -> candidate != scavenger && !candidate.isRemoved() && candidate.isAlive())
                .filter(FungusCombat::isFungus)
                // 另一只拾荒者排最后：它自己也在动，圆心会重复覆盖，还跟你抢同一批掉落物
                .sorted(Comparator
                        .comparing((LivingEntity candidate) -> candidate instanceof Scavenger)
                        .thenComparingDouble(scavenger::distanceToSqr))
                .toList();

        for (LivingEntity candidate : candidates) {
            if (alive.size() >= cap) {
                return;
            }
            if (taken.add(candidate.getUUID())) {
                alive.add(candidate);
            }
        }
    }

    // ------------------------------------------------------------------
    // 存盘
    // ------------------------------------------------------------------

    private static List<UUID> read(Scavenger scavenger) {
        long[] packed = scavenger.getPersistentData().getLongArray(KEY_LINKS);
        List<UUID> ids = new ArrayList<>(packed.length / 2);
        for (int i = 0; i + 1 < packed.length; i += 2) {
            ids.add(new UUID(packed[i], packed[i + 1]));
        }
        return ids;
    }

    private static void write(Scavenger scavenger, List<LivingEntity> alive) {
        long[] packed = new long[alive.size() * 2];
        for (int i = 0; i < alive.size(); i++) {
            UUID id = alive.get(i).getUUID();
            packed[i * 2] = id.getMostSignificantBits();
            packed[i * 2 + 1] = id.getLeastSignificantBits();
        }
        scavenger.getPersistentData().put(KEY_LINKS, new LongArrayTag(packed));
    }

    /** 只在调试/文档里用得上：一串链接对象的坐标。 */
    public static List<Vec3> centers(Scavenger scavenger) {
        List<Vec3> centers = new ArrayList<>();
        for (LivingEntity linked : linked(scavenger)) {
            centers.add(linked.position());
        }
        return centers;
    }
}
