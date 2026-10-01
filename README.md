# Spore Add

面向 Minecraft **1.20.1 / Forge** 的模组：围绕 [Fungal Infection: Spore](https://modrinth.com/mod/fungal-infectionspore)（真菌感染：孢子）的真菌机制做修改与新增。

> 当前状态：**已有三块内容可用**——三种流体、两条 buff 链（可燃→爆燃 / 冻伤）、一个附魔与两个进度；尚未实机验证。

## 已实现内容

**三种流体**（各有自己的 `FluidType`、静置/流动变体、方块与桶）

| 流体 | 接触/范围内的效果 |
|---|---|
| 冷却液 | 像细雪一样失温，并逐秒累积 Spore 的「冻伤」（**封顶 10 层**） |
| 液态寒冷 | 泡在里面**或**身处半径 6 球形范围内都会失温并叠冻伤（**无上限**）；放下后立刻冻住接触的空气与流体，并向半径 6 内逐秒扩散生成冰，**内层蓝冰 / 中层浮冰 / 外层冰**，且范围内的原版冰不会融化 |
| 高能燃料 | 累积 Spore 的「可燃」（每秒 1 级，离开 10 秒后归零） |

**两条 buff 链**

- **可燃 → 爆燃**：高能燃料与 Spore 的焦油池都会给可燃；可燃被 Spore 触发时转化为「爆燃」，层数**无上限**。每层使受到的火焰伤害 **+1**，每满 10 层再附加 **1% 最大生命值**；雨天/水里火焰会灭但爆燃保留，离开后按剩余时长重新烧起来。
- **冻伤**：层数**就是 amplifier**（因为 Spore 自己在读它：伤害、减速、抗寒闸门都用它）。冷却液封顶 10 层、液态寒冷无上限，硬上限 127 层。每层给 Spore 那份冻结伤害 **+1 点平伤**，每满 10 层再附加 **2% 最大生命值**。

**「烈阳」附魔**：1 级、宝藏附魔、与冰霜行者互斥。四格护甲任意一件带上即免疫冻伤、冻结伤害与细雪冻结；附在**靴子**上时还不会陷入细雪。装备时获得进度「永恒炽阳」（根进度是「绝对零度」）。

改动前建议先读这三处的注释，它们记录了不显然的约束：`SporeFluidType`（流体视野效果的契约）、`ModFluids`（为什么不入 `fluids/water` 标签）、`ColdEffects`（寒冷效果为什么必须由实体自己的 tick 施加）。

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
├── SporeAdd.java      # 主入口（@Mod）：登记各注册表、注册进度判据
├── fluid/             # 三种流体的 FluidType 与静置/流动变体
├── block/             # 三种流体方块（各自带接触效果）
├── item/              # 三个桶 + 本 mod 的创造模式页签
├── effect/            # 可燃/爆燃/冻伤的等级记账与伤害算式
├── enchantment/       # 「烈阳」附魔与其"是否穿戴"的唯一判定
├── advancement/       # 自定义进度判据（装上烈阳）
├── event/             # Forge 事件处理
├── network/           # 等级同步包（服务端 → 客户端显示）
├── client/            # 客户端：阿拉伯数字等级、流体滤镜与雾
├── compat/            # Spore 的唯一引用点
├── world/             # 液态寒冷的影响范围登记（冰不融化也用它）
└── mixin/             # mixin 包，对应 spore_add.mixins.json 的 package
src/main/resources/
├── META-INF/mods.toml     # 模组元数据（modId/版本/作者/依赖）
├── pack.mcmeta            # 资源包描述
├── spore_add.mixins.json  # mixin 配置（通用 6 个 + 客户端 3 个）
├── assets/spore_add/      # 贴图、模型、方块状态、语言文件
└── data/spore_add/        # 两个进度：绝对零度（根）、永恒炽阳
```

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
- **配置**：接入 Forge 配置（建议 `COMMON` 类型），生成 `config/spore_add-common.toml`。注意说明文本用英文或直接写在 `mods.toml` 里——Gradle 以 ISO-8859-1 读取 `gradle.properties`，中文放那里打包后会乱码。
- **mixin**：MixinGradle 管线（含 refmap 与开发环境 `mixin.env.remapRefMap`）已接好，现有 9 个 mixin。新增时把类名登记进 `spore_add.mixins.json` 的 `"mixins"`（通用）或 `"client"`（仅客户端）列表——**漏登记的典型症状是"编译进 jar 但运行时不生效"**。客户端专用的混入必须放进 `client` 列表，否则服务端加载到只存在于客户端的类会直接崩。
- **Access Transformer**：需要公开原版私有成员时，在 `build.gradle` 里取消 `accessTransformer = file(...)` 的注释并新建 `src/main/resources/META-INF/accesstransformer.cfg`（保持纯 ASCII）。

## 许可证

[MIT](LICENSE)
