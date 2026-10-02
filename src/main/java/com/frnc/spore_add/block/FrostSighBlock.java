package com.frnc.spore_add.block;

import javax.annotation.Nullable;

import com.frnc.spore_add.SporeAddPlayerConfig;
import com.frnc.spore_add.item.ModItems;
import com.frnc.spore_add.sound.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * 「冰雪的叹息」方块本体。本工程第一个<b>非流体</b>方块。
 *
 * <p>它自己几乎不做事：右击用冰霜新星激活、方块被破坏时放掉强制加载的区块，
 * 其余都在 {@link FrostSighBlockEntity}（倒计时）与
 * {@code FrostSighShockwaveEntity}（爆发）里。
 *
 * <p>继承 {@link BaseEntityBlock} 而不是直接实现 {@code EntityBlock}：它提供了
 * {@code createTickerHelper}，能省掉手写"类型不匹配就返回 null"的那段样板。
 * 代价是它默认 {@code getRenderShape} 返回 {@code INVISIBLE}（那是给纯 BER 方块用的），
 * 本方块要正常显示模型，所以覆写回 {@code MODEL}。
 */
public class FrostSighBlock extends BaseEntityBlock {

    /**
     * 是否已被激活。
     *
     * <p><b>刻意做成方块状态而不是只存在 BlockEntity 里。</b>BlockEntity 的字段只写进 NBT，
     * 而客户端拿不到 NBT（它靠同步包），所以客户端那份 BE 的"是否激活"永远是 false——
     * 那样 {@link #getDestroyProgress} 就会在客户端给出正常的挖掘进度、却在服务端被拒，
     * 表现为"能挖、但挖完弹回去"。方块状态本来就是两端同步的，用它就一致了。
     */
    public static final BooleanProperty ACTIVATED = BooleanProperty.create("activated");

    public FrostSighBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVATED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVATED);
    }

    /** 这个方块状态是否已激活。两端都可用。 */
    public static boolean isActivated(BlockState state) {
        return state.getValue(ACTIVATED);
    }

    /** 这个方块有正常模型，不是纯 BER 方块。 */
    @Override
    public @NotNull RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FrostSighBlockEntity(pos, state);
    }

    /**
     * 倒计时只在服务端跑，所以客户端返回 null（不注册 ticker）。
     *
     * <p>{@code createTickerHelper} 会检查传进来的 BlockEntityType 是否与本 mod 的一致，
     * 不一致就返回 null——所以这里是类型安全的，不需要强转 BlockEntity。
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.FROST_SIGH.get(),
                (lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl, pos, st));
    }

    /**
     * 右击激活：手持冰霜新星 → 消耗 1 个 → 开始倒计时。
     *
     * <p>已经是激活态时直接返回成功但不消耗，免得玩家白扔物品。
     */
    @Override
    public @NotNull InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                         InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(ModItems.FROST_NOVA.get())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            // 客户端只负责播放手臂动作，真正的状态变化在服务端
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof FrostSighBlockEntity core)) {
            return InteractionResult.PASS;
        }
        if (!core.activate((ServerLevel) level, SporeAddPlayerConfig.frostSighRadius())) {
            // 已经激活过了：不消耗，也不重复开始
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        level.playSound(null, pos, ModSounds.FROST_SIGH_ARMED.get(), SoundSource.BLOCKS, 1.4F, 0.8F);
        return InteractionResult.SUCCESS;
    }

    /**
     * 激活后挖不动：连挖掘进度都不涨。
     *
     * <p>这一条只影响<b>生存模式的手感</b>（没有进度条、挖不开），真正拦住破坏的是
     * {@link #onDestroyedByPlayer}。两个都要有：只拦后者的话，生存玩家会对着它挖半天、
     * 进度条走满却什么都没发生，像是卡了。
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return isActivated(state) ? 0.0F : super.getDestroyProgress(state, player, level, pos);
    }

    /**
     * 激活后无法破坏。
     *
     * <p><b>一个返回值同时拦住创造与生存两条路径</b>——查过 {@code ServerPlayerGameMode#destroyBlock}：
     * 创造模式直接走 {@code removeBlock}，生存模式也走它，而那个方法唯一会看的就是本方法的返回值。
     * 所以这里返回 false 就够了，不需要 {@code GameMasterBlock}（那只是个空标记接口）。
     *
     * <p>代价说清楚：连管理员也拆不掉，只能靠指令换掉方块。这是需求"激活后无法取消、无法破坏"要的效果，
     * 但意味着<b>激活即不可逆</b>——激活前想清楚。
     */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (isActivated(state)) {
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** 方块被挖掉/被替换时，必须放掉强制加载的区块。 */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide()
                && !state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof FrostSighBlockEntity core) {
            core.onRemoved((ServerLevel) level);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
