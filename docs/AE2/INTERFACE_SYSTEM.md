# AE2 接口系统详解

## 接口系统概述

AE2 的接口系统（ME Interface）是自动化物流和合成系统的核心组件，允许将网络中的物品和合成模式暴露给外部设备（如装配室、机器等）。

## 接口类型

### 1. ME 接口 (ME Interface)

标准方块形式的接口，用于：
- 存储物品到相邻容器
- 提供合成样板
- 作为自动化节点的输入/输出

### 2. P2P 接口 (P2P Interface)

点对点隧道接口，用于：
- 跨维度传输物品/流体
- 长距离连接分离的网络

### 3. 样板供应器 (Pattern Provider)

专门的接口，用于：
- 提供合成样板给装配室
- 自动化合成输入/输出

## 核心接口和类

### 1. IInterfaceHost

接口主机，代表任何可以作为接口的设备：

```java
public interface IInterfaceHost extends IGridHost, IInterfaceViewable {
    // 获取接口的双重实现
    DualityInterface getInterfaceDuality();
    
    // 获取方块位置
    DimensionalCoord getLocation();
    
    // 获取接口朝向
    ForgeDirection getFront();
    
    // 检查是否需要更新
    boolean isDirty();
}
```

### 2. IInterfaceViewable

可在接口终端中查看的接口：

```java
public interface IInterfaceViewable {
    // 获取接口名称
    String getName();
    
    // 获取样板的槽位数量
    int getPatternCapacity();
    
    // 获取样板列表
    IInventory getPatterns();
    
    // 是否应该显示在终端
    boolean shouldDisplay();
    
    // 获取行数和列数
    int rows();
    int rowSize();
    
    // 获取位置
    DimensionalCoord getLocation();
}
```

### 3. DualityInterface

接口的核心实现，处理所有逻辑：

```java
public class DualityInterface implements IInventoryWatcher, IGridTickable {
    private final IInterfaceHost iHost;
    private final IGridNode gridNode;
    
    // 配置槽位（过滤器和卡片）
    private final AppEngInternalInventory config = new AppEngInternalInventory(this, 8);
    
    // 存储槽位（缓存的物品）
    private final AppEngInternalInventory storage = new AppEngInternalInventory(this, 8);
    
    // 样板槽位
    private final AppEngInternalInventory patterns = new AppEngInternalInventory(this, 9 * 4); // 36 个样板
    
    // 关键方法
    public void updateStatus() {
        // 更新接口状态，检查相邻容器等
    }
    
    public void notifyNeighbors() {
        // 通知相邻方块更新
    }
    
    public boolean pushItemsOut(EnumSet<ForgeDirection> possibleDirections) {
        // 将物品推送到相邻容器
    }
}
```

### 4. InterfaceTerminalRegistry

接口终端注册表，管理所有可查看的接口：

```java
public class InterfaceTerminalRegistry {
    // 单例模式
    public static InterfaceTerminalRegistry instance() {
        return INSTANCE;
    }
    
    // 支持的接口类型
    private final Set<Class<? extends IInterfaceViewable>> supportedClasses = new HashSet<>();
    
    // 注册接口类型
    public void addToWorldMap(Class<? extends IInterfaceViewable> iface) {
        this.supportedClasses.add(iface);
    }
    
    // 获取所有支持的接口
    public Set<Class<? extends IInterfaceViewable>> getSupportedClasses() {
        return Collections.unmodifiableSet(this.supportedClasses);
    }
}
```

## 接口工作原理

### 1. 物品推送机制

接口会尝试将存储的物品推送到相邻容器：

```java
public boolean pushItemsOut(EnumSet<ForgeDirection> possibleDirections) {
    for (ForgeDirection s : possibleDirections) {
        // 获取相邻 TileEntity
        TileEntity te = this.iHost.getTileEntity(s);
        
        if (te != null) {
            // 检查是否可以接受物品
            if (this.acceptsItems(te, s.getOpposite())) {
                // 尝试推送物品
                for (int i = 0; i < this.storage.getSizeInventory(); i++) {
                    ItemStack stack = this.storage.getStackInSlot(i);
                    if (stack != null) {
                        // 使用 InventoryAdaptor 适配不同容器类型
                        InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(te, s.getOpposite());
                        if (adaptor != null) {
                            ItemStack added = adaptor.addItems(stack.copy());
                            if (added != null) {
                                stack.stackSize = added.stackSize;
                                this.storage.setInventorySlotContents(i, added.stackSize > 0 ? added : null);
                                didSomething = true;
                            } else {
                                this.storage.setInventorySlotContents(i, null);
                                didSomething = true;
                            }
                        }
                    }
                }
            }
        }
    }
    return didSomething;
}
```

### 2. 样板处理

接口存储样板并提供给装配室：

```java
public void updatePatterns() {
    // 遍历所有样板槽位
    for (int i = 0; i < this.patterns.getSizeInventory(); i++) {
        ItemStack patternStack = this.patterns.getStackInSlot(i);
        
        if (patternStack != null && patternStack.getItem() instanceof ICraftingPatternItem) {
            ICraftingPatternDetails details = 
                ((ICraftingPatternItem) patternStack.getItem()).getPattern(patternStack, this.iHost.getWorld());
            
            if (details != null) {
                // 添加到可用样板列表
                this.patternList.add(details);
            }
        }
    }
    
    // 通知网络更新
    this.updateCraftingList();
}

private void updateCraftingList() {
    try {
        final ICraftingGrid cg = this.gridNode.getGrid().getCache(ICraftingGrid.class);
        cg.addCraftingOption(this, this.patternList);
    } catch (GridAccessException e) {
        // 忽略
    }
}
```

### 3. 配置模式

接口有三种工作模式：

1. **Item Stock Mode**（库存模式）：
   - 保持特定数量的物品在存储中
   - 从网络提取或推送物品到相邻容器

2. **Pattern Mode**（样板模式）：
   - 提供合成样板给装配室
   - 处理合成输入输出

3. **Blocking Mode**（阻塞模式）：
   - 等待相邻容器清空后才推送下一批
   - 防止物品堆积

配置代码：

```java
// 在 DualityInterface 中
public void configure(ForgeDirection dir) {
    final ItemStack is = this.config.getStackInSlot(dir.ordinal());
    final YesNo yn = (YesNo) this.getConfigManager().getSetting(Settings.BLOCKING);
    
    if (is == null && yn == YesNo.NO) {
        // 无配置，非阻塞模式
        return;
    }
    
    // 应用配置...
}
```

## 访问接口数据

### 1. 获取网络中的所有接口

```java
public void listAllInterfaces(IGrid grid) {
    // 从网格获取所有接口节点
    IMachineSet interfaceNodes = grid.getMachines(IInterfaceHost.class);
    
    List<InterfaceInfo> interfaces = new ArrayList<>();
    
    for (IGridNode node : interfaceNodes) {
        IInterfaceHost host = (IInterfaceHost) node.getMachine();
        DualityInterface duality = host.getInterfaceDuality();
        
        InterfaceInfo info = new InterfaceInfo();
        
        // 基本信息
        info.name = host.getName();
        info.location = host.getLocation();
        info.online = node.isActive();
        
        // 获取样板
        IInventory patternInv = host.getPatterns();
        for (int i = 0; i < patternInv.getSizeInventory(); i++) {
            ItemStack pattern = patternInv.getStackInSlot(i);
            if (pattern != null && pattern.getItem() instanceof ICraftingPatternItem) {
                ICraftingPatternDetails details = 
                    ((ICraftingPatternItem) pattern.getItem()).getPattern(pattern, world);
                if (details != null) {
                    info.patterns.add(details);
                }
            }
        }
        
        // 获取存储内容
        IInventory storage = duality.getStorage();
        for (int i = 0; i < storage.getSizeInventory(); i++) {
            ItemStack stack = storage.getStackInSlot(i);
            if (stack != null) {
                info.storedItems.add(stack);
            }
        }
        
        interfaces.add(info);
    }
    
    return interfaces;
}
```

### 2. 获取接口终端数据

参考 `ContainerInterfaceTerminal.java`：

```java
public void updateList() {
    final HashMap<String, ClientDCInternalInv> oldList = this.byName;
    this.byName = new HashMap<String, ClientDCInternalInv>();
    
    if (this.monitor != null) {
        // 获取所有支持的接口类型
        Set<Class<? extends IInterfaceViewable>> supportedClasses = 
            InterfaceTerminalRegistry.instance().getSupportedClasses();
        
        for (Class<? extends IInterfaceViewable> clazz : supportedClasses) {
            // 获取网格中该类型的所有节点
            for (IGridNode node : this.monitor.getGrid().getMachines(clazz)) {
                IInterfaceViewable machine = (IInterfaceViewable) node.getMachine();
                
                if (!machine.shouldDisplay()) {
                    continue;
                }
                
                // 添加到列表
                this.byName.put(machine.getName(), new ClientDCInternalInv(machine));
            }
        }
    }
}
```

### 3. 远程访问接口数据

通过 API 访问接口信息：

```java
// 从方块获取接口信息
public InterfaceData getInterfaceData(TileEntity te) {
    if (te instanceof IInterfaceHost) {
        IInterfaceHost host = (IInterfaceHost) te;
        
        InterfaceData data = new InterfaceData();
        data.name = host.getName();
        data.patterns = new ArrayList<>();
        data.storedItems = new ArrayList<>();
        
        // 获取样板
        IInventory patterns = host.getPatterns();
        for (int i = 0; i < patterns.getSizeInventory(); i++) {
            ItemStack stack = patterns.getStackInSlot(i);
            if (stack != null) {
                data.patterns.add(stack.copy());
            }
        }
        
        // 获取存储物品
        DualityInterface duality = host.getInterfaceDuality();
        IInventory storage = duality.getStorage();
        for (int i = 0; i < storage.getSizeInventory(); i++) {
            ItemStack stack = storage.getStackInSlot(i);
            if (stack != null) {
                data.storedItems.add(stack.copy());
            }
        }
        
        return data;
    }
    return null;
}
```

## 接口配置

### 1. 配置选项

接口有以下配置选项：

- **Blocking Mode**（阻塞模式）：等待相邻容器清空
- **Crafting Only**（仅合成）：只处理合成，不存储物品
- **Interface Terminal**（接口终端）：是否在接口终端显示

### 2. 编程方式配置

```java
// 获取接口的配置管理器
IConfigManager configManager = duality.getConfigManager();

// 设置选项
configManager.putSetting(Settings.BLOCKING, YesNo.YES);
configManager.putSetting(Settings.CRAFTING_ONLY, YesNo.NO);
configManager.putSetting(Settings.INTERFACE_TERMINAL, YesNo.YES);

// 获取当前配置
YesNo blocking = (YesNo) configManager.getSetting(Settings.BLOCKING);
```

### 3. 配置槽位

接口有 8 个配置槽位（每个方向一个）：

```java
// 设置配置
IInventory configInv = duality.getConfig();

// 设置第一槽位为钻石
configInv.setInventorySlotContents(0, new ItemStack(Items.diamond, 1));

// 接口会尝试保持该物品在相邻容器中
```

## 高级接口用法

### 1. 监听接口变化

```java
// 监听接口模式变化
public class InterfaceWatcher implements IInventoryWatcher {
    private final DualityInterface duality;
    
    @Override
    public void onInventoryChanged(IInventory inventory) {
        if (inventory == duality.getPatterns()) {
            // 样板列表发生变化
            duality.updatePatterns();
        } else if (inventory == duality.getConfig()) {
            // 配置发生变化
            duality.configureAll();
        }
    }
}
```

### 2. 自定义接口行为

创建自定义接口实现：

```java
public class CustomInterface extends AEBaseTile implements IInterfaceHost {
    private final DualityInterface duality = new DualityInterface(this.getProxy(), this);
    
    @Override
    public DualityInterface getInterfaceDuality() {
        return this.duality;
    }
    
    @Override
    public String getName() {
        return "Custom Interface";
    }
    
    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return this.duality.getGridNode();
    }
}
```

### 3. 扩展接口功能

添加自定义数据到接口：

```java
// 在 DualityInterface 中添加自定义数据字段
public class ExtendedDualityInterface extends DualityInterface {
    private final Map<String, Object> customData = new HashMap<>();
    
    public void setCustomData(String key, Object value) {
        this.customData.put(key, value);
    }
    
    public Object getCustomData(String key) {
        return this.customData.get(key);
    }
}
```

## 接口状态监控

### 1. 监控接口在线状态

```java
// 检查接口是否在线
public boolean isInterfaceOnline(IInterfaceHost host) {
    try {
        IGridNode node = host.getGridNode(ForgeDirection.UNKNOWN);
        return node != null && node.isActive();
    } catch (Exception e) {
        return false;
    }
}
```

### 2. 获取接口统计

```java
// 获取接口统计信息
public InterfaceStats getInterfaceStats(IInterfaceHost host) {
    InterfaceStats stats = new InterfaceStats();
    
    // 样板数量
    IInventory patterns = host.getPatterns();
    stats.patternCount = 0;
    for (int i = 0; i < patterns.getSizeInventory(); i++) {
        if (patterns.getStackInSlot(i) != null) {
            stats.patternCount++;
        }
    }
    
    // 存储物品数量
    DualityInterface duality = host.getInterfaceDuality();
    IInventory storage = duality.getStorage();
    stats.storedItemCount = 0;
    for (int i = 0; i < storage.getSizeInventory(); i++) {
        ItemStack stack = storage.getStackInSlot(i);
        if (stack != null) {
            stats.storedItemCount += stack.stackSize;
        }
    }
    
    return stats;
}
```

## 最佳实践

1. **总是检查节点是否为 null**：接口可能未连接到网络
2. **处理网络异常**：网络可能在任何时候断开
3. **缓存 duality 引用**：避免重复获取
4. **使用事件系统**：监听库存变化而不是轮询
5. **注意线程安全**：接口操作可能在任何线程执行
6. **批量操作**：批量处理样板和物品变更

## 常见问题

### Q: 接口不工作？

A: 检查：
- 是否连接到控制器
- 是否有足够通道
- 配置是否正确
- 相邻容器是否可以接受物品

### Q: 样板不显示？

A: 检查：
- 样板是否正确放置
- 接口是否在线
- 接口终端是否支持该接口类型

### Q: 如何扩展接口功能？

A: 继承 DualityInterface 或实现 IInterfaceHost

## 相关文档

- [CORE_ARCHITECTURE.md](./CORE_ARCHITECTURE.md) - 核心架构
- [NETWORK_SYSTEM.md](./NETWORK_SYSTEM.md) - 网络系统
- [STORAGE_SYSTEM.md](./STORAGE_SYSTEM.md) - 存储系统
- [BLOCK_TILEENTITY_GUIDE.md](./BLOCK_TILEENTITY_GUIDE.md) - 方块开发
