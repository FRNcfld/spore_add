package com.frnc.spore_add.entity;

import com.frnc.spore_add.SporeAddConfig;
import com.frnc.spore_add.sound.ModSounds;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 冰封掉落物：一层可敲碎的冰壳，里面封着一份 {@link ItemStack}。
 *
 * <p>由「冰雪的叹息」的冲击环生成——生物被击杀后掉出来的东西会被封进这里，敲碎才掉出来。
 *
 * <h2>为什么不让 ItemEntity 当乘客</h2>
 * 曾经有个"冰封生物"版本，用的是"受害者当载具的乘客"那套（照 Iron's Spells 的冰霜之墓）。
 * 掉落物这条路<b>刻意不沿用</b>：{@code ItemEntity#tick} 照常涨 {@code age}（5 分钟后自己消失）、
 * 还一直参与合并与拾取，留着它当乘客就得额外写一套"冻住它 / 解冻它"，收尾还要还原那些标志。
 * 直接把堆存下来、破冰时生成一个<b>全新</b>的 {@code ItemEntity} 更干净——新的 {@code age} 与拾取延迟，
 * 也不会被算成"一个躺了很久的掉落物"。
 *
 * <h2>冰壳是原版冰块，零新素材</h2>
 * 见 {@code FrozenCapsuleRenderer}：直接让方块渲染器画一个 {@code Blocks.ICE}。
 * 原版冰的模型层本来就是 {@code translucent}，所以是一块半透明的冰。
 *
 * <h2>碰撞箱只用于射线拾取</h2>
 * {@code isPushable()} / {@code canBeCollidedWith()} 用基类 {@code Entity} 的默认值（都是 false），
 * 所以它不会被推动、不参与碰撞，只保留 {@code isPickable}（基类默认 {@code !isRemoved()}）让玩家能敲。
 * 代价说清楚：<b>可以穿过冰壳走进去</b>。
 */
public class FrozenCapsuleEntity extends Entity {

    private static final String KEY_MELT_AT = "MeltAt";
    private static final String KEY_LOOT = "Loot";

    /** 冰壳的边长（格）。掉落物本体很小，这个尺寸是为了"看得出是一块冰"。 */
    private static final float SHELL_SIZE = 0.6F;

    /** 冰壳里封着的那份东西。 */
    private ItemStack loot = ItemStack.EMPTY;

    /** 冰壳自动融化的时刻（绝对游戏时间）。{@code 0} = 永不自动融化，只能敲碎。 */
    private long meltAtGameTime;

    public FrozenCapsuleEntity(EntityType<? extends FrozenCapsuleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    /**
     * 把一个掉落物封进冰壳。只应在服务端调用。
     *
     * <p>原掉落物<b>直接抹掉</b>，堆存下来，破冰时另生成一个全新的——见类注释。
     */
    public static void encaseLoot(ServerLevel level, ItemEntity item) {
        ItemStack stack = item.getItem();
        if (stack.isEmpty()) {
            return;
        }
        FrozenCapsuleEntity capsule = new FrozenCapsuleEntity(ModEntities.FROZEN_CAPSULE.get(), level);
        capsule.setPos(item.getX(), item.getY(), item.getZ());
        capsule.loot = stack.copy();
        // 配置里的 0 是**哨兵值**，表示"永不自动融化"而不是"立刻融化"，所以必须原样保留 0
        int meltTicks = SporeAddConfig.frostSighIceMeltTicks();
        capsule.meltAtGameTime = meltTicks > 0 ? level.getGameTime() + meltTicks : 0L;

        level.addFreshEntity(capsule);
        item.discard();
    }

    @Override
    protected void defineSynchedData() {
        // 没有需要同步的东西：客户端只需要知道"这里有一块冰"，
        // 尺寸是常量、内容物不画出来（理由见 FrozenCapsuleRenderer）。
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;   // 客户端什么都不做：冰壳由渲染器画
        }

        if (loot.isEmpty()) {
            discard();   // 内容物没了（理论上不该发生），壳也别留着
            return;
        }
        if (meltAtGameTime > 0L && server.getGameTime() >= meltAtGameTime) {
            breakOut(server);
        }
    }

    /**
     * 把冰壳打碎：掉出物品、放碎裂声、退场。
     *
     * <p>生成的是<b>全新</b>的 {@code ItemEntity}，不是复用原来那个——冰封期间"躺"了多久都不算数。
     */
    private void breakOut(ServerLevel level) {
        level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), loot.copy()));
        loot = ItemStack.EMPTY;
        level.playSound(null, getX(), getY(), getZ(),
                ModSounds.FROST_SIGH_ICE_SHATTER.get(), SoundSource.BLOCKS, 1.2F, 0.8F);
        discard();
    }

    /**
     * 挨一下打就碎。
     *
     * <p>刻意<b>不做</b>耐久：冰封是这一发的残留物，不是一堵需要经营的墙。
     * 想让它更结实的话，加个计数器字段即可。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) {
            // 客户端先答应下来，真正的结算在服务端——与服务端不同步的返回值只会让音效对不上
            return true;
        }
        breakOut((ServerLevel) level());
        return true;
    }

    public float getShellWidth() {
        return SHELL_SIZE;
    }

    public float getShellHeight() {
        return SHELL_SIZE;
    }

    /**
     * 碰撞箱就是外观大小——它同时是"射线能不能打到"的判定框。
     */
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(SHELL_SIZE, SHELL_SIZE);
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong(KEY_MELT_AT, meltAtGameTime);
        tag.put(KEY_LOOT, loot.save(new CompoundTag()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        meltAtGameTime = tag.getLong(KEY_MELT_AT);
        loot = tag.contains(KEY_LOOT) ? ItemStack.of(tag.getCompound(KEY_LOOT)) : ItemStack.EMPTY;
    }
}
