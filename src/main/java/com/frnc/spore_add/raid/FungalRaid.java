package com.frnc.spore_add.raid;

import java.util.UUID;

import javax.annotation.Nullable;

/**
 * 一次真菌袭击的全部状态。由 {@link RaidManager} 创建与推进，本身只存数据。
 *
 * <h2>为什么只记「打谁」而不记「谁在打」</h2>
 * 参战的真菌不是被新生成的，而是从世上已有的真菌里<b>调</b>过来的（需求 2：心智直接传送）。
 * 它们传送过去、拿到增益之后就自己按 AI 打，不需要我们持续指挥。所以"参战名单"是没有意义的：
 * 记下来也只是记住一批可能在半路被玩家杀掉的实体 id。
 *
 * <p>要让袭击"结束"，靠的是<b>阶段进度与时间上限</b>，而不是"所有参战者都死了"——
 * 后者既算不准（玩家可能跑掉、怪可能被别的怪杀了），也会让袭击在没有玩家参与时永远挂着。
 * 我方围剿那一段的结束条件则相反：那里确实看"场上清完了没有"，但另有一个每波超时兜底。
 *
 * <h2>阶段只有一个单向转换</h2>
 * {@code PREP → ARENA → WAVES → 结束}，没有回头路。准备阶段没凑够条件就<b>取消</b>整次袭击，
 * 而不是退回准备——那样只会让玩家看到台词反复播。
 */
public final class FungalRaid {

    /**
     * 袭击阶段。单向推进，没有回头路。
     *
     * <pre>
     *   PREP → ARENA → WAVES → 结束
     *            ↘______↗
     * </pre>
     * 中间的 {@link #ARENA} 是可跳过的：掷骰没中就直接从准备进 {@link #WAVES}。
     */
    public enum Phase {

        /** 准备：发台词、后台给心智加成（消耗↓、获取↑、发育↑），并在结束前发「恐惧吧」。 */
        PREP,

        /**
         * 前半段攻击：出怪整个交给 Spore 的竞技之须（{@code ArenaEntity}）。
         *
         * <p>我们只做两件事：盯着它有没有缩回消失、以及给它召出来的真菌上参战增益。
         */
        ARENA,

        /** 后半段攻击：我们自己的围剿波次——传送 + 逐波强化，打完发自己的战利品表。 */
        WAVES
    }

    /** 打谁。 */
    private final UUID target;

    /** 开始时的游戏刻。用来算总时长，也用来在日志里对齐时间线。 */
    private final long startGameTime;

    private Phase phase = Phase.PREP;

    /** 进入当前阶段之后过了多少 tick。换阶段时归零。 */
    private int phaseTicks;

    /** 总时长，跨阶段累计。准备阶段的超时判定用它。 */
    private int totalTicks;

    /** 准备阶段那句「恐惧吧」有没有发过。见 {@link #markWarned()}。 */
    private boolean warned;

    /** 已经打完了几波（{@link Phase#WAVES}）。0 表示还没开始第一波。 */
    private int wavesDone;

    /** 这一次袭击一共打算打几波。在进入 {@link Phase#WAVES} 时算好，之后不变。 */
    private int totalWaves;

    /** 我们种下的那只竞技之须。它缩回或消失后就进 {@link Phase#WAVES}。 */
    @Nullable
    private java.util.UUID arenaTendril;

    /**
     * 目标玩家躲在黑名单维度里、连续缺席了多少 tick。
     *
     * <p>玩家一回到允许的维度就归零（见 {@link #resetAway()}）。
     */
    private int awayTicks;

    public FungalRaid(UUID target, long startGameTime) {
        this.target = target;
        this.startGameTime = startGameTime;
    }

    public UUID target() {
        return target;
    }

    public Phase phase() {
        return phase;
    }

    public int phaseTicks() {
        return phaseTicks;
    }

    public int totalTicks() {
        return totalTicks;
    }

    public long startGameTime() {
        return startGameTime;
    }

    /**
     * 正常推进一 tick（玩家在战场上）。
     *
     * <p>返回 true 表示这次袭击已经结束，调用方应当把它从表里摘掉。
     * 目前恒返回 false——结束判定都在调用方，留这个返回值是为了让调用点的形状统一。
     */
    public boolean tick() {
        phaseTicks++;
        totalTicks++;
        return false;
    }

    /**
     * 玩家不在战场上时推进一 tick：<b>只累计缺席时间，阶段计时停住</b>。
     *
     * <p>为什么阶段计时必须停住：不停的话，跑进下界的玩家会在几秒后被
     * {@code arenaTimeoutSeconds} 判失败——那等于"躲一下就直接输"，
     * 与需求给的"300 秒内回来就不算输"矛盾。冻结之后那 300 秒才是真正可用的窗口。
     */
    public void tickAway() {
        awayTicks++;
    }

    /** 玩家回到了允许的维度：缺席计时归零。 */
    public void resetAway() {
        awayTicks = 0;
    }

    /** 连续缺席了多少 tick。 */
    public int awayTicks() {
        return awayTicks;
    }

    // ------------------------------------------------------------------
    // 阶段推进
    // ------------------------------------------------------------------

    /**
     * 转入某一阶段。返回 false 表示它已经不在上一个阶段了（重复调用是安全的空操作）。
     *
     * <p>只允许「PREP → ARENA/WAVES」与「ARENA → WAVES」这两条边，别的组合直接拒绝——
     * 阶段是单向的，把这件事交给调用方去自觉遵守迟早会出乱子。
     */
    public boolean advanceTo(Phase next) {
        boolean legal = switch (next) {
            case ARENA -> phase == Phase.PREP;
            case WAVES -> phase == Phase.PREP || phase == Phase.ARENA;
            case PREP -> false;
        };
        if (!legal) {
            return false;
        }
        phase = next;
        phaseTicks = 0;
        return true;
    }

    /** 准备阶段那句「恐惧吧」发过了没有。 */
    public boolean warned() {
        return warned;
    }

    /** 记下「恐惧吧」已经发过。见 {@link #warned()}。 */
    public void markWarned() {
        warned = true;
    }

    // ------------------------------------------------------------------
    // 竞技之须（前半段）
    // ------------------------------------------------------------------

    /** 记下我们种下的竞技之须，用来盯它有没有消失。 */
    public void setArenaTendril(@Nullable java.util.UUID tendril) {
        this.arenaTendril = tendril;
    }

    @Nullable
    public java.util.UUID arenaTendril() {
        return arenaTendril;
    }

    // ------------------------------------------------------------------
    // 我方围剿波次（后半段）
    // ------------------------------------------------------------------

    /** 定下这次一共打几波。进入 {@link Phase#WAVES} 时调一次。 */
    public void setTotalWaves(int totalWaves) {
        this.totalWaves = Math.max(0, totalWaves);
    }

    public int totalWaves() {
        return totalWaves;
    }

    /** 已经打完了几波。 */
    public int wavesDone() {
        return wavesDone;
    }

    /** 当前正在打第几波（从 1 开始）；还没开始时返回 1。 */
    public int currentWave() {
        return Math.min(totalWaves, wavesDone + 1);
    }

    /** 记一波打完。 */
    public void markWaveDone() {
        wavesDone++;
    }

    /**
     * 把<b>当前阶段内</b>的计时归零，但不换阶段。
     *
     * <p>用于"同一阶段内的第 N 波"——波与波之间不是阶段切换，但每波各自要用满
     * {@code ownWaveSeconds}，所以需要一个重置阶段计时、却不推进阶段的操作。
     * 用 {@link #advanceTo(Phase)} 做不到这件事（它会拒绝同阶段的转换，那是有意的）。
     */
    public void restartPhaseTicks() {
        phaseTicks = 0;
    }

    /** 波次是不是已经全部打完。 */
    public boolean allWavesDone() {
        return wavesDone >= totalWaves;
    }

    /**
     * 当前这一波的参战增益该加几级。
     *
     * <p>需求：波次越高，真菌越强。用"已经打完的波数"当基准，于是第一波不加成、
     * 之后逐波递增。amplifier 有 127 的字节上限，所以这里顺手封顶——
     * 配置配得离谱时也不该把一个坏掉的 amplifier 写进同步数据。
     */
    public int waveAmplifierBonus(int perWave) {
        return Math.min(120, Math.max(0, wavesDone) * Math.max(0, perWave));
    }

}
