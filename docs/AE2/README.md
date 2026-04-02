# AE2 开发文档

欢迎使用 AE2 (Applied Energistics 2) 开发文档！本文档基于 AE2 源代码分析，提供详细的架构、API 和开发指南。

## 文档列表

### 📚 核心架构
- **[CORE_ARCHITECTURE.md](./CORE_ARCHITECTURE.md)** - AE2 核心架构概述
  - 模组整体结构
  - 关键组件介绍
  - 包组织和模块划分
  - 开发建议和最佳实践

### 🔌 网络系统
- **[NETWORK_SYSTEM.md](./NETWORK_SYSTEM.md)** - ME 网络系统详解
  - 网络基础概念
  - 网格缓存系统
  - 节点连接流程
  - 网络事件处理
  - 网络诊断和调试

### 💾 存储系统
- **[STORAGE_SYSTEM.md](./STORAGE_SYSTEM.md)** - 存储系统详解
  - 存储架构层次
  - 核心接口和类
  - 存储单元类型
  - 物品/流体操作
  - 外部存储集成

### 🔧 接口系统
- **[INTERFACE_SYSTEM.md](./INTERFACE_SYSTEM.md)** - 接口系统详解
  - 接口类型和用途
  - IInterfaceHost 和 DualityInterface
  - 物品推送机制
  - 样板处理流程
  - 配置模式详解

### 🏗️ 方块开发
- **[BLOCK_TILEENTITY_GUIDE.md](./BLOCK_TILEENTITY_GUIDE.md)** - 方块与 TileEntity 开发指南
  - 方块层次结构
  - 创建网络方块
  - 能量集成
  - 事件处理
  - GUI 集成
  - 配置管理

### ⚙️ 合成系统
- **[CRAFTING_SYSTEM.md](./CRAFTING_SYSTEM.md)** - 合成系统详解
  - 合成架构和流程
  - 合成树构建
  - 样板编码和读取
  - 自动合成触发
  - 合成进度监控

### 📝 API 快速参考
- **[API_QUICK_REFERENCE.md](./API_QUICK_REFERENCE.md)** - API 快速参考
  - 核心 API 入口
  - 网格访问
  - 存储操作
  - 合成操作
  - 实用工具函数
  - 常见枚举和配置

## 快速开始

### 1. 环境搭建

确保你的开发环境已配置：
- Minecraft Forge 开发环境
- AE2 源码或库文件
- Java 开发工具 (JDK 8+)

### 2. 基础示例

```java
// 获取 API 实例
AEApi api = AEApi.instance();

// 访问网格存储
IStorageGrid storage = grid.getCache(IStorageGrid.class);
IMEMonitor<IAEItemStack> items = storage.getItemInventory();

// 读取物品
IItemList<IAEItemStack> itemList = items.getStorageList();
for (IAEItemStack stack : itemList) {
    System.out.println("物品: " + stack.getItemStack().getDisplayName() 
                     + " 数量: " + stack.getStackSize());
}
```

### 3. 创建网络方块

参考 [BLOCK_TILEENTITY_GUIDE.md](./BLOCK_TILEENTITY_GUIDE.md) 创建连接到 ME 网络的方块。

## 关键概念

### ME 网络
AE2 的核心概念，连接多个设备实现自动化存储、物流和合成。

### 网格缓存 (GridCache)
网络功能模块，包括存储、合成、能量等。

### 双重模式 (Duality)
复杂功能拆分为 Duality 类，如 DualityInterface。

### 存储单元 (Cells)
物理存储介质，支持物品、流体和空间存储。

### 接口 (Interface)
连接网络与外部设备的桥梁，支持物品推送和样板供应。

## 开发建议

1. **使用 API**：优先使用 `appeng.api` 包中的接口
2. **异常处理**：处理 GridAccessException 等网络异常
3. **线程安全**：网络操作可能在任意线程执行
4. **事件驱动**：使用 MENetworkEventSubscribe 监听事件
5. **性能优化**：缓存引用，批量操作
6. **节点生命周期**：正确创建和销毁网格节点

## 文档更新

本文档基于 AE2 rv3-beta-695-GTNH 版本编写，随着版本更新可能需要调整。

## 贡献

欢迎提交问题、建议和改进！
