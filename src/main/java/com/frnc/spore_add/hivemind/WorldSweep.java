package com.frnc.spore_add.hivemind;

import java.util.ArrayList;
import java.util.List;

import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.SporeAddDebugConfig.Area;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.debug.SporeAddDebug;
import com.frnc.spore_add.fungus.LootValues;
import com.frnc.spore_add.fungus.Resources;
import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 需求的「清扫世界」：某个维度里心智够多时，它们会联合起来把整片地方扫一遍。
 *
 * <h2>触发是三重闸门</h2>
 * <ol>
 *   <li><b>该维度里的心智数 >= {@code hivemind.sweep.minHiveminds}</b>（默认 3）——
 *       这条保证它只出现在心智成气候的地方，世界早期根本不会触发；</li>
 *   <li><b>周期到点</b>（{@code intervalMinutes}，默认 20 分钟）；</li>
 *   <li><b>掷中 {@code chance}</b>（默认 50%）。</li>
 * </ol>
 * 所以它是一件稀有事：默认配置下平均 40 分钟才发动一次。
 *
 * <h2>为什么用「游戏刻取模」而不是存一个倒计时</h2>
 * 倒计时要么存盘（多一份要维护的存档状态），要么放内存（服务器一重启就从头数，
 * 频繁重启的服上可能永远等不到 20 分钟）。而 {@code getGameTime()} 是单调递增且<b>本身存盘</b>的，
 * 于是「到点」这件事可以直接由它算出来，不需要我们记任何东西——重启后接着走同一个节奏。
 *
 * <p>各维度按自己的 id 散列出一个相位偏移，所以它们不会在同一拍里一起清扫；
 * 反过来，同一个维度每次都在固定的相位上判定，节奏是稳定的。
 *
 * <h2>效果：把掉落物一次性收走，折算成资源</h2>
 * <b>不掷转化概率</b>——它是"一扫而空"，不是"逐件捡"。所以
 * <pre>
 *   总资源 = Σ(单价 × 数量) × 倍率
 *   倍率   = multiplier + (心智数 - minHiveminds) × perExtraHivemind
 * </pre>
 * 默认下 3 只心智 2.0 倍、5 只 3.0 倍、10 只 5.5 倍。资源交给
 * {@link Resources#deliverToAll}，也就是<b>按心智数均分</b>给这个维度里的心智——
 * 「联合发动」在资源分配上也成立。
 *
 * <h2>它不豁免任何掉落物</h2>
 * 玩家丢出的、死亡掉落的，一样会被收走。这是刻意的（需求要的就是"清扫世界"），
 * 但也意味着**玩家死了之后如果 20 分钟内没跑回去捡，东西就没了**。想要豁免的话，
 * 判据应该加在 {@link #sweep} 的过滤里（{@code ItemEntity#getOwner()} 能认出玩家丢的），
 * 而不是在别处打补丁。
 *
 * <h2>一个会削弱它实际体感的事实</h2>
 * 原版 {@code ItemEntity} <b>6000 tick（5 分钟）就自然消失</b>，而且只在所在区块加载时计时。
 * 所以 20 分钟一次的清扫，在原版行为下能扫到的多半只是<b>玩家附近那 5 分钟内掉的</b>东西。
 * 区块未加载处的掉落物倒是会一直留着——它们才是这个能力真正会清掉的那一批。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID)
public final class WorldSweep {

    private static final Logger LOGGER = LogUtils.getLogger();

    private WorldSweep() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!SporeAddFungusConfig.hivemindSweepEnabled()) {
            return;
        }
        int period = SporeAddFungusConfig.hivemindSweepIntervalTicks();
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            tickLevel(level, period);
        }
    }

    /** 这个维度到点了吗；到点就掷骰、够格就清扫。 */
    private static void tickLevel(ServerLevel level, int period) {
        // 各维度错开相位，别在同一拍里一起清扫（那会在一帧里做完全部工作）
        int phase = Math.floorMod(level.dimension().location().hashCode(), period);
        if (Math.floorMod(level.getGameTime() + phase, period) != 0L) {
            return;
        }

        // 下面两条闸门都在"到点"之后才走到（默认 20 分钟一次、每维度错开相位），
        // 所以可以放心打日志。上面那条相位闸门**不能**打——它每 tick 都会命中一次。
        List<Proto> hiveminds = hivemindsIn(level);
        int count = hiveminds.size();
        if (count < SporeAddFungusConfig.hivemindSweepMinHiveminds()) {
            SporeAddDebug.log(Area.HIVEMIND, "清扫判定 @ {}：心智 {} 只 / 需要 {} 只，不发动",
                    level.dimension().location(), count, SporeAddFungusConfig.hivemindSweepMinHiveminds());
            return;
        }
        double chance = SporeAddFungusConfig.hivemindSweepChance();
        double roll = level.random.nextDouble();
        if (roll >= chance) {
            SporeAddDebug.log(Area.HIVEMIND, "清扫判定 @ {}：心智 {} 只，概率 {}、掷出 {}，不发动",
                    level.dimension().location(), count, chance, roll);
            return;
        }

        int stacks = sweep(level, count);
        if (stacks > 0) {
            LOGGER.info("[SporeAdd] 清扫世界发动：{} 维度收走 {} 堆掉落物（{} 只心智参战）",
                    level.dimension().location(), stacks, count);
            announce(level);
        }
    }

    /**
     * 把这个维度里所有掉落物收走并折算成资源。
     *
     * <p><b>先收集、再统一删</b>：{@code getAllEntities()} 是活着的集合视图，
     * 边遍历边 {@code discard()} 有并发修改的风险（{@code RaidManager} 那边踩过同一个坑）。
     *
     * @return 收走了多少堆（0 表示地上什么都没有，那就当作没发生、也不公告）
     */
    private static int sweep(ServerLevel level, int hivemindCount) {
        List<ItemEntity> doomed = new ArrayList<>();
        double totalValue = 0.0D;

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof ItemEntity item) || item.isRemoved()) {
                continue;
            }
            ItemStack stack = item.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            // 不掷转化概率：清扫是一次性的"一扫而空"，按满价值算，倍率见配置
            totalValue += LootValues.pricingOf(stack).value() * stack.getCount();
            doomed.add(item);
        }
        if (doomed.isEmpty()) {
            return 0;
        }
        for (ItemEntity item : doomed) {
            item.discard();
        }

        // 倍率随参战心智数上浮；资源按心智数均分给它们——「联合发动」在分配上也成立
        Resources.deliverToAll(level, totalValue * SporeAddFungusConfig.hivemindSweepMultiplier(hivemindCount));
        return doomed.size();
    }

    /** 这个维度里还活着的心智。 */
    private static List<Proto> hivemindsIn(ServerLevel level) {
        List<Proto> found = new ArrayList<>();
        for (Proto hivemind : SporeCompat.hiveminds()) {
            if (hivemind.level() == level && !hivemind.isRemoved() && hivemind.isAlive()) {
                found.add(hivemind);
            }
        }
        return found;
    }

    /**
     * 告诉这个维度里的玩家发生了什么。
     *
     * <p>这一条不是装饰：整片地上的掉落物凭空消失，没有提示的话看起来只会像 bug。
     */
    private static void announce(ServerLevel level) {
        Component message = Component.translatable("message.spore_add.sweep");
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }
}
