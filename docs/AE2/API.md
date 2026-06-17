## AE2 API 参考

### 核心 API 入口

```java
AEApi api = AEApi.instance();
IAEItemStack aeStack = api.storage().createItemStack(itemStack);
IAEFluidStack aeFluid = api.storage().createFluidStack(fluidStack);
```

---

## 网格访问

```java
IGridNode node = host.getGridNode(ForgeDirection.UNKNOWN);
IGrid grid = node.getGrid();

IStorageGrid storage = grid.getCache(IStorageGrid.class);
ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
IPathingGrid pathing = grid.getCache(IPathingGrid.class);
```

节点状态：`node.isActive()`, `node.isPowered()`, `node.getUsedChannels()`

---

## 存储操作

### 存储层次

```
IStorageGrid
  → IMEMonitor<T> (监听+读写)
    → IItemList<T> (完整列表)
      → IAEStack<T> (单个堆栈)
        → ItemStack / FluidStack
```

### 读取存储

```java
IMEMonitor<IAEItemStack> items = storage.getItemInventory();
IItemList<IAEItemStack> itemList = items.getStorageList();

for (IAEItemStack stack : itemList) {
    ItemStack mcStack = stack.getItemStack();
    long count = stack.getStackSize();
}
```

流体类似：`storage.getFluidInventory()` → `IAEFluidStack` → `getFluidStack()`

### 注入物品

```java
IAEItemStack aeStack = AEApi.instance().storage().createItemStack(itemStack);
IAEItemStack notInjected = items.injectItems(aeStack, Actionable.MODULATE, actionSource);
```

### 提取物品

```java
IAEItemStack request = AEApi.instance().storage().createItemStack(pattern);
request.setStackSize(amount);
IAEItemStack extracted = items.extractItems(request, Actionable.MODULATE, actionSource);
```

### 操作类型

```java
Actionable.SIMULATE   // 模拟，不改变实际存储
Actionable.MODULATE   // 实际执行
```

### 监听存储变化

```java
items.addListener(new IMEMonitorHandlerReceiver<IAEItemStack>() {
    @Override
    public void postChange(IBaseMonitor<IAEItemStack> monitor, Iterable<IAEItemStack> change, BaseActionSource source) {
        // change 中的 stack.getStackSize() 正数=增加，负数=减少
    }
    @Override
    public boolean isValid(Object effectiveGrid) { return true; }
}, owner);
```

---

## 合成操作

### 合成架构

```
ICraftingGrid (合成网格)
  ├── CraftingJob (合成任务)
  │     └── CraftingTreeNode (递归合成树)
  │           └── CraftingTreeProcess (合成处理)
  ├── ICraftingCPU (合成CPU)
  └── ICraftingPatternDetails (样板详情)
```

### 提交合成任务

```java
ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
IAEItemStack request = AEApi.instance().storage().createItemStack(outputItem);
request.setStackSize(amount);

ICraftingLink link = crafting.beginCraftingJob(world, grid, actionSource, request, callback);
```

### 读取样板

```java
for (IGridNode node : grid.getMachines(IInterfaceHost.class)) {
    IInterfaceHost host = (IInterfaceHost) node.getMachine();
    IInventory patterns = host.getPatterns();

    for (int i = 0; i < patterns.getSizeInventory(); i++) {
        ItemStack patternStack = patterns.getStackInSlot(i);
        if (patternStack != null && patternStack.getItem() instanceof ICraftingPatternItem) {
            ICraftingPatternDetails details =
                ((ICraftingPatternItem) patternStack.getItem()).getPattern(patternStack, world);
            IAEItemStack[] inputs = details.getInputs();
            IAEItemStack[] outputs = details.getOutputs();
            boolean isCraftable = details.isCraftable(); // true=合成样板, false=处理样板
        }
    }
}
```

### 遍历合成 CPU

```java
ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
for (ICraftingCPU cpu : crafting.getCpus()) {
    String name = cpu.getName();
    long storage = cpu.getAvailableStorage();
    int processors = cpu.getCoProcessors();
    boolean busy = cpu.isBusy();
}
```

---

## 接口操作

### 遍历网络接口

```java
Set<Class<? extends IInterfaceViewable>> classes = InterfaceTerminalRegistry.instance().getSupportedClasses();
for (Class<? extends IInterfaceViewable> clazz : classes) {
    for (IGridNode node : grid.getMachines(clazz)) {
        IInterfaceViewable machine = (IInterfaceViewable) node.getMachine();
        String name = machine.getName();
        IInventory patterns = machine.getPatterns();
    }
}
```

### 通过坐标直接获取

```java
TileEntity te = world.getTileEntity(x, y, z);
IInventory patterns = null;

if (te instanceof IInterfaceHost) {
    patterns = ((IInterfaceHost) te).getPatterns();
} else if (te instanceof TileMultipart mp) {
    Iterator<TMultiPart> it = mp.partList().iterator();
    while (it.hasNext()) {
        TMultiPart part = it.next();
        if (part instanceof IInterfaceHost) {
            patterns = ((IInterfaceHost) part).getPatterns();
            break;
        }
    }
}
```

---

## 常用枚举

| 枚举 | 值 |
|------|------|
| `AECableType` | `GLASS`, `COVERED`, `SMART`, `DENSE_SMART` |
| `Actionable` | `SIMULATE`, `MODULATE` |
| `YesNo` | `YES`, `NO` |
| `RedstoneMode` | `IGNORE`, `LOW`, `HIGH`, `SIGNAL`, `NO_SIGNAL` |

## 错误处理

```java
try {
    IGrid grid = node.getGrid();
    IStorageGrid storage = grid.getCache(IStorageGrid.class);
} catch (GridAccessException e) {
    // 网络不可访问
}
```
