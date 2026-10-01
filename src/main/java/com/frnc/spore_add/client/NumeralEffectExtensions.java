package com.frnc.spore_add.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

/**
 * 把 buff 的等级画成<b>阿拉伯数字</b>的客户端渲染扩展。可燃与爆燃共用同一份实现，
 * 因为两者的等级都来自 {@link ClientBuffLevelsCache}（服务端同步过来的），差别只在取哪个数——
 * 那由 {@link DisplayedEffectLevel} 统一判断。
 *
 * <p>原版的等级显示有两处限制，都是我们要绕开的：等级只在 amplifier 1~9 时显示，而且写成罗马数字
 * （见 {@code EffectRenderingInventoryScreen#getEffectName}）。我们的两个 buff 层数动辄上百，
 * 那条路根本表达不了，所以两个 buff 的 amplifier 都固定成 0、自己画。
 *
 * <h2>坐标与字号都是照抄/对齐原版得出的，不要随手改</h2>
 * 三处落点从 {@code EffectRenderingInventoryScreen} 与 {@code Gui} 的源码核出：HUD 上图标画在槽位内缩 3 格处；
 * 背包界面里回调给的 y 是行首、图标实际画在 {@code y+7}；名称与时长分别在 {@code (x+28, y+6)} 与 {@code (x+28, y+16)}。
 */
public final class NumeralEffectExtensions implements IClientMobEffectExtensions {

    private static final int HUD_ICON_INSET = 3;
    private static final int INVENTORY_ICON_OFFSET_Y = 7;
    private static final int LABEL_OFFSET_X = 28;
    private static final int LABEL_OFFSET_Y = 6;
    private static final int LABEL_LINE_HEIGHT = 10;

    private static final int ICON_SIZE = 18;
    private static final int NAME_COLOR = 16777215;
    private static final int DURATION_COLOR = 8355711;

    /** 数字的紫色。 */
    private static final int LEVEL_COLOR = 0xC77DFF;

    /** 数字相对原版字号缩到多少。 */
    private static final float LEVEL_SCALE = 0.6F;

    /** 数字藏在图标右下角，并向外越过一格，让它更贴角。 */
    private static final int LEVEL_OVERHANG = 1;

    /** 剩余时间进入这个窗口（tick）后才开始闪，与原版的 200 一致。 */
    private static final int BLINK_WINDOW = 200;

    /** 脉动幅度的下限与上限：见 {@link #blinkAlpha} 说明为什么不能像原版那样从 0 起。 */
    private static final float BLINK_MIN_AMPLITUDE = 0.14F;
    private static final float BLINK_MAX_AMPLITUDE = 0.50F;

    @Override
    public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics graphics,
                                 int x, int y, float z, float vanillaAlpha) {
        int iconX = x + HUD_ICON_INSET;
        int iconY = y + HUD_ICON_INSET;
        // 刻意不用原版递进来的 vanillaAlpha，改用自己的曲线（原因见 blinkAlpha），
        // 图标与数字一起闪，否则会出现"图标在闪、紫色数字不闪"的割裂感
        float alpha = blinkAlpha(instance);
        blitIcon(graphics, instance.getEffect(), iconX, iconY, alpha);
        drawLevel(graphics, iconX, iconY, DisplayedEffectLevel.of(instance), alpha);
        // 返回 true 表示"我已经画好了"，原版不再重画这个图标
        return true;
    }

    @Override
    public boolean renderInventoryIcon(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                                       GuiGraphics graphics, int x, int y, int blitOffset) {
        // 背包界面原版不做闪烁，这里也跟着不闪
        int iconY = y + INVENTORY_ICON_OFFSET_Y;
        blitIcon(graphics, instance.getEffect(), x, iconY, 1.0F);
        drawLevel(graphics, x, iconY, DisplayedEffectLevel.of(instance), 1.0F);
        return true;
    }

    /**
     * 「快结束时闪烁」的透明度，我们自己算。
     *
     * <h2>为什么不用原版递进来的那个值</h2>
     * 原版的公式（{@code Gui#renderEffects}）是为<b>长</b> buff 设计的：它的脉动幅度项随
     * "进入 200 tick 窗口之后又过了多久"增长。而爆燃的时长上限<b>就是</b> 200 tick（10 秒），
     * 它一诞生就处在窗口的最外端，于是原版算出来是个恒定值：
     *
     * <pre>
     *   剩余 200 tick: 脉动区间 [0.500, 0.500]   ← 完全不闪，只是恒定半透明
     *   剩余 180 tick: 脉动区间 [0.481, 0.525]   ← ±2%，看不出
     *   剩余 100 tick: 脉动区间 [0.381, 0.625]   ← 这时才看得出
     * </pre>
     *
     * 也就是说照搬原版会得到一个"一直半透明、不闪烁"的图标——这正是要修的现象。
     *
     * <p>所以这里把幅度改成<b>从下限起步</b>、随剩余时间缩短而增大到上限：刚进入窗口时轻微呼吸，
     * 越接近结束脉动越强，到期那一刻在满不透明与半透明之间大幅摆动。频率沿用原版的每 10 tick 一周期，
     * 观感与其它 buff 一致。
     *
     * @return 1.0 表示完全不透明
     */
    private static float blinkAlpha(MobEffectInstance instance) {
        int remaining = instance.getDuration();
        if (remaining < 0 || remaining > BLINK_WINDOW) {
            return 1.0F;
        }
        float closeness = 1.0F - Mth.clamp(remaining / (float) BLINK_WINDOW, 0.0F, 1.0F);
        float amplitude = BLINK_MIN_AMPLITUDE + (BLINK_MAX_AMPLITUDE - BLINK_MIN_AMPLITUDE) * closeness;
        float wave = 0.5F + 0.5F * Mth.cos(remaining * (float) Math.PI / 5.0F);
        return 1.0F - amplitude * wave;
    }

    @Override
    public boolean renderInventoryText(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                                       GuiGraphics graphics, int x, int y, int blitOffset) {
        Font font = Minecraft.getInstance().font;
        // 返回 true 会跳过原版那两行，所以名称与时长都得自己画全，否则它们会直接消失
        graphics.drawString(font, DisplayedEffectLevel.nameWithLevel(instance, DisplayedEffectLevel.of(instance)),
                x + LABEL_OFFSET_X, y + LABEL_OFFSET_Y, NAME_COLOR);
        graphics.drawString(font, MobEffectUtil.formatDuration(instance, 1.0F),
                x + LABEL_OFFSET_X, y + LABEL_OFFSET_Y + LABEL_LINE_HEIGHT, DURATION_COLOR);
        return true;
    }

    /** 从 mob_effects 图集取图标——原版就是按注册表键自动加载 {@code textures/mob_effect/<注册名>.png} 的。 */
    private static void blitIcon(GuiGraphics graphics, MobEffect effect, int x, int y, float alpha) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(effect);
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(x, y, 0, ICON_SIZE, ICON_SIZE, sprite);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 把等级缩小后画进图标的右下角。
     *
     * <p>缩小靠 {@code PoseStack}：先平移到锚点（图标右下角再向外一格），再整体缩放，
     * 之后按缩放后的坐标回拉自身的宽高，于是文字的右下角正好落在锚点上。
     * 这样位数变多时是向左压过图标，而不是向右侵入相邻槽位。
     */
    private static void drawLevel(GuiGraphics graphics, int iconX, int iconY, int level, float alpha) {
        if (level <= 0) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String text = Integer.toString(level);

        PoseStack pose = graphics.pose();
        // 数字跟着图标一起闪：setColor 走的是 RenderSystem 的 shader 颜色，文字绘制也会乘上它
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        pose.pushPose();
        pose.translate(iconX + ICON_SIZE + LEVEL_OVERHANG, iconY + ICON_SIZE + LEVEL_OVERHANG, 0.0F);
        pose.scale(LEVEL_SCALE, LEVEL_SCALE, 1.0F);
        graphics.drawString(font, text, -font.width(text), -font.lineHeight, LEVEL_COLOR, true);
        pose.popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

}
