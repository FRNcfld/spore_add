package com.frnc.spore_add.hatred;

import java.util.List;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.BaseEntities.EvolvedInfected;
import com.Harbinger.Spore.Sentities.BaseEntities.Hyper;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.Harbinger.Spore.Sentities.BaseEntities.Organoid;
import com.Harbinger.Spore.Sentities.CasingGenerator;
import com.Harbinger.Spore.Sentities.ChunkLoaderMob;
import com.Harbinger.Spore.Sentities.EvolvingInfected;
import com.Harbinger.Spore.Sentities.FoliageSpread;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.fungus.FungusCombat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「杀这只真菌该记多少恨意值」的全部算法。纯计算，不碰世界、不写账本。
 *
 * <h2>需求里列的五项因子，各自对应一个什么数</h2>
 * <pre>
 *   恨意值 = 基础值
 *          × 等级      ← 类继承：基础感染体 / 进化体 / 超级体 / 器官类 / 灾厄类
 *          × 类型      ← 配置表的按实体覆盖（默认全 1.0，留给按物种微调）
 *          × 重要性    ← 职能：会不会加载区块 / 生成菌壳 / 铺植被 / 还能不能进化
 *          × 是否链接  ← Infected#getLinked()
 *          × (1 + 发育) ← Infected#getEvoPoints() × 权重，带上限
 * </pre>
 * 「重要性」由 Spore 自己的标记接口推出来，不是又一张按实体的表——需求把它与「类型」并列，
 * 而这两者在数据上确实不同：类型是"这个物种我想给多少"，重要性是"它在阵营里管什么用"。
 * 两张表都留了按实体的覆盖口，任一张留空就等于关掉该维度。
 *
 * <h2>为什么「等级」不认 TrueCalamity 接口</h2>
 * Spore 里利维坦与洞食者的分节实体（{@code LeviathanMultipart} / {@code HohlMultipart}）是
 * 独立的 {@code LivingEntity}，而且<b>也实现了</b> {@code TrueCalamity} 接口。它们会照常触发死亡事件。
 * 认接口的话，砍掉一节尾巴会按灾厄类记 25 倍。所以判据只认 {@code Calamity} 这个父类——
 * 已核对过：真正的灾厄（吞噬者 / 利维坦 / 洞食者 / 围攻者 / 榴弹者……）全都是它的子类，
 * 而那些分节实体都不是。剩下不匹配的落到 {@link Tier#OTHER}，默认倍率是 <b>0</b>。
 */
public final class HatredValues {

    /**
     * 真菌的等级档位。
     *
     * <p><b>序号与配置里的 {@code hatredTierMultiplier}</b> 一一对应，改顺序等于改配置含义，
     * 所以这里显式写着序号，别随手调换。
     */
    public enum Tier {

        /** 0：基础感染体（{@code Infected} 本身）。 */
        INFECTED,

        /** 1：进化体（{@code EvolvedInfected}）。 */
        EVOLVED,

        /** 2：超级体（{@code Hyper}）。注意它与 {@code EvolvedInfected} 是兄弟，不是父子。 */
        HYPER,

        /** 3：器官类（{@code Organoid}：心智 Proto、蜂巢肿瘤、丘……）。 */
        ORGANOID,

        /** 4：灾厄类（{@code Calamity} 的子类）。 */
        CALAMITY,

        /** 5：都不匹配的真菌实体（BOSS 分节实体、感染爪之类的工具实体），默认倍率 0。 */
        OTHER
    }

    private HatredValues() {
    }

    // ------------------------------------------------------------------
    // 等级
    // ------------------------------------------------------------------

    /**
     * 判档。<b>顺序有讲究</b>：从最具体往下判，否则子类会被父类先截走。
     *
     * <p>{@code Calamity} 与 {@code Organoid} 都是 {@code UtilityEntity} 的子类（互不相干），
     * 而 {@code Hyper} 与 {@code EvolvedInfected} 都是 {@code Infected} 的子类（也互不相干），
     * 所以这两组内部无所谓顺序，但组与组之间必须先判 "不是 Infected 的那两支" 之外的具体档。
     */
    public static Tier tierOf(Entity entity) {
        if (entity instanceof Calamity) {
            return Tier.CALAMITY;
        }
        if (entity instanceof Organoid) {
            return Tier.ORGANOID;
        }
        if (entity instanceof Hyper) {
            return Tier.HYPER;
        }
        if (entity instanceof EvolvedInfected) {
            return Tier.EVOLVED;
        }
        if (entity instanceof Infected) {
            return Tier.INFECTED;
        }
        return Tier.OTHER;
    }

    // ------------------------------------------------------------------
    // 一次击杀值多少
    // ------------------------------------------------------------------

    /**
     * 击杀这只真菌该记的恨意值。结果已按 {@code hatredMaxPerKill} 夹住。
     *
     * <p>调用方负责先判断"这确实是一只真菌"（见 {@link FungusCombat#isFungus}）——
     * 本方法对非真菌也可能算出一个非 0 的值（{@code Tier.OTHER} 就算 0，但配置可以调大），
     * 所以那道判断不能省。
     */
    public static double killValue(LivingEntity victim) {
        Tier tier = tierOf(victim);
        double value = SporeAddFungusConfig.hatredBasePerKill();
        value *= SporeAddFungusConfig.hatredTierMultiplier(tier.ordinal());
        value *= overrideMultiplier(SporeAddFungusConfig.hatredTypeMultipliers(), victim, 1.0D);
        value *= importanceMultiplier(victim);
        value *= linkedMultiplier(victim);
        value *= 1.0D + developmentBonus(victim);
        return Math.min(value, SporeAddFungusConfig.hatredMaxPerKill());
    }

    /**
     * 发育程度带来的额外倍率（不含基础的 1.0）。
     *
     * <p>只有 {@code Infected} 有进化点——Spore 让它靠击杀累积、攒够就进化，
     * 所以进化点越高代表这只真菌越"成熟"，杀它拿到的东西更多。器官类没有这个概念，恒为 0。
     */
    private static double developmentBonus(Entity entity) {
        if (!(entity instanceof Infected infected)) {
            return 0.0D;
        }
        double points = Math.max(0, infected.getEvoPoints());
        return Math.min(SporeAddFungusConfig.hatredDevelopmentCap(),
                points * SporeAddFungusConfig.hatredDevelopmentWeight());
    }

    /**
     * 链接状态带来的倍率。
     *
     * <p>只有 {@code Infected} 有这个状态（Spore 的心智扫描会给范围内的感染体置 linked）。
     * 器官类没有，所以它们不参与这一项，恒为 1.0——而不是被当成"未链接"扣一档。
     */
    private static double linkedMultiplier(Entity entity) {
        if (entity instanceof Infected infected) {
            return infected.getLinked()
                    ? SporeAddFungusConfig.hatredLinkedMultiplier()
                    : SporeAddFungusConfig.hatredUnlinkedMultiplier();
        }
        return 1.0D;
    }

    /**
     * 「重要性」：由 Spore 的职能标记接口推出来，几个都命中就相乘。
     *
     * <p>用的全是 Spore 自己用来标记"这个实体有特殊职责"的接口，所以这里不需要维护一张
     * 会随 Spore 版本失效的实体清单。整合包想改某一只的权重，用配置里的覆盖表。
     */
    private static double importanceMultiplier(Entity entity) {
        double role = 1.0D;
        if (entity instanceof ChunkLoaderMob) {
            role *= SporeAddFungusConfig.hatredImportanceChunkLoader();
        }
        if (entity instanceof CasingGenerator) {
            role *= SporeAddFungusConfig.hatredImportanceCasingGenerator();
        }
        if (entity instanceof FoliageSpread) {
            role *= SporeAddFungusConfig.hatredImportanceFoliageSpread();
        }
        if (entity instanceof EvolvingInfected) {
            role *= SporeAddFungusConfig.hatredImportanceEvolving();
        }
        return overrideMultiplier(SporeAddFungusConfig.hatredImportanceMultipliers(), entity, role);
    }

    // ------------------------------------------------------------------
    // 配置里的 "实体id|数值" 表
    // ------------------------------------------------------------------

    /**
     * 在覆盖表里找这个实体；没找到或条目写坏了就返回 {@code fallback}。
     *
     * <p>沿用 Spore 自己那套 {@code "a|b"} 字符串列表的写法（见 {@code SporeCompat.cduBlockCleaning()}），
     * 整合包不用学新格式。写坏的条目<b>跳过</b>而不是抛异常：配置是自由文本，
     * 一个多打的空格不该让一次击杀把服务器崩掉。
     */
    private static double overrideMultiplier(List<? extends String> table, Entity entity, double fallback) {
        if (table.isEmpty()) {
            return fallback;
        }
        String id = entity.getEncodeId();
        if (id == null) {
            return fallback;
        }
        for (String entry : table) {
            int separator = entry.indexOf('|');
            if (separator <= 0 || separator == entry.length() - 1) {
                continue;
            }
            if (!entry.substring(0, separator).trim().equals(id)) {
                continue;
            }
            try {
                return Double.parseDouble(entry.substring(separator + 1).trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * 被 Despawning System 清理掉的一只真菌折算多少资源。
     *
     * <p>只乘<b>等级倍率</b>，不乘类型 / 重要性 / 链接 / 发育——那四项回答的是
     * "玩家杀了它有多可恨"，而这里问的是"回收了多少生物质"，两者不是一回事。
     *
     * <p>复用同一张等级表是有意的：不然就得再维护第二套 tier 配置，
     * 而"灾厄级比基础感染体值钱"这件事在两处本来就该是一致的。
     */
    public static double despawnValue(LivingEntity entity) {
        Tier tier = tierOf(entity);
        return SporeAddFungusConfig.despawnBaseValue()
                * SporeAddFungusConfig.hatredTierMultiplier(tier.ordinal());
    }

    // ------------------------------------------------------------------
    // 世界恨意值反过来给真菌的减伤
    // ------------------------------------------------------------------

    /**
     * 世界恨意值给真菌带来的减伤比例（0 ~ 上限）。
     *
     * <p>抽出来是因为有<b>两个</b>读者：真正改伤害的 {@code HatredEvents#onHurt}，
     * 以及扫描仪要显示的数。两处各写一遍的话，扫描仪迟早会显示一个和实际生效不一样的数字——
     * 那种错最难发现，因为两边看起来都"对"。
     */
    public static double fungusDamageReduction(double worldHatred) {
        return Math.min(SporeAddFungusConfig.fungusMaxResistance(),
                Math.max(0.0D, worldHatred) * SporeAddFungusConfig.fungusResistancePerHatred());
    }

    // ------------------------------------------------------------------
    // 袭击档位
    // ------------------------------------------------------------------

    /**
     * 这个恨意值落在第几个档位里（{@code floor(值 / 档位宽度)}）。
     *
     * <p>触发检测看的是"这个序号有没有变大"——所以同一次跨档只掷一次骰，
     * 而在档位内反复涨落（吃东西降一点、再打一点回来）不会反复触发。
     */
    public static long thresholdIndex(double hatred) {
        double step = SporeAddFungusConfig.raidThresholdStep();
        if (step <= 0.0D) {
            return 0L;
        }
        return (long) Math.floor(Math.max(0.0D, hatred) / step);
    }
}
