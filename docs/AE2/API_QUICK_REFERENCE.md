# AE2 API 快速参考

## 核心 API 入口

```java
// 获取 API 实例
AEApi api = AEApi.instance();
IAppEngApi appEngApi = api;
```

## 网格访问

### 获取网格节点

```java
// 从方块获取节点
IGridHost host = (IGridHost) tileEntity;
IGridNode node = host.getGridNode(ForgeDirection.UNKNOWN);

// 创建新节点
IGridNode node = api.createGridNode(new BlockPos(x, y, z));
```

### 访问网格缓存

```java
// 从节点获取网格
IGrid grid = node.getGrid();

// 获取存储网格
IStorageGrid storage = grid.getCache(IStorageGrid.class);

// 获取合成网格
ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);

// 获取能量网格
IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
```

## 存储操作

### 读取存储

```java
// 获取物品监视器
IMEMonitor<IAEItemStack> items = storage.getItemInventory();

// 获取物品列表
IItemList<IAEItemStack> itemList = items.getStorageList();

// 遍历物品
for (IAEItemStack stack : itemList) {
    ItemStack mcStack = stack.getItemStack();
    long count = stack.getStackSize();
}
```

### 注入物品

```java
// 创建 AE 堆栈
IAEItemStack aeStack = api.storage().createItemStack(itemStack);

// 注入到网络
IAEItemStack notInjected = items.injectItems(
    aeStack,
    Actionable.MODULATE,
    BaseActionSource.fromPlayer(player)
);
```

### 提取物品

```java
// 创建请求
IAEItemStack request = api.storage().createItemStack(pattern);
request.setStackSize(amount);

// 从网络提取
IAEItemStack extracted = items.extractItems(
    request,
    Actionable.MODULATE,
    BaseActionSource.fromPlayer(player)
);

if (extracted != null) {
    ItemStack result = extracted.getItemStack();
}
```

## 合成操作

### 提交合成任务

```java
// 创建请求
IAEItemStack request = api.storage().createItemStack(outputItem);
request.setStackSize(amount);

// 提交合成
ICraftingLink link = crafting.beginCraftingJob(
    world,
    grid,
    actionSource,
    request,
    callback
);
```

### 获取样板

```java
// 从网格获取所有接口
IMachineSet interfaces = grid.getMachines(IInterfaceHost.class);

for (IGridNode node : interfaces) {
    IInterfaceHost host = (IInterfaceHost) node.getMachine();
    IInventory patterns = host.getPatterns();
    
    for (int i = 0; i < patterns.getSizeInventory(); i++) {
        ItemStack patternStack = patterns.getStackInSlot(i);
        if (patternStack != null) {
            ICraftingPatternDetails pattern = 
                ((ICraftingPatternItem) patternStack.getItem())
                    .getPattern(patternStack, world);
            // 处理样板
        }
    }
}
```

## 网络和节点

### 检查节点状态

```java
// 检查节点是否激活
boolean active = node.isActive();

// 检查节点是否有能量
boolean powered = node.isPowered();

// 获取使用通道数
int channels = node.getUsedChannels();
```

### 网格事件订阅

```java
@MENetworkEventSubscribe
public void onPowerChange(MENetworkPowerStatusChange event) {
    if (this.node == event.node) {
        // 处理能量变化
    }
}

@MENetworkEventSubscribe
public void onChannelChange(MENetworkChannelChanged event) {
    // 处理通道变化
}
```

## 实用工具

### 物品/流体转换

```java
// 物品到 AE 堆栈
IAEItemStack itemStack = api.storage().createItemStack(itemStack);

// 流体到 AE 堆栈
IAEFluidStack fluidStack = api.storage().createFluidStack(fluidStack);

// AE 堆栈到 Minecraft 堆栈
ItemStack mcStack = aeStack.getItemStack();
FluidStack mcFluid = aeStack.getFluidStack();
```

### 位置工具

```java
// 创建位置
BlockPos pos = new BlockPos(x, y, z);
DimensionalCoord coord = new DimensionalCoord(world, x, y, z);

// 从 TileEntity 获取位置
DimensionalCoord coord = new DimensionalCoord(tileEntity);
```

### 配置管理

```java
// 创建配置管理器
ConfigManager configManager = new ConfigManager(this);

// 注册配置选项
configManager.registerSetting(Settings.BLOCKING, YesNo.NO);
configManager.registerSetting(Settings.CRAFTING_ONLY, YesNo.NO);

// 获取/设置配置
YesNo blocking = (YesNo) configManager.getSetting(Settings.BLOCKING);
configManager.putSetting(Settings.BLOCKING, YesNo.YES);
```

## 常见枚举

### 通道类型

```java
AECableType.GLASS      // 玻璃线缆
AECableType.COVERED    // 覆盖线缆
AECableType.SMART      // 智能线缆
AECableType.DENSE_SMART // 致密智能线缆
```

### 是/否选项

```java
YesNo.YES
YesNo.NO
```

### 红石模式

```java
RedstoneMode.IGNORE    // 忽略红石
RedstoneMode.LOW       // 低信号激活
RedstoneMode.HIGH      // 高信号激活
RedstoneMode.SIGNAL    // 有信号激活
RedstoneMode.NO_SIGNAL // 无信号激活
```

### 操作类型

```java
Actionable.SIMULATE    // 模拟操作
Actionable.MODULATE    // 实际执行
```

## 错误处理

### 网格访问异常

```java
try {
    IGrid grid = node.getGrid();
    IStorageGrid storage = grid.getCache(IStorageGrid.class);
    // 使用 storage
} catch (GridAccessException e) {
    // 网络不可访问
    AELog.error(e);
}
```

### 节点状态检查

```java
if (node != null && node.isActive()) {
    // 节点在线，可以安全操作
    try {
        IGrid grid = node.getGrid();
        // 使用 grid
    } catch (GridAccessException e) {
        // 处理异常
    }
} else {
    // 节点离线
}
```

## 调试技巧

### 打印存储内容

```java
public void printStorage(IStorageGrid storage) {
    IMEMonitor<IAEItemStack> items = storage.getItemInventory();
    IItemList<IAEItemStack> list = items.getStorageList();
    
    AELog.info("=== 存储内容 ===");
    for (IAEItemStack stack : list) {
        AELog.info("{} x{}", 
            stack.getItemStack().getDisplayName(),
            stack.getStackSize()
        );
    }
}
```

### 检查网格状态

```java
public void checkGridStatus(IGrid grid) {
    IPathingGrid pathing = grid.getCache(IPathingGrid.class);
    IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
    
    AELog.info("控制器状态: {}", pathing.getControllerState());
    AELog.info("网络启动中: {}", pathing.isNetworkBooting());
    AELog.info("能量存储: {}/{}", 
        energy.getEnergyStored(),
        energy.getMaxStoredPower()
    );
}
```

## 性能提示

1. **缓存引用**：缓存 grid、storage、crafting 等引用
2. **批量操作**：尽量批量处理物品和合成
3. **避免阻塞**：不要在主线程执行耗时网络操作
4. **使用事件**：监听事件而不是轮询状态
5. **及时清理**：及时移除监听器和回调

## 相关文档

- [CORE_ARCHITECTURE.md](./CORE_ARCHITECTURE.md) - 核心架构
- [NETWORK_SYSTEM.md](./NETWORK_SYSTEM.md) - 网络系统详解
- [STORAGE_SYSTEM.md](./STORAGE_SYSTEM.md) - 存储系统详解
- [BLOCK_TILEENTITY_GUIDE.md](./BLOCK_TILEENTITY_GUIDE.md) - 方块开发指南
