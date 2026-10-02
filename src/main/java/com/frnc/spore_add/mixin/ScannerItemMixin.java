package com.frnc.spore_add.mixin;

import com.Harbinger.Spore.Sitems.ScannerItem;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.fungus.FungusCombat;
import com.frnc.spore_add.hatred.HatredData;
import com.frnc.spore_add.hatred.HatredValues;
import com.frnc.spore_add.hatred.PlayerHatredBuffs;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给 Spore 的<b>扫描仪</b>加两条信息：世界恨意值（扫到真菌时）与个人恨意值（扫空时）。
 *
 * <h2>扫描仪本来是怎么工作的</h2>
 * {@code ScannerItem#use}：服务端朝视线方向取一个 32 格长的盒子，找出盒内<b>最近的</b>生物——
 * 所以"右键生物"其实是"对着生物右键"，不需要真的点到它。找到就播 {@code SCANNER_MOB} 音效并
 * 打印一屏信息（位置、名称、血量、威胁等级、杀戮点、进化时间、菌巢连接……）；
 * <b>找不到就只播一个 {@code SCANNER_EMPTY} 音效，什么字都不显示</b>。
 * 所以"扫空"这一条是它本来就空着的入口，本 mixin 正好填上。
 *
 * <h2>为什么只注入一处</h2>
 * 两种情形（扫到真菌 / 扫空）本来可以分别注入 {@code showInfo} 与 {@code use}，
 * 但那样要写两处、且判定条件会分散。这里统一挂在 {@code use} 的返回处，自己再调一次
 * {@code getScannedEntity} 来分流——与 Spore 自己用的是同一个方法、同一个盒子，
 * 所以两边对"扫到了谁"的判断<b>必然一致</b>，不会出现"它显示扫到了、我说扫空了"。
 *
 * <p>代价是同一次扫描里那个盒子被查了两次。可以接受：扫描仪自带 20 tick 冷却，
 * 而且那只是一次 AABB 实体查询，不是逐格扫描。
 *
 * <h2>扫到真菌时显示什么</h2>
 * 需求要的是"世界恨意值，以及世界恨意值给予真菌的增益"。世界恨意值给真菌的唯一增益就是
 * <b>减伤</b>（{@code HatredValues.fungusDamageReduction}，只对非玩家伤害生效），
 * 所以显示"世界恨意值 + 那个减伤百分比"。顺带显示击杀它可得多少恨意值——
 * 那是同一次击杀结算用的数，扫一眼能知道该不该打。
 *
 * <h2>为什么"扫自己"要蹲下</h2>
 * 站着对空气右键是个很随便的动作（随手挥、试手感、误触都会触发），每次都糊一屏数字反而碍事。
 * 蹲下是个明确的"我要求查看"的信号，所以自检留给了它。
 * 代价是玩家不会自己发现这个功能——但它的定位本来就是给想研究数值的人用的。
 *
 * <h2>为什么扫非真菌生物不额外显示</h2>
 * 世界恨意值是<b>全阵营</b>的性质，跟"扫的是不是真菌"无关，但只有对着真菌看它才有意义；
 * 对着牛羊显示"真菌减伤 12%"只会让人困惑。所以那一条严格限定在真菌上。
 *
 * <h2>{@code remap} 保持默认</h2>
 * {@code use} 是<b>原版</b> {@code Item#use} 的覆写，生产环境里叫 SRG 名，所以要走 refmap。
 * 注解里也只有这一个引用，没有需要 {@code remap = false} 的东西。
 */
@Mixin(ScannerItem.class)
public abstract class ScannerItemMixin {

    @Inject(method = "use", at = @At("RETURN"))
    private void sporeAdd$appendHatredInfo(Level level, Player player, InteractionHand hand,
                                           CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        // Spore 那一整段本身就只在服务端跑（客户端还没扫），这里跟着同样的判断，
        // 免得在客户端多打印一份、变成两条重影
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // 分流用的盒子与 Spore 刚才用的是同一个方法，见类注释
        LivingEntity victim = ((ScannerItem) (Object) this).getScannedEntity(player, level);
        if (victim != null) {
            // 扫到真菌 → 报世界恨意值。扫到别的生物不额外显示，理由见类注释
            if (FungusCombat.isFungus(victim)) {
                showWorldHatred(player, serverLevel, victim);
            }
            return;
        }

        // 扫空：**只有蹲下**才算"我要扫自己"。
        // 站着对空气右键不显示任何东西——那是玩家在随手挥、不是在扫描，
        // 每次都糊一屏数字反而碍事。蹲下是个明确的"我要求查看"的动作。
        if (player.isShiftKeyDown()) {
            showPersonalHatred(serverLevel, player);
        }
    }

    /**
     * 扫到真菌：报世界恨意值，以及它换来的减伤。
     *
     * <p>减伤用的是 {@link HatredValues#fungusDamageReduction}——与真正改伤害的那条路径
     * 是<b>同一个方法</b>，所以这里显示的数就是实际生效的数（含上限）。
     */
    private static void showWorldHatred(Player player, ServerLevel level, LivingEntity victim) {
        double worldHatred = HatredData.get(level).total();
        line(player, Component.translatable("message.spore_add.scan.world_hatred", format(worldHatred)));
        line(player, Component.translatable("message.spore_add.scan.fungus_resistance",
                percent(HatredValues.fungusDamageReduction(worldHatred) * 100.0D)));
        // 顺带一行"杀掉它值多少恨意值"——同一次击杀结算用的就是这个数，扫一眼能决定打不打
        line(player, Component.translatable("message.spore_add.scan.kill_value",
                format(HatredValues.killValue(victim))));
    }

    /**
     * 扫空：报玩家自己的恨意值，以及它换来的五项增益。
     *
     * <p>五项全走 {@link PlayerHatredBuffs} 里那五个方法——与真正加属性/改伤害用的是同一份计算，
     * 所以显示的就是实际生效的（含各自的封顶）。
     */
    private static void showPersonalHatred(ServerLevel level, Player player) {
        double hatred = HatredData.get(level).get(player.getUUID());
        line(player, Component.translatable("message.spore_add.scan.personal_hatred", format(hatred)));
        line(player, Component.translatable("message.spore_add.scan.tier",
                Long.toString(HatredValues.thresholdIndex(hatred)),
                format(SporeAddFungusConfig.raidThresholdStep())));
        line(player, Component.translatable("message.spore_add.scan.buff_header"));
        line(player, Component.translatable("message.spore_add.scan.buff_attack",
                format(PlayerHatredBuffs.attackBonus(hatred))));
        line(player, Component.translatable("message.spore_add.scan.buff_armor",
                format(PlayerHatredBuffs.armorBonus(hatred))));
        line(player, Component.translatable("message.spore_add.scan.buff_luck",
                format(PlayerHatredBuffs.luckBonus(hatred))));
        line(player, Component.translatable("message.spore_add.scan.buff_reduction",
                percent(PlayerHatredBuffs.damageReduction(hatred) * 100.0D)));
        line(player, Component.translatable("message.spore_add.scan.buff_final_damage",
                percent(PlayerHatredBuffs.finalDamageBonus(hatred) * 100.0D)));
    }

    /**
     * 往聊天栏发一行。
     *
     * <p>与 Spore 自己的 scanner 输出一样用 {@code displayClientMessage(..., false)}——
     * 走聊天框而不是动作栏，好让一屏数据留在记录里、能翻回去看。
     */
    private static void line(Player player, Component text) {
        player.displayClientMessage(text, false);
    }

    /** 恨意值保留一位小数：它是浮点，直接打印会带一串没意义的尾数。 */
    private static String format(double value) {
        return String.format("%.1f", value);
    }

    /** 把已经是百分数的值印成 "12.3%"。 */
    private static String percent(double value) {
        return String.format("%.1f%%", value);
    }
}
