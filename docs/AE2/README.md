# AE2 开发文档

基于 AE2 源代码分析的技术文档，源码位于 `tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/`。

## 文档索引

| 文档 | 内容 |
|------|------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 核心架构：包结构、方块/TE层次、ME网络、网格缓存、创建网络方块 |
| [API.md](./API.md) | API 参考：网格访问、存储读写、合成操作、接口遍历、常用枚举 |
| [ME.md](./ME.md) | ME 接口：接口体系、获取机器名称、多方块传播机制、坐标直取样板 |
| [FMP.md](./FMP.md) | ForgeMultipart 集成：CableBusPart、PartRegistry、解析 FMP 方块 |

## 快速开始

```java
AEApi api = AEApi.instance();
IGrid grid = node.getGrid();

// 读取存储
IStorageGrid storage = grid.getCache(IStorageGrid.class);
for (IAEItemStack stack : storage.getItemInventory().getStorageList()) {
    stack.getItemStack().getDisplayName();  // 物品名
    stack.getStackSize();                    // 数量
}

// 遍历接口
for (IGridNode n : grid.getMachines(IInterfaceHost.class)) {
    IInterfaceHost host = (IInterfaceHost) n.getMachine();
    host.getPatterns();  // 样板
    host.getName();      // 名称
}
```
