## AE2 与 ForgeMultipart (FMP) 集成

AE2 通过 ForgeMultipart 实现线缆总线和石英火把等方块的多部件共存，使它们可以与其他 FMP 部件（如遮盖板、微方块等）共享同一个方块空间。

### 架构概览

```
TileMultipart (FMP 容器 TileEntity)
  └── partList() → 所有 TMultiPart
       ├── CableBusPart → getCableBus() → CableBusContainer (AE2 线缆总线)
       │     └── 各面 IPart (线缆/面板/终端等)
       └── QuartzTorchPart → getBlock() (石英火把)
```

### 关键源文件

| 文件 | 职责 |
|------|------|
| [`FMP.java`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/integration/modules/FMP.java) | FMP 集成入口，实现 `IPartFactory` + `IPartConverter` |
| [`CableBusPart.java`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/fmp/CableBusPart.java) | AE2 线缆总线的 FMP Part 实现，封装 `CableBusContainer` |
| [`PartRegistry.java`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/fmp/PartRegistry.java) | FMP Part 注册表，映射 AE2 方块到 FMP Part |
| [`QuartzTorchPart.java`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/fmp/QuartzTorchPart.java) | 石英火把的 FMP Part 实现 |
| [`FMPEvent.java`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/fmp/FMPEvent.java) | FMP 放置事件处理 |
| [`FMPPlacementHelper.java`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/fmp/FMPPlacementHelper.java) | FMP 放置辅助 |

### 1. 初始化与注册

[`FMP.init()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/integration/modules/FMP.java#L93) 中完成三件事：

1. **注册微方块材料**：将 AE2 的石英、陨石等方块注册为 FMP 微方块材料（`BlockMicroMaterial.createAndRegister`）
2. **注册 Part 工厂**：`MultiPartRegistry.registerParts(this, data)` — `this` 实现了 `IPartFactory`，FMP 通过 [`createPart()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/integration/modules/FMP.java#L54) 按名称创建 Part
3. **注册方块转换器**：`MultiPartRegistry.registerConverter(this)` — `this` 实现了 `IPartConverter`，[`convert()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/integration/modules/FMP.java#L65) 将普通 AE2 方块转为 FMP Part

[`PartRegistry`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/fmp/PartRegistry.java#L23) 枚举定义了两种 Part：

```java
QuartzTorchPart("ae2_torch", BlockQuartzTorch.class, QuartzTorchPart.class),
CableBusPart("ae2_cablebus", BlockCableBus.class, CableBusPart.class);
```

### 2. CableBusPart — 核心适配器

[`CableBusPart`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/fmp/CableBusPart.java#L75) 继承自 `JCuboidPart`，实现了 `AEMultiTile` 接口，内部封装了一个 [`CableBusContainer`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/parts/CableBusContainer.java)：

```java
private CableBusContainer cb = new CableBusContainer(this);
```

它将所有 `IPartHost` 接口方法委托给 `cb`：

| CableBusPart 方法 | 委托给 CableBusContainer |
|---|---|
| `getPart(side)` | `cb.getPart(side)` |
| `addPart(is, side, owner)` | `cb.addPart(is, side, owner)` |
| `removePart(side, suppressUpdate)` | `cb.removePart(side, suppressUpdate)` |
| `getFacadeContainer()` | `cb.getFacadeContainer()` |
| `getColor()` | `cb.getColor()` |
| `selectPart(pos)` | `cb.selectPart(pos)` |
| `isEmpty()` | `cb.isEmpty()` |

`getType()` 返回 [`PartRegistry.CableBusPart.getName()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/fmp/CableBusPart.java#L143) = `"ae2_cablebus"`。

### 3. 从 TileEntity 获取 AE2 信息

[`FMP.getOrCreateHost()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/integration/modules/FMP.java#L129) 展示了标准流程：

```java
TileMultipart mp = TileMultipart.getOrConvertTile(tile.getWorldObj(), loc);
if (mp != null) {
    Iterator<TMultiPart> i = mp.partList().iterator();
    while (i.hasNext()) {
        TMultiPart p = i.next();
        if (p instanceof CableBusPart) {
            return (IPartHost) p;  // CableBusPart 实现了 IPartHost
        }
    }
}
```

[`FMP.getCableContainer()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/integration/modules/FMP.java#L152) 更直接地获取 `CableBusContainer`：

```java
if (te instanceof TileMultipart mp) {
    Iterator<TMultiPart> i = mp.partList().iterator();
    while (i.hasNext()) {
        TMultiPart p = i.next();
        if (p instanceof CableBusPart) {
            return ((CableBusPart) p).getCableBus();
        }
    }
}
```

### 4. 解析 CableBusPart 内部信息

获取到 `CableBusContainer` 后，可以访问：

```java
CableBusContainer cbc = cableBusPart.getCableBus();

// 各面安装的 Part（线缆、终端、面板等）
for (ForgeDirection dir : ForgeDirection.values()) {
    IPart sidePart = cbc.getPart(dir);
    if (sidePart != null) {
        ItemStack stack = sidePart.getItemStack(PartItemStack.Break);
        if (sidePart instanceof IPartCable) {
            AECableType cableType = sidePart.getCableConnectionType(dir);
        }
    }
}

// 伪装板
IFacadeContainer facades = cbc.getFacadeContainer();
for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
    IFacadePart facade = facades.getFacade(dir);
    if (facade != null) {
        ItemStack facadeStack = facade.getItemStack();
    }
}

// 频道颜色
AEColor color = cbc.getColor();
```

### 5. QuartzTorchPart

[`QuartzTorchPart`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/fmp/QuartzTorchPart.java#L27) 继承自 `McSidedMetaPart`，是石英火把的 FMP 封装：

- `getType()` 返回 `"ae2_torch"`
- [`getBlock()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/fmp/QuartzTorchPart.java#L80) 返回原始的 `BlockQuartzTorch`
- `meta` 字段存储朝向（`ForgeDirection` 的 ordinal）

### 6. 方块转换

[`FMP.convert()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/integration/modules/FMP.java#L65) 将普通 AE2 方块转为 FMP Part，使已有方块可以合并到多方块容器中：

```java
public TMultiPart convert(final World world, final BlockCoord pos) {
    final Block blk = world.getBlock(pos.x, pos.y, pos.z);
    final int meta = world.getBlockMetadata(pos.x, pos.y, pos.z);
    final TMultiPart part = PartRegistry.getPartByBlock(blk, meta);
    if (part instanceof CableBusPart cbp) {
        cbp.convertFromTile(world.getTileEntity(pos.x, pos.y, pos.z));
    }
    return part;
}
```

[`PartRegistry.getPartByBlock()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/fmp/PartRegistry.java#L39) 通过方块类型匹配对应的 Part 类型。
