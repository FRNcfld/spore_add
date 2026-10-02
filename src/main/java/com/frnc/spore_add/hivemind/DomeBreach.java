package com.frnc.spore_add.hivemind;

import java.util.List;
import java.util.Set;

import com.Harbinger.Spore.Core.Sblocks;
import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.compat.SporeCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 生物质穹顶被破坏时，把心智存储里的生物「漏」出来几头。
 *
 * <h2>穹顶是什么（反编译 Spore 2.2.0j 确认）</h2>
 * 心智（{@code Proto}）实现 {@code CasingGenerator}，它的 {@code generateCasing()} 调了两次
 * {@code generateChasing}：<b>半径 32、厚 2</b> 一层，<b>半径 16、厚 1</b> 一层——
 * 两层同心球壳（这就是「生物质穹顶」和「内层壳」的本体，中心是心智的 {@code NODE}，
 * 也就是 {@code getOnPos()}）。壳是逐块长出来的：每个候选位置只有 10% 概率放一块，
 * 且要求旁边已经有实心方块，所以它是稀疏的、越攒越厚。
 *
 * <p>壳用的方块就是 {@code CasingGenerator#possibleBlocks()} 那一份表（见 {@link Refs}）。
 * 顺带纠正一个容易搞错的点：{@code spore:fungal_shell}（真菌伞壳）<b>确实存在</b>，
 * 但它只被 {@code createFungalStalks} 当作竖直「菌柄」的装饰方块，<b>不是</b>穹顶的一部分，
 * 所以砸它不该触发漏出。
 *
 * <h2>为什么用破坏事件而不是定时盘点</h2>
 * 穹顶是稀疏的、还会持续生长，所以没有一个稳定的「完整值」可以拿来比对损坏程度；
 * 而「被破坏」本身是明确的、由玩家或爆炸触发的事件。做成事件既准确又几乎不花性能——
 * 定时去数半径 32 球壳里的方块，每只心智每次都要扫十几万个位置，那才是真的贵。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID)
public final class DomeBreach {

    /** 上次漏出的冷却记在心智自己的持久数据里，键存「下次允许漏出的游戏刻」。 */
    private static final String KEY_COOLDOWN = "spore_add:spill_cooldown";

    private DomeBreach() {
    }

    /** 玩家砸掉一块方块。 */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tryBreach(level, event.getPos());
        }
    }

    /**
     * 爆炸波及的方块。
     *
     * <p>一次爆炸可能掀掉几十块穹顶，但冷却只允许漏一次，所以一旦这次爆炸真的
     * 「用掉」了一次机会就立刻收手——不继续对剩下的方块做无用判定。
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<BlockPos> affected = event.getAffectedBlocks();
        for (int i = 0; i < affected.size(); i++) {
            if (tryBreach(level, affected.get(i))) {
                return;
            }
        }
    }

    /**
     * 试着因为「{@code pos} 这块壳被破坏」而漏一次。
     *
     * @return true 表示这次机会已经被用掉（冷却已写入），调用方不必再看别的方块
     */
    private static boolean tryBreach(ServerLevel level, BlockPos pos) {
        int limit = SporeAddFungusConfig.hivemindSpillCount();
        if (limit <= 0) {
            return false;   // 漏出机制关掉了（spillCount = 0）
        }
        if (!isCasing(level.getBlockState(pos))) {
            return false;
        }
        Proto hivemind = nearestHivemind(level, pos);
        if (hivemind == null || HivemindStorage.count(hivemind) <= 0) {
            return false;   // 附近没有心智，或它本来就是空的——没什么可漏
        }
        var data = hivemind.getPersistentData();
        long now = level.getGameTime();
        if (data.getLong(KEY_COOLDOWN) > now) {
            return false;
        }
        // 先落冷却再掷骰：否则"砸了几十块"就是几十次独立掷骰，一次爆炸能让存货决堤。
        // 冷却落在心智身上而不是方块上，所以多只心智各算各的。
        data.putLong(KEY_COOLDOWN, now + SporeAddFungusConfig.hivemindSpillCooldownTicks());
        if (level.random.nextDouble() < SporeAddFungusConfig.hivemindSpillChance()) {
            HivemindStorage.spill(hivemind, level, hivemind.position(), limit);
        }
        return true;
    }

    /** 这块方块属不属于穹顶躯壳。 */
    private static boolean isCasing(BlockState state) {
        return Refs.CASING.contains(state.getBlock());
    }

    /** 离 {@code pos} 最近、且在穹顶半径内的心智。 */
    private static Proto nearestHivemind(ServerLevel level, BlockPos pos) {
        double radius = SporeAddFungusConfig.hivemindDomeRadius();
        double best = radius * radius;
        Proto found = null;
        for (Proto hivemind : SporeCompat.hiveminds()) {
            if (hivemind.isRemoved() || hivemind.level() != level) {
                continue;
            }
            double distance = hivemind.blockPosition().distSqr(pos);
            if (distance <= best) {
                best = distance;
                found = hivemind;
            }
        }
        return found;
    }

    /**
     * 穹顶躯壳的方块表，取自 Spore 自己生成穹顶时用的 {@code possibleBlocks()}。
     *
     * <p>放在内部类里是为了<b>延迟到首次使用才初始化</b>——与 {@code SporeCompat} 同一个理由：
     * 注册表在游戏运行期是冻结的，值不会变，但在类加载期就去取会撞上「注册还没完成」的时机问题。
     */
    private static final class Refs {

        private static final Set<Block> CASING = Set.of(
                Sblocks.BIOMASS_BLOCK.get(),
                Sblocks.ROOTED_BIOMASS.get(),
                Sblocks.CALCIFIED_BIOMASS_BLOCK.get(),
                Sblocks.SICKEN_BIOMASS_BLOCK.get(),
                Sblocks.GASTRIC_BIOMASS.get());

        private Refs() {
        }
    }
}
