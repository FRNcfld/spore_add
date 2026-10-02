package com.frnc.spore_add.fungus;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Sentities.BaseEntities.EvolvedInfected;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.Harbinger.Spore.Sentities.EvolvingInfected;
import com.frnc.spore_add.SporeAddFungusConfig;
import com.frnc.spore_add.scavenger.Scavenger;

/**
 * 需求的「真菌更易进化」。
 *
 * <h2>Spore 原本的进化节奏</h2>
 * 进化由 {@code EvolvingInfected} 的两个默认方法驱动（{@code tickEvolution} / {@code tickHyperEvolution}），
 * 每 20 tick 一次，条件是两个计数器都到位：
 * <ol>
 *   <li><b>进化点</b>：{@code evoPoints >= min_kills}（默认 1；hyper 用 {@code min_kills_hyper}，默认 7）。
 *       {@code Infected#awardKillScore} 每击杀一个生物 +1 点，所以这一条读作"至少杀过一个"。</li>
 *   <li><b>进化冷却</b>：{@code evolutionCoolDown >= evolution_age_human}（默认 300，单位是"次"，
 *       而它每秒才 +1，所以读作 300 秒；hyper 用 600）。</li>
 * </ol>
 * 关键在于第 2 条的那个 {@code else if}：冷却<b>只在身上没有冻伤时才 +1</b>。
 * 也就是说，一个泡在寒冷里、或被人用冻伤武器缠着的真菌，进化进度是<b>完全停滞</b>的。
 *
 * <h2>本类的做法</h2>
 * 每秒（{@code tickCount % 20 == 0}）额外给冷却加上 {@code evolutionSpeedBonus} 点。
 * 这一手同时解决两件事：
 * <ul>
 *   <li><b>更快</b>：默认 +4，加上 Spore 自己的 +1，300 秒的门槛约 60 秒就跨过去了；</li>
 *   <li><b>冻伤不再卡死</b>：我们这笔加法<b>不看冻伤</b>，所以被冻着的真菌仍在积累进化进度。
 *       与需求 5 的"抗寒"是同一套设计意图——寒冷该削弱真菌的战斗力，但不该把它的成长彻底摁死。</li>
 * </ul>
 *
 * <h2>为什么走事件而不是 mixin 那个接口</h2>
 * 逻辑在 {@code EvolvingInfected} 的<b>接口默认方法</b>里，注入它要写接口 mixin；
 * 而这里需要的两个操作（读冷却、写冷却）在 {@code Infected} 上都是 public。
 * 走 {@code LivingTickEvent} 既不用碰第三方接口的字节码，也顺带把"不看冻伤"这件事实现得更直白。
 *
 * <p>代价是每个 LivingEntity 每 tick 都要过一次 {@code instanceof}——一次类型检查，
 * 相比原版每 tick 在实体上跑的其它事可以忽略。
 */
public final class FungusEvolution {

    /** Spore 自己的推进节拍也是 20 tick。跟随它，好让"额外 +N"能直接按每秒的心智模型来读。 */
    private static final int TICKS_PER_PROGRESS = 20;

    private FungusEvolution() {
    }

    /**
     * 每 tick 由 {@code ModEvents#onLivingTick} 转交进来。调用方负责挡住客户端。
     *
     * <p><b>只在够格时才推进</b>：{@code evoPoints} 没到门槛就什么都不做。
     * 这一条不是可选的——冷却是个"我在这个段位待了多久"的计时器，
     * 要是让一个没杀过东西的真菌也偷偷攒冷却，它会在第一次击杀的瞬间直接进化，
     * 那就不是"更易进化"，而是"跳过进化条件"了。
     */
    public static void tick(Infected infected) {
        if (infected.tickCount % TICKS_PER_PROGRESS != 0) {
            return;
        }
        // 只有会进化的那一批才有这个计数器可言。Spore 里恰好是：
        // 基础感染体（进化成进化体）与进化体（Hyper 进化）两类，都实现了这个接口
        if (!(infected instanceof EvolvingInfected)) {
            return;
        }
        // 拾荒者要从这里排除掉：它继承自 InfectedHuman，所以**也会** instanceof EvolvingInfected
        // （接口是继承的，没法"不实现"），而需求是它无法进化。
        // Scavenger 自己也把 tickEvolution 覆写成空了，这里是第二道——两道都留着是有意的：
        // 那边防的是任何调用方，这边防的是我们自己的加速逻辑白写一个没人读的计数器。
        if (infected instanceof Scavenger) {
            return;
        }
        int bonus = SporeAddFungusConfig.evolutionSpeedBonus();
        if (bonus <= 0) {
            return;
        }
        if (infected.getEvoPoints() < evolutionPointThreshold(infected)) {
            return;
        }
        infected.setEvolution(infected.getEvolutionCoolDown() + bonus);
    }

    /**
     * 这个真菌当前段位需要的进化点门槛。
     *
     * <p>Spore 用两个不同的配置项：基础感染体要 {@code min_kills}（默认 1），
     * 进化体要 {@code min_kills_hyper}（默认 7）。判据用 {@code instanceof EvolvedInfected}——
     * 已核对过 Spore 的调用图：调 {@code tickHyperEvolution} 的正好就是 {@code EvolvedInfected} 的子类，
     * 调 {@code tickEvolution} 的正好是那些不走这条继承链的基础感染体，两者没有交集。
     */
    private static int evolutionPointThreshold(Infected infected) {
        return infected instanceof EvolvedInfected
                ? SConfig.SERVER.min_kills_hyper.get()
                : SConfig.SERVER.min_kills.get();
    }
}
