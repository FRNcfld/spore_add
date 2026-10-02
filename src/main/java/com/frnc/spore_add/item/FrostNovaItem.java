package com.frnc.spore_add.item;

import java.util.List;

import com.frnc.spore_add.SporeAddPlayerConfig;
import com.frnc.spore_add.entity.FrostNovaEntity;
import com.frnc.spore_add.sound.ModSounds;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 「冰霜新星」：长按右键蓄力（与原版弓同一套机制），松手发射一枚直线飞行的弹体，
 * 落点处冻出冰球并给周围生物叠冻伤。
 *
 * <h2>为什么照抄 BowItem 的那几个覆写</h2>
 * 蓄力不是一个"自己数 tick"的功能，而是原版现成的一整套状态机：{@link #use} 调
 * {@code startUsingItem} 把物品置为"正在使用"，然后由 {@code LivingEntity} 每 tick 递减
 * {@code getUseItemRemainingTicks()}，松手时回调 {@link #releaseUsing}。所以这里必须
 * <b>四个方法都覆写且取值自洽</b>：
 * <ul>
 *   <li>{@link #getUseDuration(ItemStack)} 返回 {@link #MAX_USE_DURATION}（弓同款 72000）——
 *       <b>签名必须带 {@code ItemStack}</b>，写成无参的 {@code getUseDuration()} 是不覆写，蓄力会恒为 0；</li>
 *   <li>{@link #getUseAnimation(ItemStack)} 返回 {@link UseAnim#BOW}，否则手臂不摆拉弓动作，
 *       客户端的 {@code pulling} 物品属性也不会为真（它读的就是 {@code isUsingItem()}）；</li>
 *   <li>{@link #releaseUsing} 用 {@code getUseDuration(stack) - timeLeft} 反推蓄力时长——
 *       这个 72000 的基数同时决定了客户端 {@code pull} 属性算出来是多少，
 *       而模型里的档位阈值（0.65 / 0.9）就是按它折算的。</li>
 * </ul>
 *
 * <h2>威力随蓄力线性变化</h2>
 * 蓄力时长归一化成 0~1 的系数后交给弹体（见 {@link SporeAddPlayerConfig#scaledByPower}）：两个半径、
 * 冻伤秒数、冻伤层数四项都按它缩放，最低蓄力是满蓄力的 {@code minPowerFraction} 倍。
 * <b>弹道不随蓄力变化</b>——速度是常量。
 *
 * <h2>消耗与静默取消</h2>
 * 是消耗品，每次发射扣 1 个（创造模式不扣）。两种情况会静默取消、什么都不发生：
 * 蓄力不足 {@code minChargeTicks} 就松手；以及蓄力期间切换物品——后者由原版完成
 * （{@code updatingUsingItem} 发现手持物品变了会走 {@code stopUsingItem}，那条路<b>不</b>回调
 * {@code releaseUsing}），与弓的行为一致，不需要额外处理。
 */
public class FrostNovaItem extends Item {

    /**
     * "使用"状态的最长时长。
     *
     * <p>照抄 {@code BowItem} 的 72000。这个数只是让蓄力状态不会自己到期，
     * 真正决定拉满的是配置里的 {@code chargeTicks}（默认 20）——两者不是一回事。
     */
    private static final int MAX_USE_DURATION = 72000;

    /** 蓄力音效的间隔（tick）。太密会糊成一片，5 tick 一声正好听得出进度。 */
    private static final int CHARGE_SOUND_INTERVAL_TICKS = 5;

    public FrostNovaItem(Properties properties) {
        super(properties);
    }

    /**
     * 按住 Shift 时展开详细数值，否则只给一行提示。
     *
     * <p><b>所有数字都是现读配置的</b>，不是写死的文案——改了 {@code config/spore_add-player-common.toml}
     * 里的任何一个旋钮，这里显示的范围、秒数、层数都会跟着变。所以它同时也是一份"当前生效参数"的自检面板。
     */
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (!ItemTooltips.detailVisible()) {
            ItemTooltips.addHoldShiftHint(tooltip);
            return;
        }

        double speed = SporeAddPlayerConfig.frostNovaSpeed();
        int fuseTicks = SporeAddPlayerConfig.frostNovaAutoDetonateTicks();
        double range = speed * fuseTicks;
        float power = SporeAddPlayerConfig.frostNovaExplosionPower(1.0D);

        ItemTooltips.addLine(tooltip, "tooltip.spore_add.frost_nova.range",
                trim(speed), fuseTicks / 20, trim(range));
        ItemTooltips.addLine(tooltip, "tooltip.spore_add.frost_nova.impact",
                SporeAddPlayerConfig.frostNovaBlockRadius(), SporeAddPlayerConfig.frostNovaEntityRadius());
        ItemTooltips.addLine(tooltip, "tooltip.spore_add.frost_nova.frostbite",
                SporeAddPlayerConfig.frostNovaFrostbiteSeconds(), SporeAddPlayerConfig.frostNovaFrostbiteLevel());
        ItemTooltips.addLine(tooltip, "tooltip.spore_add.frost_nova.explosion",
                trim(power), trim(power * 2.0D));
        ItemTooltips.addLine(tooltip, "tooltip.spore_add.frost_nova.secondary",
                SporeAddPlayerConfig.frostNovaSecondaryDelayTicks() / 20,
                trim(SporeAddPlayerConfig.frostNovaSecondaryRangeMultiplier()),
                trim(SporeAddPlayerConfig.frostNovaSecondaryPowerMultiplier()));
        ItemTooltips.addLine(tooltip, "tooltip.spore_add.frost_nova.charge",
                SporeAddPlayerConfig.frostNovaChargeTicks(), SporeAddPlayerConfig.frostNovaMinChargeTicks());
    }

    /** 去掉多余的小数位：1.5 显示成 "1.5"、2.0 显示成 "2"。 */
    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 不需要像弓那样先检查"有没有弹药"——这个物品自己就是弹药
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return MAX_USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    /**
     * 蓄力中的音效：每隔几 tick 一声晶体清响，音调随蓄力升高，给"拉了多少"一个听觉刻度。
     *
     * <p>用紫水晶的音色是因为原版没有冰系的蓄力音，而紫水晶那几声是所有原版音效里最接近
     * "结晶"质感的。不自己塞 ogg：那要额外维护音频资源，收益不成比例。
     *
     * <p><b>蓄力不足 {@code minChargeTicks} 时不发声</b>：不是单纯为了好听——这个音效的字幕是
     * "冰霜新星已就绪"，而蓄力不足时松手根本不会发射，那时候提示"已就绪"就是在骗人。
     * 所以门槛与 {@link #releaseUsing} 用的是同一个值。
     *
     * <p>只在服务端放（{@code playSound} 传 null 播放者即广播给附近玩家）。两侧都调的话会响两遍。
     */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide()) {
            return;
        }
        int charge = getUseDuration(stack) - remainingUseDuration;
        if (charge < SporeAddPlayerConfig.frostNovaMinChargeTicks()
                || charge % CHARGE_SOUND_INTERVAL_TICKS != 0) {
            return;
        }
        float progress = Math.min(1.0F, (float) charge / SporeAddPlayerConfig.frostNovaChargeTicks());
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                ModSounds.FROST_NOVA_CHARGING.get(), SoundSource.PLAYERS, 0.5F, 0.6F + progress);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }

        int charge = getUseDuration(stack) - timeLeft;
        if (charge < SporeAddPlayerConfig.frostNovaMinChargeTicks()) {
            return;   // 蓄力不足，静默取消（与弓"拉不满不发"一致）
        }

        // 生成实体与扣物品都只在服务端做。
        // 原版语义下 releaseUsing 本来就只在服务端被调到（completeUsingItem 有 isClientSide 闸门），
        // 但这是公开方法，别的模组可能从客户端调进来，所以自己再守一道。
        if (level.isClientSide()) {
            return;
        }

        double power = Math.min(1.0D, (double) charge / SporeAddPlayerConfig.frostNovaChargeTicks());
        level.addFreshEntity(new FrostNovaEntity(level, player, power));

        // 音调随威力升高，满蓄力听起来更"满"
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FROST_NOVA_LAUNCH.get(), SoundSource.PLAYERS, 1.0F, 0.8F + 0.4F * (float) power);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }
}
