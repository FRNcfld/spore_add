package com.frnc.spore_add.fungus;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.frnc.spore_add.SporeAddFungusConfig;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * 「这件掉落物值多少资源」的全部算法，由配置里的转化表驱动。
 *
 * <h2>表的两种写法</h2>
 * <pre>
 *   minecraft:iron_ingot|6      按物品 id 匹配
 *   #minecraft:logs|0.5         按物品标签匹配（# 开头）
 * </pre>
 * 标签那条是为了让整合包一次给一整类物品定价，不必逐个列 id——
 * 与 Spore 自己的配置表（{@code "a|b"} 字符串列表）同一套写法，不用学新格式。
 *
 * <h2>先出现的那条赢</h2>
 * 顺序匹配、命中即停。所以把具体的 id 写在标签前面，就能用 id 覆盖标签——
 * 配置注释里也是这么说的。反过来「后匹配覆盖」那种做法（Spore 的 CDU 就是那样）
 * 会让人没法预测结果，具体规则被笼统规则盖掉尤其费解。
 *
 * <h2>解析结果会缓存</h2>
 * 真菌每搜寻一次掉落物就要查一次表，而表在游戏运行期几乎不变。
 * 缓存以"原始配置列表本身"为键：{@link #rules()} 比较一次
 * {@code List.equals}（十几个字符串），相等就复用上次解析出来的规则，
 * 于是既省掉了每次的字符串切分与注册表查询，又能在玩家改配置后自动重解析。
 */
public final class LootValues {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 一条已经解析好的规则：按物品或按标签匹配，命中给多少资源。 */
    private record Rule(@Nullable Item item, @Nullable TagKey<Item> tag, double value) {

        boolean matches(ItemStack stack) {
            if (item != null) {
                return stack.is(item);
            }
            return tag != null && stack.is(tag);
        }
    }

    /** 上一次解析用的原始配置。用 equals 比较，所以玩家改了配置会自动重解析。 */
    private static List<? extends String> cachedRaw;

    private static List<Rule> cachedRules = List.of();

    private LootValues() {
    }

    /**
     * 这堆物品值多少资源。
     *
     * <p>按<b>整堆</b>算（单价 × 数量），所以捡到一组 64 个的东西是一次结算一大笔，
     * 而不是分 64 次——真菌一次只捡得起一件掉落物实体，那一件里可能就是一整组。
     */
    public static double valueOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        for (Rule rule : rules()) {
            if (rule.matches(stack)) {
                return rule.value() * stack.getCount();
            }
        }
        return SporeAddFungusConfig.lootDefaultValue() * stack.getCount();
    }

    // ------------------------------------------------------------------

    private static List<Rule> rules() {
        List<? extends String> raw = SporeAddFungusConfig.lootValues();
        if (raw.equals(cachedRaw)) {
            return cachedRules;
        }
        List<Rule> parsed = parse(raw);
        cachedRaw = List.copyOf(raw);
        cachedRules = parsed;
        return parsed;
    }

    /**
     * 解析转化表。写坏的条目<b>跳过</b>而不是抛异常：配置是自由文本，
     * 一个手滑的空格不该让某只真菌捡东西时把服务器崩掉。
     */
    private static List<Rule> parse(List<? extends String> raw) {
        List<Rule> rules = new ArrayList<>();
        for (String entry : raw) {
            int separator = entry.indexOf('|');
            if (separator <= 0 || separator == entry.length() - 1) {
                warn(entry, "缺少分隔符 '|'");
                continue;
            }
            String key = entry.substring(0, separator).trim();
            double value;
            try {
                value = Double.parseDouble(entry.substring(separator + 1).trim());
            } catch (NumberFormatException e) {
                warn(entry, "右侧不是数字");
                continue;
            }
            if (value <= 0.0D) {
                continue;   // 值 <= 0 等于"不捡这一条"，不必留成规则
            }

            if (key.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(key.substring(1));
                if (id == null) {
                    warn(entry, "标签 id 不合法（要写成 #命名空间:路径）");
                    continue;
                }
                rules.add(new Rule(null, TagKey.create(Registries.ITEM, id), value));
            } else {
                ResourceLocation id = ResourceLocation.tryParse(key);
                Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
                // get 对不存在的 id 返回 AIR，那等同于"这条没用"，跳过
                if (item == null || item == net.minecraft.world.item.Items.AIR) {
                    warn(entry, "物品 id 不合法或不存在");
                    continue;
                }
                rules.add(new Rule(item, null, value));
            }
        }
        return rules;
    }

    private static void warn(String entry, String reason) {
        LOGGER.warn("[SporeAdd] 掉落物转化表里的这一条被跳过（{}）：{}", reason, entry);
    }
}
