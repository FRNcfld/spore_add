package com.frnc.spore_add.fungus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.frnc.spore_add.SporeAdd;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 掉落物转化<b>黑名单</b>（数据包形式）：名单里的东西<b>一概不捡</b>。
 *
 * <h2>文件放哪、怎么写（一个文件一条）</h2>
 * <pre>
 *   data/&lt;你的命名空间&gt;/loot_blacklist/&lt;任意名字&gt;.json
 *
 *   { "item": "minecraft:cobblestone" }
 *   { "tag":  "minecraft:flowers" }
 * </pre>
 *
 * <h2>它优先于一切</h2>
 * 判断发生在「挑目标」那一步：黑名单里的东西<b>根本不会去捡</b>，不是"捡了但给 0"。
 * 同一件东西既在转化表里又在黑名单里时，以黑名单为准。
 *
 * <p>与 {@link LootValues} 分开目录、分开解析，是因为两者的<b>字段本来就不一样</b>
 * （黑名单没有 value/chance）。硬塞进一个 schema 会变成"有条件字段"，
 * 写错时更难给出有用的报错。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID)
public final class LootBlacklist extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 数据包目录名：{@code data/<ns>/loot_blacklist/<name>.json}。 */
    private static final String DIRECTORY = "loot_blacklist";

    private static final String KEY_ITEM = "item";
    private static final String KEY_TAG = "tag";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final LootBlacklist INSTANCE = new LootBlacklist();

    /** 名单。读盘前为空 = 什么都不排除，这是安全的默认值。 */
    private static List<Entry> entries = List.of();

    private LootBlacklist() {
        super(GSON, DIRECTORY);
    }

    /** {@code tag} 为 null 表示按物品匹配。 */
    private record Entry(Item item, TagKey<Item> tag) {

        boolean matches(ItemStack stack) {
            return tag == null ? stack.is(item) : stack.is(tag);
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    /** 这件掉落物在不在黑名单里。在的话不该去捡。 */
    public static boolean isBlacklisted(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Entry entry : entries) {
            if (entry.matches(stack)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Entry> collected = new ArrayList<>();
        files.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), collected));
        entries = List.copyOf(collected);
        if (!entries.isEmpty()) {
            LOGGER.info("[SporeAdd] 掉落物黑名单已加载，共 {} 条", entries.size());
        }
    }

    private static void readFile(ResourceLocation file, JsonElement element, List<Entry> collected) {
        if (!element.isJsonObject()) {
            warn(file, "不是一个 JSON 对象");
            return;
        }
        JsonObject object = element.getAsJsonObject();
        boolean byItem = object.has(KEY_ITEM);
        boolean byTag = object.has(KEY_TAG);
        if (byItem == byTag) {   // 两个都有、或两个都没有
            warn(file, "必须且只能有 item 或 tag 之一");
            return;
        }
        if (byItem) {
            ResourceLocation id = ResourceLocation.tryParse(object.get(KEY_ITEM).getAsString());
            Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
            if (item == null || item == Items.AIR) {
                warn(file, "item 不合法或不存在");
                return;
            }
            collected.add(new Entry(item, null));
        } else {
            ResourceLocation id = ResourceLocation.tryParse(object.get(KEY_TAG).getAsString());
            if (id == null) {
                warn(file, "tag 不合法（要写成 命名空间:路径，不要写 #）");
                return;
            }
            collected.add(new Entry(null, TagKey.create(Registries.ITEM, id)));
        }
    }

    private static void warn(ResourceLocation file, String reason) {
        LOGGER.warn("[SporeAdd] 掉落物黑名单的这一条被跳过（{}）：{}", reason, file);
    }
}
