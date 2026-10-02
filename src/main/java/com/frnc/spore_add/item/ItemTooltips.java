package com.frnc.spore_add.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 物品 tooltip 的共用部分：把"按住 Shift 看详情"这件事收在一处。
 *
 * <h2>为什么在公共类里引用 {@link Screen}</h2>
 * {@code Screen} 是纯客户端类，而这个类是公共的。这么做是安全的，理由有两条：
 * <ul>
 *   <li>{@code Item#appendHoverText} <b>不是</b> {@code @OnlyIn(Dist.CLIENT)}
 *       （查过 {@code Item.java}），但它在原版里只被 GUI 的 tooltip 代码调用，也就是只在客户端跑；</li>
 *   <li>Forge 的 DistCleaner 只处理<b>被标注</b>的成员，方法体里的普通引用不会被改写——
 *       所以专用服务端只要不调用这个方法，就不会去加载 {@code Screen}。</li>
 * </ul>
 * 换来的好处是"Shift 这个判定"与"按 Shift 提示"只有一份，两个物品不会各写一套然后慢慢漂移。
 */
public final class ItemTooltips {

    private ItemTooltips() {
    }

    /** 是否应该展开详细信息。 */
    public static boolean detailVisible() {
        return Screen.hasShiftDown();
    }

    /** 加一行"按住 Shift 查看详情"的灰色提示。 */
    public static void addHoldShiftHint(List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.spore_add.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 一行普通信息。 */
    public static void addLine(List<Component> tooltip, String key, Object... args) {
        tooltip.add(Component.translatable(key, args).withStyle(ChatFormatting.GRAY));
    }
}
