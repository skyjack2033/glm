我已把代码审查项按根因核对过，计划按“先修确定破坏运行时语义的问题，再修兼容/一致性问题”的顺序处理。当前分支使用 `GT5-Unofficial 5.09.52.594`，这个版本没有新版 `IOutputHatchTransaction` 路径，所以我不会硬加会破坏 build 的流体 transaction 接口；流体侧先修当前依赖下真实存在的挂载归属和清理问题。

**实施计划**

1. **修复物品输出 transaction 的 commit 语义**
   - 修改 `src/main/java/com/github/skyjack2033/wirelessmehatch/output/WirelessItemOutputTransaction.java`。
   - 把当前 `storePartial()` 里直接调用 `core.storeItem(stack, false)` 的真实写入改成 transaction 内部缓冲。
   - 用 `Map<GTUtility.ItemId, Long>` 和 `bufferedTotal` 记录本次 transaction 已接受物品。
   - `hasAvailableSpace()` 用 `core.getItemCached() + bufferedTotal < core.getItemCapacity()` 判断。
   - `storePartial()` 只减少传入 `ItemStack.stackSize` 并写入缓冲，不改 `WirelessUnifiedOutputCore`。
   - `commit()` 再按 `Integer.MAX_VALUE` 分块调用 `core.storeItem(..., false)` 写入真实无线缓存，并防止重复 commit。
   - 目标：恢复 GT 输出 transaction 的原子语义，避免 recipe check、void protection 或未提交 transaction 污染无线缓存。

2. **应用默认容量配置，并让 NBT 读取保持合理 fallback**
   - 修改 `WirelessUnifiedOutputCore`，新增可注入默认 item/fluid capacity 的构造路径，避免默认永远是 `Long.MAX_VALUE`。
   - 修改 `MTEWirelessUnifiedOutputAssemblyME` 两个构造函数，传入 `Config.defaultItemCapacity` 和 `Config.defaultFluidCapacity`。
   - 调整 `WirelessUnifiedOutputCore.readFromNBT()`：当 tag 中没有 capacity key 时保留构造时默认值，而不是重置为 `Long.MAX_VALUE`。
   - 目标：README 和配置文件暴露的 `defaultItemCapacity/defaultFluidCapacity` 实际生效。

3. **补旧输出仓 NBT 的最小迁移路径**
   - 修改 `WirelessLinkManager.readFromNBT()`，当新 `wirelessTarget` 不存在但旧顶层 `bound/ctrlDim/ctrlX/ctrlY/ctrlZ` 存在时，构造一个 `ME_CONTROLLER` 类型的 `WirelessLinkTarget`。
   - 修改 `WirelessUnifiedOutputCore`，增加读取旧 `itemProvider` / `fluidProvider` cache 格式的方法：读取旧 `cache.eN.item/fluid + amount`，转换到当前 `itemCache/fluidCache`。
   - 修改 `MTEWirelessUnifiedOutputAssemblyME.loadNBTData()`：优先读取新 `wirelessOutput`；如果不存在，再尝试旧 provider cache 迁移。
   - 目标：旧 `17001` 输出仓升级为统一输出总成时，尽量保留绑定坐标和缓存内容。

4. **处理旧输入仓 ID 的存档风险**
   - 保留新统一输出总成默认使用旧输出仓 ID `17001`。
   - 增加一个低行为的 legacy input placeholder 注册到旧输入仓 ID `17002`，用于旧存档加载时避免空 MTE/无效方块；它不恢复已删除的无线输入功能，只明确显示该旧输入仓已废弃并需要替换。
   - 新增或恢复 `Config.legacyWirelessInputHatchMeId`，默认 `17002`，并尽量兼容旧配置项 `wirelessInputHatchMeId`。
   - 如果当前 GT 基类构造签名使 placeholder 代价明显扩大，我会先实现配置兼容和文档说明，不重引入旧输入逻辑。

5. **修复流体 delegate 挂到错误控制器和 stale attachment 的风险**
   - 修改 `MultiblockOutputAttachment`。
   - 构造时传入 owner bus，也就是 `MTEWirelessUnifiedOutputAssemblyME` 自身。
   - 扫描附近 controller 时，不再选择第一个 `MTEMultiBlockBase`，而是反射读取 controller 的 `mOutputBusses`，只选择已经包含当前 assembly 的 controller。
   - 当 controller 变化、找不到匹配 controller、或 assembly 移除时，从旧 controller 的 `mOutputHatches` 移除 delegate。
   - 修改 `MTEWirelessUnifiedOutputAssemblyME` 构造和 `onRemoval()` 配合新的 attachment 生命周期。
   - 目标：避免附近多个多方块时流体 delegate 被插到错误机器，也避免结构重建后旧 controller 残留 delegate。

6. **修复 Security Terminal 记录路径**
   - 修改 `ItemWirelessLinkTool`。
   - 增加 `onItemUseFirst()`，在 AE2 Security Terminal 打开 GUI 前优先处理 link tool 对支持目标的记录/绑定。
   - 抽出共享的 block-use helper，避免 `onItemUseFirst()` 和 `onItemUse()` 重复发消息或重复写 NBT。
   - 保留潜行清空目标行为，但普通右键 Security Terminal 应能记录目标。
   - 目标：README 中“右键 ME Controller 或 Security Terminal”的流程实际可用。

7. **修复资源和元数据不一致**
   - 添加 `src/main/resources/assets/wirelessmehatch/textures/items/wireless_link_tool.png`，避免 link tool missing texture。
   - 更新 `src/main/resources/mcmod.info`，移除旧 Memory Card / Wireless Access Point / input-output hatch 描述，改为当前 Wireless Link Tool 和 unified output assembly 流程。
   - 检查 `en_US.lang` / `zh_CN.lang`，补 legacy placeholder 名称和必要提示文本。

8. **验证**
   - 先运行 `./gradlew compileJava`，确认接口签名和 GT 版本兼容。
   - 再运行 `./gradlew build`，确认资源打包和整体构建通过。
   - 用 `rg` 复查旧运行时引用，确保旧 Memory Card/WAP/已删除类引用只剩文档或明确 legacy 兼容说明。
   - 复查 `git diff --stat` 和关键 diff，确认没有改动无关文件。

**预期边界**

- 不恢复已删除的无线输入功能；只做旧输入 ID 的加载保护/提示，避免旧存档直接坏掉。
- 不在当前 GT `5.09.52.594` 依赖下添加新版 `IOutputHatchTransaction` 流体事务代码，因为当前依赖缺少该接口。若之后把 GT 依赖升回包含 `IOutputHatchTransaction` 的版本，再单独补流体 transaction。
- 不提交 git commit，除非你之后明确要求提交。