package com.frnc.spore_add.fungus;

import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.hatred.HatredData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 「把一笔资源交给真菌阵营」的唯一出口。
 *
 * <p>三条路会产出资源：玩家死于真菌之手（需求 6，{@code ×2} 的补偿）、
 * 真菌捡到掉落物（新机制 1）、Despawning System 清理掉真菌（新机制 2）。
 * 三者的<b>发放规则</b>只有两种——撒给全体、或交给最近的那个——但
 * <b>"发不出去就存起来"</b>这一步是完全一样的，所以收在这里，免得三处各写一遍、
 * 其中一处忘了处理"一只心智都没有"的情形（那笔资源就凭空消失了）。
 *
 * <h2>为什么要连暂存一起发</h2>
 * 每次发放都会把暂存区里欠着的一并倒出来（{@link HatredData#drainPending()}）。
 * 需求写的是"若无心智则暂时存储"，那么"存着"的状态应该在<b>有心智的第一个瞬间</b>结束，
 * 而不是等下一次恰好又有资源进来才顺带补发——后者会让暂存一直躺着不动。
 *
 * <p>代价是：玩家先死一次（欠下 200 点），然后某只小兵捡到一块腐肉（1 点），
 * 那 200 点会在这一刻一次性到账。这是刻意的——债本来就该在有收款人时立刻还清。
 */
public final class Resources {

    private Resources() {
    }

    /**
     * 撒给<b>所有</b>心智，均分。用在全阵营性质的收入上（玩家死亡补偿、系统清理回收）。
     *
     * <p>各维度的心智都会分到——"全阵营"就该是全域的，不像捡东西那样有地理限制。
     */
    public static void deliverToAll(ServerLevel level, double amount) {
        int budget = budgetWithPending(level, amount);
        if (budget <= 0) {
            return;
        }
        returnLeftover(level, SporeCompat.grantResourcesToHiveminds(budget));
    }

    /**
     * 交给<b>离该位置最近的</b>心智。用在"某只真菌捡到了东西"这条路上。
     *
     * <p>只认同维度的心智，理由见 {@code SporeCompat#grantResourcesToNearestHivemind}。
     */
    public static void deliverToNearest(ServerLevel level, Vec3 pos, double amount) {
        int budget = budgetWithPending(level, amount);
        if (budget <= 0) {
            return;
        }
        returnLeftover(level, SporeCompat.grantResourcesToNearestHivemind(level, pos, budget));
    }

    // ------------------------------------------------------------------

    /**
     * 本次实际能发出去多少：新到账的 + 暂存里欠着的。
     *
     * <p>取整用 {@code floor}：暂存的小数部分原样留在暂存里（下面只减掉发出去的整数部分），
     * 于是反复进来 {@code 0.6} 点也不会因为每次都取整成 0 而永远攒不起来。
     */
    private static int budgetWithPending(ServerLevel level, double amount) {
        HatredData data = HatredData.get(level);
        double total = Math.max(0.0D, amount) + data.pending();
        int whole = (int) Math.floor(total);
        if (whole <= 0) {
            // 攒着还不够 1 点：把新的零头并进暂存，下次继续
            if (amount > 0.0D) {
                data.addPending(amount);
            }
            return 0;
        }
        // 只从暂存里扣掉"发出去了多少"，零头留着
        data.drainPending();
        double remainder = total - whole;
        if (remainder > 0.0D) {
            data.addPending(remainder);
        }
        return whole;
    }

    /** 没发出去的部分退回暂存。 */
    private static void returnLeftover(ServerLevel level, int leftover) {
        if (leftover > 0) {
            HatredData.get(level).addPending(leftover);
        }
    }
}
