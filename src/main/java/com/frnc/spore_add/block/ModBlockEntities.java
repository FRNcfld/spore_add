package com.frnc.spore_add.block;

import com.frnc.spore_add.SporeAdd;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本 mod 的 BlockEntityType 注册表。目前只有「冰雪的叹息」一个。
 *
 * <h2>关于 {@code build(null)}</h2>
 * 那个参数是原版的 datafixer 类型，原版用
 * {@code Util.fetchChoiceType(References.BLOCK_ENTITY, key)} 取。这里传 {@code null}：
 * 查过 {@code BlockEntityType} 的源码，那个字段<b>只被写入、从未被读取</b>（类里没有它的访问器，
 * 也没有任何内部使用），所以传 null 是安全的，也是绝大多数模组的选择。
 *
 * <p>另外注意：{@code Builder.of(...)} 的第二个参数是"这个 BlockEntityType 能挂在哪些方块上"，
 * 漏填会让原版记一条警告，并且方块**无法**创建 BlockEntity。
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SporeAdd.MOD_ID);

    /** 「冰雪的叹息」的方块实体。 */
    public static final RegistryObject<BlockEntityType<FrostSighBlockEntity>> FROST_SIGH =
            BLOCK_ENTITIES.register("frost_sigh", () -> BlockEntityType.Builder
                    .of(FrostSighBlockEntity::new, ModBlocks.FROST_SIGH.get())
                    .build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
