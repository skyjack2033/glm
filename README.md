<p align="right"><a href="./README.en.md">English</a></p>

<div align="center">
  <h1>Wireless ME Hatch</h1>
  <p>GregTech 多方块的无线 ME 统一输出总成</p>
  <p>
    <a href="https://github.com/skyjack2033/glm/actions/workflows/build-and-test.yml"><img alt="CI" src="https://github.com/skyjack2033/glm/actions/workflows/build-and-test.yml/badge.svg?branch=master"></a>
    <a href="https://github.com/skyjack2033/glm/releases"><img alt="Release" src="https://img.shields.io/github/v/release/skyjack2033/glm"></a>
    <a href="./LICENSE"><img alt="MIT License" src="https://img.shields.io/github/license/skyjack2033/glm"></a>
  </p>
</div>

> [!IMPORTANT]
> Wireless ME Hatch 是非官方 GTNH 社区模组，不隶属于 GTNH，也不由 GTNH 团队维护。安装、兼容性与运行问题请在本仓库的 [Issues](https://github.com/skyjack2033/glm/issues) 反馈，不要转交 GTNH 官方支持渠道。

## 功能概览

- 无线统一输出总成是一个真实的 `MTEHatchOutput`，同时直接实现 `IOutputBus`。同一个有效的 MetaTileEntity 同时承担流体输出仓和物品输出总线职责，没有隐藏 delegate，也没有第二个 MTE。
- 精确描述符 Mixin 将该实例接入普通 GT 多方块和 GT++ 蒸汽多方块的原生输出路径；物品输出、流体输出与多流体并行仍由相应的 GT 路径处理。
- 无线链接工具记录明确的 AE2 ME Controller 或 Security Terminal，再把总成绑定到该目标网络；实现不会全局发现玩家的 ME 网络。

## 行为与限制

- 总成是纯输出设备；模组不注册输入仓或旧输入仓占位 MTE。
- 物品使用一个跨所有物品类型的总缓存容量；流体使用一个由所有流体类型共享的总缓存容量，而不是每种流体各有一份容量。
- 多流体配方的并行数按所有流体输出的合计需求与共享流体剩余容量计算，并受配方 `maxParallel` 限制。
- 网络断开、ME 空间不足或插入所需能量不足时，未插入的物品与流体会留在持久缓存中；网络恢复后会自动重试。容量和缓存随方块 NBT 保存。
- 无线代理仍需要 AE2 供电、频道和安全权限。总成没有物理线缆连接面，不能用 ME 线缆直接接入。
- `Bound` 只表示目标记录存在；绑定时的绿色聊天消息也不表示已经 `Connected`。目标区块未加载、无电、无可用频道或安全校验失败时，目标保持 `Bound`，连接状态为 `Disconnected`。
- 青色 Wi-Fi 的 `ACTIVE` 状态只表示连接存在且代理处于 active 状态，不表示 ME 网络仍有可用存储空间。

## 兼容性与依赖

| 组件 | 精确版本 |
| --- | --- |
| Minecraft | `1.7.10` |
| Minecraft Forge | `10.13.4.1614` |
| GTNH 测试环境 | `2.9.0 beta test environment` |
| GT5-Unofficial | `5.09.52.594` |
| Applied Energistics 2 | `rv3-beta-977-GTNH` |

这些版本是当前开发和验证基线；本文档不把兼容性外推到任何更旧版本。

## 下载与安装

| 渠道 | 更新规则 | 用途 |
| --- | --- | --- |
| [正式 Release](https://github.com/skyjack2033/glm/releases) | 推送 `v*` 标签时创建 | 稳定使用与可复现版本 |
| [`dev-build` prerelease](https://github.com/skyjack2033/glm/releases/tag/dev-build) | `master` 每次成功构建后更新 | 测试最新开发状态 |

1. 备份世界和实例配置。
2. 从 Release 资产或本地 `build/libs/` 中只选择唯一一个文件名不含 `-dev`、`-sources` 或 `-api` 的 production JAR。
3. 在客户端和服务端的 `mods/` 目录都安装同一个 production JAR。
4. 删除或移走旧版、重复的 Wireless ME Hatch JAR，避免同一模组被重复加载，再启动游戏。

## 绑定与解绑

1. 具有目标 ME 网络权限的玩家手持 Wireless Link Tool，右键 ME Controller 或 Security Terminal，将目标记录到工具中。
2. 使用同一个工具右键 Wireless Unified Output Assembly，将工具内的目标绑定到总成。
3. 连接状态最多约每 1 秒（20 tick）刷新一次；绑定记录会在目标暂时不可用时保留并继续重试。
4. 潜行右键任意方块会清除工具内的目标，但不会解绑已经绑定的总成。
5. 使用螺丝刀右键总成会清除总成的目标并解绑。

如果扫描器显示 `Bound` + `Disconnected`，依次检查目标区块是否已加载、AE2 网络供电、可用频道，以及记录目标的玩家是否具有该网络的安全权限。

## 状态图标

| 状态 | 图标 | 含义 |
| --- | --- | --- |
| `ACTIVE` | <img src="src/main/resources/assets/wirelessmehatch/textures/blocks/iconsets/OVERLAY_WIRELESS_UNIFIED_OUTPUT_ASSEMBLY_ACTIVE.png" width="32" height="32" alt="ACTIVE cyan Wi-Fi"> | 青色 Wi-Fi；仅当无线连接存在且 AE2 代理 active 时显示。 |
| 非 `ACTIVE` | <img src="src/main/resources/assets/wirelessmehatch/textures/blocks/iconsets/OVERLAY_WIRELESS_UNIFIED_OUTPUT_ASSEMBLY.png" width="32" height="32" alt="Inactive red X"> | 红色 X；表示当前没有可用的 active connection。 |

扫描器信息显示物品缓存/容量、流体缓存/容量、`Bound`/`Unbound` 和 `Connected`/`Disconnected`。状态图标约每 20 tick 更新一次；青色 Wi-Fi 不保证 ME 中还有可插入空间。

## 配置

首次启动后配置文件位于 `config/wirelessmehatch.cfg`。

| 配置项 | 默认值 | 范围或单位 | 说明 |
| --- | --- | --- | --- |
| `metaTileEntityIds.wirelessUnifiedOutputAssemblyMeId` | `31701` | `17000..32000` | Wireless Unified Output Assembly 的 MetaTileEntity ID。 |
| `outputCapacity.defaultItemCapacity` | `9223372036854775807` | item | 新放置总成的物品总缓存容量。 |
| `outputCapacity.defaultFluidCapacity` | `9223372036854775807` | mB | 新放置总成的共享流体总缓存容量。 |

旧配置中的 `legacyWirelessInputHatchMeId` 和 `wirelessInputHatchMeId` 已不再读取，可以删除。升级前请先备份存档，并在旧版本中移除所有旧无线输入仓；本版本不再为其保留 `17002`。已有配置不会自动改写统一输出总成的 ID，以免破坏存档；若要修改该 ID，请先移除使用旧 ID 的总成。容量默认值只影响新放置的总成；已保存方块会从 NBT 恢复各自的 `long` 容量和缓存。

## 构建与验证

| 阶段 | 工具 | JVM 或产物目标 |
| --- | --- | --- |
| 构建 | Gradle `9.4.0`、gtnhgradle `2.0.26` | 构建 JVM 为 JDK `25` |
| 开发客户端 | `runClient17` | 使用 Java `17` toolchain |
| production JAR | Jabel | Java `8` class file major `52` |

POSIX 完整验证命令：

```bash
./gradlew clean spotlessJavaCheck test build --rerun-tasks --console=plain
```

Windows 完整验证命令：

```powershell
.\gradlew.bat clean spotlessJavaCheck test build --rerun-tasks --console=plain
```

启动 Java 17 开发客户端：

```bash
./gradlew runClient17
```

```powershell
.\gradlew.bat runClient17
```

CI 覆盖所有临时分支的 push、面向 `master` 的 pull request，以及手动触发。CI 使用同一套完整验证任务。

## 技术文档

- [无线统一输出总成架构](docs/architecture/wireless-unified-output-assembly.md)
- [GTNH 2.9.0 运行时 API 报告](docs/architecture/gtnh-290-runtime-api-report.md)
- [仓库展示与自动化契约](docs/architecture/repository-presentation-and-automation.md)

## 贡献

1. 从 `master` 创建一个临时功能分支。
2. 在该分支提交变更并等待分支 CI 通过。
3. 创建面向 `master` 的 pull request。
4. 审查和 CI 均通过后合并，然后删除本地与远程临时分支。

`master` 是唯一长期分支。只有 `master` 成功构建会更新移动的 `dev-build` prerelease；只有 `v*` 标签会创建正式 Release。

## 许可证与致谢

本项目采用 [MIT License](LICENSE)，Copyright (c) 2026 skyjack2033。

感谢 GTNH、GT5-Unofficial、Applied Energistics 2、TST、GTNL 和 GTNH Actions 项目及其贡献者提供运行环境、实现参考和构建基础设施。
