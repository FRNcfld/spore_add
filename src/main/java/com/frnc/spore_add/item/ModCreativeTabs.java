package com.frnc.spore_add.item;

import java.util.Iterator;
import java.util.Map;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.enchantment.ModEnchantments;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本 mod 自己的创造模式物品栏页签。
 *
 * <p>流体方块没有对应的 BlockItem，所以三种流体在本页签里表现为三个桶；这样它们才不需要靠命令
 * 取得。页签注册在<b>原版</b>的创造模式页签注册表 {@link Registries#CREATIVE_MODE_TAB} 上，
 * 而不是 Forge 的某个注册表。
 *
 * <p>另外它还负责把「烈阳」附魔书<b>从原版页签里挪走</b>，见 {@link #onBuildTabContents}。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SporeAdd.MOD_ID);

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    public static final RegistryObject<CreativeModeTab> SPORE_ADD_TAB =
            CREATIVE_MODE_TABS.register("spore_add", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + SporeAdd.MOD_ID))
                    .icon(() -> new ItemStack(ModItems.COOLANT_BUCKET.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.COOLANT_BUCKET.get());
                        output.accept(ModItems.LIQUID_COLD_BUCKET.get());
                        output.accept(ModItems.HIGH_ENERGY_FUEL_BUCKET.get());
                        output.accept(ModItems.FROST_NOVA.get());
                        output.accept(ModItems.FROST_SIGH.get());
                        // 附魔书不是注册项，得自己构造出来
                        output.accept(warmthBook());
                    })
                    .build());

    /**
     * 「烈阳」的附魔书本体的规范形态。
     *
     * <p>附魔书是同一个 {@code minecraft:enchanted_book} 物品靠 NBT 区分的，没有独立的注册项，
     * 所以只能每次现造一个。
     */
    private static ItemStack warmthBook() {
        return EnchantedBookItem.createForEnchantment(
                new EnchantmentInstance(ModEnchantments.WARMTH.get(), 1));
    }

    /**
     * 把「烈阳」附魔书从<b>原版</b>的页签里拿掉，让它只出现在本 mod 的页签下。
     *
     * <h2>它原本在哪儿</h2>
     * 原版在构建材料页签（{@code INGREDIENTS}）时会把<b>所有</b>附魔书塞进去：
     *
     * <pre>
     *   Set&lt;EnchantmentCategory&gt; set = EnumSet.allOf(EnchantmentCategory.class);
     *   generateEnchantmentBookTypesOnlyMaxLevel(output, ..., set, PARENT_TAB_ONLY);
     * </pre>
     *
     * 它按 Forge 的 {@code IForgeEnchantment#allowedInCreativeTab} 过滤，而那个默认实现是
     * {@code isAllowedOnBooks() && 类别在集合里}——集合是「全部类别」，所以任何附魔的书都会进去，
     * 包括本 mod 的「烈阳」。要"转移"就得先从那里删掉。
     *
     * <h2>为什么用迭代器删，而不是 remove(ItemStack)</h2>
     * {@code getEntries()} 是个哈希表，{@code remove(K)} 靠键的哈希定位，
     * 也就是要求我造的这本书与原版那本书的 NBT 逐字节一致。虽然两边都走
     * {@code createForEnchantment}、结果应该一样，但那是"应该"——一旦 NBT 的顺序或某个字段不同，
     * 删除会<b>静默失效</b>（页签里出现两本书，一本在错误的页签下）。
     * 迭代器逐个比对则没有这个隐患，代价只是遍历一遍页签内容。
     *
     * <p>只动 {@code INGREDIENTS}：搜索页签那份是原版特意加的（{@code SEARCH_TAB_ONLY}），
     * 搜索页本来就该能搜到所有东西，不该删。
     */
    @SubscribeEvent
    public static void onBuildTabContents(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            return;
        }
        ItemStack target = warmthBook();
        Iterator<Map.Entry<ItemStack, CreativeModeTab.TabVisibility>> entries = event.getEntries().iterator();
        while (entries.hasNext()) {
            ItemStack stack = entries.next().getKey();
            // 先看物品再比 NBT：绝大多数条目会在第一步就被否掉，省下逐条 NBT 比较
            if (stack.is(Items.ENCHANTED_BOOK) && ItemStack.matches(target, stack)) {
                entries.remove();
            }
        }
    }
}
