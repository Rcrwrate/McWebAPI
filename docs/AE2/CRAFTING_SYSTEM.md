# AE2 合成系统详解

## 概述

AE2 的自动合成系统是模组最强大的功能之一，支持基于样板的自动化合成、处理链管理和实时合成监控。

## 合成系统架构

### 核心组件

```
CraftingJob (合成任务)
  └── CraftingTreeNode (合成树节点)
        ├── CraftingTreeProcess (合成处理)
        │     └── CraftingWatcher (合成监视器)
        └── 输入/输出栈
```

### 关键接口和类

1. **ICraftingGrid** - 合成网格，管理整个合成系统
2. **CraftingJob** - 单个合成任务
3. **CraftingTreeNode** - 合成树的递归节点
4. **ICraftingPatternDetails** - 合成样板详情
5. **CraftingWatcher** - 监控库存变化触发自动合成

## 合成流程

### 1. 合成任务提交流程

```java
// 客户端请求合成
ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);

// 创建合成请求
IAEItemStack request = AEApi.instance().storage().createItemStack(targetItem);
request.setStackSize(amount);

// 提交合成任务
ICraftingLink link = craftingGrid.beginCraftingJob(
    world,
    grid,
    actionSource,
    request,
    null // 回调函数
);
```

### 2. 合成树构建

```java
// CraftingJob 构建合成树
public CraftingJob(World world, IGrid grid, BaseActionSource actionSource, 
                   IAEItemStack output, ICraftingCallback callback) {
    // 创建根节点
    this.tree = new CraftingTreeNode(
        this,
        null, // 父节点
        output, // 输出物品
        null, // 样板（稍后查找）
        1, // 深度
        null // 链接
    );
}

// CraftingTreeNode 递归构建子节点
public CraftingTreeNode(CraftingJob job, CraftingTreeNode parent, 
                        IAEItemStack output, ICraftingPatternDetails pattern, 
                        int depth, CraftingLink link) {
    this.job = job;
    this.parent = parent;
    this.output = output;
    this.pattern = pattern;
    this.depth = depth;
    
    if (pattern != null) {
        // 获取输入物品
        IAEItemStack[] inputs = pattern.getInputs();
        
        // 为每个输入创建子节点
        for (IAEItemStack input : inputs) {
            if (input != null) {
                // 递归创建子节点
                CraftingTreeNode child = new CraftingTreeNode(
                    job, this, input, null, depth + 1, link
                );
                this.nodes.add(child);
            }
        }
    }
}
```

### 3. 合成执行流程

```java
// 在 Tick 中执行合成
@Override
public void onTick() {
    if (this.remainingOperations > 0 && this.currentPattern != null) {
        // 检查输入物品是否齐全
        if (this.hasAllInputs()) {
            // 提取输入物品
            this.extractInputs();
            
            // 执行合成
            this.executeCrafting();
            
            // 输出物品
            this.outputResults();
            
            this.remainingOperations--;
        }
    }
}

// 提取输入物品
private void extractInputs() {
    IStorageGrid storage = this.grid.getCache(IStorageGrid.class);
    IMEMonitor<IAEItemStack> inventory = storage.getItemInventory();
    
    for (IAEItemStack input : this.currentPattern.getInputs()) {
        if (input != null) {
            // 从网络提取
            IAEItemStack extracted = inventory.extractItems(
                input.copy(),
                Actionable.MODULATE,
                this.actionSource
            );
            
            // 放入临时库存
            this.craftingInventory.add(extracted);
        }
    }
}
```

## 合成样板

### 1. 编码样板

样板定义了输入输出关系：

```java
// 创建样板
public ItemStack encodePattern(IInventory craftingTable, ItemStack output) {
    ItemStack pattern = AEApi.instance().definitions().items().blankPattern().maybeStack(1).orNull();
    
    if (pattern != null && pattern.getItem() instanceof ICraftingPatternItem) {
        NBTTagCompound tag = new NBTTagCompound();
        
        // 设置输入
        NBTTagList inputs = new NBTTagList();
        for (int i = 0; i < 9; i++) {
            ItemStack input = craftingTable.getStackInSlot(i);
            if (input != null) {
                NBTTagCompound inputTag = new NBTTagCompound();
                input.writeToNBT(inputTag);
                inputs.appendTag(inputTag);
            }
        }
        tag.setTag("in", inputs);
        
        // 设置输出
        NBTTagCompound outputTag = new NBTTagCompound();
        output.writeToNBT(outputTag);
        tag.setTag("out", outputTag);
        
        pattern.setTagCompound(tag);
    }
    
    return pattern;
}
```

### 2. 读取样板

```java
// 从样板读取信息
public ICraftingPatternDetails readPattern(ItemStack patternStack) {
    if (patternStack != null && patternStack.getItem() instanceof ICraftingPatternItem) {
        ICraftingPatternItem patternItem = (ICraftingPatternItem) patternStack.getItem();
        return patternItem.getPattern(patternStack, world);
    }
    return null;
}

// 获取样板输入输出
public void printPatternDetails(ICraftingPatternDetails pattern) {
    System.out.println("=== 样板详情 ===");
    
    // 输出物品
    IAEItemStack[] outputs = pattern.getOutputs();
    for (IAEItemStack output : outputs) {
        if (output != null) {
            System.out.println("输出: " + output.getItemStack().getDisplayName() + " x" + output.getStackSize());
        }
    }
    
    // 输入物品
    IAEItemStack[] inputs = pattern.getInputs();
    for (int i = 0; i < inputs.length; i++) {
        IAEItemStack input = inputs[i];
        if (input != null) {
            System.out.println("输入[" + i + "]: " + input.getItemStack().getDisplayName() + " x" + input.getStackSize());
        }
    }
    
    // 是否是处理样板
    System.out.println("处理样板: " + pattern.isCraftable());
}
```

## 自动合成触发

### 1. 使用监视器

```java
// 创建合成监视器
public class AutoCraftingMonitor {
    private final ICraftingGrid craftingGrid;
    private final IAEItemStack targetItem;
    private final long threshold;
    
    public AutoCraftingMonitor(ICraftingGrid craftingGrid, IAEItemStack target, long threshold) {
        this.craftingGrid = craftingGrid;
        this.targetItem = target;
        this.threshold = threshold;
    }
    
    // 检查库存并触发合成
    public void checkAndCraft(IStorageGrid storageGrid) {
        IMEMonitor<IAEItemStack> monitor = storageGrid.getItemInventory();
        IItemList<IAEItemStack> items = monitor.getStorageList();
        
        IAEItemStack current = items.findPrecise(this.targetItem);
        long currentCount = current != null ? current.getStackSize() : 0;
        
        if (currentCount < this.threshold) {
            long toCraft = this.threshold - currentCount;
            
            // 提交合成任务
            IAEItemStack request = this.targetItem.copy();
            request.setStackSize(toCraft);
            
            this.craftingGrid.beginCraftingJob(
                world,
                grid,
                actionSource,
                request,
                null
            );
        }
    }
}
```

### 2. 批量合成

```java
// 批量合成多个物品
public void craftMultiple(Map<IAEItemStack, Long> requests) {
    for (Map.Entry<IAEItemStack, Long> entry : requests.entrySet()) {
        IAEItemStack request = entry.getKey().copy();
        request.setStackSize(entry.getValue());
        
        this.craftingGrid.beginCraftingJob(
            world,
            grid,
            actionSource,
            request,
            callback
        );
    }
}
```

## 监控合成进度

### 1. 获取活跃合成任务

```java
// 从 CraftingGrid 获取活跃任务
public List<ActiveCraftingJob> getActiveCraftingJobs(ICraftingGrid craftingGrid) {
    List<ActiveCraftingJob> jobs = new ArrayList<>();
    
    // 访问活跃任务列表
    // 注意：这需要访问内部实现或使用 API
    
    return jobs;
}
```

### 2. 监听合成完成

```java
// 实现合成回调
public class CraftingCallback implements ICraftingCallback {
    @Override
    public void calculationComplete(ICraftingJob job) {
        System.out.println("合成计算完成: " + job.getOutput().getItemStack().getDisplayName());
    }
    
    @Override
    public void jobComplete(ICraftingLink link) {
        System.out.println("合成任务完成！");
    }
}
```

## 进阶合成技巧

### 1. 自定义合成处理器

```java
// 创建自定义合成处理器
public class CustomCraftingHandler extends CraftingTreeProcess {
    private final IInventory processingInventory;
    
    public CustomCraftingHandler(CraftingJob job, CraftingTreeNode parent, 
                                 IAEItemStack output, ICraftingPatternDetails details, 
                                 int depth, CraftingLink link) {
        super(job, parent, output, details, depth, link);
        this.processingInventory = createProcessingInventory();
    }
    
    @Override
    public boolean isDone() {
        // 自定义完成检测逻辑
        return super.isDone() && checkCustomConditions();
    }
    
    private boolean checkCustomConditions() {
        // 检查自定义条件
        return true;
    }
}
```

### 2. 并行合成

```java
// 并行执行多个合成任务
public void executeParallelCrafting(List<ICraftingJob> jobs) {
    ExecutorService executor = Executors.newFixedThreadPool(4);
    
    for (ICraftingJob job : jobs) {
        executor.submit(() -> {
            // 执行合成
            job.start();
            
            // 等待完成
            while (!job.isDone()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            System.out.println("合成完成: " + job.getOutput());
        });
    }
    
    executor.shutdown();
}
```

### 3. 优先级合成

```java
// 按优先级排序合成任务
public void prioritizeCrafting(List<ICraftingJob> jobs) {
    // 按输出物品重要性排序
    jobs.sort((job1, job2) -> {
        IAEItemStack output1 = job1.getOutput();
        IAEItemStack output2 = job2.getOutput();
        
        // 自定义优先级逻辑
        int priority1 = getItemPriority(output1);
        int priority2 = getItemPriority(output2);
        
        return Integer.compare(priority2, priority1); // 降序
    });
    
    // 按优先级执行
    for (ICraftingJob job : jobs) {
        job.start();
    }
}

private int getItemPriority(IAEItemStack stack) {
    // 实现优先级逻辑
    Item item = stack.getItem();
    if (item == Items.diamond) return 100;
    if (item == Items.iron_ingot) return 50;
    return 10;
}
```

## 合成统计

### 1. 计算合成成本

```java
// 计算合成成本
public CraftingCost calculateCraftingCost(ICraftingPatternDetails pattern, IStorageGrid storage) {
    CraftingCost cost = new CraftingCost();
    
    IAEItemStack[] inputs = pattern.getInputs();
    IMEMonitor<IAEItemStack> inventory = storage.getItemInventory();
    
    for (IAEItemStack input : inputs) {
        if (input != null) {
            // 计算所需数量
            long needed = input.getStackSize();
            
            // 检查库存
            IAEItemStack available = inventory.getStorageList().findPrecise(input);
            long has = available != null ? available.getStackSize() : 0;
            
            if (has >= needed) {
                // 库存充足
                cost.availableItems.add(input.copy());
            } else {
                // 需要合成
                IAEItemStack toCraft = input.copy();
                toCraft.setStackSize(needed - has);
                cost.missingItems.add(toCraft);
            }
        }
    }
    
    return cost;
}

public class CraftingCost {
    public List<IAEItemStack> availableItems = new ArrayList<>();
    public List<IAEItemStack> missingItems = new ArrayList<>();
    
    public boolean canCraft() {
        return missingItems.isEmpty();
    }
}
```

### 2. 合成性能分析

```java
// 分析合成性能
public CraftingPerformance analyzeCraftingPerformance(List<ICraftingJob> jobs) {
    CraftingPerformance performance = new CraftingPerformance();
    
    long totalTime = 0;
    long totalItems = 0;
    
    for (ICraftingJob job : jobs) {
        totalTime += job.getDuration();
        totalItems += job.getOutput().getStackSize();
    }
    
    performance.averageTimePerJob = jobs.isEmpty() ? 0 : totalTime / jobs.size();
    performance.itemsPerSecond = totalTime > 0 ? (totalItems * 1000) / totalTime : 0;
    
    return performance;
}
```

## 常见问题

### Q: 合成任务卡住？

A: 检查：
- 是否有足够原材料
- 装配室是否正确连接
- 通道是否充足
- 能量是否充足

### Q: 如何取消合成任务？

A: 调用 `ICraftingLink.cancel()` 方法

### Q: 合成任务优先级如何工作？

A: 装配室按距离控制器的距离和通道分配优先级

## 最佳实践

1. **批量提交**：一次性提交所有合成请求，减少网络通信
2. **监听事件**：使用 ICraftingCallback 监听合成状态
3. **检查成本**：提前计算合成成本，避免中途失败
4. **错误处理**：处理 GridAccessException 和其他网络异常
5. **性能优化**：避免频繁创建 CraftingJob，重用计算结果
6. **并行化**：对于大量独立合成，使用并行处理

## 相关文档

- [CORE_ARCHITECTURE.md](./CORE_ARCHITECTURE.md) - 核心架构
- [NETWORK_SYSTEM.md](./NETWORK_SYSTEM.md) - 网络系统
- [STORAGE_SYSTEM.md](./STORAGE_SYSTEM.md) - 存储系统
- [BLOCK_TILEENTITY_GUIDE.md](./BLOCK_TILEENTITY_GUIDE.md) - 方块开发
