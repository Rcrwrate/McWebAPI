## AE2 核心架构

### 包结构

```
appeng/
├── api/              # 公开API接口（IAppEngApi, IGridNode, IGridHost, IStorageGrid...）
├── core/             # 核心功能（AppEng主类、Api实现、Registration注册、AEConfig配置）
├── me/               # ME网络系统实现（Grid, GridNode, GridConnection）
├── helpers/          # 辅助类（DualityInterface, IInterfaceHost, ICustomNameObject）
├── tile/             # TileEntity实现（AEBaseTile, AENetworkInvTile, TileInterface...）
├── block/            # 方块实现（AEBaseBlock, AEBaseTileBlock, BlockCableBus）
├── parts/            # 部件系统（CableBusContainer, PartInterface, 各总线/终端）
├── fmp/              # ForgeMultipart集成（CableBusPart, PartRegistry）
├── container/        # GUI容器
├── crafting/         # 自动合成（CraftingJob, CraftingTreeNode, CraftingLink）
└── util/             # 工具类（InventoryAdaptor, Platform, ConfigManager）
```

### 方块与 TileEntity 层次

```
Block → AEBaseBlock → AEBaseTileBlock (带TileEntity)
                        ├── AEDecorativeBlock (装饰)
                        └── AEBaseSlabBlock (半砖)

TileEntity → AEBaseTile (基类，提供坐标/世界/NBT/更新等)
               ├── AENetworkTile (IGridHost + AENetworkProxy)
               │     └── AENetworkInvTile (带物品栏)
               │           └── TileInterface, TileCableBus...
               └── 独立实现 IGridHost 的 Tile
```

| 基类 | 用途 | TileEntity |
|------|------|------------|
| `AEBaseBlock` | 简单方块 | 否 |
| `AEBaseTileBlock` | 复杂方块 | 是 |
| `AEDecorativeBlock` | 装饰方块 | 否 |

---

## ME 网络

### 核心组件

```
IGrid (整个网络的实例)
  ├── IGridNode (单个节点，每设备一个)
  │     └── IGridHost (节点的拥有者，即方块/部件)
  └── GridConnection (节点间的物理连接)
```

### 网格缓存系统

通过 `grid.getCache(Class)` 访问，每种缓存负责不同网络功能：

| 缓存 | 职责 | 常用方法 |
|------|------|----------|
| `IStorageGrid` | 存储 | `getItemInventory()`, `getFluidInventory()` |
| `ICraftingGrid` | 合成 | `beginCraftingJob()`, `getCpus()` |
| `IEnergyGrid` | 能量 | `getEnergyStored()`, `getMaxStoredPower()` |
| `IPathingGrid` | 路径 | `getControllerState()`, `isNetworkBooting()` |
| `ITickManager` | Tick | `wakeDevice()`, `sleepDevice()` |
| `ISecurityGrid` | 安全 | 权限管理 |

### 通道规则

- 智能线缆：8 通道/线
- 致密线缆：32 通道/线
- 每控制器面最多 32 通道
- P2P 隧道可跨维度/远距离

### 网络事件

```java
@MENetworkEventSubscribe
public void onPowerChange(MENetworkPowerStatusChange event) { ... }
@MENetworkEventSubscribe
public void onChannelChange(MENetworkChannelChanged event) { ... }
```

常见事件：`MENetworkPowerStatusChange`, `MENetworkChannelChanged`, `MENetworkCraftingPatternChange`

---

## 创建网络方块

### 基本 TileEntity

```java
public class TileCustomMachine extends AEBaseTile implements IGridHost {
    private IGridNode node;

    @Override
    public IGridNode getGridNode(ForgeDirection dir) { return node; }

    @Override
    public void gridChanged() { markForUpdate(); }

    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) { return AECableType.SMART; }

    @Override
    public void securityBreak() {}
}
```

### 使用 Duality 模式

复杂功能拆分为 Duality 类（如 `DualityInterface`），TileEntity/Part 只做委托：

```java
public class TileCustomInterface extends AENetworkInvTile implements IInterfaceHost {
    private final DualityInterface duality = new DualityInterface(this.getProxy(), this);

    @Override
    public DualityInterface getInterfaceDuality() { return this.duality; }
    // 所有 IInterfaceHost default 方法自动委托给 duality
}
```

### 配置管理

```java
ConfigManager cm = new ConfigManager(this);
cm.registerSetting(Settings.BLOCKING, YesNo.NO);
cm.registerSetting(Settings.INTERFACE_TERMINAL, YesNo.YES);
```

### 开发要点

1. 继承 `AEBaseTile` 获取网络/能量/渲染基础功能
2. 实现需要的接口：`IGridHost`, `IInterfaceHost`, `IStorageMonitorable` 等
3. 在 `onChunkUnload` 中 `node.destroy()`，避免泄漏
4. 使用 `Duality` 模式拆分复杂逻辑
5. 事件驱动，不要轮询
6. 捕获 `GridAccessException`，网络随时可能断开
