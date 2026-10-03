package com.frnc.spore_add.hivemind;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import com.Harbinger.Spore.Recipes.WombRecipe;
import com.Harbinger.Spore.Sentities.Organoids.Proto;
import com.Harbinger.Spore.Sentities.Organoids.Womb;
import com.frnc.spore_add.SporeAdd;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.compat.SporeCompat;
import com.frnc.spore_add.fungus.WombGate;
import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 心智**出资**替卡住的重构体补齐突变。
 *
 * <h2>为什么需要它</h2>
 * {@link WombGate} 要求重构体先被喂够几种属性才准孵化，而属性只能靠喂<b>特定进化体</b>
 * （骑士、温迪戈、撕裂者、鸣蜂……）。玩家不配合的话，重构体会一直凑不齐——
 * 这条能力就是让心智自己把这件事办掉：<b>花自己的生物质买缺失的那几种属性</b>。
 *
 * <h2>缺哪几种从哪来 —— 读 Spore 自己的配方表</h2>
 * 不把七个属性 id 写在配置里，也不硬编码在代码里，而是问
 * {@code RecipeManager} 要 {@code WombRecipe} 的全部实例、取它们的 {@code getAttribute()}。
 * 这样它<b>自动跟着 Spore 的数据走</b>：Spore 改配方、整合包加配方，这里都不用动。
 *
 * <h2>出价</h2>
 * 每次注入扣 {@code mindFundingCost} 点生物质（走 {@code Proto#eatBiomass}），
 * 比自己吃一只真菌贵得多——它是"买"，不是"捡"。生物质不够就这一轮不办，
 * 等它自己攒够（心智的收入本来就随袭击翻倍）。
 *
 * <p>一次只办一只重构体、只补<b>一种</b>属性：这样多只心智会自然分工，
 * 也不会在一拍之内把一只心智掏空。
 */
@Mod.EventBusSubscriber(modid = SporeAdd.MOD_ID)
public final class WombSupport {

    private static final Logger LOGGER = LogUtils.getLogger();

    private WombSupport() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!SporeAddFungusConfig.wombMindFundingEnabled()) {
            return;
        }
        int interval = SporeAddFungusConfig.wombMindFundingIntervalTicks();
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % interval != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            fundOnce(level);
        }
    }

    /** 这一轮让每只出得起价的心智各资助一只重构体。 */
    private static void fundOnce(ServerLevel level) {
        List<Proto> minds = hivemindsIn(level);
        if (minds.isEmpty()) {
            return;
        }
        Set<String> known = recipeAttributes(level);
        if (known.isEmpty()) {
            // Spore 的配方表读不到（没装 Spore 数据包、或它改了配方类型）——
            // 那就没有"缺失的属性"这个概念，静默跳过即可，不必刷日志
            return;
        }
        int cost = SporeAddFungusConfig.wombMindFundingCost();
        double range = SporeAddFungusConfig.wombMindFundingRange();

        for (Proto mind : minds) {
            if (mind.getBiomass() < cost) {
                continue;   // 出不起价，等它攒
            }
            Womb target = stalledWombNear(level, mind, range);
            if (target == null) {
                continue;   // 它附近没有需要资助的重构体
            }
            String missing = firstMissing(target, known);
            if (missing == null) {
                continue;   // 七种都齐了，只等生物质
            }
            mind.eatBiomass(cost);
            // getAttributeIDs 返回的是**内部那个活的 list**（不是副本），直接 add 就是想要的效果。
            // 用裸 List 是为了跟 Spore 的签名对齐——WombMutationMixin 那边同理。
            @SuppressWarnings("unchecked")
            List<String> mutations = target.getAttributeIDs();
            mutations.add(missing);
            LOGGER.info("[SporeAdd] 心智出资：替重构体 {} 补上突变 {}（耗 {} 生物质，现有 {} 种）",
                    target.getUUID(), missing, cost, WombGate.distinctMutations(target));
        }
    }

    /** 范围内第一只**正卡在门槛上**的重构体（同样卡住的取最近的）。 */
    private static Womb stalledWombNear(ServerLevel level, Proto mind, double range) {
        AABB area = mind.getBoundingBox().inflate(range);
        return level.getEntitiesOfClass(Womb.class, area).stream()
                .filter(WombGate::isStalled)
                .min((a, b) -> Double.compare(mind.distanceToSqr(a), mind.distanceToSqr(b)))
                .orElse(null);
    }

    /** 这只重构体还缺哪一种属性；七种齐了就返回 null。 */
    private static String firstMissing(Womb womb, Set<String> known) {
        // getAttributeIDs 是裸 List（元素声明上只是 Object），所以显式收成 String 再用——
        // 直接 new HashSet<>(list) 会在赋给 Set<String> 时推断失败
        Set<String> have = new HashSet<>();
        for (Object id : (List<?>) womb.getAttributeIDs()) {
            if (id != null) {
                have.add(id.toString());
            }
        }
        for (String attribute : known) {
            if (!have.contains(attribute)) {
                return attribute;
            }
        }
        return null;
    }

    /**
     * Spore 里全部同化配方所对应的属性 id。
     *
     * <p>{@code getAllRecipesFor} 要的是 {@code RecipeType}，而 {@code WombRecipe} 的类型实例
     * 就是 Spore 自己查配方时用的那个（见 {@code Womb#getCurrentRecipe}）。
     */
    private static Set<String> recipeAttributes(ServerLevel level) {
        Set<String> attributes = new HashSet<>();
        for (WombRecipe recipe : level.getRecipeManager()
                .getAllRecipesFor(WombRecipe.WombRecipeType.INSTANCE)) {
            String attribute = recipe.getAttribute();
            if (attribute != null && !attribute.isEmpty()) {
                attributes.add(attribute);
            }
        }
        return attributes;
    }

    private static List<Proto> hivemindsIn(ServerLevel level) {
        List<Proto> found = new ArrayList<>();
        for (Proto mind : SporeCompat.hiveminds()) {
            if (mind.level() == level && !mind.isRemoved() && mind.isAlive()) {
                found.add(mind);
            }
        }
        return found;
    }
}
