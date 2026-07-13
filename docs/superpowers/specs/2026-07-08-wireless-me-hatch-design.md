# 无线 ME 输入/输出仓设计方案

## 概述

为 GTNH (GregTech New Horizons) 开发两个无线 ME 仓：

1. **Wireless Output Hatch ME**（无线合并输出仓）：多方块机器产物 -> 无线连接 -> ME 网络，同时处理物品和流体
2. **Wireless Input Hatch ME**（无线合并输入仓）：ME 网络 -> 无线连接 -> 多方块机器，同时拉取物品和流体

核心特性：放置在世界中后，通过绑定 AE2 无线接入点（WAP）的 serial，使用 AE2 原生的 `createGridConnection` API 建立无形连接到 ME 网络，跨维度可用，不检查距离。

最低适配 GTNH 2.9.0。

---

## 关键技术决策总结

| 决策点 | 选择 | 理由 |
|---|---|---|
| 连接机制 | `createGridConnection` 无形连接 | AE2 WAP 不支持方块级无线中继；`createGridConnection` 是 AE2 公开 API，量子链桥/P2P 用的同一套 |
| 连接目标定位 | 绑定卡（AE2 内存卡）+ WAP serial | 复用 AE2 现有物品，P2P 隧道同款 `freq` 模式 |
| 距离检查 | 不检查，跨维度可用 | 仿量子链桥模式 |
| 物品+流体合并 | 合并为一个仓 | 用户需求 |
| 输出仓合并基类 | 继承 `MTEHatchOutput` + 手动实现 `IOutputBus` | Java 单继承限制，`MTEHatchOutput`（流体）和 `MTEHatchOutputBus`（物品）是平级兄弟 |
| 输入仓合并 | 实现现有 `IDualInputHatch` 接口 | GT 原生支持，无需改控制器 |
| 输出仓多方块识别 | Mixin 扩展 `MTEMultiBlockBase` | 输出侧无 `IDualOutputHatch`，需新建接口 + Mixin |
| GUI 框架 | MUI2（`com.cleanroommc.modularui`） | GTNH 已迁移到 MUI2 |
| 容量 | 保留 cell 槽 + 最大基础容量（`Long.MAX_VALUE`） | 用户需求 |
| 仓等级 | 单一等级，不分 LV/MV/HV... | 用户需求 |

---

## 第 1 节：项目基础配置

**当前状态**：`I:\glm` 是未经修改的 GTNH ExampleMod 模板（modid=`mymodid`，无 GT/AE2 依赖）。

**配置变更**：

### 1.1 `gradle.properties` 重命名
- `modId=wirelessmehatch`
- `modGroup=com.github.skyjack2033.wirelessmehatch`
- `modName=Wireless ME Hatch`
- `generateGradleTokenClass=com.github.skyjack2033.wirelessmehatch.Tags`

### 1.2 `dependencies.gradle` 添加依赖
```groovy
dependencies {
    devOnlyNonPublishable(rfg.deobf("com.github.GTNewHorizons:GT5-Unofficial:<version>:dev"))
    devOnlyNonPublishable(rfg.deobf("com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:<version>:dev"))
}
```
版本需适配 GTNH 2.9.0。具体版本号在实现时确定。

### 1.3 Mixin 启用
- `gradle.properties`：`usesMixins = true`
- 新建 `src/main/resources/mixin.wirelessmehatch.json` Mixin 配置文件

### 1.4 `mcmod.info` 更新
- 更新依赖声明：`required-after:gregtech;appliedenergistics2`

---

## 第 2 节：无线 ME 连接机制

### 2.1 技术方案

每个无线仓持有自己的 `AENetworkProxy`（创建一个 `IGridNode`），通过 `AEApi.instance().createGridConnection(myNode, remoteNode)` 与目标 ME 网络建立**无形连接**（invisible connection）。

这是 AE2 公开 API，与量子链桥（`QuantumCluster`）/P2P ME 隧道（`PartP2PTunnelME`）用的同一套机制。连接无方向、无线缆渲染。

### 2.2 绑定流程（复用 AE2 内存卡 `ToolMemoryCard`）

1. 玩家手持 AE2 内存卡，**潜行右键** AE2 无线接入点（WAP）：
   - 通过 WAP 的 `getLocatableSerial()` 获取 serial（long）
   - 写入内存卡 NBT：`Data.wapSerial`（long），`Config` 设为标识字符串（如 `"wirelessmehatch.wap_binding"`）
   - 调用 `IMemoryCard.setMemoryCardContents()` + `notifyUser(SETTINGS_SAVED)`

2. 玩家手持记录了 serial 的内存卡，**右键**无线仓：
   - 仓的 `onRightClick` 检测手持物品 `instanceof IMemoryCard`
   - 读取 `getData().getLong("wapSerial")`
   - 存储到仓自身 NBT（`wapSerial` 字段）
   - 触发连接建立

3. 连接建立：
   - `AEApi.instance().registries().locatable().getLocatableBy(serial)` 找到目标 WAP
   - 取 `wap.getGridNode(ForgeDirection.UNKNOWN)` 获取 WAP 的 node
   - 调用 `AEApi.instance().createGridConnection(myNode, wapNode)`
   - **不检查距离**，跨维度也能连接

4. **螺丝刀右键仓**：清除绑定（`wapSerial = 0`，`connection.destroy()`）

### 2.3 连接生命周期管理

仿量子链桥的 `ConnectionWrapper` 模式：

- 持有 `IGridConnection` 引用
- 定期 tick 检查连接有效性：
  - 对端 WAP 是否仍存在（`getLocatableBy` 非 null）
  - 连接是否仍 active
- 方块 invalidate / chunk unload / 更换绑定时 `connection.destroy()`
- `createGridConnection` 可能抛 `FailedConnection`（颜色不匹配、安全限制），需 catch 并通过 GUI/聊天提示用户

### 2.4 安全系统兼容

通过 `gridNode.setPlayerID(...)` 设置放置者 ID，避免被 AE2 安全终端阻断。

### 2.5 核心类

`WirelessGridManager` - 封装连接建立/维护/断开逻辑，被两个仓复用。主要方法：
- `bind(long wapSerial)` - 绑定到指定 WAP
- `establishConnection()` - 建立 createGridConnection
- `tickCheck()` - 定期检查连接有效性
- `destroy()` - 断开连接
- `isConnected()` - 连接状态查询

---

## 第 3 节：合并仓架构与多方块识别

### 3.1 输出仓合并方案

#### 新建 `IDualOutputHatch` 接口

仿 `IDualInputHatch`（GT 输入侧已有），为输出侧创建对称接口：

```java
public interface IDualOutputHatch extends IMetaTileEntity {
    // 流体输出事务
    IOutputHatchTransaction createFluidTransaction();
    // 物品输出事务
    IOutputBusTransaction createItemTransaction();
    // 物品输出入口（IOutputBus 接口方法）
    boolean storePartial(ItemStack stack, boolean simulate);
    // 流体输出入口（IOutputHatch 相关）
    int fill(FluidStack fluid, boolean doFill);
}
```

#### 新建 `MTEWirelessOutputHatchME` 类

- **继承** `MTEHatchOutput`（获得流体能力：`mFluid`/`fill`/`IFluidHandler`）
- **实现** `IDualOutputHatch` + `IGridProxyable` + `IPowerChannelState` + `IMEConnectable` + `ICellContainer` + `IPriorityHost`
- **集成** `WirelessGridManager`（无线连接管理，通过组合而非接口）
- **持有两个 provider**（组合，非继承）：
  ```java
  private final MTEHatchOutputMEBase<IAEFluidStack> fluidProvider =
      new MTEHatchOutputMEBase<IAEFluidStack>(this, Long.MAX_VALUE) {};
  private final MTEHatchOutputMEBase<IAEItemStack> itemProvider =
      new MTEHatchOutputMEBase<IAEItemStack>(this, Long.MAX_VALUE) {};
  ```
- **物品侧手动实现** `storePartial`（委托给 `itemProvider.storePartial`）和 `IOutputBus` 相关方法
- **共享单一 `AENetworkProxy`**（两个 provider 通过 `Environment.getIGridProxyable()` 回调同一宿主）
- `getGridNode`/`getActionableNode` 返回共享 proxy 的 node
- `Environment.getNetworkInvtory()` 分别取 `getStorage().getFluidInventory()` / `getStorage().getItemInventory()`
- **无线连接**：集成 `WirelessGridManager`，实现 `IWirelessMEHatch`

#### `Environment<T>` 接口实现

两个 provider 各自实现 `MTEHatchOutputMEBase.Environment<T>`：
- `getChannel()` - 返回 `StorageChannel.FLUIDS` / `StorageChannel.ITEMS`
- `getNetworkInvtory()` - 返回对应 channel 的 `IMEInventory`
- `saveStackToNBT(T)` / `loadStackFromNBT(NBTTagCompound)` - 序列化
- `getCellStack()` - 返回对应 cell 槽的 ItemStack
- `getIGridProxyable()` - 返回 `this`（共享 proxy）
- `getActionSource()` - 返回 `MachineSource`

### 3.2 输入仓合并方案

#### 新建 `MTEHatchInputMEBase<T>` 泛型基类

仿 `MTEHatchOutputMEBase`，为输入侧创建泛型基类（原版输入仓没有泛型化，是复制粘贴的两份代码）：

```java
public abstract class MTEHatchInputMEBase<T extends IAEStack<T>> {
    public interface Environment<T extends IAEStack<T>> {
        StorageChannel getChannel();
        IMEInventory<T> getNetworkInventory() throws GridAccessException;
        AENetworkProxy getProxy();
        // ... 其他回调方法
    }
    // 统一的 getProxy / updateValidGridProxySides / IGridProxyable / IStackWatcherHost 实现
    // 统一的 updateInformationSlot / endRecipeProcessing / Slot 内部类（泛型化）
}
```

#### 新建 `MTEWirelessInputHatchME` 类

- **继承** `MTEHatchInput`（获得流体能力）
- **实现** `IDualInputHatch` + `IGridProxyable` 等（**无需改控制器**，GT 原生支持 `IDualInputHatch`）
- 持有两个 provider：`MTEHatchInputMEBase<IAEFluidStack>` + `MTEHatchInputMEBase<IAEItemStack>`
- 实现 `IDualInputHatch.inventories()` 返回同时包含流体和物品的 `IDualInputInventory` 迭代器
- 共享单一 `AENetworkProxy`
- **无线连接**：集成 `WirelessGridManager`

---

## 第 4 节：物品/流体数据流与 provider 复用

### 4.1 输出仓数据流（机器->ME）

| 组件 | 流体侧 | 物品侧 |
|---|---|---|
| 入口方法 | `fill(FluidStack, doFill)` -> `fluidProvider.storePartial(AEFluidStack, !doFill)` | `storePartial(ItemStack, simulate)` -> `itemProvider.storePartial(AEItemStack, simulate)` |
| 批量推送 | `flushCachedStack()` 每 40 tick，`Platform.poweredInsert` 到 `getNetworkInvtory()` | 同左，各自取对应 channel 的 inventory |
| cell 槽 | 流体 cell（`FCBaseItemCell`/`ItemFluidVoidStorageCell`） | 物品 cell（`ItemBasicStorageCell`/`ItemVoidStorageCell`） |
| Transaction | `createFluidTransaction()` -> 内部 `MEOutputHatchTransaction` | `createItemTransaction()` -> 内部 `MEOutputBusTransaction` |

### 4.2 输入仓数据流（ME->机器）

| 组件 | 流体侧 | 物品侧 |
|---|---|---|
| 拉取方法 | `drain(side, fluid, amount, doDrain)` -> `Platform.poweredExtraction` | `getStackInSlot`（配方期间）+ `endRecipeProcessing` -> `Platform.poweredExtraction` |
| Slot 模型 | 16 个流体配置槽（config + extracted + extractedAmount） | 16 个物品配置槽 + circuit 槽 + manual 槽 |
| 监听 | `IStackWatcherHost` 监听特定栈变化 -> `scheduleRecipeCheck` | 同左 |

### 4.3 共享 `AENetworkProxy` 设计

- 合并仓持有一个主 `AENetworkProxy`（`GridFlags.REQUIRE_CHANNEL`）
- 两个 provider 通过 `Environment.getIGridProxyable()` 回调到同一宿主的 proxy
- cell 槽扩展为按 channel 区分（流体 cell 槽 + 物品 cell 槽）
- `MTEHatchOutputMEBase` 内部多处用 `env.getCellStack()`，合并时需让 provider 知道自己对应哪个槽

### 4.4 需要的 AE2 API 接口

**网络连接**：`IGridNode`、`AENetworkProxy`、`IGridProxyable`、`GridFlags`、`AECableType`、`DimensionalCoord`、`GridAccessException`

**存储访问**：`IMEInventory`、`IMEInventoryHandler`、`IMEMonitorHandlerReceiver`、`ISaveProvider`、`StorageChannel`、`ICellContainer`、`IAEStack`/`IAEItemStack`/`IAEFluidStack`、`IStorageGrid`、`IEnergySource`/`IEnergyGrid`

**注册表/工具**：`AEApi.instance().registries().cell().getCellInventory()`、`AEApi.instance().registries().locatable().getLocatableBy()`、`AEApi.instance().createGridConnection()`、`Platform.poweredInsert()`、`Platform.poweredExtraction()`

**状态/事件/优先级**：`IPowerChannelState`、`IPriorityHost`、`MENetworkChannelsChanged`、`MENetworkCellArrayUpdate`、`BaseActionSource`/`MachineSource`、`Actionable`、`AccessRestriction`、`AEColor`

---

## 第 5 节：GUI 设计（MUI2）

使用 GregTech 的 ModularUI 2（MUI2，包名 `com.cleanroommc.modularui`）。

### 5.1 无线输出仓 GUI（`MTEWirelessOutputHatchMEGui`）

- `useMui2()` 返回 `true`
- 用 `GTGuis.mteTemplatePanelBuilder(...)` 构建标准面板
- **连接状态区**：
  - `BooleanSyncValue(isConnected)` 同步连接状态
  - `IKey.dynamic(...)` 显示已连接/未连接、绑定的 WAP serial（十六进制）
- **双 cell 槽**：两个 `ItemSlot` + `ModularSlot`
  - 流体 cell 槽：`isItemValidForSlot` 只接受 `FCBaseItemCell`/`ItemFluidVoidStorageCell`
  - 物品 cell 槽：`isItemValidForSlot` 只接受 `ItemBasicStorageCell`/`ItemVoidStorageCell`
- **缓存内容**：`GenericListSyncHandler` 同步流体/物品缓存列表
- **优先级**：`IntSyncValue` + `TextFieldWidget`
- **Cache Mode / Check Mode**：`ToggleButton` + `BooleanSyncValue`

### 5.2 无线输入仓 GUI（`MTEWirelessInputHatchMEGui`）

- **连接状态区**：同上
- **双配置槽组**：
  - `ItemSlotGridBuilder` 4x4 物品幽灵槽
  - 流体配置槽（幽灵槽，16 个）
- **circuit/manual 槽**：标准 GT 槽位
- **高级版**：`ToggleButton` auto-pull、`TextFieldWidget` 最小抽取量/刷新间隔

### 5.3 绑定交互（世界交互，不在 GUI 内）

- **内存卡潜行右键 WAP**：`MemoryCardHandler` 处理，记录 WAP 的 `getLocatableSerial()` 到内存卡 NBT `Data.wapSerial`
- **内存卡右键仓**：仓的 `onRightClick`/`onScrewdriverRightClick` 读取内存卡 NBT 的 `wapSerial` 并存储到仓 NBT
- **螺丝刀右键仓**：清除绑定（`wapSerial = 0`，`connection.destroy()`）

### 5.4 MUI2 关键 API 参考

| 用途 | 类/方法 |
|---|---|
| 面板构建 | `GTGuis.mteTemplatePanelBuilder(mte, data, syncManager, uiSettings)` |
| 动态文本 | `IKey.dynamic(Supplier<String>).asWidget()` |
| 物品槽 | `new ItemSlot().slot(new ModularSlot(handler, index))` |
| 槽位网格 | `new ItemSlotGridBuilder(handler, syncManager).size(cols, rows).build()` |
| 流体显示 | `new FluidDisplayWidget().value(IValue<FluidStack>).capacity(int)` |
| 同步值 | `new BooleanSyncValue(getter, setter)` / `new IntSyncValue(getter, setter)` |
| 切换按钮 | `new ToggleButton().value(syncValue).overlay(texture)` |
| 布局 | `Flow.col().coverChildren().childPadding(2).child(widget)` |
| 弹出面板 | `syncManager.syncedPanel("name", true, (m, h) -> createPanel())` |

---

## 第 6 节：容量规格

- **输出仓**：单一等级（不分 LV/MV/HV...），保留 cell 槽（流体 cell + 物品 cell），基础缓存容量设为 `Long.MAX_VALUE`（相当于原版插入虚空 cell 的效果）。cell 槽仍可用于过滤/分区，但不限制缓存上限
- **输入仓**：无缓存概念（与原版一致），直接从 ME 网络拉取，16 个配置槽
- **原版参考值**：物品输出总线 ME 缓存 1600，流体输出仓 ME 缓存 128000 mB，实际容量由 cell 决定

---

## 第 7 节：Mixin 扩展多方块控制器

### 7.1 目标

让所有继承 `MTEMultiBlockBase` 的多方块机器识别 `IDualOutputHatch`，使合并输出仓同时被当作流体 hatch 和物品 bus 使用。

### 7.2 背景

GT 多方块控制器 `MTEMultiBlockBase.addToMachineList()` 按具体类 `instanceof` 收集 hatch（`MTEHatchOutput`/`MTEHatchOutputBus` 等），每个分支 `return` 互斥。输入侧有 `IDualInputHatch` 接口优先检查的先例，但输出侧没有对应的 `IDualOutputHatch`。

### 7.3 Mixin 类 `MTEMultiBlockBaseMixin`

作用于 `gregtech.api.metatileentity.implementations.MTEMultiBlockBase`：

1. **新增字段**（`@Unique`）：
   ```java
   @Unique
   public List<IDualOutputHatch> mDualOutputHatches = new ArrayList<>();
   ```

2. **修改 `addToMachineList`**（`@Inject` at HEAD 或 `@Redirect`）：
   - 在 `instanceof MTEHatchOutputBus`/`MTEHatchOutput` 检查前插入 `instanceof IDualOutputHatch` 优先检查
   - 命中则加入 `mDualOutputHatches` 并 return（与 `IDualInputHatch` 模式对称）

3. **修改 `addOutputBusToMachineList`/`addOutputHatchToMachineList`**：
   - 添加 `instanceof IDualOutputHatch` 优先检查

4. **输出分发逻辑**：
   - 在遍历 `mOutputBusses` 调用 `storePartial` 的地方，额外遍历 `mDualOutputHatches` 调用其 `storePartial`
   - 在遍历 `mOutputHatches` 调用 `fill` 的地方，额外遍历 `mDualOutputHatches` 调用其 `fill`
   - 需审计 `addOutput`、`depleteInput`、`getOutputFluids` 等方法

5. **清理逻辑**：
   - 在 `clearHatches` 中清空 `mDualOutputHatches`

### 7.4 输入侧

无需 Mixin：`IDualInputHatch` 是 GT 原生支持的，控制器已有优先检查逻辑。

### 7.5 兼容性

- Mixin 作用在 `MTEMultiBlockBase` 基类，所有子类（包括 `MTEEnhancedMultiBlockBase` 和其他 addon 的多方块）自动继承修改后的行为
- 只要子类没有重写 `addToMachineList` 和输出分发方法（绝大多数没有），就会自动获得 `IDualOutputHatch` 支持

---

## 第 8 节：MetaTileEntity 注册与物品

### 8.1 注册

在 mod 的 `preInit`/`init` 阶段注册 2 个 MTE：
```java
GregTechAPI.METATILEENTITIES[id1] = new MTEWirelessOutputHatchME(id1, "wireless_output_hatch_me", "Wireless Output Hatch (ME)");
GregTechAPI.METATILEENTITIES[id2] = new MTEWirelessInputHatchME(id2, "wireless_input_hatch_me", "Wireless Input Hatch (ME)");
```

### 8.2 ID 分配

使用 GTNH addon 推荐的 ID 范围（避免与 GT 原版 ID 2710-2718 和其他 addon 冲突）。具体范围在实现时查询 GTNH addon ID 分配表确定。

### 8.3 物品/方块

GT 自动为注册的 MTE 生成物品和方块（通过 `GT_Block_Machines`），不需要手动注册方块。

### 8.4 纹理

制作仓的方块纹理和 GUI 图标，风格与原版 ME 仓一致但有无线信号标识区分。

### 8.5 合成配方

在 `postInit` 阶段注册：
- 无线输出仓 = ME 输出仓 + ME 输出总线 + AE2 量子链桥相关材料（如量子纠缠奇点）+ 无线相关组件
- 无线输入仓 = ME 输入仓 + ME 输入总线 + 类似材料

---

## 第 9 节：文件结构

```
src/main/java/com/github/skyjack2033/wirelessmehatch/
├── WirelessMEHatch.java                    # @Mod 入口类
├── CommonProxy.java / ClientProxy.java     # 代理
├── Config.java                              # 配置
├── api/
│   └── IDualOutputHatch.java               # 双输出 hatch 接口
├── metatileentity/
│   ├── MTEWirelessOutputHatchME.java       # 无线合并输出仓
│   └── MTEWirelessInputHatchME.java        # 无线合并输入仓
├── me/
│   ├── WirelessGridManager.java            # 无线连接管理（createGridConnection 生命周期）
│   ├── MTEHatchInputMEBase.java            # 输入侧泛型基类
│   └── MemoryCardHandler.java              # 内存卡绑定逻辑
├── gui/
│   ├── MTEWirelessOutputHatchMEGui.java    # 输出仓 GUI (MUI2)
│   └── MTEWirelessInputHatchMEGui.java     # 输入仓 GUI (MUI2)
├── loader/
│   └── MetaTileEntityLoader.java           # MTE 注册
├── recipe/
│   └── RecipeLoader.java                   # 合成配方注册
└── mixin/
    └── MTEMultiBlockBaseMixin.java          # Mixin 扩展控制器

src/main/resources/
├── mcmod.info                               # 模组元数据
├── mixin.wirelessmehatch.json               # Mixin 配置
└── assets/wirelessmehatch/textures/         # 纹理资源
```

---

## 测试策略

GTNH 模组无单元测试框架，依赖游戏内验证：

1. **构建验证**：`./gradlew build` 成功
2. **Mixin 验证**：游戏启动无 Mixin 报错，`MTEMultiBlockBase` 被正确修改
3. **功能验证**（游戏内）：
   - 绑定流程：内存卡记录 WAP serial -> 绑定到仓 -> 连接状态正确显示
   - 输出仓：多方块产物（物品+流体）通过无线连接进入 ME 网络
   - 输入仓：ME 网络物品+流体被多方块配方正确消耗
   - 跨维度连接：仓与 WAP 在不同维度仍正常工作
   - 多方块识别：所有继承 `MTEMultiBlockBase` 的机器都能识别合并仓
   - 连接生命周期：方块拆除/对端消失时连接正确断开，无幽灵连接
   - cell 槽过滤：插入不同类型 cell 后过滤行为正确
   - 安全系统：放置者 ID 正确设置，不被安全终端阻断

---

## 实现步骤概览

1. 项目基础配置（重命名 modId/modGroup、添加 GT/AE2 依赖、启用 Mixin）
2. 新建 `IDualOutputHatch` 接口
3. 实现 `WirelessGridManager`（无线连接核心：createGridConnection 生命周期管理）
4. 实现 `MemoryCardHandler`（内存卡绑定 WAP serial）
5. 实现 `MTEWirelessOutputHatchME`（输出仓，复用 `MTEHatchOutputMEBase` 双 provider）
6. 实现 `MTEHatchInputMEBase<T>` 泛型基类 + `MTEWirelessInputHatchME`（输入仓）
7. 实现 Mixin `MTEMultiBlockBaseMixin`（扩展 addToMachineList + 输出分发）
8. 实现 GUI（MUI2：输出仓 GUI + 输入仓 GUI）
9. 注册 MTE + 合成配方
10. 纹理资源制作
11. 构建验证 + 游戏内测试
