package com.frnc.spore_add.item;

import java.util.List;

import com.frnc.spore_add.SporeAddPlayerConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * 「冰雪的叹息」的方块物品。存在的唯一理由是<b>要覆写 tooltip</b>——
 * 方块本体与放置逻辑都在 {@code FrostSighBlock} 里。
 *
 * <p>和冰霜新星一样，按住 Shift 展开详细数值，且所有数字都是现读配置的。
 * 这个方块有太多可调项（半径、倒计时、冲击环时长、冻伤、冰封、降雪），
 * 没有一个"按住 Shift 就能看到当前生效值"的地方会很难调。
 */
public class FrostSighItem extends BlockItem {

    /** 配置里存的是分钟，换算成 tick 用。 */
    private static final int TICKS_PER_MINUTE = 60 * 20;

    public FrostSighItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // 必须调 super：BlockItem 的实现会把 tooltip 转交给方块自己的 appendHoverText，
        // 漏掉它等于把方块那层的信息静默丢掉。
        super.appendHoverText(stack, level, tooltip, flag);

        if (!ItemTooltips.detailVisible()) {
            ItemTooltips.addHoldShiftHint(tooltip);
            return;
        }

        ItemTooltips.addLine(tooltip, "tooltip.spore_add.frost_sigh.area",
                SporeAddPlayerConfig.frostSighRadius());
        ItemTooltips.addLine(tooltip, "tooltip.spore_add.frost_sigh.countdown",
                SporeAddPlayerConfig.frostSighCountdownTicks() / 20,
                SporeAddPlayerConfig.frostSighShockwaveTicks() / 20);
        ItemTooltips.addLine(tooltip, "tooltip.spore_add.frost_sigh.frostbite",
                SporeAddPlayerConfig.frostSighFrostbiteLevel(),
                SporeAddPlayerConfig.frostSighFrostbiteTicks() / TICKS_PER_MINUTE,
                SporeAddPlayerConfig.frostSighFrozenTicks() / 20);
        ItemTooltips.addLine(tooltip, "tooltip.spore_add.frost_sigh.snow",
                SporeAddPlayerConfig.frostSighSnowTicks() / TICKS_PER_MINUTE);
        ItemTooltips.addLine(tooltip, "tooltip.spore_add.frost_sigh.activate");
    }
}
