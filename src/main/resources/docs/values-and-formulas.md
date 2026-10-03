# Spore Add 数值与公式说明

这份文档随 mod 的 jar 一起分发，讲清楚三件事：

1. **每个数值旋钮在哪、默认多少、范围多少** —— 想调什么，直接查表定位到键名；
2. **各处用到的计算公式** —— 那些"调了 A 会影响 B"的连带关系；
3. **名单与表格的格式** —— 掉落物定价、黑名单这些东西不写在这里，它们是数据包。

配置文件在 `config/` 下：

| 文件 | 管什么 |
|---|---|
| `spore_add-player-common.toml` | **让玩家更强、更好用**的东西：冰霜武器、可燃/爆燃、恨意值给玩家的增益 |
| `spore_add-fungus-common.toml` | **让真菌更强**的东西：真菌加强、灾厄重构体、拾荒者、恨意值系统、真菌袭击、心智存储、资源 |
| `spore_add-debug-common.toml` | **调试检查点**，默认全关。打开后各子系统会打决策日志（见第八节） |

改动在**重启游戏**或执行 **`/reload`** 之后生效；
调试检查点是例外——它可以在游戏里用 `/spore_add debug` 当场切换，改动会写回文件（见第八节）。

> 本文档的键名与默认值直接从代码里的配置定义生成。如果你装的版本里某个键找不到，
> 说明你手里这份文档与 jar 不是同一个版本。

---

## 一、哪些数值在配置里，哪些在数据包里

分界线是**「谁往里面加东西」**：

- **表 / 名单**（往集合里加*条目*）→ **数据包**。
  能随整合包分发、能被别的数据包覆盖、能 `/reload` 热更。
  目前有三处：掉落物定价 `loot_values`、掉落物黑名单 `loot_blacklist`、袭击维度黑名单 `raid_blacklist`。
  格式见本文第五节。
- **标量旋钮**（一个数字或一个开关）→ **配置**。
  因为配置能给每一格配上说明和范围校验；而 JSON 两样都没有（注释没地方写，写错的值也不会被夹回来）。
- **只读的硬上限**（协议限制，不是懒得做成可配置）→ 见本文第六节。

---

## 二、玩家侧数值速查表

文件：`config/spore_add-player-common.toml`

段落顺序：`frostNova` → `frostSigh` → `mistClear` → `liquidCold` → `coolant` → `frostbite` → `warmth` → `combustion` → `playerBuffs`

| 键 | 默认 | 范围 | 说明（配置里的第一行注释） |
|---|---|---|---|
| `frostNova.blockRadius` | `4` | 1 ~ 16 | 满蓄力时，落点处被替换成冰的球半径（格）。1 ~ 16。 |
| `frostNova.entityRadius` | `12` | 1 ~ 32 | 满蓄力时，落点处被施加冻伤的实体球半径（格）。1 ~ 32。 |
| `frostNova.explosionPower` | `8.0` | 0.0 ~ 32.0 | 满蓄力时的爆炸强度。原版 TNT 是 4.0，默认 8.0 即 TNT 的两倍。0 ~ 32。 |
| `frostNova.frostbiteSeconds` | `32` | 1 ~ 600 | 满蓄力时冻伤的持续秒数。1 ~ 600。 |
| `frostNova.frostbiteLevel` | `10` | 1 ~ 127 | 满蓄力时的冻伤显示层数（界面上的那个数字，= amplifier + 1）。1 ~ 127。 |
| `frostNova.minPowerFraction` | `0.20` | 0.0 ~ 1.0 | 最低蓄力（刚够发射）时的威力系数，0 ~ 1。 |
| `frostNova.chargeTicks` | `100` | 1 ~ 200 | 拉满所需的时间（tick）。20 与原版弓一致。1 ~ 200。 |
| `frostNova.minChargeTicks` | `20` | 0 ~ 200 | 低于这个蓄力时间（tick）松手就完全不发射。0 ~ 200。 |
| `frostNova.speedPerTick` | `1.5` | 0.1 ~ 10.0 | 弹体的飞行速度。0.1 ~ 10.0，默认 1.5。 |
| `frostNova.autoDetonateSeconds` | `5` | 1 ~ 60 | 弹体投出后多少秒仍未命中就自动引爆。1 ~ 60。 |
| `frostNova.cloudSeconds` | `10` | 1 ~ 600 | 一次爆炸那团霜雾持续多少秒。1 ~ 600，默认 10。 |
| `frostNova.secondary.delaySeconds` | `30` | 1 ~ 600 | 首次爆炸后多少秒二次引爆。1 ~ 600，默认 30。 |
| `frostNova.secondary.rangeMultiplier` | `2.0` | 1.0 ~ 4.0 | 影响范围倍率。1.0 ~ 4.0，默认 2.0。 |
| `frostNova.secondary.powerMultiplier` | `1.5` | 1.0 ~ 5.0 | 冻伤强度倍率：层数与秒数一起乘。1.0 ~ 5.0，默认 1.5。 |
| `frostNova.secondary.cloudSeconds` | `20` | 1 ~ 600 | 冰雾持续多少秒。1 ~ 600，默认 20。 |
| `frostSigh.radius` | `128` | 1 ~ 256 | 影响半径（格）。圆形，不是球。1 ~ 256。 |
| `frostSigh.sourceToLiquidColdChance` | `0.01` | 0.0 ~ 1.0 | 圆盘内每个**流体源**变成液态寒冷的概率。0.0 ~ 1.0，默认 0.01（1%）。 |
| `frostSigh.fullLayersUntil` | `0.75` | 0.0 ~ 1.0 | 铺三层冰的相对半径分界。0.0 ~ 1.0，默认 0.75。 |
| `frostSigh.twoLayersUntil` | `0.92` | 0.0 ~ 1.0 | 铺两层冰的相对半径分界。0.0 ~ 1.0，默认 0.92。 |
| `frostSigh.shockwaveSeconds` | `20` | 1 ~ 300 | 冲击环从中心扩散到边缘所需的秒数。1 ~ 300。 |
| `frostSigh.countdownSeconds` | `60` | 1 ~ 600 | 用冰霜新星激活后，多少秒爆发。1 ~ 600。 |
| `frostSigh.forceLoadChunks` | `true` | 开 / 关 | 倒计时期间是否强制加载圆盘覆盖的区块。 |
| `frostSigh.frozenSeconds` | `10` | 0 ~ 300 | 冲击环扫过时，玩家被冰封（无法移动）的秒数。0 ~ 300。0 表示不冰封。 |
| `frostSigh.frostbiteLevel` | `100` | 1 ~ 127 | 冲击环扫过时施加的冻伤显示层数（= amplifier + 1）。1 ~ 127。 |
| `frostSigh.frostbiteMinutes` | `10` | 1 ~ 60 | 上述冻伤持续多少分钟。1 ~ 60。 |
| `frostSigh.snowMinutes` | `10` | 1 ~ 60 | 爆发后降雪持续多少分钟。1 ~ 60。默认 10（需求原本写 60，但那是很重的负担）。 |
| `frostSigh.mistMinutes` | `10` | 1 ~ 60 | 冰雾持续多少分钟。1 ~ 60。 |
| `frostSigh.coldBiome` | `minecraft:snowy_plains` | 文本 | 爆发后把影响范围内的生物群系改成哪一个。 |
| `frostSigh.freezeItems` | `true` | 开 / 关 | 是否把冲击环范围内的掉落物冰封起来：外面裹一层可敲碎的半透明冰壳， |
| `frostSigh.freezeMaxEntities` | `512` | 0 ~ 4096 | 单次爆发最多冰封多少个掉落物。0 ~ 4096。 |
| `frostSigh.iceMeltSeconds` | `0` | 0 ~ 86400 | 冰壳多少秒后自动融化、把东西掉出来。0 ~ 86400。 |
| `frostSigh.mushroomSeconds` | `45` | 1 ~ 600 | 爆发后蘑菇云持续多少秒。1 ~ 600。 |
| `frostSigh.secondRingDelaySeconds` | `2` | 0 ~ 60 | 2 号冲击环比 1 号环晚多少秒起跑。0 ~ 60。 |
| `frostSigh.spike.maxRelativeRadius` | `0.75` | 0.01 ~ 1.0 | 冰刺能长到多外（相对半径）。0.01 ~ 1.0，默认 0.75。 |
| `frostSigh.spike.minHeight` | `10` | 0 ~ 320 | 低于这个高度干脆不长。0 ~ 320，默认 10（格）。 |
| `frostSigh.spike.cellSize` | `7` | 1 ~ 64 | 冰刺山尖的格子边长（格）。1 ~ 64，默认 7。 |
| `frostSigh.spike.rarity` | `2` | 1 ~ 64 | 多少个格子里长一个山尖。1 ~ 64，默认 2（约一半）。 |
| `frostSigh.spike.baseRadius` | `3` | 1 ~ 32 | 单根冰刺的底面半径（格）。1 ~ 32，默认 3。 |
| `frostSigh.spike.maxHeight` | `70` | 0 ~ 320 | 正中心最高那根冰刺的高度（格）。0 ~ 320，默认 70。 |
| `mistClear.enabled` | `true` | 开 / 关 | 总开关。默认开。关掉 = 雾只剩粒子与冻伤，不再清方块（回到改动之前的行为）。 |
| `mistClear.intervalTicks` | `20` | 1 ~ 200 | 每隔多少 tick 清一次。1 ~ 200，默认 20（1 秒）。 |
| `mistClear.pulseSeconds` | `60` | 1 ~ 600 | 冰雪的叹息：一道环从中心推到边缘要多少秒。1 ~ 600，默认 60。 |
| `mistClear.pulseIdleSeconds` | `30` | 0 ~ 600 | 两道环之间歇多少秒（上一道推完之后算起）。0 ~ 600，默认 30。 |
| `liquidCold.radius` | `8` | 1 ~ 16 | 液态寒冷的影响半径（格）。1 ~ 16。 |
| `liquidCold.spreadAttemptsPerSecond` | `16` | 1 ~ 256 | 每个源头方块每秒做多少次冰扩散取样。1 ~ 256，默认 16。 |
| `liquidCold.spreadSuccessChance` | `0.5` | 0.0 ~ 1.0 | 每次取样成功替换方块的**概率**。0.0 ~ 1.0，默认 0.5。 |
| `coolant.frostbiteCap` | `10` | 1 ~ 127 | 冷却液能把冻伤推到的**显示层数**上限（= amplifier + 1）。1 ~ 127，默认 10。 |
| `frostbite.durationTicks` | `240` | 20 ~ 2400 | 本模组施加的冻伤持续多少 tick。20 ~ 2400，默认 240（12 秒）。 |
| `frostbite.minIntervalTicks` | `20` | 1 ~ 200 | 两次涨层之间的最短间隔（tick）。1 ~ 200，默认 20（每秒最多 +1 层）。 |
| `frostbite.maxHealthBonusPerTenLevels` | `0.02` | 0.0 ~ 1.0 | 每满一档额外附加的最大生命比例。0.0 ~ 1.0，默认 0.02（2%）。 |
| `frostbite.levelsPerBonusTier` | `10` | 1 ~ 127 | 多少层算一档。1 ~ 127，默认 10。 |
| `warmth.frostbiteImmunityPerPiece` | `0.25` | 0.0 ~ 1.0 | 每件「烈阳」抵消的冻伤比例。0.0 ~ 1.0，默认 0.25（四件叠满即全免）。 |
| `warmth.frostDecayPerTick` | `2` | 0 ~ 20 | 原版每 tick 把冻结刻数减掉多少。0 ~ 20，默认 2。 |
| `warmth.meltTopUpPeriodTicks` | `2` | 1 ~ 20 | 补发零头的周期（tick）。1 ~ 20，默认 2。 |
| `combustion.ignitableDurationTicks` | `200` | 20 ~ 2400 | 「可燃」buff 的持续 tick 数。20 ~ 2400，默认 200（10 秒）。 |
| `combustion.deflagrationDurationTicks` | `200` | 20 ~ 2400 | 「爆燃」buff 的持续 tick 数。20 ~ 2400，默认 200（10 秒）。 |
| `combustion.fireDamagePerStack` | `1.0` | 0.0 ~ 100.0 | 每层爆燃附加的固定火焰伤害点数。0.0 ~ 100.0，默认 1.0。 |
| `combustion.maxHealthBonusPerTenStacks` | `0.01` | 0.0 ~ 1.0 | 每满一档额外附加的最大生命比例。0.0 ~ 1.0，默认 0.01（1%）。 |
| `combustion.stacksPerBonusTier` | `10` | 1 ~ 127 | 多少层算一档。1 ~ 127，默认 10。 |
| `playerBuffs.attackDamagePerHatred` | `0.001` | 0.0 ~ 100.0 | 每 1 点个人恨意值换到的基础攻击力。默认 0.001（即 1000 点换 1 点攻击力）。 |
| `playerBuffs.maxAttackDamage` | `20.0` | 0.0 ~ 10000.0 | 基础攻击力加成的上限。默认 20.0（约等于一把下界合金剑的伤害量级）。 |
| `playerBuffs.armorPerHatred` | `0.0005` | 0.0 ~ 100.0 | 每 1 点个人恨意值换到的防御力（原版护甲值）。默认 0.0005（即 2000 点换 1 点）。 |
| `playerBuffs.maxArmor` | `10.0` | 0.0 ~ 10000.0 | 防御力加成的上限。默认 10.0（原版满套装约 20 点）。 |
| `playerBuffs.luckPerHatred` | `0.0005` | 0.0 ~ 100.0 | 每 1 点个人恨意值换到的幸运值。默认 0.0005（即 2000 点换 1 点）。 |
| `playerBuffs.maxLuck` | `5.0` | 0.0 ~ 10000.0 | 幸运值加成的上限。默认 5.0。 |
| `playerBuffs.damageReductionPerHatred` | `0.00002` | 0.0 ~ 1.0 | 每 1 点个人恨意值换到的减伤比例。默认 0.00002（即 25000 点减伤 50%）。 |
| `playerBuffs.maxDamageReduction` | `0.5` | 0.0 ~ 0.99 | 玩家减伤的上限。0.0 ~ 0.99，默认 0.5。 |
| `playerBuffs.finalDamagePerHatred` | `0.00002` | 0.0 ~ 1.0 | 每 1 点个人恨意值换到的最终伤害加成比例。默认 0.00002（即 25000 点 +50%）。 |
| `playerBuffs.maxFinalDamage` | `0.5` | 0.0 ~ 100.0 | 最终伤害加成的上限。默认 0.5（+50%）。 |

---

## 三、真菌侧数值速查表

文件：`config/spore_add-fungus-common.toml`

段落顺序：`fungus` → `womb` → `scavenger` → `hatred` → `food` → `death` → `fungusResistance` → `raid` → `hivemind` → `loot`

其中 `raid` 有 38 个键，按袭击的生命周期拆成了七张子表：
`raid.trigger` → `raid.prep` → `raid.bonus` → `raid.attack` → `raid.arena` → `raid.ownWave` → `raid.ownLoot`。

| 键 | 默认 | 范围 | 说明（配置里的第一行注释） |
|---|---|---|---|
| `fungus.evolutionSpeedBonus` | `4` | 0 ~ 60 | 每秒额外获得的进化进度点。0 ~ 60，默认 4。 |
| `fungus.huntEnabled` | `true` | 开 / 关 | 是否让真菌主动猎杀一切生物（带「打得过才打」的判定）。默认开。 |
| `fungus.huntPriority` | `3` | 0 ~ 20 | 「猎杀一切生物」那条目标挂在目标选择器上的优先级。0 ~ 20，默认 3。 |
| `fungus.huntCautionRatio` | `1.0` | 0.1 ~ 5.0 | 「打得过」的裕度。0.1 ~ 5.0，默认 1.0。 |
| `fungus.huntArmorEhpPerPoint` | `0.04` | 0.0 ~ 1.0 | 「打得过才打」判定里，每 1 点护甲折算成多少有效生命倍率。0.0 ~ 1.0，默认 0.04。 |
| `fungus.huntSearchIntervalTicks` | `10` | 1 ~ 200 | 「攻击一切生物」那条目标多久搜一次目标（tick）。1 ~ 200，默认 10。 |
| `fungus.sensingMultiplier` | `2.0` | 1.0 ~ 4.0 | 真菌感知范围（FOLLOW_RANGE）的倍率。1.0 ~ 4.0，默认 2.0（16 格 → 32 格）。 |
| `fungus.freezeDamageMultiplier` | `2.0` | 0.0 ~ 10.0 | 真菌受到的冰冻伤害倍率。0.0 ~ 10.0，默认 2.0。 |
| `fungus.coldFrostbiteIntervalTicks` | `400` | 20 ~ 2400 | 寒冷环境两次「自我施加冻伤」之间的最短间隔（tick）。20 ~ 2400，默认 400。 |
| `fungus.coldHungerPenaltyFactor` | `0.5` | 0.0 ~ 1.0 | 寒冷加速饥饿时，额外那一点保留的比例。0.0 ~ 1.0，默认 0.5。 |
| `fungus.coldBiomeThreshold` | `-0.2` | -1.0 ~ 0.2 | 真菌眼里的「寒冷」生物群系温度上限。-1.0 ~ 0.2，默认 -0.2。 |
| `womb.mutationMultiplier` | `2.0` | 1.0 ~ 10.0 | 同化喂食时，一次喂食算几条突变。1.0 ~ 10.0，默认 2.0。 |
| `womb.raidMutationMultiplier` | `2.0` | 1.0 ~ 10.0 | 上一条在真菌袭击期间的额外倍率。1.0 ~ 10.0，默认 2.0。 |
| `womb.raidBonusesEnabled` | `true` | 开 / 关 | 袭击的制造速度 / 资源获取加成是否也作用到灾厄重构体。默认开。 |
| `womb.hatchMinMutationTypes` | `4` | 0 ~ 7 | 孵化前至少要喂到几种**不同**的突变属性。0 ~ 7，默认 4（过半）。 |
| `womb.hatchMaxStallSeconds` | `300` | 0 ~ 3600 | 基础诞生条件（生物质）满足后，最多允许滞留多少秒再无条件孵化。0 ~ 3600，默认 300（5 分钟）。 |
| `womb.mindFundingEnabled` | `true` | 开 / 关 | 心智是否**出资**替卡住的重构体补齐突变。默认开。 |
| `womb.mindFundingCost` | `20` | 0 ~ 10000 | 补一种属性要花多少生物质。0 ~ 10000，默认 20。 |
| `womb.mindFundingIntervalSeconds` | `30` | 1 ~ 600 | 多久尝试资助一次（秒）。1 ~ 600，默认 30。 |
| `womb.mindFundingRange` | `64.0` | 8.0 ~ 256.0 | 心智从多大范围内找需要资助的重构体（格）。8.0 ~ 256.0，默认 64.0。 |
| `womb.hatchExitEnabled` | `true` | 开 / 关 | 灾厄孵化后是否把它挪到重构体外面去。默认开。 |
| `womb.hatchExitSearchRadius` | `8.0` | 1.0 ~ 32.0 | 找壳外空位时最多向外找多远（格）。1.0 ~ 32.0，默认 8.0。 |
| `womb.hatchExitFromDome` | `true` | 开 / 关 | 孵化出的灾厄是否要被送出心智的生物质穹顶（不只是送出重构体本身）。默认开。 |
| `womb.hatchExitDomeMargin` | `2.0` | 0.0 ~ 16.0 | 送出穹顶时，在穹顶半径之外再留出多少格。0.0 ~ 16.0，默认 2.0。 |
| `scavenger.transformMinChance` | `0.10` | 0.0 ~ 1.0 | 菌染人类生成时转变成拾荒者的概率**下限**。0.0 ~ 1.0，默认 0.10（需求写的 10%）。 |
| `scavenger.transformMaxChance` | `0.50` | 0.0 ~ 1.0 | 转变概率的**上限**。0.0 ~ 1.0，默认 0.50（需求写的 50%）。 |
| `scavenger.transformItemRadius` | `16.0` | 1.0 ~ 64.0 | 统计「附近有多少掉落物」时的半径（格）。1.0 ~ 64.0，默认 16.0。 |
| `scavenger.transformItemCountForMax` | `32` | 1 ~ 512 | 掉落物到多少件时转变概率达到上限。1 ~ 512，默认 32。 |
| `scavenger.lootBonusPerMinute` | `0.25` | 0.0 ~ 10.0 | 存活时间换算成拾荒加成的速率：每存活一分钟，收获倍率加多少。0.0 ~ 10.0，默认 0.25。 |
| `scavenger.lootBonusMax` | `2.0` | 0.0 ~ 100.0 | 存活加成的上限（不含基础的 1.0）。0.0 ~ 100.0，默认 2.0（即最多 ×3）。 |
| `scavenger.healthPerMinute` | `6.0` | 0.0 ~ 100.0 | 拾荒者每存活一分钟增加多少最大生命。0.0 ~ 100.0，默认 6.0。 |
| `scavenger.healthMaxBonus` | `90.0` | 0.0 ~ 500.0 | 最大生命成长的上限。0.0 ~ 500.0，默认 90.0。 |
| `scavenger.armorPerMinute` | `1.5` | 0.0 ~ 10.0 | 每存活一分钟增加多少护甲。0.0 ~ 10.0，默认 1.5。 |
| `scavenger.armorMaxBonus` | `19.0` | 0.0 ~ 100.0 | 护甲成长的上限。0.0 ~ 100.0，默认 19.0。 |
| `scavenger.speedPerMinute` | `0.015` | 0.0 ~ 1.0 | 每存活一分钟增加多少移动速度。0.0 ~ 1.0，默认 0.015。 |
| `scavenger.speedMaxBonus` | `0.10` | 0.0 ~ 1.0 | 移动速度成长的上限（属性绝对值）。0.0 ~ 1.0，默认 0.10。 |
| `scavenger.regenHpsPerMinute` | `1.0` | 0.0 ~ 10.0 | 每存活一分钟，每秒回复的生命点数涨多少。0.0 ~ 10.0，默认 1.0。 |
| `scavenger.regenMaxHps` | `5.0` | 0.0 ~ 50.0 | 每秒回复生命的上限。0.0 ~ 50.0，默认 5.0。 |
| `scavenger.moveCueCooldownSeconds` | `8` | 1 ~ 600 | 「拾荒者移动」那条字幕的提示音最短间隔（秒）。1 ~ 600，默认 8。 |
| `scavenger.storageBase` | `50.0` | 0.0 ~ 100000.0 | 它自己身上那个「资源存储」的上限**基数**。0.0 ~ 100000.0，默认 50.0。 |
| `scavenger.storagePerMinute` | `10.0` | 0.0 ~ 10000.0 | 每存活一分钟，存储上限涨多少。0.0 ~ 10000.0，默认 10.0。 |
| `scavenger.storageMaxBonus` | `2000.0` | 0.0 ~ 1000000.0 | 上面的成长封顶。0.0 ~ 1000000.0，默认 2000.0。 |
| `scavenger.lootRadius` | `32` | 1 ~ 64 | 拾荒者搜寻掉落物的半径（格）。1 ~ 64，默认 32。 |
| `scavenger.roamAwarenessRadius` | `64` | 16 ~ 128 | 闲逛时朝战利品偏的感知半径（格）。16 ~ 128，默认 64。 |
| `scavenger.lootSprintRadius` | `8.0` | 0.0 ~ 64.0 | 离目标掉落物多近时开始**奔袭**（格）。1.0 ~ 64.0，默认 8.0。 |
| `scavenger.lootSprintSpeed` | `1.5` | 1.0 ~ 5.0 | 奔袭时的移速倍率。1.0 ~ 5.0，默认 1.5。 |
| `scavenger.linkScanIntervalTicks` | `100` | 1 ~ 1200 | 多久重建一次链接（校验旧的 + 补新的）。1 ~ 1200，默认 100（5 秒）。 |
| `scavenger.linkScanRadius` | `48.0` | 1.0 ~ 128.0 | 直连时从多大范围内挑同伴（格）。1.0 ~ 128.0，默认 48.0。 |
| `scavenger.linkBreakRadius` | `64.0` | 1.0 ~ 256.0 | 链接对象离我超过多远就算走散、断开（格）。1.0 ~ 256.0，默认 64.0。 |
| `scavenger.linkProbeRadius` | `24.0` | 1.0 ~ 64.0 | 每个链接对象向我提供一个多大的搜索圆心（格）。1.0 ~ 64.0，默认 24.0。 |
| `scavenger.linkMinLinks` | `1` | 0 ~ 32 | 直连数量的**下限**（刚转变时）。0 ~ 32，默认 1。 |
| `scavenger.linkMaxLinks` | `3` | 0 ~ 32 | 直连数量的**上限**（活久了之后）。0 ~ 32，默认 3。 |
| `scavenger.linkRampMinutes` | `10.0` | 0.01 ~ 120.0 | 从下限爬到上限需要存活多少分钟。0.01 ~ 120.0，默认 10.0。 |
| `scavenger.linkHivemindMinLinks` | `2` | 0 ~ 32 | **经心智**间接链接时的数量下限。0 ~ 32，默认 2。 |
| `scavenger.linkHivemindMaxLinks` | `6` | 0 ~ 32 | 经心智间接链接时的数量上限。0 ~ 32，默认 6。 |
| `scavenger.lootWeightDistance` | `0.35` | 0.0 ~ 10.0 | 【权重】距离。0.0 ~ 10.0，默认 0.35（最高的一项）。 |
| `scavenger.lootWeightArrival` | `0.10` | 0.0 ~ 10.0 | 【权重】到达时间。0.0 ~ 10.0，默认 0.10。 |
| `scavenger.lootWeightCount` | `0.15` | 0.0 ~ 10.0 | 【权重】掉落物数量。0.0 ~ 10.0，默认 0.15。 |
| `scavenger.lootWeightType` | `0.25` | 0.0 ~ 10.0 | 【权重】掉落物类型（= 稀有度）。0.0 ~ 10.0，默认 0.25。 |
| `scavenger.lootWeightEfficiency` | `0.15` | 0.0 ~ 10.0 | 【权重】转换资源效率（= 转化概率 chance）。0.0 ~ 10.0，默认 0.15。 |
| `scavenger.lootWeightDanger` | `0.0` | 0.0 ~ 10.0 | 【权重】路径上的危险。0.0 ~ 10.0，默认 0.0（**不做**）。 |
| `scavenger.lootClusterRadius` | `8.0` | 1.0 ~ 64.0 | 聚堆的三维格边长（格）。1.0 ~ 64.0，默认 8.0。 |
| `scavenger.lootValueHalf` | `32.0` | 0.1 ~ 10000.0 | 价值与稀有度的半饱和点（资源）。0.1 ~ 10000.0，默认 32.0。 |
| `scavenger.lootCountHalf` | `8.0` | 0.1 ~ 1000.0 | 数量的半饱和点（件）。0.1 ~ 1000.0，默认 8.0。 |
| `scavenger.lootMaxVerticalClimbFactor` | `0.5` | 0.0 ~ 10.0 | 到达时间里，每向上 1 格按多少格水平距离计。0.0 ~ 10.0，默认 0.5。 |
| `scavenger.fleeHealthFraction` | `0.5` | 0.0 ~ 1.0 | 血量低于最大生命的这个比例时，**无条件**逃跑（即使面对玩家）。0.0 ~ 1.0，默认 0.5。 |
| `scavenger.fleeThreatRadius` | `16.0` | 1.0 ~ 64.0 | 感知到多远的威胁就跑（格）。1.0 ~ 64.0，默认 16.0。 |
| `scavenger.fleeSurviveSeconds` | `4.0` | 1.0 ~ 60.0 | 「锁定它的生物能在这么多秒内打死它」就跑。1.0 ~ 60.0，默认 4.0。 |
| `scavenger.fleeAggroMemoryTicks` | `200` | 20 ~ 1200 | 被谁打过之后，多少 tick 内仍把它算作「对我有仇恨」。20 ~ 1200，默认 200（10 秒）。 |
| `scavenger.fleeSpeed` | `1.5` | 0.1 ~ 5.0 | 逃跑时的移动速度倍率。0.1 ~ 5.0，默认 1.5。 |
| `scavenger.fleeTargetHorizontalDistance` | `16` | 1 ~ 64 | 选逃跑落点时向外搜的水平距离（格）。1 ~ 64，默认 16。 |
| `scavenger.fleeTargetVerticalRange` | `7` | 1 ~ 64 | 上述搜索允许的高差（格）。1 ~ 64，默认 7。 |
| `scavenger.fleePriority` | `0` | 0 ~ 20 | 逃跑目标的优先级。0 ~ 20，默认 0（数字越小越优先）。 |
| `scavenger.deliveryRadius` | `24.0` | 1.0 ~ 128.0 | 把收获交给同伙时扫描的半径（格）。1.0 ~ 128.0，默认 24.0。 |
| `scavenger.healMinTargets` | `2` | 0 ~ 32 | 一次最多同时治疗几个残血同伙（存活时间短时）。0 ~ 32，默认 2。 |
| `scavenger.healMaxTargets` | `5` | 0 ~ 32 | 随存活时间最多能涨到几个。0 ~ 32，默认 5。 |
| `scavenger.healRampMinutes` | `10.0` | 0.01 ~ 120.0 | 从 min 涨到 max 需要存活多少分钟。0.01 ~ 120.0，默认 10.0。 |
| `scavenger.healMinTier` | `1` | 0 ~ 5 | 多高的等级才算「值得优先抢救」。0 ~ 5，默认 1。 |
| `scavenger.healWoundedFraction` | `0.6` | 0.0 ~ 1.0 | 血量低于最大生命的这个比例才算「残血」、才值得治疗。0.0 ~ 1.0，默认 0.6。 |
| `scavenger.healthPerResource` | `1.0` | 0.0 ~ 1000.0 | 1 点资源能换多少点生命值。0.0 ~ 1000.0，默认 1.0。 |
| `scavenger.evoPointsPerResource` | `0.1` | 0.0 ~ 100.0 | 1 点资源能换多少进化点。0.0 ~ 100.0，默认 0.1。 |
| `scavenger.seekAllyThreshold` | `0.8` | 0.1 ~ 1.0 | 存量达到上限的多少比例就算「即将蓄满」、该去找队友了。0.1 ~ 1.0，默认 0.8。 |
| `scavenger.seekAllyRadius` | `64.0` | 8.0 ~ 256.0 | 专程找接收者时的搜索半径（格）。8.0 ~ 256.0，默认 64.0。 |
| `scavenger.seekAllyCooldownSeconds` | `30` | 1 ~ 600 | 两次「去找队友」之间的冷却（秒）。1 ~ 600，默认 30。 |
| `scavenger.maxCount` | `20` | 0 ~ 500 | 世上同时存在的拾荒者**上限**。0 ~ 500，默认 20。 |
| `scavenger.lootPriority` | `2` | 0 ~ 20 | 拾荒目标挂在行动目标选择器上的优先级。0 ~ 20，默认 2。 |
| `scavenger.deathDropItems` | `见下` | 名单 | 它死的时候，身上存的资源按汇率换成哪些物品掉在原地。 |
| `scavenger.deathDropResourcePerItem` | `20` | 1 ~ 10000 | 多少资源换一个掉落物。1 ~ 10000，默认 20。 |
| `scavenger.deathDropMaxItems` | `32` | 0 ~ 512 | 单次死亡最多掉几个。0 ~ 512，默认 32。 |
| `hatred.basePerKill` | `10.0` | 0.0 ~ 10000.0 | 击杀一只真菌的基础恨意值，其余倍率都乘在它上面。0.0 ~ 10000.0，默认 10.0。 |
| `hatred.maxPerKill` | `1000.0` | 0.0 ~ 1000000.0 | 单次击杀最多能拿多少恨意值。0.0 ~ 1000000.0，默认 1000.0。 |
| `hatred.tierInfected` | `1.0` | 0.0 ~ 1000.0 | 【等级】基础感染体（Infected 本身，如感染人类 / 感染者 / 感染村民）的倍率。默认 1.0。 |
| `hatred.tierEvolved` | `2.5` | 0.0 ~ 1000.0 | 【等级】进化体（EvolvedInfected，如骑士 / 蛮兽）的倍率。默认 2.5。 |
| `hatred.tierHyper` | `6.0` | 0.0 ~ 1000.0 | 【等级】超级体（Hyper）的倍率。默认 6.0。 |
| `hatred.tierOrganoid` | `4.0` | 0.0 ~ 1000.0 | 【等级】器官类（Organoid：心智 Proto、蜂巢肿瘤 HiveTumor、丘 Mound……）的倍率。默认 4.0。 |
| `hatred.tierCalamity` | `25.0` | 0.0 ~ 1000.0 | 【等级】灾厄类（Calamity 及其子类：吞噬者 / 利维坦 / 洞食者 / 围攻者 / 榴弹者……）的倍率。默认 25.0。 |
| `hatred.tierOther` | `0.0` | 0.0 ~ 1000.0 | 【等级】上面都不匹配的真菌实体的倍率。0.0 ~ 1000.0，**默认 0.0**。 |
| `hatred.linkedMultiplier` | `2.0` | 0.0 ~ 1000.0 | 【是否与心智链接】链接状态（Infected#getLinked()）为真时的倍率。默认 2.0。 |
| `hatred.unlinkedMultiplier` | `1.0` | 0.0 ~ 1000.0 | 【是否与心智链接】未链接时的倍率。默认 1.0（不给惩罚，只是拿不到链接加成）。 |
| `hatred.developmentWeight` | `0.1` | 0.0 ~ 10.0 | 【进化难度 / 发育程度】每 1 进化点（Infected#getEvoPoints()）换算成多少额外倍率。 |
| `hatred.developmentCap` | `5.0` | 0.0 ~ 100.0 | 发育程度带来的额外倍率上限（不含基础的 1.0）。0.0 ~ 100.0，默认 5.0（即最多 +500%）。 |
| `hatred.typeMultipliers` | `见下` | 名单 | 【类型】按实体微调，格式 "实体id/倍率"，可写多行。默认空 = 全部按 1.0。 |
| `hatred.importanceMultipliers` | `见下` | 名单 | 【重要性】按实体覆盖，格式 "实体id/倍率"。默认空 = 用下面的职能默认值。 |
| `hatred.importanceChunkLoader` | `4.0` | 0.0 ~ 1000.0 | 【重要性】会加载区块的真菌（Spore 的 ChunkLoaderMob 接口）的倍率。默认 4.0。 |
| `hatred.importanceCasingGenerator` | `3.0` | 0.0 ~ 1000.0 | 【重要性】能生成菌壳的真菌（CasingGenerator 接口，主要是心智 Proto）的倍率。默认 3.0。 |
| `hatred.importanceFoliageSpread` | `2.0` | 0.0 ~ 1000.0 | 【重要性】会铺开植被的真菌（FoliageSpread 接口）的倍率。默认 2.0。 |
| `hatred.importanceEvolving` | `1.5` | 0.0 ~ 1000.0 | 【重要性】还有进化余地的真菌（EvolvingInfected 接口）的倍率。默认 1.5。 |
| `food.reduceChance` | `0.10` | 0.0 ~ 1.0 | 触发「降低恨意值」的概率。0.0 ~ 1.0，默认 0.10。 |
| `food.reduceRatio` | `0.10` | 0.0 ~ 1.0 | 降低当前恨意值的比例。0.0 ~ 1.0，默认 0.10。 |
| `food.increaseChance` | `0.05` | 0.0 ~ 1.0 | 触发「提高恨意值」的概率。0.0 ~ 1.0，默认 0.05。 |
| `food.increaseRatio` | `0.20` | 0.0 ~ 1.0 | 提高当前恨意值的比例。0.0 ~ 1.0，默认 0.20。 |
| `death.fungusDeathLossRatio` | `0.8` | 0.0 ~ 1.0 | 死于真菌之手时，个人恨意值损失的比例。0.0 ~ 1.0，默认 0.8。 |
| `death.resourceMultiplier` | `2.0` | 0.0 ~ 100.0 | 那笔损失换算成资源时乘的倍率。需求写的是「X2」，所以默认 2.0。 |
| `death.hivemindKillLossRatio` | `0.9` | 0.0 ~ 1.0 | 击杀心智（Proto）时，个人恨意值损失的比例。0.0 ~ 1.0，默认 0.9。 |
| `death.raidWinLossRatio` | `0.10` | 0.0 ~ 1.0 | 打赢一次真菌袭击时，个人恨意值损失的比例。0.0 ~ 1.0，默认 0.10。 |
| `fungusResistance.perHatred` | `0.00002` | 0.0 ~ 1.0 | 世界恨意值每 1 点给真菌带来的减伤比例。默认 0.00002（即全服合计 30000 点减伤 60%）。 |
| `fungusResistance.maxResistance` | `0.6` | 0.0 ~ 0.99 | 真菌减伤的上限。0.0 ~ 0.99，默认 0.6。 |
| `raid.trigger.thresholdStep` | `500.0` | 1.0 ~ 100000.0 | 个人恨意值每涨满这么多，就算跨过一个档位并掷一次触发骰。1.0 ~ 100000.0，默认 500.0。 |
| `raid.trigger.chance` | `0.5` | 0.0 ~ 1.0 | 每跨过一个档位时触发袭击的概率。0.0 ~ 1.0，默认 0.5（需求写的就是 50%）。 |
| `raid.prep.seconds` | `60` | 1 ~ 600 | 准备阶段持续多少秒后转入攻击。1 ~ 600，默认 60。 |
| `raid.prep.maxSeconds` | `180` | 1 ~ 3600 | 准备阶段最多拖多少秒。1 ~ 3600，默认 180。 |
| `raid.prep.launchMinBiomass` | `200` | 0 ~ 100000 | 发动条件之一：至少有一只心智的资源达到这个数。0 ~ 100000，默认 200。 |
| `raid.prep.launchMinFungusCount` | `8` | 0 ~ 1000 | 发动条件之二：目标玩家周围至少要有这么多真菌。0 ~ 1000，默认 8。 |
| `raid.prep.launchMinQuality` | `30.0` | 0.0 ~ 10000.0 | 发动条件之三：那些真菌的「质量」之和至少要到这个数。0.0 ~ 10000.0，默认 30.0。 |
| `raid.prep.gatherRadius` | `64.0` | 8.0 ~ 256.0 | 统计「目标玩家附近有多少真菌」时的半径（格）。8.0 ~ 256.0，默认 64.0。 |
| `raid.prep.launchCheckIntervalTicks` | `20` | 1 ~ 200 | 准备阶段每隔多少 tick 检查一次发动条件。1 ~ 200，默认 20（1 秒）。 |
| `raid.bonus.prepResourceCostMultiplier` | `0.5` | 0.0 ~ 1.0 | 准备期间心智的资源**消耗**倍率。0.0 ~ 1.0，默认 0.5（需求：降低 50%）。 |
| `raid.bonus.prepResourceGainMultiplier` | `2.0` | 1.0 ~ 10.0 | 准备期间心智的资源**获取**倍率。1.0 ~ 10.0，默认 2.0（需求：提高 100%）。 |
| `raid.bonus.prepGrowthSpeedMultiplier` | `1.5` | 1.0 ~ 5.0 | 准备期间心智的**发育速度**倍率。1.0 ~ 5.0，默认 1.5（需求：提高 50%）。 |
| `raid.bonus.attackManufactureSpeedMultiplier` | `6.0` | 1.0 ~ 20.0 | 攻击期间心智的**制造**（召唤真菌生物）速度倍率。1.0 ~ 20.0，默认 6.0。 |
| `raid.bonus.attackDespawnCapMultiplier` | `2.0` | 1.0 ~ 10.0 | 攻击期间 Despawning System 上限的倍率。1.0 ~ 10.0，默认 2.0（需求：提高 100%）。 |
| `raid.attack.teleportMinDistance` | `12.0` | 1.0 ~ 128.0 | 传送落点距目标玩家的**最近**距离（格）。1.0 ~ 128.0，默认 12.0。 |
| `raid.attack.teleportMaxDistance` | `32.0` | 1.0 ~ 256.0 | 传送落点距目标玩家的**最远**距离（格）。1.0 ~ 256.0，默认 32.0。 |
| `raid.attack.teleportSearchRadius` | `128.0` | 16.0 ~ 512.0 | 从多大范围内把真菌「调」过来（格）。16.0 ~ 512.0，默认 96.0。 |
| `raid.attack.buffs` | `见下` | 名单 | 攻击期间加在参战真菌身上的增益，格式 "效果id/持续tick/amplifier"。 |
| `raid.attack.absenceTimeoutSeconds` | `300` | 1 ~ 3600 | 玩家躲进**黑名单维度**后，最多允许缺席多少秒才判失败。1 ~ 3600，默认 300。 |
| `raid.arena.chanceBase` | `0.2` | 0.0 ~ 1.0 | 竞技之须出现概率的**基准**。0.0 ~ 1.0，默认 0.2。 |
| `raid.arena.chancePerTier` | `0.1` | 0.0 ~ 1.0 | 恨意档位每高一级，竞技之须的出现概率加多少。0.0 ~ 1.0，默认 0.1。 |
| `raid.arena.chanceMax` | `0.9` | 0.0 ~ 1.0 | 竞技之须出现概率的上限。0.0 ~ 1.0，默认 0.9。 |
| `raid.arena.waveSize` | `6` | 0 ~ 100 | 种给竞技之须的初始**波次规模**。0 ~ 100，默认 6。 |
| `raid.arena.waveLevel` | `1` | 0 ~ 2 | 种给竞技之须的初始**波次等级**。0 ~ 2，默认 1。 |
| `raid.arena.timeoutSeconds` | `300` | 1 ~ 3600 | 竞技之须阶段最长持续多少秒。1 ~ 3600，默认 300。 |
| `raid.arena.warnLeadSeconds` | `10` | 0 ~ 120 | 「恐惧吧」比**竞技之须出现**提前多少秒发出。0 ~ 120，默认 10（需求写的就是 10 秒）。 |
| `raid.ownWave.base` | `1` | 0 ~ 50 | 我方围剿波次的**基础**数量。0 ~ 50，默认 1。 |
| `raid.ownWave.perTier` | `2` | 0 ~ 10 | 恨意档位每高一级，我方波次多几波。0 ~ 10，默认 2。 |
| `raid.ownWave.max` | `10` | 0 ~ 50 | 我方波次数量的上限。0 ~ 50，默认 10。 |
| `raid.ownWave.countBase` | `3` | 1 ~ 200 | 我方第 1 波送过去几只。1 ~ 200，默认 3。 |
| `raid.ownWave.countPerWave` | `2` | 0 ~ 100 | 每往后一波多送几只。0 ~ 100，默认 2。 |
| `raid.ownWave.seconds` | `60` | 1 ~ 600 | 我方每一波**最多**等多少秒。1 ~ 600，默认 60。 |
| `raid.ownWave.clearRadius` | `32.0` | 4.0 ~ 128.0 | 判断「这一波清完了没有」时统计真菌的半径（格）。4.0 ~ 128.0，默认 32.0。 |
| `raid.ownWave.clearCount` | `4` | 0 ~ 64 | 玩家周围 raid.ownWave.clearRadius 内的真菌少于这个数，就算这一波清完了。0 ~ 64，默认 4。 |
| `raid.ownWave.buffsPerWave` | `1` | 0 ~ 10 | 每往后一波，参战增益的 amplifier 加多少。0 ~ 10，默认 1。 |
| `raid.ownLoot.table` | `见下` | 名单 | 我方战利品表，格式 "物品id/最少/最多"，可写多行。 |
| `raid.ownLoot.rollsPerWave` | `1` | 0 ~ 100 | 战利品表里每一条独立掷多少次（再乘以波次档位）。0 ~ 100，默认 1。 |
| `raid.ownLoot.radius` | `6.0` | 1.0 ~ 64.0 | 我方战利品掉在离玩家多远的范围内（格）。1.0 ~ 64.0，默认 6.0。 |
| `loot.collectEnabled` | `true` | 开 / 关 | 是否让真菌主动收集附近的掉落物。默认开。 |
| `loot.radius` | `16.0` | 1.0 ~ 64.0 | 真菌搜寻掉落物的半径（格）。1.0 ~ 64.0，默认 16.0。 |
| `loot.searchIntervalTicks` | `40` | 5 ~ 200 | 两次搜寻之间至少间隔多少 tick。5 ~ 200，默认 40（2 秒）。 |
| `loot.repathIntervalTicks` | `20` | 1 ~ 200 | 已锁定目标、但目标没动过时，隔多少 tick 重新寻一次路。1 ~ 200，默认 20（1 秒）。 |
| `loot.pickupDistance` | `2.0` | 1.0 ~ 8.0 | 靠到多近才算捡起来（格）。1.0 ~ 8.0，默认 2.0。 |
| `loot.moveSpeed` | `1.0` | 0.1 ~ 5.0 | 走过去捡东西时的移动速度倍率。0.1 ~ 5.0，默认 1.0。 |
| `loot.priority` | `8` | 1 ~ 20 | 普通真菌那条拾荒目标的优先级。1 ~ 20，默认 8，数字越小越优先。 |
| `loot.defaultValue` | `1.0` | 0.0 ~ 1000.0 | **数据包里的转化表**没列到的物品值多少资源。0.0 ~ 1000.0，默认 1.0。 |
| `loot.defaultChance` | `1.0` | 0.0 ~ 1.0 | 转化表里没列到的物品，捡起来之后的转化概率。0.0 ~ 1.0，默认 1.0。 |
| `loot.despawnHarvestEnabled` | `true` | 开 / 关 | 是否把 Despawning System 清理掉的真菌折算成资源。默认开。 |
| `loot.despawnBaseValue` | `5.0` | 0.0 ~ 10000.0 | 被 Despawn 清理掉的一只真菌折算多少资源。0.0 ~ 10000.0，默认 5.0。 |
| `hivemind.storeEnabled` | `true` | 开 / 关 | 是否让心智把造出来的生物收进存储（而不是放进世界）。默认开。 |
| `hivemind.storeMaxCount` | `64` | 0 ~ 1000 | 一只心智最多存多少生物。0 ~ 1000，默认 64。 |
| `hivemind.deployPerWave` | `4` | 0 ~ 100 | 我方围剿每一波开始时，从存储里投放几只。0 ~ 100，默认 4。 |
| `hivemind.deployWhenThreatened` | `true` | 开 / 关 | 心智受威胁时是否应急投放。默认开。 |
| `hivemind.threatDeployCount` | `6` | 0 ~ 100 | 应急投放一次放几只。0 ~ 100，默认 6。 |
| `hivemind.threatDeployCooldownSeconds` | `10` | 1 ~ 600 | 应急投放的冷却秒数。1 ~ 600，默认 10。 |
| `hivemind.spillCount` | `8` | 0 ~ 200 | 存储被打散时最多漏出几只。0 ~ 200，默认 8。 |
| `hivemind.spillChance` | `0.25` | 0.0 ~ 1.0 | 穹顶每被破坏一块方块，判一次「漏出」的概率。0.0 ~ 1.0，默认 0.25。 |
| `hivemind.spillCooldownSeconds` | `10` | 1 ~ 600 | 两次「漏出」之间的最短间隔（秒）。1 ~ 600，默认 10。 |
| `hivemind.domeRadius` | `32.0` | 4.0 ~ 128.0 | 判定一块躯壳方块属于哪只心智：心智周围多大半径内算它的穹顶（格）。默认 32.0。 |
| `hivemind.threatWindowSeconds` | `5` | 1 ~ 60 | 「心智正受威胁」的判定窗口（秒）。1 ~ 60，默认 5。 |
| `hivemind.storeCost` | `2` | 0 ~ 100 | 把一只生物收进存储要花多少资源。0 ~ 100，默认 2。 |
| `hivemind.deployScatterRadius` | `4.0` | 0.0 ~ 16.0 | 投放时，落点被占住的话最多在中心周围多少格内另找空位。0.0 ~ 16.0，默认 4.0。 |
| `hivemind.sweep.enabled` | `true` | 开 / 关 | 总开关。默认开。 |
| `hivemind.sweep.minHiveminds` | `3` | 1 ~ 64 | 一个维度里至少要有几只心智才可能发动。1 ~ 64，默认 3（需求写的就是三只）。 |
| `hivemind.sweep.intervalMinutes` | `20` | 1 ~ 1440 | 每隔多少分钟判定一次。1 ~ 1440，默认 20（需求写的就是 20 分钟）。 |
| `hivemind.sweep.chance` | `0.5` | 0.0 ~ 1.0 | 到点时发动清扫的概率。0.0 ~ 1.0，默认 0.5（需求写的就是 50%）。 |
| `hivemind.sweep.multiplier` | `2.0` | 0.0 ~ 100.0 | 清扫时的**基础**转化倍率。0.0 ~ 100.0，默认 2.0。 |
| `hivemind.sweep.perExtraHivemind` | `0.5` | 0.0 ~ 100.0 | 每多一只（超出下限的）心智，倍率再加多少。0.0 ~ 100.0，默认 0.5。 |

---

## 四、计算公式

### 4.1 恨意值

**一次击杀拿到的个人恨意值：**

```
恨意值 = min( basePerKill
               × 等级倍率
               × 类型倍率
               × 重要性倍率
               × 链接倍率
               × (1 + min(进化点 × developmentWeight, developmentCap)),
             maxPerKill )
```

| 因子 | 取值 | 说明 |
|---|---|---|
| 等级倍率 | 见下面那张等级表 | 按 Spore 的类继承关系判定 |
| 类型倍率 | `hatred.typeMultipliers`，默认空 → 1.0 | 按实体 id 单独指定 |
| 重要性倍率 | `hatred.importanceMultipliers`，默认空 → 按职能接口取默认值 | 会加载区块 4.0 / 生成菌壳 3.0 / 铺植被 2.0 / 有进化余地 1.5 |
| 链接倍率 | 与心智链接 2.0 / 未链接 1.0 | Spore 里被心智扫描到的真菌会被置为 linked |
| 发育加成 | 每 1 进化点 +0.1，最多 +5.0 | 非 Infected 的器官类没有进化点，恒为 0 |

**等级表（五档）** —— 判定顺序是「先查 Hyper，再查 EvolvedInfected」，所以两者不会串：

| 档 | 名字 | Spore 里的对应 | 默认倍率 |
|---|---|---|---|
| 0 | 基础感染体 | `Infected` 本身（感染人类 / 感染者 / 感染村民……） | 1.0 |
| 1 | 进化体 | `EvolvedInfected`（骑士 / 蛮兽……） | 2.5 |
| 2 | 超级体 | `Hyper`（与 EvolvedInfected 是**兄弟不是父子**） | 6.0 |
| 3 | 器官类 | `Organoid`（心智 Proto、蜂巢肿瘤、丘……） | 4.0 |
| 4 | 灾厄类 | `Calamity` 及其子类（吞噬者 / 利维坦 / 洞食者……） | 25.0 |
| 5 | 其他 | 上面都不匹配的 | **0.0**（刻意不记） |

> 「其他」默认给 0 是刻意的：落到这一档的绝大多数是「不是生物的生物」——BOSS 的分节实体、
> 感染爪之类的工具实体。它们必须是独立的 `LivingEntity` 才能有独立判定箱，所以确实会触发死亡事件。
>
> 灾厄那一档**只认 `Calamity` 父类，不认 `TrueCalamity` 接口**：利维坦与洞食者的分节实体
> 也实现了那个接口，但它们是尾巴与节肢。认接口的话，砍一节尾巴会按灾厄计分。

**跨档与袭击触发：**

```
档位 = floor(个人恨意值 / raid.trigger.thresholdStep)
```

只有**档位变大**的那一刻才掷一次骰子，掷中 `raid.trigger.chance` 就进入准备阶段。
同一档位内反复涨落不会重复触发。

**降低恨意值的几条途径** —— 都是「按当前值的比例削减」，不是减一个固定数：

| 途径 | 键 | 默认 |
|---|---|---|
| 死于真菌之手 | `death.fungusDeathLossRatio` | 0.8（−80%） |
| 击杀心智 | `death.hivemindKillLossRatio` | 0.9（−90%） |
| 打赢一次袭击 | `death.raidWinLossRatio` | 0.10（−10%） |
| 吃真菌类食物 | `food.reduceChance` / `food.reduceRatio` | 10% 概率 × 10% |
| 吃真菌类食物（反向） | `food.increaseChance` / `food.increaseRatio` | 5% 概率 × 20% |

「死于真菌之手」那一笔损失还会按 `death.resourceMultiplier`（默认 ×2）换算成资源，
均分给所有心智；一只心智都没有时先存起来，等有心智出现再补发。

### 4.2 世界恨意值给真菌的减伤

```
减伤比例 = min(世界恨意值 × fungusResistance.perHatred, fungusResistance.maxResistance)
```

世界恨意值 = 所有玩家（**含离线**）个人恨意值之和。
**只对非玩家伤害生效**——玩家造成的伤害完全不受影响。

### 4.3 真菌袭击

**阶段机：** `PREP →（可选）ARENA → WAVES → 收场`

**两道硬闸门**：世上必须存在心智（Spore 的 `Proto`）；触发者所在维度不在数据包黑名单里。

**准备阶段（PREP）**

- 持续 `raid.prep.seconds`（默认 60 秒）后转入攻击；最多拖 `raid.prep.maxSeconds`（默认 180 秒）。
- 每 `raid.prep.launchCheckIntervalTicks`（默认 20 tick）检查一次发动条件，**三项是与关系**：

| 条件 | 键 | 默认 |
|---|---|---|
| 至少一只心智的资源达到 | `raid.prep.launchMinBiomass` | 200 |
| 目标玩家周围至少这么多真菌 | `raid.prep.launchMinFungusCount` | 8 |
| 那些真菌的「质量」之和至少到 | `raid.prep.launchMinQuality` | 30 |

「质量」用的就是 4.1 那张等级倍率表，所以 30 大约是「十几只基础感染体」或「五只进化体」。
取样半径都是 `raid.prep.gatherRadius`（默认 64 格）。

- 准备期还施加下面那组 `raid.bonus.prep*` 加成；到 `raid.prep.maxSeconds` 还没凑够条件就**取消**这次袭击。

**袭击期间给心智的加成**（`raid.bonus` 段，袭击强度的总旋钮）：

| 键 | 作用对象 | 默认 | 作用在哪 |
|---|---|---|---|
| `prepResourceCostMultiplier` | 心智资源**消耗** | 0.5 | `Proto.eatBiomass` 的入参 |
| `prepResourceGainMultiplier` | 心智资源**获取** | 2.0 | `Proto.addBiomass` 的入参 |
| `prepGrowthSpeedMultiplier` | 心智**发育**（铺菌壳） | 1.5 | 生成菌壳那个分支的 200 tick 节拍 |
| `attackManufactureSpeedMultiplier` | 心智**制造**（造兵） | 6.0 | 召唤那个分支的 200 tick 节拍 |
| `attackDespawnCapMultiplier` | Despawning System 上限 | 2.0 | Spore 那几个 `max_*_cap` 算出来的上限 |

> `prep*` 只在准备阶段生效，`attack*` 只在攻击阶段生效。全部填 1.0 就是 Spore 原样。

**竞技之须（ARENA）** —— 按下式掷一次，中了才出现：

```
出现概率 = min(raid.arena.chanceBase + 恨意档位 × raid.arena.chancePerTier, raid.arena.chanceMax)
默认：    min(0.2 + 档位 × 0.1, 0.9)
```

`raid.arena.warnLeadSeconds` 是「恐惧吧」比它出现提前多少秒发出（基准是**触须出现**那一刻，不是准备阶段开始）。

种给它的初始规模 `raid.arena.waveSize`（默认 6）与等级 `raid.arena.waveLevel`（默认 1，对应 Spore 自己的
Raid level 1/2/3 出怪表）。**它会自己往上加**：每 40 tick 按附近玩家的护甲/生命/背包重算一遍，
所以给的是起步值而不是上限。

竞技之须自己会在「场上真菌少于 4 只」时缩回消失——那是**通过**；到 `raid.arena.timeoutSeconds`
（默认 300 秒）还没清场则判**玩家失败**，走「死于真菌之手」那条线结算。

**我方围剿（WAVES）**

```
波数        = min(raid.ownWave.base + 恨意档位 × raid.ownWave.perTier, raid.ownWave.max)
              默认 min(1 + 档位 × 2, 10)
第 N 波数量 = raid.ownWave.countBase + (N-1) × raid.ownWave.countPerWave
              默认 3 + (N-1) × 2      ← 没有人为的每波上限
```

每波数量真正的上限来自世界：能调动的真菌只限于 `raid.attack.teleportSearchRadius`
（默认 128 格）内已有的那些，而它们的总数又受 Spore 的 Despawn 上限约束（那个在攻击期间被翻倍了）。

- 每波开头传送一批（落点距玩家 `teleportMinDistance` ~ `teleportMaxDistance`），
  再从心智存储里投放 `hivemind.deployPerWave` 只。
- 参战真菌挂上 `raid.attack.buffs`，第 N 波的 amplifier 再加 `(N-1) × raid.ownWave.buffsPerWave`。
- **进入下一波的条件**是「玩家周围 `raid.ownWave.clearRadius` 内的真菌少于 `raid.ownWave.clearCount` 只」；
  `raid.ownWave.seconds` 只是清不掉时的超时兜底。
- 豁免：每 `raid.attack.absenceTimeoutSeconds` 累计缺席判失败（见下）。

**收场**

| 结果 | 结算 |
|---|---|
| 打赢 | 掉 `raid.ownLoot` 表；个人恨意值 ×(1 − `death.raidWinLossRatio`) |
| 触须超时 / 躲黑名单维度逾期 | 走「死于真菌之手」那条线：×(1 − `death.fungusDeathLossRatio`) + 资源 ×`death.resourceMultiplier` |
| 玩家下线 | 中性结束，不结算 |

**躲进黑名单维度会冻结整场袭击**（阶段计时也停），只累计缺席时间；回来就从冻结处继续。
冻结是必须的：不停的话，躲进下界的玩家几秒后就会被 `raid.arena.timeoutSeconds` 判失败。

**我方战利品（`raid.ownLoot`）** —— 每条独立掷 `波数 × raid.ownLoot.rollsPerWave` 次。

### 4.4 灾厄重构体（Womb）

```
一次喂食追加的突变条数 = 1 × womb.mutationMultiplier × (袭击期间 ? womb.raidMutationMultiplier : 1)
默认：平时 2 条，袭击期间 4 条
```

**为什么是乘条数而不是改某个上限**：反编译 Spore 2.2.0j 确认，`Womb` 并没有「最多叠 N 条」的
上限常量。`addMutation` 只是把配方的属性 id 追加进一份**不去重、不封顶**的列表 `attributeIDs`，
孵化灾厄时 `summon` 再遍历整个列表、每条给对应属性 +1.0 基础值（重复的照样各算一次）。
所以「叠满要喂多少只」完全等于「这份列表有多长」。

倍率在**喂食那一刻**锁定：追加进列表的是重复条目本身，所以关掉袭击之后，
先前喂进去的那些条目不会被追溯放大。

`womb.raidBonusesEnabled` 是一个单独的开关：袭击的**制造速度 / 资源获取**加成是否也作用到它。
默认开。它不是心智——自带独立的生物质与配方体系，与心智之间没有任何数据通路
（它孵化灾厄是直接 `create + addFreshEntity`，不走心智的 `summonMob`），所以那项加成原本到不了它。

**孵化门槛：喂够属性才准孵**

Spore 原版的**孵化触发条件是生物质攒够**，而生物质主要来自**吃掉**靠上来的真菌。两条路的差距是：

| 生物质来源 | 速率 | 攒满 100 点需要 |
|---|---|---|
| 被动节拍（`recontructor_clock` = 30 秒/点） | 每 30 秒 +1 | **50 分钟** |
| **吃掉**真菌（`reconstructor_assimilation` = 5/只） | 每约 2 秒 +5 | **约 40 秒** |

所以一只蹲在菌群里的重构体**不到一分钟**就吃饱了，根本等不到喂进去几种属性——
而**属性只来自喂食**（7 个同化配方，每个要喂对应的特定进化体）。

`womb.hatchMinMutationTypes`（默认 **4**，范围 0~7）就是那道闸门：**至少喂到这么多种不同的属性才准孵化**。
没喂够时**它不会停止进食**——继续吃、继续攒，直到凑够。填 0 = 关掉门槛、回到 Spore 原样。

> **为什么是"种类"而不是"条数"**：`attributeIDs` 是一份**可重复**的列表，而 `mutationMultiplier`
> 会让一次喂食追加多条。按条数设门槛的话，改那个倍率就会连带改变门槛的含义；
> 按种类数则稳定——7 就是"七个配方全喂到"。
>
> **死亡孵化不受门槛约束**：生物质过半的重构体被打死时照旧当场孵一只（Spore 原设）。
> 保留它还有个作用：**玩家不能靠"提前把重构体打死"来彻底抹掉灾厄**。

**滞留上限：最多等 5 分钟**

光有门槛的话，凑不齐属性的重构体会**永远卡在那里吃**——不孵、也不消失，成为地图上一台一直运转的
吞噬装置。所以 `womb.hatchMaxStallSeconds`（默认 **300**，范围 0~3600）兜底：
**基础条件满足后最多滞留这么久，到点无条件孵化**，孵出来的是它当时攒到的那点属性。填 0 = 不设上限。

> 计时从**第一次被门槛拦下**那一刻算起——而 `summon` 只在生物质攒够时才会被调用，
> 所以那一刻正好等于"基础诞生条件满足"。标记记在重构体自己的持久数据里，
> 区块卸载与服务器重启都不清零。

**心智出资补齐突变**

门槛要求先喂够属性，而属性只能靠喂特定进化体（骑士、温迪戈、撕裂者、鸣蜂……）。
玩家不配合的话重构体就一直凑不齐——`womb.mindFundingEnabled`（默认开）让**心智自己把这件事办掉**：

```
每 mindFundingIntervalSeconds（默认 30 秒）一次：
  范围内（mindFundingRange，默认 64 格）找一只**正卡在门槛上**的重构体
  有，且自己出得起 mindFundingCost（默认 20 生物质）→ 扣生物质、替它补上一种缺的属性
```

> **缺哪几种是读 Spore 的配方表得来的**（`RecipeManager#getAllRecipesFor(WombRecipe)` 里每条配方的
> `getAttribute()`），不写在配置里也不硬编码——**自动跟着 Spore 的数据走**：Spore 改配方、
> 整合包加配方，这里都不用动。
>
> 出价 20 生物质 vs 吃一只真菌给 5 点，所以它是「买」而不是「捡」。出不起价的心智这一轮不办，
> 等自己攒够（袭击期间它的收入本来就会翻倍）。一次只办一只、只补**一种**属性，
> 所以多只心智会自然分工，也不会在一拍之内把一只心智掏空。

**灾厄出壳**：孵化出来的灾厄会被挪出「壳」外——默认是**心智的生物质穹顶之外**
（`hatchExitFromDome`，穹顶半径 + `hatchExitDomeMargin` 格）；没有穹顶罩着它时退回
「只挪出重构体自己那圈」（`hatchExitSearchRadius`）。

> **落点的 Y 按地表算，不沿用重构体的高度**（`WombHatch#atSurface`，用
> `getHeight(MOTION_BLOCKING_NO_LEAVES, ...)`，照抄 Spore 自己的 `Proto#teleportToSurface`）。
> 重构体是埋在地形里的土丘，它的 Y 就是"山包内部"的高度；照着这个高度往外推三十几格，
> 那边通常是山体、树木或水面，一个方向都站不住——**于是整条穹顶路径空手而归，
> 悄悄退回那个几乎不动的弱落点**。这正是"灾厄孵化后没有传送到壳外"的成因。
>
> 挂点在 `Womb#summon` 的**末尾**（`WombSummonMixin`），不是灾厄的 `setSearchArea`：
> 后者有两个毛病——`readAdditionalSaveData` 也会调它，而那时 `tickCount` 同样是 0，
> 于是**每次区块重载**都会把站在重构体旁边的灾厄再传送一次；而且它太早，
> 后面还有套属性 / `finalizeSpawn` / `addFreshEntity` 三步，任何一步动了坐标都会把落点悄悄覆盖。
>
> 两条路都没找到落点时**不动它，并留一行 INFO 日志**（含"附近没有心智"与"穹顶外一圈都放不下"
> 两个原因）。没有这行日志的话，"没传送"看起来会像 bug 而查不出是哪一步失败的。

### 4.5 拾荒者

**转变概率**（菌染人类生成时）—— 两道闸门相乘：

```
基础概率 = transformMinChance
         + (transformMaxChance - transformMinChance)
           × min(附近掉落物件数 / transformItemCountForMax, 1)
           默认：0.10 → 0.50，32 件封顶

数量系数 = max(0, 1 - 现存拾荒者 / maxCount)
           默认上限 20

实际概率 = 基础概率 × 数量系数
```

> **最终会有几只由 `maxCount` 决定，不由基础概率决定。** 数量系数在「现存 = 上限」时正好为 0，
> 所以只数朝上限收敛；`transformMinChance` / `transformMaxChance` 只影响**多快**收敛到那里——
> 把它们调大调小，最终只数不变。
>
> 实际会略微停在**略低于上限**的地方：拾荒者会被打死，而上限附近招募极慢
> （上限 20、现存 18 时系数只剩 0.1）。所以想让稳定值正好落在 20 附近，
> 上限可以给得比 20 稍高一点。

**上限本身就是概率归零点**：现存到 `maxCount` 时系数正好是 0，所以不必再单独判一次上限，
也就不会出现两条规则互相打架。现存数量是增量维护的（实体进出世界时 ±1）。

**它怎么找战利品**（两个半径分工不同，别混）：

| 半径 | 默认 | 管什么 |
|---|---|---|
| `lootRadius` | 32 | **捡东西时能看见多远**。走进去之后就由拾荒目标接管，走过去捡起来。 |
| `roamAwarenessRadius` | 64 | **闲逛时朝哪走**。没东西可捡时它本来只是随机游荡；这一项让它先看看这个半径内有没有战利品，有就朝那个方向偏着走。 |

> `roamAwarenessRadius` **必须大于** `lootRadius`，否则不生效：感知到的东西本来就在捡拾范围内、
> 拾荒目标会直接去拿。两者相等 = 退回原版的纯随机闲逛。
>
> 朝战利品那一路是**偏置**而不是直奔——落点必须落在"朝向战利品"的半平面里，
> 但仍是散步的节奏。所以它看起来像循着味儿慢慢摸过去。

**链接侦察：把同伴变成额外的搜索圆心**

拾荒者会链上一批同伴，**每个同伴向它提供一个以自己为圆心、半径 `linkProbeRadius`（默认 24）的搜索圈**。
于是它的"视野"不是一个圆，而是自己那一个加上每个链接对象那一个。

| 参数 | 默认 | 说明 |
|---|---|---|
| `linkScanIntervalTicks` | 100 | 多久重建一次链接（校验旧的 + 补新的） |
| `linkScanRadius` | 48 | 直连时从多大范围内挑同伴 |
| `linkBreakRadius` | 64 | 超过它就算走散、断开（**不低于扫描半径**，否则刚链上就断） |
| `linkProbeRadius` | 24 | 每个同伴提供的搜索圆心半径 |
| `linkMinLinks` → `linkMaxLinks` | 1 → 3 | 直连数量：**随存活时间从下限爬到上限**，用 `linkRampMinutes`（默认 10 分钟）走完 |
| `linkHivemindMinLinks` → `linkHivemindMaxLinks` | 2 → 6 | **经心智**间接链接时的数量，同样随存活爬升 |

> **链接关系由本 mod 自己维护**（存在拾荒者自己的持久数据里，一串 UUID），
> **不碰 Spore 的 `linked`**。原因是已核实的硬事实：那个布尔是"被心智登记过"的全局标记、
> **没有 owner 指针**、而且全 Spore **没有一处把它置回 false**——它表达不了"走散就断"，
> 还被恨意值与心智死亡凋零两处读着。
>
> 「心智麾下的真菌」取的是**心智自己的扫描盒**（`Proto.seachbox()`，受 Spore 的 `proto_range`
> 膨胀）里那些**已链接**的感染体——Spore 里没有成员名单，那个盒子就是它每次登记时用的范围，
> 所以语义正好对上。经心智链上的同伴**不看它离拾荒者多远**（它们本来就在心智周围），
> 判据落在心智身上。

**目标选择：聚堆 + 六因子综合评分**

地上的一处战场会散着几十件掉落物，不聚堆的话"走过去能拿到多少"根本看不出来。所以先按
`lootClusterRadius`（默认 8 格）立方格把它们聚成堆，再对每一堆算：

```
score = w距离 × 距离分
      + w到达 × 到达时间分
      + w数量 × 数量分
      + w类型 × 稀有度分
      + w效率 × 转化概率分
      + w危险 × 0                    ← 权重键保留，当前恒为 0（不做）

距离分     = 1 − min(水平距离 ÷ lootRadius, 1)
到达时间分 = 1 − min(到达秒数 ÷ (lootRadius ÷ 移速), 1)
             到达秒数 = (水平距离 + 向上高差 × lootMaxVerticalClimbFactor) ÷ 移速
数量分     = 件数 ÷ (件数 + lootCountHalf)
稀有度分   = 堆内最高单价 ÷ (堆内最高单价 + lootValueHalf)
转化概率分 = 按价值加权的平均 chance（本身就在 0~1）
```

六项权重都在 `scavenger.lootWeight*`，默认 0.35 / 0.10 / 0.15 / 0.25 / 0.15 / 0.0。
取分数最高的那一堆，堆内取离它最近的那一件作为导航目标。

> **为什么"类型"取堆里最高的单价**：一份里有一颗钻石，就该比五十块腐肉更值得跑一趟。
>
> **为什么数量与价值用半饱和 `x/(x+半值)` 而不是"除以某个上限"**：掉落物价值没有上界，
> 整合包可以写一条价值 1000 的条目；硬除上限会让所有高价堆一律顶到满分、失去分辨力。
> 半饱和永远单调、永远小于 1。
>
> **权重全配成 0 会怎样**：每一堆都是 0 分 → 全部并列 → 由并列时的"就近优先"决出结果，
> 也就是自动退回成"挑最近的"。所以把权重调坏不会退化成"不捡东西"。
>
> **`lootWeightArrival` 与 `lootWeightDistance` 在平地上近似共线**（到达时间就是距离除以速度），
> 通常只调其中一个。它唯一不可替代的作用是惩罚"在高处"的目标——向下不罚、向上按
> `lootMaxVerticalClimbFactor` 折算。

**追不到就放弃**：一件掉落物如果 10 秒内还走不到（隔墙、悬空、在另一侧水下），
拾荒者会放弃它、并在 30 秒内不再考虑它。没有这一条的话它会一直重寻路却永远走不到，
而它持有移动标志——<b>闲逛、环顾全都起不来，整只怪就那么站着不动</b>。


**它有两条免疫，另有一条刻意不免疫：**

| 路径 | 结果 | 靠什么 |
|---|---|---|
| Spore 的 Despawning System | **免疫** | `ScavengerDespawnProtectionMixin` |
| 原版「走远就消失」 | **免疫** | `Scavenger#removeWhenFarAway` 返回 false |
| 灾厄重构体的同化 | **免疫** | `WombAssimilationMixin`（让它压根不进同化循环） |
| **和平难度** | **不免疫，正常消失** | 与其它真菌一致——`Monster` 的 `shouldDespawnInPeaceful()` 是 true，本 mod 不覆写它 |
| 被打死 | 正常死亡 | 走 `remove(KILLED)`，与上面那些 `discard()` 不是同一个入口 |

> 和平难度是刻意的例外：Spore 自己的出生条件（`checkMonsterInfectedRules`）第一句就是
> 「难度是和平就返回 false」，也就是和平难度下真菌根本不刷。留一只免疫和平的拾荒者
> 在那种世界里既没有同伙也没有意义。
>
> 代价是**它在普通难度下不会自然减员**——数量只由 `maxCount` 与转变概率控制。

**四条随存活时间的成长曲线** —— 都是「线性爬升、各自封顶」，时间基准是那只拾荒者
**自己维护并存盘**的存活时长（`Scavenger#survivalMinutes()`）：

| 属性 | 速率键 | 封顶键 | 默认 | 满成长时的总值 |
|---|---|---|---|---|
| 最大生命 | `healthPerMinute` | `healthMaxBonus` | 6.0/分，最多 +90（15 分钟满） | **105**（基础 15） |
| 护甲 | `armorPerMinute` | `armorMaxBonus` | 1.5/分，最多 +19（约 13 分钟满） | **20**（基础 1，原版满套装等效） |
| 移动速度 | `speedPerMinute` | `speedMaxBonus` | 0.015/分，最多 +0.10（约 7 分钟满） | **0.30**（基础 0.2） |
| 每秒回血 | `regenHpsPerMinute` | `regenMaxHps` | 1.0/分，最多 5.0/秒（5 分钟满） | **5.0/秒** |
| 拾荒收益倍率 | `lootBonusPerMinute` | `lootBonusMax` | 0.25/分，最多 ×3 | — |

> **成长值只保留 1 位小数**（`Scavenger#round1`）：`分钟数 × 每分钟` 会算出
> `14.600000000000001` 这种数，它会原样出现在属性面板上。只作用于本 mod 加的修饰符。
>
> 移动速度是**属性绝对值**不是倍率：Spore 给菌染人类的移速基础值是 0.2，
> 而**玩家的行走是 0.1、疾跑约 0.13**——所以满成长的拾荒者比疾跑的玩家快一倍多。
> 它同时还持有逃跑的 `fleeSpeed`（1.5）与奔袭的 `lootSprintSpeed`（1.5），
> **这几个倍率是相乘的**，想留出反制空间就调小 `speedMaxBonus`。
>
> 满成长时它是全 mod 里最硬的一只后勤：105 生命、20 护甲（80% 减伤，等效 525 生命）、
> 每秒回 5 点。**但它不还手**，所以这份硬度只延长「处理掉它」的时间，不构成战斗威胁。
>
> 回血与进化点用小数累加器攒够整数才发一次——`heal()` 只收整数，0.5 点/秒直接取整就是永不回血。

**奔袭**：锁定了目标、且离它进入 `lootSprintRadius`（默认 8 格）以内时，
移速乘上 `lootSprintSpeed`（默认 1.5）。只作用于"走过去捡"这一段——找东西、闲逛都不受影响。

> 移速是在下令寻路那一刻写死的，所以基类里有一条「**速度变了就立刻重新寻路**」：
> 没有它的话，踏进奔袭范围那一下要等最多一个重寻路周期（默认 1 秒）才生效，
> 看起来就不像扑过去、而像走两步才忽然想起该跑。

**它有存储** —— 捡到资源先试着送出去，送不掉就揣在自己身上，上限随存活时间成长：

```
存储上限 = storageBase + min(storageMaxBonus, 存活分钟数 × storagePerMinute)
默认：    50 + min(2000, 分钟 × 10)
          1 分钟 60 → 10 分钟 150 → 1 小时 650 → 3.3 小时封顶 2050
```

存储的载体就是**那个暂存累加器**（`spore_add:loot_credit`，存在实体持久数据里、随实体存盘），
没有第三个账本。**装不下就丢**——超出上限的部分直接消失。

**交付的四级优先链**（由 `ScavengerDelivery` 实现，前两级共用 `deliveryRadius` 这一个半径）：

1. 已链接心智且有同维度心智 → 交给最近的那个心智；
2. 否则有「等级 ≥ `healMinTier` 且血量 < `healWoundedFraction`」的同伙 → 按血最少优先，
   最多治疗 `healMinTargets` ~ `healMaxTargets` 个（随存活时间从前者线性涨到后者，
   用 `healRampMinutes` 分钟走完）；
3. 否则**身上还揣得下** → **先存着**（这一级不消耗任何资源，是常态出口）；
4. 连存都存不下了 → 兜底折算成进化点分给附近真菌。

换算比：1 点资源 = `healthPerResource` 点生命 = `evoPointsPerResource` 点进化点（默认 1.0 / 0.1）。

> 第 3 级是这一轮新加的、也是关键的一级：以前"存着"只是前两级都没命中的副作用，
> 而第 3 级（折算进化点）几乎总能成功，资源根本留不住——于是「存满了该去找队友」这件事
> 永远不会发生。现在它是显式的一级，带上限闸门。

**存满了就去找队友**（`ScavengerSeekAllyGoal`，优先级夹在逃跑 0 与拾荒 2 之间）：

- 存量达到上限的 `seekAllyThreshold`（默认 80%）**且**冷却已过**且**半径 `seekAllyRadius`
  内找得到接收者，三者都满足才启动；
- 接收者优先**残血同伙**（与交付链第 2 级同一套判据），其次**心智**（只在已链接时才认）；
- 走到 `deliveryRadius` 内就把存量整笔推出去（这一趟**不许再存回去**，否则白跑）；
- **没到阈值就什么都不做**——它继续捡东西。这正是需求里那句「若存储未满，则优先拾荒」。

**死后把存量掉出来**：`Scavenger#die` 读存量，按汇率换成物品掉在原地，然后清空累加器。

```
掉落个数 = min(deathDropMaxItems, 存量 ÷ deathDropResourcePerItem)
默认：     min(32, 存量 ÷ 20)
```

物品从 `deathDropItems` 里**随机取**（默认只有 `spore:biomass`），写坏的 id 跳过并记一行日志。

> 为什么不"还给真菌"（比如塞进世界暂存等心智补发）：那会让**玩家杀死拾荒者等于把资源送给真菌**，
> 与激励机制正好相反。掉在地上谁都能捡，"打劫后勤"才成为一个真正的奖励。
>
> 换算用「汇率 + 白名单」而不是回查掉落物转化表：存量是抽象数字，反查要遍历整张表做凑数找零，
> 既慢又难解释，而玩家只关心打死它能捡到多少。

**逃跑**（第一要务，优先级 `fleePriority` 默认 0）：

- **被足够强的威胁盯上**才跑。两道闸门：
  1. **它得真的盯上我** —— `Mob#getTarget() == 自己`，或它最近打过我且还在
     `fleeAggroMemoryTicks`（默认 200 tick = 10 秒）之内；
  2. **算得出危险** —— `有效生命 ÷ Σ 锁定者的每秒输出 < fleeSurviveSeconds`（默认 4 秒）才跑。
- 血量低于 `fleeHealthFraction` 时**无条件**跑（即使面对玩家）；
- 落点在 `fleeTargetHorizontalDistance` × `fleeTargetVerticalRange` 的范围内选，
  方向背离最近的那个锁定者。

> ⚠️ **一群僵尸围着它，它不会跑。** 原版僵尸、骷髅这类敌对生物的目标选择里**不含"怪物"**，
> 所以它们根本不会把拾荒者当目标——只有铁傀儡这类会打怪物的生物、别的模组里"攻击一切"的怪、
> 以及**打过它的玩家**才会触发。这是「不要光顾着跑」的直接结果，第一次看到别当成 bug。
>
> 标定参考：刚转变的拾荒者约 15.6 有效生命，一只僵尸每秒 3 点 → 预计存活 5.2 秒，
> 默认 4.0 下**一只不跑、两只才跑**。而靠存活成长养到 40 有效生命的老拾荒者要每秒十几点才跑得动它——
> 「活得越久越沉得住气」是靠复用同一套硬度估算**免费**得到的，没有第二条成长曲线。

**饥饿**：它不会饿死——**捡到东西就把饥饿清零**（`ScavengerLootGoal#onCollected` 里那句
`setHunger(0)`）。

> Spore 的饥饿判据是 `canStarve()`，它要求「进化点为 0」，而**击杀会把饥饿清零**
> （`Infected#awardKillScore` 里也有这一句）——普通真菌靠**打猎**填饱自己。
> 拾荒者永远不击杀、进化点永远停在 0，所以它是全阵营里唯一一个**只挨饿、没有填饱手段**
> 的成员：饥饿攒到 Spore 的阈值后会被挂上 `spore:starvation`，每 80 tick（4 秒）造成 1 点通用伤害，
> 只有 `Infected` 会中招。刚转变时自回血还低（0.5/秒要一分钟后才有），15 点血约 60 秒见底。
>
> 它本来只有一条活路：保留了 Spore 的 `InfectedConsumeFromRemains`，饥荒时去吃残骸，
> 而吃残骸给 +1 进化点、`canStarve()` 立刻变 false。现在**捡掉落物也算吃到了**——
> 把「打猎」换成「觅食」，与它的定位一致。
> 判据放在拾取那一刻、而不是"掷中转化概率之后"：东西那时已经被删掉了，它确实吃下去了。

它在普通难度下不会被清理，所以存活成长总能攒满；和平难度下则与其它真菌一起消失（见上面那张表）。

### 4.6 冰霜

**冰霜新星的蓄力缩放：**

```
威力系数 = minPowerFraction + (1 - minPowerFraction) × clamp(蓄力进度, 0, 1)
           默认：0.20 → 1.00

整数类数值（两个半径、秒数、层数）= max(1, round(满蓄力值 × 威力系数))
小数类数值（爆炸强度）            = 满蓄力值 × 威力系数
```

所有随蓄力变化的数值都过同一个函数，所以曲线必然一致——不会出现「半径涨得比层数快」。

**冻伤伤害**（`FrostbiteAllMobsMixin` 把它**加进 Spore 那一次冻结伤害里**，不是单独再打一次）：

```
附加伤害 = 层数 × 1 点 + floor(层数 / levelsPerBonusTier) × maxHealthBonusPerTenLevels × 最大生命
默认：    层数 × 1 点 + floor(层数 / 10) × 2% × 最大生命
```

> 加进同一次结算而不是补第二跳，是因为原版 `LivingEntity#hurt` 有 20 tick 的受伤无敌帧，
> `invulnerableTime > 10` 时若新伤害不大于上次就**整条丢弃**。顺带也让这份加成像原版规则一样
> 接受护甲与抗性结算。
>
> 档数按**显示层数**算（= amplifier + 1）：10 层为 1 档、20 层为 2 档。127 层封顶时是 12 档 = 24%。

**爆燃伤害**（`BuffLevels#fireDamageBonus`，同样加进同一次火焰伤害里）：

```
附加伤害 = 层数 × fireDamagePerStack + floor(层数 / stacksPerBonusTier) × maxHealthBonusPerTenStacks × 最大生命
默认：    层数 × 1 点 + floor(层数 / 10) × 1% × 最大生命
```

**「烈阳」按件减免**（`Warmth#resistanceFraction`）：

```
免疫比例 = min(1.0, 件数 × warmth.frostbiteImmunityPerPiece)
默认：    件数 × 0.25      → 四件叠满即 100% 全免
```

它管两件事：① 按比例缩小**新来**的冻伤（层数与时长一起缩）；② 让**已经吃到的**细雪冻结
消退得更快（每 tick 额外减 `frostDecayPerTick × 免疫比例`，按 `meltTopUpPeriodTicks` 攒够整数补发）。

> `warmth.frostDecayPerTick` 必须与 vanilla 实际的每 tick 衰减量一致（原版硬编码 2）。
> 它不改原版行为，只改我们「补零头」时假设的基准，**除非你同时改了原版，否则不要动它**。

**冰刺地形的高度函数：**

```
中心系数 = 1 - 距离 / (影响半径 × spike.maxRelativeRadius)
若中心系数 ≤ 0 → 这一列不长

高度 = spike.maxHeight × peakScale × (1 - 到山尖的距离 / spike.baseRadius) × sqrt(中心系数)
       低于 spike.minHeight 的一律不长
```

地图被切成 `spike.cellSize` 见方的格子，其中 `1 / spike.rarity` 的格子里有一个山尖，
山尖在本格内的落点是伪随机的（由格子坐标与爆心算出的盐决定）。
判断某一列时看的是 **3×3 个格子**——山尖可能长在隔壁格子里、锥体伸进本格。

> 形状是 `(x, z)` 的**纯函数**，所以冲击环扫到哪一列就现算哪一列，
> 不需要在实体里存一张「哪里该长刺」的表，也就没有「世界重载后剩下的刺长不出来」的问题。
> 中心系数开了平方根：线性版本下三分之二半径处就只剩三成，满地的矮桩子。

### 4.7 资源与收集

真菌捡起一件掉落物时：

```
资源 = 整堆价值(= 单价 × 数量) × 拾荒者存活加成(仅拾荒者)   ← 掷骰命中 chance 才结算
```

**每件掉落物各掷各的骰**（`loot_values` 里的 `chance`）。没中就是这次什么都没转化出来，
但东西**照样被捡走**——概率管的是「转化出多少资源」，不是「捡不捡」。

**被 Despawning System 清理掉的真菌**折算成资源：

```
资源 = despawnBaseValue × 该生物的等级倍率
默认：5.0 × 等级倍率
```

**注意这里没乘「类型 / 重要性 / 链接 / 发育」**：那些是「玩家杀了它有多可恨」的维度，
对「系统清理回收了多少生物质」没有意义。

### 4.8 清扫世界（心智的联合能力）

某个维度里心智够多时，它们会联合起来把整片地方扫一遍——**把该维度里所有掉落物收走、折算成资源**。

**触发是三重闸门，三条都满足才发动：**

| 闸门 | 键 | 默认 |
|---|---|---|
| 该维度里的心智数 ≥ | `hivemind.sweep.minHiveminds` | 3 |
| 周期到点 | `hivemind.sweep.intervalMinutes` | 20 分钟 |
| 掷中概率 | `hivemind.sweep.chance` | 0.5 |

所以默认下平均 **40 分钟**才发动一次，而且只出现在心智成气候的维度里。

**效果与换算：**

```
总资源 = Σ(单价 × 数量) × 倍率          ← 不掷转化概率，按满价值算
倍率   = multiplier + (心智数 - minHiveminds) × perExtraHivemind
默认：   2.0 + (心智数 - 3) × 0.5
         3 只 → 2.0 倍、5 只 → 3.0 倍、10 只 → 5.5 倍
```

资源交给 `Resources.deliverToAll`，也就是**按心智数均分**给该维度里的心智——
「联合发动」在分配上也成立。

> **它不豁免任何掉落物**：玩家丢出的、死亡掉落的，一样会被收走。
> 这是刻意的（需求要的就是「清扫世界」），但也意味着**玩家死了之后如果 20 分钟内没跑回去捡，
> 东西就没了**。
>
> **一个会削弱它实际体感的事实**：原版 `ItemEntity` **6000 tick（5 分钟）就自然消失**，
> 而且只在所在区块加载时计时。所以 20 分钟一次的清扫，在原版行为下能扫到的多半只是
> **玩家附近那 5 分钟内掉的**东西；**区块未加载处的掉落物倒是会一直留着**——
> 它们才是这个能力真正会清掉的那一批。
>
> **触发时会给该维度的玩家发一条消息**（`message.spore_add.sweep`）。这不是装饰：
> 整片地上的掉落物凭空消失，没有提示的话只会被当成 bug。
>
> 周期用**游戏刻取模**实现，不存任何倒计时状态：`getGameTime()` 本身存盘且单调递增，
> 所以重启后接着走同一个节奏。各维度按自己 id 的散列错开相位，不会在同一拍里一起清扫。

### 4.9 冰雾的持续清理

核弹与冰霜新星爆发后都会留下一团雾，**这团雾在消散之前一直按 CDU 的规则清真菌方块**。

规则与爆发那一下**完全同一套**（残骸→冰冻残骸、胆汁→结壳胆汁、生物质/膜→冻伤生物质、
配置与数据包的转换表、最后兜底变空气，先匹配先赢）——见 `FungalClearing` 的类注释。

**形状与各自爆发一致**，所以"看到雾的地方就是会被清的地方"这条原则仍然成立：

| | 形状 | 清法 |
|---|---|---|
| 冰霜新星 | 半径十几格的**球** | 每次整球扫一遍（`FungalClearing.clear`），每 `intervalTicks` 一次 |
| 冰雪的叹息 | 铺在圆盘上的**地面盘** | **每隔一段时间从中心推出一道可见的环**，环推到哪就清到哪 |

**核弹为什么要摊到时间上**：一次整片扫是 O(r³)，半径 128 时是 **880 万个方块**，一秒一遍不可能。

```
一道环推完全程 = pulseSeconds 秒
两道环之间歇   = pulseIdleSeconds 秒
每 tick 处理的格子数 ≈ 整盘格子数 ÷ (pulseSeconds × 20)
默认：2000 万格 ÷ (60 × 20) ≈ 16k 格/tick
```

> **为什么是"环"而不是"行主序游标"**（这一条是改出来的，值得留着）：
> 最初写的是行主序游标——`dx` 从 `-r` 到 `r`、每个 `dx` 里 `dz` 走一遍，每次推固定列数。
> 它确实最终会覆盖整个圆盘，但有两个致命问题：
>
> 1. **空间上不连贯**：任意时刻只在扫一条很窄的**行带**，玩家看到的是一片安静，
>    完全不知道它在工作；
> 2. **追不上真菌再生**：默认预算下整盘要 **402 秒**才走完一遍，而冰雾默认只活 600 秒，
>    于是整盘只能覆盖约 **1.5 遍**——你在圆盘某一侧放的菌，要 5~6 分钟才轮得到一次，
>    而第二遍从 6.7 分钟才开始、走到中心已经是第 10.1 分钟，雾已经散了。
>    净效果就是玩家报的那句「**这团雾根本不清菌**」。
>
> 换成按环推进之后，同一份工作量被组织成一道道从中心推出去的环：空间连贯、**看得见**
> （每道环有一圈粒子），而且**每一处在每遍之内必然轮到一次**。
>
> **为什么不用 CDU 的概率**：`FungalClearing` 的类注释里写着它刻意丢掉了 CDU 的概率
> （数据包表 20%、生物质 10%、落叶 20%），理由是"新星是一次性爆发，照抄概率会留下 80% 的真菌方块"。
> 这里沿用同一个决定：一次扫到的就必定清掉，靠**反复经过**而不是靠概率把范围覆盖完。
>
> **环的相位不存盘**，而是由「距 2 号环推完过了多久」直接算出来。记字段的话得存盘，
> 漏存一次就永久错位；算出来的则读档、区块卸载再加载都不会乱。代价是服务器卡顿后相位会一次
> 跳好几 tick（环带一下变得很宽），那个被夹在 8 个 tick 的行程以内——被跳过的范围交给下一道环。

---

## 五、名单与表格（数据包）

这三处都是**一个文件一条**，放在 `data/<你的命名空间>/<目录>/<任意名字>.json`。
命名空间不必是 `spore_add`——你自己的数据包也能往里加。

### 5.1 掉落物定价 `loot_values`

```json
{ "item": "minecraft:iron_ingot", "value": 6, "chance": 1.0 }
{ "tag":  "minecraft:logs",       "value": 1, "chance": 0.35 }
```

- `item` 与 `tag` **二选一**（不能都给，也不能都不给）；
- `value` 必须 > 0，它是**单价**（结算时乘这一堆的数量）；
- `chance` 可省，省略 = 1.0。取值必须在 (0, 1] 之间，写 0.35 就是 35%。

**匹配顺序：物品条目优先于标签条目**，同组内按文件路径排序。
实际效果就是「给某个物品单独写一条，就能盖过它所属标签的那条」。

**没列到的物品也算「纳入了」**——走配置里的 `loot.defaultValue` / `loot.defaultChance`
（默认 1.0 / 1.0，也就是全都收）。想排除某类东西请写黑名单，而不是把默认值调回 0
（那会让所有没列到的物品统统不捡，等于把这张表变回白名单）。

**内置的 17 条：**

| 文件 | 匹配 | 单价 | 概率 |
|---|---|---|---|
| `amalgamated_biomass.json` | `#spore:amalgamated_biomass` | 6 | 100% |
| `body_parts.json` | `#spore:body_parts` | 4 | 100% |
| `bone.json` | `minecraft:bone` | 1 | 100% |
| `coals.json` | `#minecraft:coals` | 2 | 100% |
| `corrosive_parts.json` | `#spore:corrosive_parts` | 6 | 100% |
| `diamond.json` | `minecraft:diamond` | 16 | 100% |
| `gold_ingot.json` | `minecraft:gold_ingot` | 8 | 100% |
| `inf_parts.json` | `#spore:inf_parts` | 4 | 100% |
| `iron_ingot.json` | `minecraft:iron_ingot` | 6 | 100% |
| `logs.json` | `#minecraft:logs` | 1 | 100% |
| `meat.json` | `#spore:meat` | 1 | 100% |
| `planks.json` | `#minecraft:planks` | 1 | 100% |
| `putrid_parts.json` | `#spore:putrid_parts` | 3 | 100% |
| `reagents.json` | `#spore:reagents` | 3 | 100% |
| `rotten_flesh.json` | `minecraft:rotten_flesh` | 1 | 100% |
| `stitches.json` | `#spore:stitches` | 3 | 100% |
| `wool.json` | `#minecraft:wool` | 1 | 100% |

### 5.2 掉落物黑名单 `loot_blacklist`

```json
{ "item": "minecraft:cobblestone" }
{ "tag":  "minecraft:flowers" }
```

**它优先于一切**：判断发生在「挑目标」那一步——黑名单里的东西**根本不会去捡**，
不是「捡了但给 0」。同一件东西既在转化表里又在黑名单里时，以黑名单为准。

内置：**空**（`data/spore_add/loot_blacklist/` 下没有任何文件，所以什么都不排除）。

### 5.3 袭击维度黑名单 `raid_blacklist`

```json
{
  "replace": false,
  "dimensions": ["minecraft:the_end", "some_mod:some_dimension"]
}
```

- 一个文件可以列多个维度；多个文件会**合并**（除非 `replace` 为 `true`）；
- 玩家躲进黑名单维度后，整场袭击会**冻结**（阶段计时也停），只累计缺席时间；
  逾期不归判玩家失败。见 `raid.attack.absenceTimeoutSeconds`。

内置：`default.json`，`dimensions` 为**空数组**，也就是默认没有任何维度会被禁止。

> 这里刻意**不用维度标签**：1.20.1 的 `minecraft:dimension` 这个 id 被
> `Registry<Level>` 与 `Registry<LevelStem>` 两个注册表共用，用标签会踩到歧义。

### 5.4 其它内置名单

**真菌类食物** `data/spore_add/tags/items/fungal_food.json` —— 吃这些东西会触发 `food` 段那两支恨意值变化。
内置 10 项，全部是 Spore 的食物：`biomass_bacon`、`organoid_soup`、`brain_noodles`、
`meaty_icecream`、`fleshy_ribs`、`sausage`、`vigil_eye_soup`、`reforged_biomass_t/w/a`。
往这个标签里加东西即可扩展。

**冻伤保护方块** `data/spore_add/tags/blocks/frost_proof.json` —— 冰霜武器不会替换这些方块。
内置 5 项：`nether_portal`、`end_portal`、`end_gateway`、`dragon_egg`、`spore_add:frost_sigh`。
（硬度为负的方块如基岩、屏障另有一条不靠标签的兜底。）

**真菌阵营标签** `data/spore/tags/entity_types/fungus_entities.json` —— 往 Spore 自己的
`#spore:fungus_entities` 里追加本 mod 的拾荒者。这样 Spore 的扫描仪、竞技之须等功能
都会把它当成真菌。

**默认的袭击增益表** `raid.attack.buffs`：

```
minecraft:speed|200|1       速度 I
minecraft:strength|200|1    力量 I
minecraft:resistance|200|0  抗性 I
```

格式是 `效果id|持续tick|amplifier`（与 Spore 自己那几张 debuff 表同构）。
持续 200 tick 是配合默认 10 秒的刷新间隔——刚好无缝续上。

**默认的我方战利品表** `raid.ownLoot.table`：

```
spore:living_core|1|1       格式：物品id|最少|最多
spore:hardened_bind|1|3
spore:fleshy_claw|1|2
spore:calcified_tumor|1|2
spore:corrosive_sack|1|2
minecraft:diamond|1|1
```

---

## 六、只读的硬上限

下面这些**不是「没做成可配置」，而是协议或原版的硬限制**，配了也不会生效：

| 值 | 上限 | 原因 |
|---|---|---|
| 冻伤 / 爆燃的 amplifier | 126 | 网络同步与存档序列化都用**有符号 byte**，超过 127 会静默损坏。本 mod 的约定是「层数 = amplifier + 1」，所以层数封顶 127。 |
| 可燃 / 爆燃的**层数** | 无上限 | 层数不走 vanilla 那套（存不下），而是存在实体自己的持久数据里，界面上那个数字由客户端自绘。 |
| 冰刺高度 | 世界建筑高度 | 超过 `getMaxBuildHeight` 的部分会被截断。 |
| 核弹影响半径的列数 | 由 `shockwaveSeconds` 反推 | 环的最外圈每 tick 要处理的列数约为 `2π·R / (20 × 秒数)`。半径 128 时：20 秒 → 每 tick 约 257 列（可行）；1 秒 → 约 5147 列（必然卡服）。 |
| 液态寒冷半径 | 16 | 冰扩散每秒只在球的外接立方体里随机取样，半径越大球内占比越低（半径 6 约 52%，半径 16 只剩约 12%），再大就几乎抽不到点。 |

---

## 七、想改某个具体的东西，该动哪

| 我想要…… | 动这里 |
|---|---|
| 让真菌更凶 / 更弱 | `spore_add-fungus-common.toml` 的 `fungus` 段 |
| 让袭击来得更频繁 / 更少 | `raid.trigger` 段 |
| 让袭击的过程更长 / 更短 | `raid.prep.seconds`、`raid.ownWave.seconds` |
| 让袭击期间的怪更多 | `raid.ownWave.countBase` / `raid.ownWave.countPerWave`、`raid.attack.teleportSearchRadius` |
| 改袭击奖励 | `raid.ownLoot.table` |
| 关掉竞技之须 | `raid.arena.chanceBase` 与 `raid.arena.chancePerTier` 都填 0 |
| 让某个维度免疫袭击 | 加一个 `data/<ns>/raid_blacklist/*.json` |
| 让真菌不捡某类东西 | 加一个 `data/<ns>/loot_blacklist/*.json` |
| 改掉落物的资源价值 | 改 `data/spore_add/loot_values/` 下的文件，或加自己的 |
| 让玩家变强 | `spore_add-player-common.toml` |
| 改冰霜武器的数值 | `frostNova` / `liquidCold` / `frostSigh` 段 |
| 改冻伤普遍更强 / 更弱 | `frostbite` 段（四件冰霜武器共用） |
| 改「烈阳」的减免 | `warmth` 段 |
| 改可燃 / 爆燃 | `combustion` 段 |
| 让拾荒者更少见 | `scavenger.maxCount`、`transformMinChance` / `transformMaxChance` |
| 关掉某个机制 | 找它的 `*Enabled` 开关，或把对应的倍率填 1.0 / 概率填 0 |
| 查"为什么没触发 / 没生效" | `/spore_add debug <分组> on`，看 `[SporeAdd][分组]` 开头的日志（见第八节） |
| 给整合包写配方时找锚点 | 用 `spore_add:frost_nova_0`（物品）与 `spore_add:frost_sigh_0`（方块），它们没有任何机制，外观与成品相同 |

---

## 八、调试检查点

文件：`config/spore_add-debug-common.toml`。**默认全关**——不改它的时候，日志输出与没有这套
设施时逐行一致。

它把袭击 / 心智存储 / 拾荒者 / 灾厄重构体这些子系统里**原本静默的分支**（条件不够、
掷骰没中、注入没生效、投放返回 0、交付走了哪一级）打成日志，用来排查
"为什么没触发""为什么没生效"。

段落顺序：`debug`

| 键 | 默认 | 范围 | 说明（配置里的第一行注释） |
|---|---|---|---|
| `debug.enabled` | `false` | 开关 | **总闸**。关着时下面九个分组一个都不输出，且判定是一次短路。 |
| `debug.fungus` | `true` | 开关 | 真菌加强：进化加速、猎杀判定、感知范围、抗寒、冰冻伤害倍率。 |
| `debug.womb` | `true` | 开关 | 灾厄重构体：同化突变、孵化闸门、出壳搬移、心智出资补突变。 |
| `debug.scavenger` | `true` | 开关 | 拾荒者：生成转变、逃跑判据、拾荒评分、交付链、存量上限、死后掉落。 |
| `debug.hatred` | `true` | 开关 | 恨意值：击杀取值、越档掷骰、世界减伤、给玩家的增益。 |
| `debug.raid` | `true` | 开关 | 真菌袭击：阶段机、发动条件、竞技之须、波次。 |
| `debug.hivemind` | `true` | 开关 | 心智存储：收进存储、投放、清扫世界、穹顶漏出。 |
| `debug.loot` | `true` | 开关 | 资源：转化表命中、捡起掷骰、回收与暂存。 |
| `debug.cold` | `true` | 开关 | 玩家侧的冰霜内容：冰霜新星、冰雪的叹息、液态寒冷与冷却液。 |
| `debug.mixin` | `true` | 开关 | 只打 mixin 注入点自己的日志（"注入到底有没有生效"）。 |

**九个分组默认就是 `true`，它们是过滤器而不是闸门**——真正的闸门只有总闸。
这与其它两节的直觉相反，所以特别说明：把分组全设成 `false` 并不会更安静，那件事只有总闸能做。

### 8.1 在游戏里开关

```
/spore_add debug                 # 看总闸与九个分组的当前状态
/spore_add debug on|off          # 总闸
/spore_add debug <分组> on|off   # 单个分组
```

需要权限等级 2。改动会**写回配置文件**（当场生效、重启后仍在），所以"改了但重启又变回去"
不会发生；反过来说，调试完记得关掉。

### 8.2 日志格式

每行都带分组前缀，可以单独过滤某一类、或反向排除：

```
[SporeAdd][RAID] 准备未达成：资源 120/200、数量 5/8、质量 12.0/30.0
[SporeAdd][HATRED] 越档掷骰：500.0 → 1010.0（档位 1 → 2），概率 0.5，掷出 0.83 → 不触发
```

```bash
grep "\[SporeAdd\]\[RAID\]" logs/latest.log
```

### 8.3 开销

关着的时候，判定是"一次短路 + 一次布尔读"，热的调用点（逐 tick 的那些）也只多这一下。
**但要注意**：如果某个调用点把日志参数写在了 `log(...)` 的实参里，那些参数无论开关都会求值。
所以本 mod 里逐 tick 的检查点一律写成"先判 `on()` 再打"——这一点在
`debug/SporeAddDebug` 的类注释里写明了。
