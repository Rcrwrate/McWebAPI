# AE2 核心架构指南

## 概述

Applied Energistics 2 (AE2) 是一个模块化的 Minecraft 模组，核心架构围绕自动化存储、物流和合成系统设计。本指南介绍 AE2 的核心架构和关键组件。

## 核心包结构

```
appeng/
├── api/              # 公开API接口
├── core/             # 核心功能（配置、注册、API实现）
├── me/               # ME网络系统实现
├── helpers/          # 辅助类和工具
├── tile/             # TileEntity实现
├── block/            # 方块实现
├── parts/            # 部件（FMP）系统
├── container/        # GUI容器
├── crafting/         # 自动合成系统
├── network/          # 网络通信
└── util/             # 工具类
```

## 关键架构组件

### 1. API 层 (`appeng.api`)

AE2 提供了一套完整的 API，允许其他模组与 AE 网络交互：

- **AEApi.java** - API 入口点，通过 `AEApi.instance()` 访问
- **IAppEngApi.java** - 主要 API 接口定义
- **网络包** - 定义网格、节点、存储等核心接口
  - `IStorageGrid` - 存储网格接口
  - `IGridNode` - 网格节点接口
  - `IGridHost` - 网格主机接口
  - `IMEMonitor` - 存储监视器接口

### 2. 核心系统 (`appeng.core`)

- **AppEng.java** - 模组主类，负责初始化和事件注册
- **Api.java** - API 实现类
- **Registration.java** - 所有方块、物品、TileEntity 的注册中心
- **AEConfig.java** - 配置管理系统

### 3. ME 网络系统 (`appeng.me`)

ME（Matter Energy）网络是 AE2 的核心：

- **Grid.java** - 网格实现，管理网络中所有节点
- **GridNode.java** - 单个网络节点实现
- **GridConnection.java** - 节点间连接管理
- **GridStorage.java** - 网络存储管理

**网络拓扑：**
```
IGrid (网络)
  └── 多个 IGridNode (节点)
        └── 通过 GridConnection 连接
              └── 每个节点有 IGridHost (主机)
```

### 4. 存储系统

存储系统分为多层：

```
IMEMonitor (监视器)
  └── IItemList (物品/流体列表)
        └── IAEStack (AE 堆栈，支持物品和流体)
              └── ItemStack / FluidStack (Minecraft 原生)
```

关键类：
- **IStorageGrid** - 存储网格，提供物品和流体库存访问
- **MECraftingInventory** - 合成库存管理
- **CellInventory** - 存储单元库存实现

### 5. 方块和 TileEntity 架构

#### 方块层次：
```
Block (Minecraft)
  └── AEBaseBlock (AE2 基础方块)
        ├── AEBaseTileBlock (带 TileEntity 的方块)
        ├── AEBaseSlabBlock (半砖)
        └── AEDecorativeBlock (装饰方块)
```

#### TileEntity 层次：
```
TileEntity (Minecraft)
  └── AEBaseTile (AE2 基础 TileEntity)
        ├── 网络相关 Tile (实现 IGridHost)
        └── 存储相关 Tile (实现 ICellContainer)
```

### 6. 部件系统 (`appeng.parts`)

部件是安装在方块面上的组件（需要 FMP - ForgeMultipart）：

- **AEBasePart.java** - 基础部件类
- **CableBusContainer.java** - 线缆总线容器，管理多个部件
- 各类部件实现：导入/导出总线、终端、P2P 等

### 7. 自动合成系统 (`appeng.crafting`)

```
CraftingJob (合成任务)
  └── CraftingTreeNode (合成树节点)
        └── CraftingTreeProcess (合成处理节点)
              └── CraftingWatcher (合成监视器)
```

关键类：
- **CraftingJob** - 管理整个合成任务
- **CraftingTreeNode** - 递归构建合成树
- **CraftingLink** - 连接多个合成任务
- **CraftingWatcher** - 监控库存变化触发自动合成

## 网格缓存系统

AE2 使用网格缓存（GridCache）来管理不同类型的网络功能：

```java
// 获取不同类型的网格缓存
IStorageGrid storage = grid.getCache(IStorageGrid.class);
ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
IPathingGrid pathing = grid.getCache(IPathingGrid.class);
```

缓存类型：
- **IStorageGrid** - 物品/流体存储
- **ICraftingGrid** - 自动合成
- **IEnergyGrid** - 能量管理
- **IPathingGrid** - 网络路径计算
- **ISecurityGrid** - 安全权限
- **ITickManager** - 网络 tick 管理

## 事件系统

AE2 使用自定义事件系统：

```java
// MENetworkEvent 基类
MENetworkEvent
  ├── MENetworkBootingStatusChange
  ├── MENetworkCellArrayUpdate
  ├── MENetworkChannelChanged
  ├── MENetworkControllerChange
  └── MENetworkPowerStatusChange
```

事件通过 `NetworkEventBus` 分发。

## 开发建议

1. **实现 IGridHost**：如果你的方块需要连接 ME 网络
2. **使用 Duality 模式**：复杂功能拆分为 Duality 类（如 DualityInterface）
3. **遵循 API**：尽量使用 `appeng.api` 而不是内部实现
4. **处理网络异常**：网络操作可能失败，需要处理 GridAccessException
5. **线程安全**：网络操作可能在主线程或其他线程执行

## 示例：创建网络方块

```java
public class MyAEMachine extends AEBaseTile implements IGridHost {
    private IGridNode node;

    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return node;
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (node != null) {
            node.destroy();
        }
    }

    // 获取存储网格
    public IStorageGrid getStorage() {
        try {
            return node.getGrid().getCache(IStorageGrid.class);
        } catch (GridAccessException e) {
            return null;
        }
    }
}
```

## 相关文档

- [NETWORK_SYSTEM.md](./NETWORK_SYSTEM.md) - 深入了解 ME 网络
- [STORAGE_SYSTEM.md](./STORAGE_SYSTEM.md) - 存储系统详解
- [BLOCK_TILEENTITY_GUIDE.md](./BLOCK_TILEENTITY_GUIDE.md) - 方块和 TileEntity 开发
