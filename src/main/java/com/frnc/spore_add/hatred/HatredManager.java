package com.frnc.spore_add.hatred;

import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.fungus.Resources;
import com.frnc.spore_add.raid.RaidManager;

import net.minecraft.server.level.ServerPlayer;

/**
 * 改动恨意值的<b>唯一</b>入口。
 *
 * <h2>为什么要有这么一层</h2>
 * 需求里改恨意值的地方有六七处（击杀、死亡、吃食物、击杀心智、打赢袭击……），
 * 而每次改动之后要做的事情是同一套，且漏一件就会出问题：
 * <ol>
 *   <li><b>刷新玩家增益</b>——属性修饰符不会自己跟着变；</li>
 *   <li><b>越档检测并掷触发骰</b>——需求 5 的 50% 概率触发袭击。</li>
 * </ol>
 * 让每个事件各写一遍这套，迟早会漏掉其中一份（表现是"属性加成停在旧值"这种很难查的问题）。
 * 所以全部收敛到这里，事件层只负责"算出该变多少"。
 *
 * <p><b>原先还有第三步"同步给客户端"</b>（喂屏幕上的 HUD 数字）。HUD 已按需求删除，
 * 那一步连同它的包与客户端缓存一起没了——现在想看恨意值只有两个服务端入口：
 * {@code /spore_add hatred} 命令与扫描仪。
 *
 * <h2>越档检测为什么用序号比较</h2>
 * 记下改动前后的值，各自取 {@link HatredValues#thresholdIndex}，只有序号<b>变大</b>才算跨档。
 * 这样"在档位边界附近反复涨落"不会反复触发，而"一次拿走两大档"也只触发一次——
 * 两次需求里的"每提高到一定程度"指的正是这种跨档。
 */
public final class HatredManager {

    private HatredManager() {
    }

    // ------------------------------------------------------------------
    // 改动入口
    // ------------------------------------------------------------------

    /** 增加恨意值（击杀真菌等）。 */
    public static void add(ServerPlayer player, double amount) {
        if (amount <= 0.0D) {
            return;
        }
        HatredData data = HatredData.get(player.serverLevel());
        double before = data.get(player.getUUID());
        double after = data.add(player.getUUID(), amount);
        afterChange(player, before, after);
    }

    /**
     * 按比例削减，返回<b>被削掉的那部分</b>。
     *
     * <p>返回值是给"死亡补偿"那一处用的：需求要把损失的那笔换算成资源发给心智，
     * 所以必须知道究竟掉了多少，而不是只知道新值。
     */
    public static double reduceByRatio(ServerPlayer player, double ratio) {
        if (ratio <= 0.0D) {
            return 0.0D;
        }
        HatredData data = HatredData.get(player.serverLevel());
        double before = data.get(player.getUUID());
        double lost = data.reduceByRatio(player.getUUID(), ratio);
        afterChange(player, before, data.get(player.getUUID()));
        return lost;
    }

    /** 直接设定（命令用）。 */
    public static void set(ServerPlayer player, double value) {
        HatredData data = HatredData.get(player.serverLevel());
        double before = data.get(player.getUUID());
        data.set(player.getUUID(), value);
        afterChange(player, before, data.get(player.getUUID()));
    }

    /**
     * 玩家<b>输给真菌</b>时的那笔账：按比例削他的恨意值，并把损失换算成资源给所有心智。
     *
     * <h2>两个入口共用这一份</h2>
     * <ul>
     *   <li>死于真菌之手（需求 6）——{@code HatredEvents#onDeath} 里那条；</li>
     *   <li>袭击失败（{@code arenaTimeoutSeconds} 到点时仍没把场上真菌清干净）——
     *       {@code RaidManager#tickArena} 那条。</li>
     * </ul>
     * 需求明确说了后者"走玩家被真菌击杀的那条线"，所以两处必须是<b>同一段代码</b>，
     * 而不是照着参数各写一遍——否则以后调了 `death` 段的任何一个数，两边就会不一致。
     *
     * <p>资源那笔用的是 {@code death.resourceMultiplier}（默认 ×2）。发放规则
     * （含"一只心智都没有就先存起来"）在 {@code Resources} 里，见那个类的类注释。
     *
     * @return 被削掉的恨意值
     */
    public static double punishPlayerDefeat(ServerPlayer player) {
        double lost = reduceByRatio(player, SporeAddFungusConfig.deathLossRatio());
        if (lost <= 0.0D) {
            return 0.0D;
        }
        Resources.deliverToAll(player.serverLevel(), lost * SporeAddFungusConfig.deathResourceMultiplier());
        return lost;
    }

    /** 只读：这个玩家的个人恨意值。 */
    public static double of(ServerPlayer player) {
        return HatredData.get(player.serverLevel()).get(player.getUUID());
    }

    /** 只读：世界恨意值。 */
    public static double worldTotal(ServerPlayer player) {
        return HatredData.get(player.serverLevel()).total();
    }

    // ------------------------------------------------------------------

    /**
     * 每次改动之后的统一收尾。见类注释。
     *
     * <p>顺序无所谓，但三件事都要做。掷骰用玩家自己的随机源（不是 {@code Math.random()}），
     * 这样在同一个世界里它是可复现的、也不与别的模组抢全局随机流。
     */
    private static void afterChange(ServerPlayer player, double before, double after) {
        PlayerHatredBuffs.refresh(player);

        if (HatredValues.thresholdIndex(after) > HatredValues.thresholdIndex(before)
                && player.getRandom().nextDouble() < SporeAddFungusConfig.raidTriggerChance()) {
            RaidManager.tryStart(player);
        }
    }
}
