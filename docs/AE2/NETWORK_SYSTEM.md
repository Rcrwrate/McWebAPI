# AE2 网络系统详解

## 网络基础概念

### ME 网络 (Matter Energy Network)

ME 网络是 AE2 的核心，是一个连接多个设备的逻辑网络，用于传输物品、能量和信息。

### 关键组件

1. **IGrid** - 整个网络的实例
2. **IGridNode** - 网络中的单个节点（每个连接到网络的设备）
3. **IGridHost** - 拥有网格节点的主体（方块或部件）
4. **GridConnection** - 节点之间的物理连接

## 网络拓扑结构

```
ME Controller (控制器)
  ├── Dense Cable (致密线缆)
  │     └── 多个设备节点
  └── Smart Cable (智能线缆)
        └── 单个设备节点
```

### 网络规则

1. **必须有一个控制器**：网络必须至少有一个 ME Controller
2. **控制器容量**：每个控制器面最多连接 32 个设备
3. **线缆类型**：
   - 智能线缆：每根线 8 通道
   - 致密线缆：每根线 32 通道
4. **P2P 通道**：P2P 隧道可以扩展网络范围

## 节点连接流程

### 1. 节点创建

```java
// 在 TileEntity 中创建节点
this.node = AEApi.instance().createGridNode(new BlockPos(this));
```

### 2. 节点连接

当两个 IGridHost 方块相邻时，会自动创建 GridConnection：

```java
// GridConnection.create() 自动处理连接逻辑
GridConnection connection = GridConnection.create(nodeA, nodeB, ForgeDirection.UNKNOWN);
```

### 3. 网络发现

当节点连接时，会进行网络发现：

```java
// GridSplitDetector 处理网络分割和合并
// GridPropagator 传播网络变化
```

## 网格缓存 (GridCache)

网格缓存是网络功能的核心，每种缓存负责不同的网络功能：

### 缓存类型

```java
// 在 Grid.java 中管理所有缓存
private final Map<Class<? extends IGridCache>, IGridCache> caches = new HashMap<>();

// 获取缓存示例
IStorageGrid storage = grid.getCache(IStorageGrid.class);
ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
```

### 1. IStorageGrid - 存储缓存

```java
public interface IStorageGrid extends IGridCache, IStorageMonitorable {
    // 获取物品库存
    IMEMonitor<IAEItemStack> getItemInventory();
    
    // 获取流体库存
    IMEMonitor<IAEFluidStack> getFluidInventory();
    
    // 刷新存储列表
    void postAlterationOfStoredItems();
}
```

### 2. ICraftingGrid - 合成缓存

管理自动合成任务和模式：

```java
public interface ICraftingGrid extends IGridCache {
    // 提交合成任务
    ICraftingLink submitJob(ICraftingJob job);
    
    // 获取可提供的合成模式
    List<ICraftingPatternDetails> getCraftingPatterns(IAEItemStack item);
}
```

### 3. IEnergyGrid - 能量缓存

管理网络能量：

```java
public interface IEnergyGrid extends IGridCache, IEnergySource {
    // 注入能量
    double injectPower(double amount);
    
    // 获取能量存储
    double getEnergyStored();
    
    // 获取最大存储
    double getMaxStoredPower();
}
```

### 4. IPathingGrid - 路径缓存

计算网络路径和通道：

```java
public interface IPathingGrid extends IGridCache {
    // 获取控制器状态
    ControllerState getControllerState();
    
    // 网络是否在线
    boolean isNetworkBooting();
}
```

### 5. ITickManager - Tick 管理

管理网络 Tick：

```java
public interface ITickManager extends IGridCache {
    // 唤醒设备
    void wakeDevice(Object target);
    
    // 睡眠设备
    void sleepDevice(Object target);
}
```

## 网络事件

网络事件通过 `MENetworkEvent` 系统分发：

```java
// 事件基类
public abstract class MENetworkEvent {
    protected final IGridNode node;
    protected final Object target;
}

// 常见事件类型
MENetworkBootingStatusChange - 网络启动状态变化
MENetworkCellArrayUpdate - 存储单元更新
MENetworkChannelChanged - 通道变化
MENetworkControllerChange - 控制器变化
MENetworkPowerStatusChange - 能量状态变化
```

事件处理示例：

```java
// 在 AEBaseTile 中
@MENetworkEventSubscribe
public void powerChanged(MENetworkPowerStatusChange event) {
    if (this.getGridNode() == event.node) {
        // 处理能量状态变化
    }
}
```

## 网络连接示例

### 示例 1：获取网络存储

```java
public IStorageGrid getStorageGrid(IGridHost host) {
    try {
        IGridNode node = host.getGridNode(ForgeDirection.UNKNOWN);
        if (node != null && node.isActive()) {
            IGrid grid = node.getGrid();
            return grid.getCache(IStorageGrid.class);
        }
    } catch (GridAccessException e) {
        // 处理异常
    }
    return null;
}
```

### 示例 2：遍历网络中的所有接口

```java
public void listAllInterfaces(IGrid grid) {
    // 获取所有接口节点
    IMachineSet interfaceNodes = grid.getMachines(IInterfaceHost.class);
    
    for (IGridNode node : interfaceNodes) {
        IInterfaceHost host = (IInterfaceHost) node.getMachine();
        DualityInterface duality = host.getInterfaceDuality();
        
        // 处理接口信息
        String name = host.getName();
        IInventory patterns = host.getPatterns();
        // ...
    }
}
```

### 示例 3：监控网络变化

```java
public class NetworkMonitor {
    private IGrid grid;
    
    public void startMonitoring(IGridNode node) {
        if (node != null) {
            this.grid = node.getGrid();
            // 订阅事件
            this.grid.postEvent(new MENetworkEventSubscribe(this));
        }
    }
    
    @MENetworkEventSubscribe
    public void onChannelChange(MENetworkChannelChanged event) {
        // 处理通道变化
        System.out.println("Network channels changed!");
    }
}
```

## 网络通道系统

### 通道类型

1. **普通通道**：32 个/控制器面
2. **致密通道**：32*8=256 个（通过致密线缆）
3. **P2P 通道**：可跨维度/远距离传输

### 通道分配

```java
// GridNode.java 中的通道管理
private int usedChannels = 0;
private int lastUsedChannels = 0;

// 获取所需通道数
public int getUsedChannels() {
    return this.usedChannels;
}
```

通道自动分配流程：
1. 设备连接到网络时请求通道
2. PathingGrid 计算可用路径
3. 如果控制器有足够通道，分配成功
4. 如果通道不足，设备显示离线

## 网络诊断和调试

### 1. 获取网络状态

```java
public void printNetworkStatus(IGrid grid) {
    IPathingGrid pathing = grid.getCache(IPathingGrid.class);
    IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
    
    System.out.println("Controller State: " + pathing.getControllerState());
    System.out.println("Network Online: " + !pathing.isNetworkBooting());
    System.out.println("Energy Stored: " + energy.getEnergyStored());
    System.out.println("Max Energy: " + energy.getMaxStoredPower());
}
```

### 2. 检查节点状态

```java
public void checkNodeStatus(IGridNode node) {
    System.out.println("Node Active: " + node.isActive());
    System.out.println("Node Powered: " + node.isPowered());
    System.out.println("Used Channels: " + node.getUsedChannels());
    System.out.println("Grid: " + (node.getGrid() != null ? "Connected" : "Not connected"));
}
```

### 3. 查找网络中的设备

```java
public <T> List<T> findDevices(IGrid grid, Class<T> deviceClass) {
    List<T> devices = new ArrayList<>();
    IMachineSet nodes = grid.getMachines(deviceClass);
    
    for (IGridNode node : nodes) {
        T device = (T) node.getMachine();
        devices.add(device);
    }
    
    return devices;
}
```

## 最佳实践

1. **总是检查节点是否为 null**：`getGridNode()` 可能返回 null
2. **捕获 GridAccessException**：网络可能不可访问
3. **检查节点是否激活**：`node.isActive()` 确保设备在线
4. **避免频繁获取网格**：缓存网格引用以提高性能
5. **使用事件系统**：订阅网络事件而不是轮询状态
6. **注意线程安全**：网络操作可能在任意线程执行

## 常见问题

### Q: 为什么设备显示离线？
A: 可能原因：
- 没有连接到控制器
- 通道不足
- 能量不足
- 网络正在启动

### Q: 如何扩展现有网络功能？
A: 创建自定义 IGridCache 实现并在网格注册

### Q: 如何调试网络问题？
A: 使用网络工具（网络可视化器）或监听 MENetworkEvent

## 相关文档

- [CORE_ARCHITECTURE.md](./CORE_ARCHITECTURE.md) - 核心架构
- [STORAGE_SYSTEM.md](./STORAGE_SYSTEM.md) - 存储系统
- [BLOCK_TILEENTITY_GUIDE.md](./BLOCK_TILEENTITY_GUIDE.md) - 方块开发
