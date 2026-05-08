用户想知道如何在AE2源代码中解析一个ForgeMultipart方块的所有信息。我需要先查看AE2的源代码结构，找到与ForgeMultipart相关的代码。

首先，我需要：
1. 查看tools目录下的AE2源代码结构
2. 搜索与ForgeMultipart相关的类或文件
3. 找到解析方块信息的入口点

让我先列出目录结构并搜索相关文件。
用户想了解如何解析一个ForgeMultipart方块的所有信息。我找到了相关文件，现在需要读取FMP集成模块、CableBusPart、PartRegistry等关键文件来理解AE2是如何处理ForgeMultipart方块的。

让我并行读取几个关键文件：
1. FMP.java - FMP集成模块
2. CableBusPart.java - FMP的Part实现
3. PartRegistry.java - Part注册表
4. QuartzTorchPart.java - 另一个FMP part示例
用户想知道如何解析一个ForgeMultipart方块的所有信息。我已经读取了AE2中处理FMP的关键文件。

从代码中可以看出：

1. `FMP.java` 是AE2与ForgeMultipart的集成模块，实现了 `IPartFactory` 和 `IPartConverter`。
2. `CableBusPart.java` 是AE2的核心FMP部件，继承自 `JCuboidPart`，内部封装了 `CableBusContainer`。
3. `PartRegistry.java` 是AE2的FMP部件注册表。
4. `QuartzTorchPart.java` 是一个简单的FMP部件示例。

要解析一个ForgeMultipart方块，关键是：
1. 判断一个TileEntity是否是 `TileMultipart`
2. 从 `TileMultipart` 中获取 `partList()`
3. 遍历partList，检查每个TMultiPart的类型

让我再搜索一下FMP事件处理和TileMultipart的使用，以提供更完整的信息。同时我也需要看看AEHandler.java，因为用户项目中可能有相关的处理代码。
用户想知道如何解析一个ForgeMultipart方块的所有信息。根据我在AE2源码中找到的信息，我现在可以给出一个完整的回答。

核心要点：
1. ForgeMultipart方块使用 `TileMultipart` 作为TileEntity
2. 通过 `TileMultipart.getOrConvertTile(world, pos)` 获取TileMultipart实例
3. `partList()` 返回一个Scala集合，包含所有的 `TMultiPart`
4. 遍历 `partList()` 可以获取每个part的信息
5. AE2中的 `CableBusPart` 继承自 `JCuboidPart`，内部包含 `CableBusContainer`，可以通过 `getCableBus()` 获取线缆总线信息
6. 对于普通方块转换来的part，如 `QuartzTorchPart`，继承自 `McSidedMetaPart`，可以通过 `getBlock()` 获取原始Block

我需要把这些信息整理成一个清晰的回答，告诉用户如何在自己的WebAPI项目中解析FMP方块。

同时，我也可以看看用户项目中是否有相关的依赖。让我查看dependencies.gradle。
根据AE2源码，解析ForgeMultipart方块信息的核心步骤如下：

## 1. 基本依赖

```groovy
// dependencies.gradle 中需要包含 FMP
compile("codechicken:ForgeMultipart:版本号:dev")
```

## 2. 核心API入口

```java
import codechicken.multipart.TileMultipart;
import codechicken.multipart.TMultiPart;
import codechicken.lib.vec.BlockCoord;
import scala.collection.Iterator;

// 获取 TileMultipart（如果不存在会尝试转换）
TileMultipart mp = TileMultipart.getOrConvertTile(world, new BlockCoord(x, y, z));
if (mp == null) return;

// 遍历所有 Part
Iterator<TMultiPart> it = mp.partList().iterator();
while (it.hasNext()) {
    TMultiPart part = it.next();
    // 解析 part...
}
```

## 3. 解析AE2的CableBusPart

AE2的线缆总线方块在FMP中以 `CableBusPart` 形式存在：

```java
import appeng.fmp.CableBusPart;

if (part instanceof CableBusPart) {
    CableBusPart cbp = (CableBusPart) part;
    
    // 获取内部的 CableBusContainer
    CableBusContainer cbc = cbp.getCableBus();
    
    // 获取各面安装的Part（线缆、总线、面板等）
    for (ForgeDirection dir : ForgeDirection.values()) {
        IPart sidePart = cbc.getPart(dir);
        if (sidePart != null) {
            // 获取物品形式
            ItemStack stack = sidePart.getItemStack(PartItemStack.Break);
            // 获取线缆类型
            if (sidePart instanceof IPartCable) {
                AECableType cableType = ((IPartCable) sidePart).getCableConnectionType(dir);
            }
        }
    }
    
    // 获取伪装板(Facade)
    IFacadeContainer facades = cbc.getFacadeContainer();
    for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
        IFacadePart facade = facades.getFacade(dir);
        if (facade != null) {
            ItemStack facadeStack = facade.getItemStack();
        }
    }
    
    // 颜色
    AEColor color = cbc.getColor();
}
```

## 4. 解析其他AE2 Part

```java
import appeng.fmp.QuartzTorchPart;
import appeng.fmp.PartRegistry;

// Part类型名称
String typeName = part.getType();

// 石英火把Part
if (part instanceof QuartzTorchPart) {
    Block block = ((QuartzTorchPart) part).getBlock();
    int meta = part.meta; // 朝向信息
}

// 或者通过注册表识别
if (PartRegistry.CableBusPart.getName().equals(typeName)) { ... }
```

## 5. 通用Part信息

所有 `TMultiPart` 都包含：

```java
// 边界框
Cuboid6 bounds = part.getBounds();

// 光照
int light = part.getLightValue();

// 是否可以碰撞
part.getCollisionBoxes();

// Part的NBT数据（保存/加载）
NBTTagCompound tag = new NBTTagCompound();
part.save(tag);
part.load(tag);

// 掉落物
Iterable<ItemStack> drops = part.getDrops();
```

## 6. 在你的WebAPI中使用的建议

参考你现有的 `AEHandler.java` 结构，可以创建一个 `/block/fmp` 或扩展 `/block/ae2` 的处理器：

```java
TileEntity te = world.getTileEntity(x, y, z);
if (te instanceof TileMultipart mp) {
    ArrayNode parts = mapper.createArrayNode();
    Iterator<TMultiPart> it = mp.partList().iterator();
    while (it.hasNext()) {
        TMultiPart part = it.next();
        ObjectNode pNode = mapper.createObjectNode();
        pNode.put("type", part.getType());
        // ...解析具体信息
        parts.add(pNode);
    }
    root.set("multipart", parts);
}
```

关键文件参考：
- `src/main/java/appeng/integration/modules/FMP.java` — FMP集成入口
- `src/main/java/appeng/fmp/CableBusPart.java` — AE线缆总线Part实现
- `src/main/java/appeng/fmp/PartRegistry.java` — AE Part注册表
- `src/main/java/appeng/fmp/FMPEvent.java` — FMP事件处理示例