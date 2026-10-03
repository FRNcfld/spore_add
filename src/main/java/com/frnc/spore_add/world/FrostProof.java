package com.frnc.spore_add.world;

import com.frnc.spore_add.SporeAdd;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 「这台冰箱不碰哪些方块」的<b>唯一名单</b>。
 *
 * <h2>为什么要有这个类</h2>
 * 本 mod 有四处会改写世界方块（冰霜新星的换冰与清真菌、冰雪的叹息的铺冰与清真菌、液态寒冷的
 * 冻结与扩散），它们各有各的循环。这些东西之前只在其中两处判过"不可破坏的方块要放过"，
 * 另外两处（尤其是叹息的铺冰）<b>根本不判</b>——结果是一发核弹会把传送门连同周围的空气一起
 * 铺成冰，传送门就那样没了。
 *
 * <p>所以名单收成一处，四处都问它。
 *
 * <h2>名单有两层</h2>
 * <ol>
 *   <li><b>硬度为负的方块</b>（基岩、屏障、命令方块、末地传送门框架……）——原版的定义就是
 *       "打不碎"，那它显然也不该被一发爆炸抹掉。这一条不靠标签，白送的。</li>
 *   <li><b>数据包标签 {@code #spore_add:frost_proof}</b>——显式列出来的，默认有五个：
 *     <ul>
 *       <li>{@code nether_portal}——下界门中间那层紫色方块；</li>
 *       <li>{@code end_portal}——末地龙池里那个回城传送门（打完末影龙出现的，就是它，
 *           见 {@code EndDragonFight}）；</li>
 *       <li>{@code end_gateway}——末地折跃门；</li>
 *       <li>{@code dragon_egg}——龙蛋。它<b>必须</b>靠这一层：硬度是 3，不是"不可破坏"，
 *           第一条兜不住它。</li>
 *       <li>{@code spore_add:frost_sigh}——本 mod 自己的核弹方块。它也得靠这一层（硬度 50）。
 *           不加的话，一发核弹会把旁边的另一颗核弹、或者冰霜新星会把一颗已经激活的核弹，
 *           直接铺成一格冰——而那条路绕过了 {@code FrostSighBlock#onDestroyedByPlayer}，
 *           所以"激活后挖不动"这条保护完全不起作用。</li>
 *     </ul>
 *     传送门那三个的硬度本来就是 -1、第一条已经覆盖，但<b>照样写进标签</b>：
 *     一是把"传送门是受保护的"这件事写在明面上，二是别的模组想保护自己的方块时，
 *     只要往这个标签里加一条即可，不需要改代码。</li>
 * </ol>
 *
 * <p><b>刻意不收构成传送门框架的方块</b>（末地传送门框架、以及搭下界门用的黑曜石）：
 * 需求要保护的是"传送门"本身，不是搭门的材料。黑曜石本来就是普通可破坏方块，不受任何一层影响；
 * 末地传送门框架虽然不在名单里，但它的硬度也是 -1，会被第一条兜住——那一层管的是
 * "不可破坏的方块"，与"是不是门框"无关，需求也明确要求它保持不动。
 * </ol>
 */
public final class FrostProof {

    /**
     * 数据包可扩展的名单：{@code data/&lt;命名空间&gt;/tags/blocks/frost_proof.json}。
     *
     * <p>用标签而不是硬编码的方块列表：别的模组（或整合包）想保护自己的方块时，
     * 加一个数据包文件就够了，不必等本 mod 更新。
     */
    public static final TagKey<Block> FROST_PROOF =
            TagKey.create(Registries.BLOCK, SporeAdd.id("frost_proof"));

    private FrostProof() {
    }

    /** 这个方块是不是名单里的——是的话，四处改写逻辑都该原样放过它。 */
    public static boolean isProtected(BlockState state) {
        // 打不碎的方块一律不碰
        if (state.getBlock().defaultDestroyTime() < 0) {
            return true;
        }
        return state.is(FROST_PROOF);
    }
}
