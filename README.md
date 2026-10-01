# Spore Add

面向 Minecraft **1.20.1 / Forge** 的模组：围绕 [Fungal Infection: Spore](https://modrinth.com/mod/fungal-infectionspore)（真菌感染：孢子）的真菌机制做修改与新增。

> 当前状态：**已有四块内容可用**——三种流体、两条 buff 链（可燃→爆燃 / 冻伤）、一件可蓄力投掷的物品（「冰霜新星」）、一个附魔与两个进度；尚未实机验证。

## 已实现内容

**三种流体**（各有自己的 `FluidType`、静置/流动变体、方块与桶）

| 流体 | 接触/范围内的效果 |
|---|---|
| 冷却液 | 像细雪一样失温，并逐秒累积 Spore 的「冻伤」（**封顶 10 层**） |
| 液态寒冷 | 泡在里面**或**身处球形范围内都会失温并叠冻伤（**无上限**）；放下后立刻冻住接触的空气与流体，并向同一半径内逐秒扩散生成冰，**内层蓝冰 / 中层浮冰 / 外层冰**，且范围内的原版冰不会融化。半径默认 6、可在配置里改 |
| 高能燃料 | 累积 Spore 的「可燃」（每秒 1 级，离开 10 秒后归零） |

**两条 buff 链**

- **可燃 → 爆燃**：高能燃料与 Spore 的焦油池都会给可燃；可燃被 Spore 触发时转化为「爆燃」，层数**无上限**。每层使受到的火焰伤害 **+1**，每满 10 层再附加 **1% 最大生命值**；雨天/水里火焰会灭但爆燃保留，离开后按剩余时长重新烧起来。
- **冻伤**：层数**就是 amplifier**（因为 Spore 自己在读它：伤害、减速、抗寒闸门都用它）。冷却液封顶 10 层、液态寒冷无上限，硬上限 127 层。每层给 Spore 那份冻结伤害 **+1 点平伤**，每满 10 层再附加 **2% 最大生命值**。

**「烈阳」附魔**：1 级、宝藏附魔、与冰霜行者互斥。四格护甲任意一件带上即免疫冻伤、冻结伤害与细雪冻结；附在**靴子**上时还不会陷入细雪。装备时获得进度「永恒炽阳」（根进度是「绝对零度」）。

它的附魔书放在本 mod 自己的创造模式页签下，并且**已从原版的材料页签里移除**——附魔书是同一个物品靠 NBT 区分的，
原版构建材料页签时会把所有附魔的书一股脑塞进去（见 `ModCreativeTabs#onBuildTabContents`）。

**「冰霜新星」物品**：长按右键蓄力（与原版弓同一套机制，含三段蓄力外观与逐级升高的蓄力音），松手发射一枚**无重力、无随机偏移、恒速**（1.5 格/tick）的直线飞行弹体，弹体带冰屑拖尾。弹体有一根**引信**：投出后 5 秒（可配置）仍未命中就**在当前位置自动引爆**——所以最大射程约 150 格。落点处依次发生：

| 效果 | 范围 | 满蓄力时的数值 |
|---|---|---|
| **爆炸伤害** | 伤害半径 16 格（= TNT 的两倍） | power 8.0；**不破坏方块**；中心满暴露约 113 点 |
| **换方块** | 球半径 4 | 内半**蓝冰** / 外半**浮冰** |
| **冻伤** | 球半径 10 | **32 秒、10 层**，含投掷者自己 |
| **清真菌** | 球半径 = 冻伤半径 | 清除真菌方块，机制照搬 Spore 的 CDU（见下） |
| **粒子云** | 球半径 = 冻伤半径 | 持续 **10 秒**，接触者同样叠冻伤 |
| **延时炸弹** | 同上那颗冰球 | **20 秒**后二次引爆：清掉那批冰、冰雾与冻伤范围 **×1.5**、冻伤强度 **×1.5**，**不产生任何伤害** |

**二次爆炸**（冰球变成的延时炸弹）细节：

- 倒计时期间有**预警**，而且做得刻意显眼：
  - **视觉**：冰球**表面**持续往外涌霜雾与冰屑（雾是主力——大团才看得见），数量在最后 5 秒
    从 2/秒 涨到 14/秒（冰屑 1/秒 → 16/秒）。粒子取在球面而不是球内：冰球是实心方块，
    取在球内会被那层冰遮住。
  - **听觉**：提示音平时每 1.5 秒一声，最后 5 秒加密到 0.3 秒一声，音量 1.2，
    音调从 0.7 升到 2.0 做出倒数感。
- 引爆时先把那批冰**清成空气**（只清蓝冰/浮冰，判定与冻结时逐字一致，不会漏掉最外层一环），
  然后铺开 1.5 倍的霜雾并施加冻伤。**不走原版爆炸**，所以没有任何伤害。
- 二次爆炸的霜雾持续 **20 秒**（可配置），比一次爆炸的 10 秒更长——范围更大，雾也该留久一点。
  注意「雾存在多久」与「雾里冻伤持续多久」是两回事，后者由 `secondaryPowerMultiplier` 决定。
- 四个数值各有配置项：`secondaryDelaySeconds` / `secondaryRangeMultiplier` / `secondaryPowerMultiplier` / `secondaryCloudSeconds`。
- 所以地表不会永久留着一个冰球——代价是清成空气而非还原，原地会留下一个球形空洞。

**清除真菌方块**：两个爆炸阶段（一次与二次）都会清理影响范围内的真菌方块，机制照搬 Spore 的 **CDU**
（那台在半径内清除真菌感染的机器）。规则按顺序判断、**先匹配先赢**：

| # | 条件 | 结果 |
|---|---|---|
| 1 | 残骸 | → 冰冻残骸 |
| 2 | 胆汁 | → 结壳胆汁 |
| 3 | 生物质 / 膜方块 | → 冻伤生物质 |
| 4 | Spore 配置 `block_cleaning` 里的「感染方块\|干净方块」 | → 表里那个方块（属性按同名复制） |
| 5 | 数据包 `spore_cdu_conversion` 表 | → 表里那个方块 |
| 6 | `#spore:fungal_blocks` 或 `#spore:removable_foliage` | → 空气 |

第 4 条才是 CDU 的**主机制**：那份配置默认就不空（感染石头→石头、感染圆石→圆石、实验室方块→lab_block…）。
少了它，新星会把感染石头"清"成空气而不是干净的石头。

三处刻意与 CDU 不同，理由都写在 `FungalClearing` 的类注释里：**不用概率**（CDU 是每 tick 反复尝试的机器，
新星是一次性的，照抄会留下 80% 的真菌方块）、**先匹配先赢**（CDU 是后匹配覆盖前匹配，行为不好预期）、
**不下雪**（CDU 有 0.1% 概率在实心方块上铺雪，和落点的冰球打架）。

**隐藏式字幕**：新星的四个时刻各有自己的字幕。音频全部沿用原版音效——`sounds.json` 里直接用
`minecraft:...` 引用原版音频文件，所以**不必自带 ogg**；字幕则是自己的，隐藏式字幕里不会把它念成
原版那条「方块破坏声」。

| 时刻 | 字幕 | 沿用的原版音 |
|---|---|---|
| 蓄力中（够门槛后每 5 tick 一声） | 冰霜新星已就绪 | 紫水晶风鸣（含原版的 0.2 音量系数） |
| 发射 | 冰霜新星已发射 | 弓弦 |
| 二次爆炸倒计时 | 冰霜新星即将爆发 | 紫水晶共振（含原版的 48 格衰减距离） |
| 二次爆炸 | 冰霜新星已爆发 | 玻璃碎裂 |

> 一次爆炸的命中音仍直接用原版 `SoundEvents.GLASS_BREAK`，所以它的字幕是原版的「方块破坏声」。

**六项威力参数里五项随蓄力线性缩放**（最低蓄力是满蓄力的 30%，走同一条曲线 `powerFactor`），
全部可在配置里改；只有粒子云的时长是定值。以默认配置为例：

| 蓄力 tick | 系数 | 爆炸 power | 伤害半径 | 中心伤害 | 换方块半径 | 冻伤半径 | 秒数 | 层数 |
|---|---|---|---|---|---|---|---|---|
| <4 | — | — | — | — | — | — | — | — |
| 4 | 0.20 | 3.52 | 7.0 格 | ~50 | 2 | 4 | 14 | 4 |
| 8 | 0.40 | 4.64 | 9.3 格 | ~66 | 2 | 6 | 19 | 6 |
| 13 | 0.65 | 6.04 | 12.1 格 | ~86 | 3 | 8 | 24 | 8 |
| 18 | 0.90 | 7.44 | 14.9 格 | ~105 | 4 | 9 | 30 | 9 |
| ≥20 | 1.00 | 8.00 | 16.0 格 | ~113 | 4 | 10 | 32 | 10 |

> 爆炸那一项要理解一件事：原版把**伤害与波及范围绑在同一个 power 上**（伤害半径 = power × 2，
> 中心满暴露伤害 ≈ 7 × power × 2），所以蓄力变长时两者一起变大——想要"范围变大但伤害不变"
> 用原版爆炸是做不到的。对照：TNT 是 power 4 → 半径 8 格、中心约 57 点；最低蓄力比 TNT 略弱，
> 满蓄力正好是它的两倍。

是消耗品，每次发射扣 1 个，可堆叠 16。

> ⚠️ 它替换的是**所有**可破坏方块（基岩等不可破坏的除外）——包括箱子、矿石与本 mod 自己的流体，且**不掉落任何物品**（箱子里的东西会一起消失）。这是需求里"所有方块被替换"的直接后果，用之前请知悉。

改动前建议先读这几处的注释，它们记录了不显然的约束：`SporeFluidType`（流体视野效果的契约）、`ModFluids`（为什么不入 `fluids/water` 标签）、`ColdEffects`（寒冷效果为什么必须由实体自己的 tick 施加）、`FrostNovaEntity`（为什么必须自己封一个寿命）、`FrostNovaCloudEntity`（为什么不用现成的 `AreaEffectCloud`）、`FrostMoteParticle`（粒子为什么必须覆写 `getRenderType`）、`SporeAddConfig`（配置值为什么不能放进静态字段）。

## 配置

首次启动会生成 `config/spore_add-common.toml`。为什么是 `COMMON` 而不是 `SERVER`，见 `SporeAddConfig` 的类注释。

| 段 | 键 | 默认 | 含义 |
|---|---|---|---|
| `frostNova` | `blockRadius` | 4 | 满蓄力时换方块的球半径（1–16） |
| | `entityRadius` | 10 | 满蓄力时施加冻伤的球半径（1–32） |
| | `explosionPower` | 8.0 | 满蓄力时的爆炸强度，伤害与范围都随之变（原版 TNT 是 4.0，0–32） |
| | `frostbiteSeconds` | 32 | 满蓄力时冻伤持续秒数 |
| | `frostbiteLevel` | 10 | 满蓄力时的冻伤**显示层数**（= amplifier + 1，上限 127） |
| | `minPowerFraction` | 0.30 | 最低蓄力时的威力系数 |
| | `chargeTicks` / `minChargeTicks` | 20 / 4 | 拉满所需 tick / 低于它就不发射 |
| | `autoDetonateSeconds` | 5 | 弹体未命中时的自动引爆秒数；1.5 格/tick × 5 秒 ≈ 150 格射程（1–60） |
| | `secondaryDelaySeconds` | 20 | 冰球二次引爆的延时秒数（1–600） |
| | `secondaryCloudSeconds` | 20 | 二次爆炸霜雾的持续秒数（1–600） |
| | `secondaryRangeMultiplier` | 1.5 | 二次爆炸的**范围**倍率；只作用于冰雾与冻伤半径（1.0–4.0） |
| | `secondaryPowerMultiplier` | 1.5 | 二次爆炸的**冻伤强度**倍率，层数与秒数一起乘（1.0–5.0） |
| `liquidCold` | `radius` | 6 | 区域寒冷、冰扩散、冰不融化共用的半径（1–16）。上限 16 是冰扩散的取样密度决定的，见该键的注释 |

## 环境要求

| 项目 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x（`loaderVersion="[47,)"`，开发用 47.4.10） |
| Java | 17 |
| 映射 | Parchment `2023.08.20-1.20.1`（叠加官方映射） |
| Spore（真菌感染：孢子） | 2.2.0j（CurseMaven file id `8342816`）/ `[2.2.0,)`（声明，**必需**） |
| JEI | 15.56.0.205 / `*`（声明，**可选**，仅在客户端） |

## 目录结构

```
src/main/java/com/frnc/spore_add/
├── SporeAdd.java       # 主入口（@Mod）：登记各注册表、注册配置与进度判据
├── SporeAddConfig.java # 配置（冰霜新星的威力参数、液态寒冷半径）
├── fluid/              # 三种流体的 FluidType 与静置/流动变体
├── block/              # 三种流体方块（各自带接触效果）
├── item/               # 三个桶 + 冰霜新星 + 本 mod 的创造模式页签
├── entity/             # 冰霜新星的弹体与粒子云，及其注册表
├── particle/           # 三个霜冻粒子的类型注册表
├── effect/             # 可燃/爆燃/冻伤的等级记账与伤害算式
├── enchantment/        # 「烈阳」附魔与其"是否穿戴"的唯一判定
├── advancement/        # 自定义进度判据（装上烈阳）
├── event/              # Forge 事件处理
├── network/            # 等级同步包（服务端 → 客户端显示）
├── client/             # 客户端：阿拉伯数字等级、流体滤镜与雾、弹体渲染器与蓄力属性
│   └── particle/       # 客户端：霜冻粒子的渲染实现
├── compat/             # Spore 的唯一引用点
├── world/              # 液态寒冷的影响范围登记（冰不融化也用它）、冰霜新星的落点爆发
└── mixin/              # mixin 包，对应 spore_add.mixins.json 的 package
src/main/resources/
├── META-INF/mods.toml     # 模组元数据（modId/版本/作者/依赖）
├── pack.mcmeta            # 资源包描述
├── spore_add.mixins.json  # mixin 配置（通用 6 个 + 客户端 3 个）
├── assets/spore_add/      # 贴图、模型、方块状态、粒子定义、语言文件
└── data/spore_add/        # 两个进度：绝对零度（根）、永恒炽阳
```

冰霜新星的物品贴图（`textures/item/frost_nova*.png`，4 张，16×16）是脚本逐像素生成的一次性产物，
生成脚本没有入库——要改配色或形状直接改那 4 张 PNG 即可。

粒子贴图（`textures/particle/`，3 张，32×32）来自第三方素材库，**不是本工程原创**，
署名与 MIT 许可全文见 `META-INF/THIRD-PARTY-NOTICES.txt`。

> 那份说明刻意放在 `META-INF/` 而不是贴图旁边：资源包加载器会扫描 `textures/` 下的**每一个**文件，
> 遇到非图片会报 `Invalid path in pack` 的 ERROR（放手边试过，确实会报）。

## 第三方素材

| 素材 | 来源 | 许可 |
|---|---|---|
| `textures/particle/frost_mist.png`、`frost_snowflake.png`、`frost_shard.png` | [Iron-Elden-Ring-Particle-library](https://github.com/shuimo0413/Iron-Elden-Ring-Particle-library)（「Iron 的法术与魔法书：艾尔登法环」粒子库） | MIT，© 2026 shuimo0413 |

按 MIT 的要求，版权与许可声明随这三张图一起放在 jar 内，见上表提到的 `THIRD-PARTY-NOTICES.txt`。

## 构建

```bash
./gradlew build       # 产物在 build/libs/
./gradlew runClient   # 开发环境启动客户端
./gradlew runServer   # 开发环境启动服务端
./gradlew runData     # 数据生成（目前没有 DataProvider，仅占位）
```

首次构建会下载并反编译 Minecraft 与两个依赖（Spore 的 jar 约 116 MB），耗时较长；后续增量构建约十几秒。

## 依赖说明

两个依赖的声明写在 `build.gradle`，版本号统一放在 `gradle.properties`：

| 依赖 | 声明方式 | 为什么 |
|---|---|---|
| Spore | `implementation fg.deobf("curse.maven:fungal-infection-spore-678295:8342816")` | 只在 CurseForge 发布，走 CurseMaven；玩法围绕它的机制，需要编译期可见 |
| JEI | `compileOnly`(forge-api + common-api) + `runtimeOnly`(完整 jar) | 只是可选兼容，不打包、不强制玩家安装 |

`mods.toml` 里对应的声明：Spore 为 `mandatory=true`（代码直接引用它的类，缺了会 `NoClassDefFoundError`）；JEI 为 `mandatory=false`（可选前置，装了才启用对应内容）。

`build.gradle` 新增的仓库源：JEI（`dvs1.progwml6.com` / `maven.blamejared.com` / `modmaven.dev`）、CurseMaven。CurseMaven 用 `content { includeGroup "curse.maven" }` 过滤而不是 `exclusiveContent`——后者会让 ForgeGradle 的本地 remap 仓库无法提供 `*_mapped_*` 产物，配合 `fg.deobf` 解析会失败（同仓库 `maid` 工程踩过这个坑）。

升级依赖时改 `gradle.properties` 里的 `jei_version` / `spore_file_id`，并同步 `spore_version_range` 与 `src/main/resources/META-INF/mods.toml`。

## 开发约定

- **命名空间**：一切注册内容都挂在 `spore_add` 下，Java 里用 `SporeAdd.id("xxx")` 生成 `ResourceLocation`。
- **注册内容**：每个功能子包（`fluid` / `block` / `item` / `effect` / `enchantment`）自己持有 `DeferredRegister` 并提供一个 `register(IEventBus)`，由 `SporeAdd` 的构造函数逐个调用。
- **事件**：`MinecraftForge.EVENT_BUS.register(this)` 已在主类里接好，直接加 `@SubscribeEvent` 方法即可。
- **配置**：`SporeAddConfig` 用 `ForgeConfigSpec`（`COMMON` 类型），生成 `config/spore_add-common.toml`。新增可调项加在那里，并且**取值只放在访问器的方法体里**，不要写成 `static final` 字段——配置的加载时机晚于方块/物品的构造。另外注意 `gradle.properties` 被 Gradle 以 ISO-8859-1 读取，中文别放那里（配置的注释写在 Java 源码里，没这个问题）。
- **mixin**：MixinGradle 管线（含 refmap 与开发环境 `mixin.env.remapRefMap`）已接好，现有 9 个 mixin。新增时把类名登记进 `spore_add.mixins.json` 的 `"mixins"`（通用）或 `"client"`（仅客户端）列表——**漏登记的典型症状是"编译进 jar 但运行时不生效"**。客户端专用的混入必须放进 `client` 列表，否则服务端加载到只存在于客户端的类会直接崩。
- **Access Transformer**：需要公开原版私有成员时，在 `build.gradle` 里取消 `accessTransformer = file(...)` 的注释并新建 `src/main/resources/META-INF/accesstransformer.cfg`（保持纯 ASCII）。

## 许可证

[MIT](LICENSE)
