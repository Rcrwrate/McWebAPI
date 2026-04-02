# AE2 存储系统详解

## 存储架构概述

AE2 的存储系统是一个多层次的抽象架构，支持物品和流体两种主要存储类型。

### 存储层次结构

```
应用层 (GUI, API)
  ↓
IMEMonitor (存储监视器)
  ↓
IStorageGrid (存储网格)
  ↓
IMEInventoryHandler (存储处理器)
  ↓
存储单元 (Cells) / 外部存储
  ↓
实际物品 (ItemStack) / 流体 (FluidStack)
```

## 核心接口

### 1. IMEMonitor<T>

存储监视器，提供存储列表和事件通知：

```java
public interface IMEMonitor<T extends IAEStack<T>> extends IMEInventoryHandler<T> {
    // 添加监听器
    void addListener(IMEMonitorHandlerReceiver<T> l, Object owner);
    
    // 移除监听器
    void removeListener(Object owner);
    
    // 获取完整的存储列表
    IItemList<T> getStorageList();
}
```

### 2. IStorageGrid

存储网格，管理网络中的所有存储：

```java
public interface IStorageGrid extends IGridCache, IStorageMonitorable {
    // 获取物品库存
    IMEMonitor<IAEItemStack> getItemInventory();
    
    // 获取流体库存
    IMEMonitor<IAEFluidStack> getFluidInventory();
    
    // 刷新存储（通知变更）
    void postAlterationOfStoredItems();
}
```

### 3. IAEStack

AE 堆栈，抽象的物品/流体堆栈：

```java
public interface IAEStack<T extends IAEStack<T>> extends Comparable<T> {
    // 堆栈大小
    long getStackSize();
    
    // 设置堆栈大小
    void setStackSize(long size);
    
    // 是否相同类型
    boolean isSameType(T other);
    
    // 转换为 Minecraft 堆栈
    T getItemStack();
}

// 具体实现
public interface IAEItemStack extends IAEStack<IAEItemStack> {
    ItemStack getItemStack();
}

public interface IAEFluidStack extends IAEStack<IAEFluidStack> {
    FluidStack getFluidStack();
}
```

### 4. IItemList<T>

存储列表，包含所有存储中的物品：

```java
public interface IItemList<T extends IAEStack<T>> extends Iterable<T> {
    // 添加堆栈
    void add(T stack);
    
    // 查找堆栈
    T findPrecise(T stack);
    
    // 是否为空
    boolean isEmpty();
    
    // 遍历所有堆栈
    Iterator<T> iterator();
}
```

## 存储单元 (Cells)

### 存储单元类型

AE2 支持多种存储单元：

1. **物品存储单元**：存储普通物品
   - 1k-ME-Storage-Cell 到 16384k-ME-Storage-Cell
   - 分类型存储单元（如金属块存储单元）

2. **流体存储单元**：存储流体
   - 1k-ME-Fluid-Storage-Cell 到 4096k-ME-Fluid-Storage-Cell

3. **空间存储单元**：存储空间信息

### 存储单元实现

存储单元实现 `IMEInventoryHandler` 接口：

```java
public class CellInventory implements IMEInventoryHandler<IAEItemStack> {
    private final IStorageCell cellType;
    private final ISaveProvider container;
    private final IItemList<IAEItemStack> cellItems;
    private final int maxItemTypes;
    private long storedItemCount;
    private long maxItemCount;
    
    // 关键方法
    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, BaseActionSource src) {
        // 注入物品
    }
    
    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable type, BaseActionSource src) {
        // 提取物品
    }
    
    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        // 获取可用物品列表
        return this.cellItems;
    }
}
```

## 外部存储

AE2 可以通过外部存储总线访问其他容器的物品：

```java
public class ExternalStorageHandler implements IMEInventoryHandler<IAEItemStack> {
    private final IInventory target;
    private final ForgeDirection side;
    
    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, BaseActionSource src) {
        // 将物品注入外部容器
        ItemStack stack = input.getItemStack();
        // ... 实现注入逻辑
    }
    
    @Override
       public IAEItemStack extractItems(IAEItemStack request, Actionable mode, BaseActionSource src) {
        // 从外部容器提取物品
        // ... 实现提取逻辑
    }
}
```

## 存储操作

### 1. 读取存储内容

```java
public void readStorageContents(IStorageGrid storageGrid) {
    // 获取物品监视器
    IMEMonitor<IAEItemStack> itemMonitor = storageGrid.getItemInventory();
    
    // 获取存储列表
    IItemList<IAEItemStack> itemList = itemMonitor.getStorageList();
    
    // 遍历所有物品
    for (IAEItemStack stack : itemList) {
        ItemStack mcStack = stack.getItemStack();
        long count = stack.getStackSize();
        
        System.out.println("Item: " + mcStack.getDisplayName() + 
                          " Count: " + count);
    }
    
    // 获取流体（类似）
    IMEMonitor<IAEFluidStack> fluidMonitor = storageGrid.getFluidInventory();
    IItemList<IAEFluidStack> fluidList = fluidMonitor.getStorageList();
    
    for (IAEFluidStack fluid : fluidList) {
        FluidStack mcFluid = fluid.getFluidStack();
        long amount = fluid.getStackSize();
        
        System.out.println("Fluid: " + mcFluid.getLocalizedName() + 
                          " Amount: " + amount + " mB");
    }
}
```

### 2. 注入物品

```java
public void injectItem(IStorageGrid storageGrid, ItemStack stack) {
    IMEMonitor<IAEItemStack> monitor = storageGrid.getItemInventory();
    
    // 转换为 AE 堆栈
    IAEItemStack aeStack = AEApi.instance().storage().createItemStack(stack);
    
    // 注入物品（模拟）
    IAEItemStack notInjected = monitor.injectItems(
        aeStack.copy(), 
        Actionable.SIMULATE,  // 先模拟
        BaseActionSource.fromPlayer(player)
    );
    
    // 如果模拟成功，实际注入
    if (notInjected == null || notInjected.getStackSize() == 0) {
        monitor.injectItems(
            aeStack, 
            Actionable.MODULATE,  // 实际执行
            BaseActionSource.fromPlayer(player)
        );
        System.out.println("注入成功！");
    } else {
        System.out.println("注入失败，剩余：" + notInjected.getStackSize());
    }
}
```

### 3. 提取物品

```java
public ItemStack extractItem(IStorageGrid storageGrid, ItemStack pattern, int amount) {
    IMEMonitor<IAEItemStack> monitor = storageGrid.getItemInventory();
    
    // 创建请求堆栈
    IAEItemStack request = AEApi.instance().storage().createItemStack(pattern);
    request.setStackSize(amount);
    
    // 提取物品（模拟）
    IAEItemStack extracted = monitor.extractItems(
        request,
        Actionable.SIMULATE,
        BaseActionSource.fromPlayer(player)
    );
    
    // 如果模拟成功，实际提取
    if (extracted != null && extracted.getStackSize() > 0) {
        IAEItemStack actual = monitor.extractItems(
            request,
            Actionable.MODULATE,
            BaseActionSource.fromPlayer(player)
        );
        return actual != null ? actual.getItemStack() : null;
    }
    
    return null;
}
```

### 4. 监听存储变化

```java
public class StorageListener implements IMEMonitorHandlerReceiver<IAEItemStack> {
    
    @Override
    public void postChange(IBaseMonitor<IAEItemStack> monitor, 
                          Iterable<IAEItemStack> change, 
                          BaseActionSource source) {
        // 存储发生变化
        for (IAEItemStack stack : change) {
            long delta = stack.getStackSize();
            if (delta > 0) {
                System.out.println("+" + delta + " " + stack.getItemStack().getDisplayName());
            } else {
                System.out.println(delta + " " + stack.getItemStack().getDisplayName());
            }
        }
    }
    
    @Override
    public boolean isValid(Object effectiveGrid) {
        return true; // 持续有效
    }
}

// 使用监听器
public void addStorageListener(IStorageGrid storageGrid) {
    IMEMonitor<IAEItemStack> monitor = storageGrid.getItemInventory();
    StorageListener listener = new StorageListener();
    monitor.addListener(listener, this);
}
```

## 存储过滤器

### 1. 存储总线过滤器

```java
// 设置存储总线只接受特定物品
IInventory config = storageBus.getConfig();
for (int i = 0; i < config.getSizeInventory(); i++) {
    ItemStack filter = config.getStackInSlot(i);
    if (filter != null) {
        // 这是一个过滤器物品
    }
}
```

### 2. 单元工作模式

```java
// 在 CellInventory 中检查是否可以接受物品
public boolean canAccept(IAEItemStack input) {
    // 检查是否达到类型限制
    if (this.cellItems.size() >= this.maxItemTypes) {
        return this.cellItems.findPrecise(input) != null;
    }
    
    // 检查预格式化
    if (this.cellType.getConfigManager().getSetting(Settings.PRE_FORMATTED) != YesNo.NO) {
        // 检查是否匹配预格式化列表
    }
    
    return true;
}
```

## 进阶存储概念

### 1. 优先级系统

```java
// IStorageGrid 中的优先级处理
// 存储处理器按优先级排序
private final LinkedList<IMEInventoryHandler> prioritizedHandlers = new LinkedList<>();

// 注入时按优先级顺序尝试
for (IMEInventoryHandler handler : this.prioritizedHandlers) {
    if (handler.canAccept(input)) {
        // 尝试注入
    }
}
```

### 2. 存储视图 (Storage View)

```java
// ICraftingGrid 中的存储视图
public IItemList<IAEItemStack> getStorageList() {
    final IItemList<IAEItemStack> out = AEApi.instance().storage().createItemList();
    
    // 从所有存储获取物品
    for (IMEInventoryHandler handler : this.handlers) {
        handler.getAvailableItems(out);
    }
    
    return out;
}
```

### 3. 存储操作类型

```java
public enum Actionable {
    // 模拟操作，不改变实际存储
    SIMULATE,
    
    // 实际执行操作，改变存储
    MODULATE
}
```

## 存储性能优化

### 1. 批量操作

```java
// 批量注入多个物品
public void injectMultiple(IStorageGrid storageGrid, List<ItemStack> items) {
    IMEMonitor<IAEItemStack> monitor = storageGrid.getItemInventory();
    
    // 创建变更列表
    IItemList<IAEItemStack> changes = AEApi.instance().storage().createItemList();
    
    for (ItemStack stack : items) {
        IAEItemStack aeStack = AEApi.instance().storage().createItemStack(stack);
        changes.add(aeStack);
    }
    
    // 一次性注入（需要自定义实现）
    // monitor.injectMultiple(changes, ...);
}
```

### 2. 缓存存储引用

```java
public class StorageCache {
    private final IStorageGrid storageGrid;
    private final IMEMonitor<IAEItemStack> itemMonitor;
    private final IMEMonitor<IAEFluidStack> fluidMonitor;
    
    public StorageCache(IGrid grid) {
        this.storageGrid = grid.getCache(IStorageGrid.class);
        this.itemMonitor = storageGrid.getItemInventory();
        this.fluidMonitor = storageGrid.getFluidInventory();
    }
}
```

## 存储调试

### 1. 打印存储统计

```java
public void printStorageStats(IStorageGrid storageGrid) {
    IMEMonitor<IAEItemStack> monitor = storageGrid.getItemInventory();
    IItemList<IAEItemStack> items = monitor.getStorageList();
    
    long totalItems = 0;
    int uniqueTypes = 0;
    
    for (IAEItemStack stack : items) {
        totalItems += stack.getStackSize();
        uniqueTypes++;
    }
    
    System.out.println("=== 存储统计 ===");
    System.out.println("物品种类: " + uniqueTypes);
    System.out.println("物品总数: " + totalItems);
}
```

### 2. 查找特定物品

```java
public IAEItemStack findItem(IStorageGrid storageGrid, ItemStack pattern) {
    IMEMonitor<IAEItemStack> monitor = storageGrid.getItemInventory();
    IItemList<IAEItemStack> items = monitor.getStorageList();
    
    IAEItemStack lookup = AEApi.instance().storage().createItemStack(pattern);
    return items.findPrecise(lookup);
}
```

## 相关文档

- [CORE_ARCHITECTURE.md](./CORE_ARCHITECTURE.md) - 核心架构
- [NETWORK_SYSTEM.md](./NETWORK_SYSTEM.md) - 网络系统
- [INTERFACE_SYSTEM.md](./INTERFACE_SYSTEM.md) - 接口系统
- [BLOCK_TILEENTITY_GUIDE.md](./BLOCK_TILEENTITY_GUIDE.md) - 方块开发
