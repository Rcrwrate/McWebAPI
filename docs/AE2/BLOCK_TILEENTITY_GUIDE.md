# AE2 方块与 TileEntity 开发指南

## 概述

本指南介绍如何在 AE2 中创建自定义方块和 TileEntity，包括网络集成、存储访问和自动化功能。

## 方块层次结构

```
Block (Minecraft)
  └── AEBaseBlock (AE2 基础方块)
        ├── AEBaseTileBlock (带 TileEntity 的方块)
        ├── AEBaseItemBlock (带物品形式的方块)
        └── AEDecorativeBlock (装饰方块)
```

### 选择合适的基类

| 基类 | 用途 | TileEntity |
|------|------|------------|
| `AEBaseBlock` | 简单方块 | 否 |
| `AEBaseTileBlock` | 复杂方块 | 是 |
| `AEDecorativeBlock` | 装饰方块 | 否 |
| `AEBaseSlabBlock` | 半砖 | 否 |
| `AEBaseStairBlock` | 楼梯 | 否 |

## 创建网络方块

### 示例：连接到 ME 网络的方块

```java
public class BlockCustomMachine extends AEBaseTileBlock {
    
    public BlockCustomMachine() {
        super(Material.iron);
        this.setBlockName("customMachine");
        this.setHardness(2.0f);
    }
    
    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileCustomMachine();
    }
}

// TileEntity 实现
public class TileCustomMachine extends AEBaseTile implements IGridHost {
    private IGridNode node;
    
    @Override
    public void validate() {
        super.validate();
        // 创建网格节点
        if (this.node == null) {
            this.node = AEApi.instance().createGridNode(
                new BlockPos(this.xCoord, this.yCoord, this.zCoord)
            );
        }
    }
    
    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return this.node;
    }
    
    @Override
    public void gridChanged() {
        // 网络变化时调用
    }
    
    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.SMART;
    }
    
    @Override
    public void securityBreak() {
        // 安全权限被破坏时调用
    }
}
```

## 访问存储

### 从 TileEntity 访问网络存储

```java
public class TileStorageAccessor extends AEBaseTile implements IGridHost {
    private IGridNode node;
    
    // 获取存储网格
    public IStorageGrid getStorageGrid() {
        try {
            if (this.node != null && this.node.isActive()) {
                IGrid grid = this.node.getGrid();
                return grid.getCache(IStorageGrid.class);
            }
        } catch (GridAccessException e) {
            AELog.error(e);
        }
        return null;
    }
    
    // 读取物品列表
    public List<ItemStack> getStoredItems() {
        IStorageGrid storage = getStorageGrid();
        if (storage == null) return Collections.emptyList();
        
        IMEMonitor<IAEItemStack> itemMonitor = storage.getItemInventory();
        IItemList<IAEItemStack> itemList = itemMonitor.getStorageList();
        
        List<ItemStack> result = new ArrayList<>();
        for (IAEItemStack stack : itemList) {
            result.add(stack.getItemStack());
        }
        return result;
    }
}
```

## 创建接口方块

### 示例：自定义 ME 接口

```java
public class TileCustomInterface extends AEBaseTile implements IInterfaceHost {
    private final DualityInterface duality;
    
    public TileCustomInterface() {
        this.duality = new DualityInterface(this.getProxy(), this);
    }
    
    @Override
    public DualityInterface getInterfaceDuality() {
        return this.duality;
    }
    
    @Override
    public String getName() {
        return "custom.interface";
    }
    
    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return this.duality.getGridNode();
    }
    
    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }
}
```

## 能量集成

### 能量接收方块

```java
public class TilePoweredBlock extends AEBaseTile implements IGridHost, IEnergySink {
    private IGridNode node;
    private final double MAX_ENERGY = 10000;
    private double storedEnergy = 0;
    
    @Override
    public double injectPower(ForgeDirection from, double amount, double voltage) {
        double toStore = Math.min(amount, MAX_ENERGY - storedEnergy);
        storedEnergy += toStore;
        return toStore;
    }
    
    @Override
    public double getPowerDemand(ForgeDirection from, double voltage) {
        return MAX_ENERGY - storedEnergy;
    }
    
    @Override
    public AECableType getCableConnectionType(ForgeDirection dir) {
        return AECableType.COVERED;
    }
}
```

## 事件处理

### 监听网络事件

```java
public class TileNetworkListener extends AEBaseTile implements IGridHost {
    private IGridNode node;
    
    @MENetworkEventSubscribe
    public void onPowerChange(MENetworkPowerStatusChange event) {
        if (this.node == event.node) {
            // 能量状态变化
            this.updatePowerState();
        }
    }
    
    @MENetworkEventSubscribe
    public void onChannelChange(MENetworkChannelChanged event) {
        // 通道变化
    }
    
    private void updatePowerState() {
        // 更新方块状态
        this.markForUpdate();
    }
}
```

## 配置管理

### 使用配置管理器

```java
public class TileConfigurable extends AEBaseTile implements IGridHost, IConfigManagerHost {
    private final ConfigManager configManager = new ConfigManager(this);
    
    public TileConfigurable() {
        // 注册配置选项
        this.configManager.registerSetting(Settings.INTERFACE_TERMINAL, YesNo.YES);
        this.configManager.registerSetting(Settings.BLOCKING, YesNo.NO);
        this.configManager.registerSetting(Settings.CRAFTING_ONLY, YesNo.NO);
    }
    
    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
        // 配置变更时调用
        this.markForUpdate();
    }
    
    public IConfigManager getConfigManager() {
        return this.configManager;
    }
}
```

## GUI 集成

### 创建带 GUI 的方块

```java
public class TileGUIMachine extends AEBaseTile implements IGridHost {
    
    @Override
    public boolean onActivate(EntityPlayer player, ForgeDirection side, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) {
            if (!this.worldObj.isRemote) {
                ContainerOpener.openContainer(
                    player, 
                    this, 
                    ForgeDirection.getOrientation(this.getForward())
                );
            }
            return true;
        }
        return false;
    }
    
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player) {
        return new ContainerCustomMachine(player.inventory, this);
    }
    
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player) {
        return new GuiCustomMachine(player.inventory, this);
    }
}
```

## 最佳实践

1. **继承 AEBaseTile**：自动获得网络、能量、渲染等基础功能
2. **实现适当的接口**：IGridHost、IEnergySink 等
3. **正确处理节点生命周期**：在 validate/onChunkUnload 中创建/销毁节点
4. **使用 Duality 模式**：复杂功能拆分为 Duality 类
5. **事件驱动**：使用 MENetworkEventSubscribe 监听网络事件
6. **线程安全**：AE2 操作可能在任意线程执行
7. **性能优化**：缓存网格引用，避免重复查找

## 示例：完整自定义方块

```java
// 方块类
public class BlockCustomNetworkDevice extends AEBaseTileBlock {
    public BlockCustomNetworkDevice() {
        super(Material.iron);
        this.setBlockName("customNetworkDevice");
        this.setHardness(2.0f);
    }
    
    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileCustomNetworkDevice();
    }
}

// TileEntity 类
public class TileCustomNetworkDevice extends AEBaseTile implements IGridHost {
    private IGridNode node;
    private final ConfigManager configManager = new ConfigManager(this);
    
    public TileCustomNetworkDevice() {
        this.configManager.registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
    }
    
    @Override
    public void validate() {
        super.validate();
        if (this.node == null) {
            this.node = AEApi.instance().createGridNode(new BlockPos(this.xCoord, this.yCoord, this.zCoord));
        }
    }
    
    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (this.node != null) {
            this.node.destroy();
        }
    }
    
    @Override
    public IGridNode getGridNode(ForgeDirection dir) {
        return this.node;
    }
    
    @Override
    public void gridChanged() {
        this.markForUpdate();
    }
    
    @MENetworkEventSubscribe
    public void onPowerChange(MENetworkPowerStatusChange event) {
        if (this.node == event.node) {
            this.markForUpdate();
        }
    }
}
```

## 相关文档

- [CORE_ARCHITECTURE.md](./CORE_ARCHITECTURE.md) - 核心架构
- [NETWORK_SYSTEM.md](./NETWORK_SYSTEM.md) - 网络系统
- [STORAGE_SYSTEM.md](./STORAGE_SYSTEM.md) - 存储系统
- [INTERFACE_SYSTEM.md](./INTERFACE_SYSTEM.md) - 接口系统
