## ME 接口

### 接口体系

```
IInterfaceViewable (API 接口，定义 getName/getPatterns/shouldDisplay 等)
  └── IInterfaceHost extends IInterfaceViewable (增加 getInterfaceDuality/getTargets 等)
        ├── TileInterface (方块形态)
        │     └── DualityInterface (核心逻辑)
        ├── PartInterface (部件形态，贴在线缆上)
        │     └── DualityInterface (核心逻辑)
        └── PartP2PInterface (P2P 隧道接口)
              └── DualityInterface (核心逻辑)
```

源文件：
- [`IInterfaceViewable`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/api/util/IInterfaceViewable.java)
- [`IInterfaceHost`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/helpers/IInterfaceHost.java)
- [`TileInterface`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/tile/misc/TileInterface.java)
- [`PartInterface`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/parts/misc/PartInterface.java)
- [`DualityInterface`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/helpers/DualityInterface.java)
- [`InterfaceTerminalRegistry`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/core/features/registries/InterfaceTerminalRegistry.java)

---

## 一、获取对应方向的机器名称

### 调用链

```
AEMEsHandler: machine.getName()
  → IInterfaceHost.getName() (default方法)
    → DualityInterface.getTermName()
```

### getTermName() 逻辑

来源：[`DualityInterface.getTermName()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/helpers/DualityInterface.java#L1466)

```java
public String getTermName() {
    // 优先级1: 自定义名称（玩家重命名）
    if (((ICustomNameObject) this.iHost).hasCustomName()) {
        return ((ICustomNameObject) this.iHost).getCustomName();
    }
    // 优先级2: 目标机器图标物品的未本地化名称
    final ItemStack item = getCrafterIcon();
    if (item != null) {
        return item.getUnlocalizedName();
    } else {
        return "Nothing";
    }
}
```

> **注意**：`getName()` 返回的是语言键而非翻译后名称。如需翻译后名称，应使用 [`getDisplayRep()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/api/util/IInterfaceViewable.java#L52) 获取 ItemStack 后调用 `getDisplayName()`。

### getCrafterIcon() — 获取目标方向机器的图标

来源：[`DualityInterface.getCrafterIcon()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/helpers/DualityInterface.java#L1351)

**步骤1 — 确定方向**：通过 `iHost.getTargets()` 获取接口面向的方向

- [`TileInterface.getTargets()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/tile/misc/TileInterface.java#L220) — 由 `pointAt` 字段决定
- [`PartInterface.getTargets()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/parts/misc/PartInterface.java#L347) — 返回面板所在面

**步骤2 — 获取目标方块**：坐标 + 方向偏移，获取对面的 TileEntity

**步骤3 — 跳过同网络ME接口**：如果对面是同一ME网络的接口则跳过

**步骤4 — 优先查找 ICraftingIconProvider capability**：

```java
ICraftingIconProvider craftingIconProvider = getCapability(directedTile, ICraftingIconProvider.class);
if (craftingIconProvider != null) {
    ItemStack icon = craftingIconProvider.getMachineCraftingIcon();
    if (icon != null) return icon;
}
```

**步骤5 — 回退到方块自身**：对 ICraftingMachine 或有 InventoryAdaptor 的方块，依次尝试 `getPickBlock` → `new ItemStack(block)` → `Item.getItemFromBlock()`

### 多方块机器的名称传播机制

ME接口可能对准多方块的**仓室(Hatch)**而非控制器。GT5 通过控制器向仓室传播图标解决此问题：

**1. 控制器提供自身图标**：[`MetaTileEntity.getMachineCraftingIcon()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/metatileentity/MetaTileEntity.java#L710)

```java
public ItemStack getMachineCraftingIcon() {
    return getStackForm(1);  // = new ItemStack(sBlockMachines, 1, mID)
}
```

**2. 控制器将图标传播给仓室**：结构成型时调用 [`hatch.updateCraftingIcon(this.getMachineCraftingIcon())`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/metatileentity/implementations/MTEMultiBlockBase.java#L1944)

**3. 仓室存储并暴露图标**：[`MTEHatch`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/metatileentity/implementations/MTEHatch.java#L15) 实现了 `ICraftingIconProvider`，通过 `ae2CraftingIcon` 字段存储控制器传来的图标

```java
private ItemStack ae2CraftingIcon;

public final void updateCraftingIcon(ItemStack icon) { this.ae2CraftingIcon = icon; }
public ItemStack getMachineCraftingIcon() { return this.ae2CraftingIcon; }
```

**4. Capability 暴露**：[`MetaTileEntity.getCapability()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/metatileentity/MetaTileEntity.java#L179) 对 `ICraftingIconProvider.class` 返回 `this`，[`BaseMetaTileEntity.getCapability()`](../../tools/GT5-Unofficial-5.09.51.482/src/main/java/gregtech/api/metatileentity/BaseMetaTileEntity.java#L986) 委托给 MTE

### 机器名称完整流程图

```
ME接口 → getCrafterIcon() → 目标方向 TileEntity
    ↓
如果是仓室(Hatch):
    getCapability(ICraftingIconProvider.class) → MTEHatch
    → getMachineCraftingIcon() → ae2CraftingIcon (控制器传播过来的)
    → 返回控制器的 ItemStack → getUnlocalizedName() = 语言键

如果是控制器:
    getCapability(ICraftingIconProvider.class) → MTEMultiBlockBase
    → getMachineCraftingIcon() → getStackForm(1) = 控制器自身ItemStack
    → getUnlocalizedName() = 语言键

如果仓室未加入多方块(ae2CraftingIcon=null):
    ICraftingIconProvider返回null → 回退到方块自身 → 显示仓室名称而非控制器名称

如果对面没有任何机器:
    getCrafterIcon()返回null → getTermName()返回 "Nothing"
```

## 二、通过坐标直接获取单个 ME 接口的样板

无需遍历整个 AE 网络，可以直接通过坐标获取特定 ME 接口的样板数据。

### 关键接口关系

[`IInterfaceHost extends IInterfaceViewable`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/helpers/IInterfaceHost.java#L28)，`IInterfaceHost` 的 default 方法 [`getPatterns()`](../../tools/Applied-Energistics-2-Unofficial-rv3-beta-702-GTNH/src/main/java/appeng/helpers/IInterfaceHost.java#L39) 委托给 `getInterfaceDuality().getPatterns()`，返回的 `AppEngInternalInventory` 中就是样板数据。

因此，只要能拿到 `IInterfaceHost` 实例，就能直接调用 `getPatterns()`。

### 方块形态（TileInterface）

```java
TileEntity te = world.getTileEntity(x, y, z);
if (te instanceof IInterfaceHost) {
    IInterfaceHost host = (IInterfaceHost) te;
    IInventory patterns = host.getPatterns();
}
```

### 部件形态（PartInterface，贴在线缆上）

需要通过 FMP 的 `TileMultipart` 获取：

```java
TileEntity te = world.getTileEntity(x, y, z);
if (te instanceof TileMultipart mp) {
    Iterator<TMultiPart> it = mp.partList().iterator();
    while (it.hasNext()) {
        TMultiPart part = it.next();
        if (part instanceof IInterfaceHost) {
            IInterfaceHost host = (IInterfaceHost) part;
            IInventory patterns = host.getPatterns();
        }
    }
}
```

### 统一写法（兼容两种形态）

```java
TileEntity te = world.getTileEntity(x, y, z);
IInventory patterns = null;

if (te instanceof IInterfaceHost) {
    // 方块形态
    patterns = ((IInterfaceHost) te).getPatterns();
} else if (te instanceof TileMultipart mp) {
    // 部件形态（FMP）
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

> `IInterfaceHost.getPatterns()` 的默认实现就是 `getInterfaceDuality().getPatterns()`，与接口终端（Interface Terminal）看到的数据来源完全一致。
