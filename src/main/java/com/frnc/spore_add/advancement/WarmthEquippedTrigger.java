package com.frnc.spore_add.advancement;

import com.frnc.spore_add.SporeAdd;
import com.google.gson.JsonObject;

import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「装备上附有烈阳的护甲」这个进度判据。
 *
 * <h2>为什么不用原版的 {@code inventory_changed}</h2>
 * 那个判据只回答"背包里有没有这件东西"——把附魔护甲捡起来、放进箱子前的那一瞬间就算达成，
 * 与需求里的"<b>装备上</b>"不是一回事。原版没有"已装备"这类判据，所以自建一个，
 * 由 {@code ModEvents} 在装备变更时触发。
 *
 * <p>写法与同仓库的其它 mod 一致（{@code SimpleCriterionTrigger} + 一个只带玩家谓词的
 * {@code TriggerInstance}）：1.20.1 的自定义判据没有注册表，靠 {@code CriteriaTriggers.register}
 * 注册，见 {@link ModTriggers}。
 */
public class WarmthEquippedTrigger extends SimpleCriterionTrigger<WarmthEquippedTrigger.TriggerInstance> {

    private static final ResourceLocation ID = SporeAdd.id("warmth_equipped");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player,
                                            DeserializationContext context) {
        // 这个判据没有额外条件可配：能触发就说明已经穿上了
        return new TriggerInstance(player);
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {

        public TriggerInstance(ContextAwarePredicate player) {
            super(ID, player);
        }
    }
}
