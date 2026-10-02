package com.frnc.spore_add.raid;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.frnc.spore_add.SporeAdd;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 袭击的<b>维度黑名单</b>（数据包形式）：列在这里的维度不会发生真菌袭击。
 *
 * <h2>文件放哪、怎么写</h2>
 * <pre>
 *   data/&lt;你的命名空间&gt;/raid_blacklist/&lt;任意名字&gt;.json
 *
 *   {
 *     "replace": false,
 *     "dimensions": ["minecraft:the_nether", "twilightforest:twilight_forest"]
 *   }
 * </pre>
 * 一个数据包可以放多个文件，会<b>合并</b>；某个文件写 {@code "replace": true} 会先清空已收集的名单
 * 再从自己开始加（与标签的 {@code replace} 语义一致，便于整合包整体覆盖别人的名单）。
 *
 * <h2>为什么不用标签</h2>
 * 维度注册表在原版里是个特例：{@code Registries} 里 <b>{@code minecraft:dimension} 这个 id 被两个注册表共用</b>
 * ——{@code Registry<Level>}（运行期实际加载的维度）与 {@code Registry<LevelStem>}（数据包定义的那个）。
 * 而 {@code data/&lt;ns&gt;/tags/dimension/*.json} 里的标签最终会落到哪一个，我没法在不启动游戏的情况下确认。
 * 与其赌一个"可能为空、于是黑名单悄悄失效"的行为，不如自己读文件：
 * 格式同样在数据包里、同样能用 {@code /reload} 重载，但解析完全由我们控制。
 *
 * <h2>黑名单管两件事</h2>
 * <ol>
 *   <li><b>不会在黑名单维度里发动</b>——{@code RaidManager#tryStart} 直接跳过。</li>
 *   <li><b>已经开打时玩家躲进去，整场袭击冻结</b>——{@code RaidManager#tickRaid} 里
 *       只累计缺席时间、阶段计时停住，给玩家 {@code raid.absenceTimeoutSeconds}（默认 300 秒）
 *       回到允许的维度。回来了就归零继续打；<b>逾期不归判玩家失败</b>，
 *       走「死于真菌之手」那条线（削恨意值 + 资源给心智）。</li>
 * </ol>
 * 第二条早期版本是"躲进去立刻结束、既不判失败也不给奖励"，后来改成现在这样：
 * 立刻结束等于把黑名单维度变成一个<b>没有代价的逃跑按钮</b>——玩家被袭击缠住时往传送门里一跳就没事了。
 * 给一个限时的窗口之后，"临时躲一下"仍然可行，但"靠它赖掉整场袭击"不行。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID)
public final class RaidDimensionBlacklist extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 数据包目录名。文件路径是 {@code data/<ns>/raid_blacklist/<name>.json}。 */
    private static final String DIRECTORY = "raid_blacklist";

    /** 字段名。 */
    private static final String KEY_REPLACE = "replace";
    private static final String KEY_DIMENSIONS = "dimensions";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 单例。数据包重载时由 Forge 事件重新登记同一个实例（重载会自动重跑 {@link #apply}）。 */
    private static final RaidDimensionBlacklist INSTANCE = new RaidDimensionBlacklist();

    /** 黑名单。读盘前为空 = 任何维度都可以发生袭击，这是安全的默认值。 */
    private static Set<ResourceLocation> blacklist = Set.of();

    private RaidDimensionBlacklist() {
        super(GSON, DIRECTORY);
    }

    /**
     * 登记到服务端的数据包重载流程里。
     *
     * <p>挂在 {@code AddReloadListenerEvent} 上：它在服务端资源（数据包）加载时触发，
     * 开服与 {@code /reload} 各触发一次——所以改完文件 {@code /reload} 就能生效，不必重启。
     */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    /**
     * 这个维度允不允许发生袭击。
     *
     * <p>取名用<b>肯定式</b>（"允不允许"）而不是否定式：调用点写
     * {@code if (!allowed(level)) return;} 比 {@code if (isBlacklisted(level))} 更容易读对，
     * 也避免了以后有人把 {@code !} 漏掉——那会正好把语义反过来。
     */
    public static boolean raidsAllowed(ServerLevel level) {
        return !blacklist.contains(level.dimension().location());
    }

    // ------------------------------------------------------------------

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Set<ResourceLocation> collected = new HashSet<>();
        // 按文件路径排序，让"谁先谁后"与"replace 清空谁"是确定的——
        // 遍历 Map 的顺序不保证，不定序的话 replace 的语义会随加载顺序漂移
        files.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), collected));
        blacklist = Set.copyOf(collected);
        if (!blacklist.isEmpty()) {
            LOGGER.info("[SporeAdd] 袭击维度黑名单已加载，共 {} 个维度：{}", blacklist.size(), blacklist);
        }
    }

    /**
     * 读一个文件。写坏的地方<b>逐条跳过</b>并留日志，不让一个笔误把整份名单（甚至开服）搞砸。
     */
    private static void readFile(ResourceLocation file, JsonElement element, Set<ResourceLocation> collected) {
        if (!element.isJsonObject()) {
            LOGGER.warn("[SporeAdd] 袭击维度黑名单的 {} 不是一个 JSON 对象，已跳过", file);
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has(KEY_REPLACE) && object.get(KEY_REPLACE).getAsBoolean()) {
            collected.clear();
        }
        if (!object.has(KEY_DIMENSIONS)) {
            return;
        }
        for (JsonElement item : object.getAsJsonArray(KEY_DIMENSIONS)) {
            ResourceLocation id = ResourceLocation.tryParse(item.getAsString());
            if (id == null) {
                LOGGER.warn("[SporeAdd] 袭击维度黑名单的 {} 里有不合法的维度 id，已跳过：{}", file, item);
                continue;
            }
            collected.add(id);
        }
    }
}
