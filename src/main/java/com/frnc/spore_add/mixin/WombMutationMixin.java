package com.frnc.spore_add.mixin;

import java.util.List;

import com.Harbinger.Spore.Recipes.WombRecipe;
import com.Harbinger.Spore.Sentities.Organoids.Womb;
import com.frnc.spore_add.raid.RaidManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 灾厄重构体（Womb）的同化喂食：让一次喂食算多条突变。
 *
 * <p>需求：「叠满属性所需的生物减少 50%，袭击期间再减少 50%（剩余值的 50%）」。
 *
 * <h2>为什么改的是「一次追加几条」，而不是某个上限</h2>
 * 反编译 Spore 2.2.0j 后确认：{@code Womb} 里<b>没有任何</b>「最多叠 N 条」的上限常量，
 * 「叠满」也不是「喂齐配方表里的全部属性」。实际情况是：
 * {@code addMutation(recipe)} 就一句
 * {@code attributeIDs.add(recipe.getAttribute())}——一份不去重、不封顶的 {@code List<String>}；
 * 孵化时 {@code summon} 遍历<b>整个列表</b>，每条给灾厄对应的属性 {@code +1.0} 基础值，
 * 所以重复条目照样各算一次（配方本身是 7 个、每个只对应一个 {@code spore:*} 属性）。
 * 于是「叠满要喂多少只」完全等于「这份列表有多长」，
 * 「少喂一半」唯一自然的落点就是「一次喂食追加两条」。
 *
 * <h2>为什么倍率必须在这里锁定</h2>
 * 另一条思路是去 {@code summon} 里把那个 {@code +1.0D} 放大。但那个常量是<b>所有条目共用</b>的，
 * 改它会把袭击期间喂的和平时喂的一起放大，而需求要的是「喂的时候正在打袭击才加倍」。
 * 往列表里追加重复条目则天然带上了这个时间语义，而且它随
 * {@code addAdditionalSaveData} 的 {@code mutations} 一起存盘，我们不需要维护任何额外状态。
 *
 * <h2>为什么 {@code remap = false}</h2>
 * 注入的 {@code addMutation}、参数 {@code WombRecipe}、以及要调的 {@code getAttributeIDs}
 * 全是 <b>Spore 自己的</b>成员，生产环境不重命名，不该进 refmap。参见 {@code FungusColdMixin} 的类注释。
 */
@Mixin(Womb.class)
public abstract class WombMutationMixin {

    /**
     * 在 {@code addMutation} 跑完之后，把「多加的那几条」补进去。
     *
     * <p>为什么选这个挂点：{@code AssimilationMenu} 的构造器里没有 Womb 字段（已用
     * {@code javap} 核对过），这套系统并没有能改属性列表的 GUI，所以喂食是
     * {@code addMutation} 的唯一调用来源。
     *
     * <p>为什么是 TAIL 追加而不是 HEAD 取消重写：{@code addMutation} 本身只有
     * {@code attributeIDs.add(...)} 一句，重写它反而要自己去够那个私有字段；
     * 而 {@code getAttributeIDs()} 返回的是<b>内部那个活的 list</b>（不是副本），
     * 直接 add 就是想要的效果，连 {@code @Shadow} 都不需要。
     */
    @Inject(method = "addMutation", at = @At("TAIL"), remap = false)
    private void sporeAdd$extraMutations(WombRecipe recipe, CallbackInfo ci) {
        int total = RaidManager.adjustWombMutationCount(1);
        if (total <= 1) {
            return;
        }
        // 原方法已经追加过第 1 条，这里只补剩下的。
        // 用 raw List 是为了跟 Spore 的签名（`public List getAttributeIDs()`）对齐，
        // 写成 List<String> 只会多一个 unchecked 警告，并不带来任何实际检查。
        List mutations = ((Womb) (Object) this).getAttributeIDs();
        String attribute = recipe.getAttribute();
        for (int i = 1; i < total; i++) {
            mutations.add(attribute);
        }
    }
}
