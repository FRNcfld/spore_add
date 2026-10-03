package com.frnc.spore_add.fungus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.SporeAddFungusConfig;
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
 * 「这件掉落物值多少资源、以及多大几率真的转化出来」——<b>数据包形式</b>的转化表。
 *
 * <h2>文件放哪、怎么写（一个文件一条）</h2>
 * <pre>
 *   data/&lt;你的命名空间&gt;/loot_values/&lt;任意名字&gt;.json
 *
 *   { "item": "minecraft:iron_ingot", "value": 6, "chance": 1.0 }    按物品
 *   { "tag":  "minecraft:logs",       "value": 1, "chance": 0.35 }   按标签
 * </pre>
 * {@code item} 与 {@code tag} 二选一，{@code chance} 可省（省略 = 1.0，也就是必然转化）。
 * 改完文件 {@code /reload} 就生效，不必重启。
 *
 * <h2>为什么从配置项搬到数据包</h2>
 * 这张表是<b>整合包作者要改的东西</b>，而配置项（{@code -common.toml}）是玩家本地的、不进整合包。
 * 数据包能随整合包分发、能被别的数据包覆盖、也能 {@code /reload} 热更。
 *
 * <h2>匹配顺序：物品优先于标签，同组内按文件路径排序</h2>
 * 以前配置版的规则是「先出现的那条赢」，但一个文件一条之后「先出现」就没意义了
 * （取决于文件名）。所以换成一个更好预测的规则：<b>先找物品条目，全都匹配不上才轮到标签条目</b>；
 * 同一组内按文件路径排序，保证结果确定（{@code Map} 的遍历顺序是不保证的）。
 * 实际效果就是想要的那条：<b>给某个物品单独写一条，就能盖过它所属标签的那条</b>。
 *
 * <h2>没列到的物品也算「纳入了」</h2>
 * 需求要「所有掉落物全部纳入」。把每个模组的每件物品都列一遍是不可能的，所以没列到的
 * 走配置里的 {@code loot.defaultValue} / {@code loot.defaultChance} 两条默认值
 * （默认 1.0 / 1.0，也就是全都收）。想排除什么请用 {@link LootBlacklist}。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID)
public final class LootValues extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 数据包目录名：{@code data/<ns>/loot_values/<name>.json}。 */
    private static final String DIRECTORY = "loot_values";

    private static final String KEY_ITEM = "item";
    private static final String KEY_TAG = "tag";
    private static final String KEY_VALUE = "value";
    private static final String KEY_CHANCE = "chance";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final LootValues INSTANCE = new LootValues();

    /** 按文件名排序后的物品条目。 */
    private static List<Rule> itemRules = List.of();

    /** 同上，标签条目。物品那一组永远先查。 */
    private static List<Rule> tagRules = List.of();

    private LootValues() {
        super(GSON, DIRECTORY);
    }

    /** 一件物品的定价。{@code value} 是<b>单价</b>（没乘数量）。 */
    public record Pricing(double value, double chance) {

        /** 有没有可能转化出资源。价值或概率为 0 就是"永远不可能"，那就不必去捡。 */
        public boolean canEverYield() {
            return value > 0.0D && chance > 0.0D;
        }
    }

    /** 一条解析好的规则。{@code tag} 为 null 表示按物品匹配。 */
    private record Rule(Item item, TagKey<Item> tag, double value, double chance) {

        boolean matches(ItemStack stack) {
            return tag == null ? stack.is(item) : stack.is(tag);
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    /**
     * 这件物品的定价。表里没有就用配置的两条默认值。
     *
     * <p>不在这里掷骰：挑目标时要问「它有没有可能转化出东西」，那时不该消耗随机数——
     * 否则真菌每扫一次视野就把附近每件掉落物的运气烧掉一轮。
     */
    public static Pricing pricingOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return new Pricing(0.0D, 0.0D);
        }
        // 物品优先于标签：给某个物品单独写一条就能盖过它所属标签的那条
        for (Rule rule : itemRules) {
            if (rule.matches(stack)) {
                return new Pricing(rule.value(), rule.chance());
            }
        }
        for (Rule rule : tagRules) {
            if (rule.matches(stack)) {
                return new Pricing(rule.value(), rule.chance());
            }
        }
        return new Pricing(SporeAddFungusConfig.lootDefaultValue(),
                SporeAddFungusConfig.lootDefaultChance());
    }

    // ------------------------------------------------------------------

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Rule> items = new ArrayList<>();
        List<Rule> tags = new ArrayList<>();
        files.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), items, tags));
        itemRules = List.copyOf(items);
        tagRules = List.copyOf(tags);
        LOGGER.info("[SporeAdd] 掉落物转化表已加载：物品条目 {} 条、标签条目 {} 条", itemRules.size(), tagRules.size());
    }

    /** 读一条。写坏的地方逐条跳过并留日志，不让一个笔误把整张表（甚至开服）搞砸。 */
    private static void readFile(ResourceLocation file, JsonElement element, List<Rule> items, List<Rule> tags) {
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
        if (!object.has(KEY_VALUE)) {
            warn(file, "缺 value");
            return;
        }
        double value = object.get(KEY_VALUE).getAsDouble();
        if (value <= 0.0D) {
            warn(file, "value 要大于 0（要排除某件东西请用 loot_blacklist）");
            return;
        }
        double chance = object.has(KEY_CHANCE) ? object.get(KEY_CHANCE).getAsDouble() : 1.0D;
        if (chance <= 0.0D || chance > 1.0D) {
            warn(file, "chance 必须在 (0,1] 之间（写 0.35 就是 35%）");
            return;
        }

        if (byItem) {
            ResourceLocation id = ResourceLocation.tryParse(object.get(KEY_ITEM).getAsString());
            Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
            // get 对不存在的 id 返回 AIR，那等同于"这条没用"，跳过
            if (item == null || item == Items.AIR) {
                warn(file, "item 不合法或不存在");
                return;
            }
            items.add(new Rule(item, null, value, chance));
        } else {
            ResourceLocation id = ResourceLocation.tryParse(object.get(KEY_TAG).getAsString());
            if (id == null) {
                warn(file, "tag 不合法（要写成 命名空间:路径，不要写 #）");
                return;
            }
            tags.add(new Rule(null, TagKey.create(Registries.ITEM, id), value, chance));
        }
    }

    private static void warn(ResourceLocation file, String reason) {
        LOGGER.warn("[SporeAdd] 掉落物转化表的这一条被跳过（{}）：{}", reason, file);
    }
}
