# OC 3D 打印

[McMod介绍](https://www.mcmod.cn/item/53161.html)

基于 OpenComputers 源代码分析，源码位于 `tools/OpenComputers-1.12.48-GTNH/`。

3D 打印件的核心思路：**一个打印件 = 一个普通方块 + TileEntity 中的形状数据**。模型不使用任何模型文件（json/OBJ），而是表示为一组 1/16 精度的 AABB 盒子（Shape），每个盒子可独立指定贴图与染色。数据流：

```
Lua API（printer3d 组件）
    → Printer TileEntity（内存中的 PrintData）
    → NBT 写入物品（ItemStack）
    → 放置时读回 TileEntity（Print）
    → 客户端 NBT 同步
    → 逐 Shape 调用原版 RenderBlocks 标准渲染管线绘制
```

| 核心类 | 位置 | 职责 |
|--------|------|------|
| `tileentity.Printer` | 服务端 | 打印机，提供 Lua API，消耗材料产出打印件 |
| `PrintData` / `PrintData.Shape` | 数据层 | 打印件的数据模型与 NBT 序列化 |
| `block.Print` / `tileentity.Print` | 世界 | 打印件方块（哑方块，几何由数据驱动） |
| `client.renderer.block.Print` | 客户端 | 世界内渲染核心 |

---

## 一、创建逻辑（打印机如何造出打印件）

### 1.1 Lua API 配置阶段

打印机通过 `printer3d` 组件暴露回调。配置只修改 TileEntity 内存中的 `PrintData`（`var data = new PrintData()`），不产生物品：

| 回调 | 作用 | 源代码位置 |
|------|------|-----------|
| `setLabel` / `setTooltip` | 自定义名称（最长 24）/悬停提示（最长 128） | [`Printer.scala#L91`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L91) / [`#L104`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L104) |
| `setLightLevel` | 打印件发光等级（0~`maxPrintLightLevel`） | [`Printer.scala#L117`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L117) |
| `setRedstoneEmitter` | 激活态红石信号强度（0~15） | [`Printer.scala#L129`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L129) |
| `setButtonMode` | 是否自动弹回（按钮模式） | [`Printer.scala#L142`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L142) |
| `setCollidable` | 开/关状态是否可碰撞（noclip） | [`Printer.scala#L154`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L154) |
| `addShape` | 添加一个盒子形状 | [`Printer.scala#L167`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L167) |
| `commit` | 提交并开始打印（可指定数量） | [`Printer.scala#L208`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L208) |
| `reset` | 清空配置（`data = new PrintData()`） | [`Printer.scala#L84`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L84) |

`addShape` 的坐标处理是关键——传入 0~16 的"体素"坐标，除以 16 归一化为 0~1 的方块内坐标；**Z 轴会被翻转**（`16 - z`），且任一轴 min==max 会抛 `empty block`：

```scala
172:177:tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala
    val minX = (args.checkInteger(0) max 0 min 16) / 16f
    val minY = (args.checkInteger(1) max 0 min 16) / 16f
    val minZ = (16 - (args.checkInteger(2) max 0 min 16)) / 16f
    val maxX = (args.checkInteger(3) max 0 min 16) / 16f
    val maxY = (args.checkInteger(4) max 0 min 16) / 16f
    val maxZ = (16 - (args.checkInteger(5) max 0 min 16)) / 16f
```

形状上限为 `Settings.get.maxPrintComplexity`（超出返回 `model too complex`，[`Printer.scala#L169`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L169)）；`state` 参数决定放入 `data.stateOn`（激活态）还是 `data.stateOff`（默认关态），实现按钮/双态打印件。

### 1.2 打印循环（updateEntity）

`commit` 只置位 `isActive`，真正的产出在每 tick 的 [`updateEntity()`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L229) 中完成：

1. **校验与扣料**：`canPrint` 要求 `stateOff` 非空且形状数不超限（[`#L74`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L74)）；`PrintData.computeCosts` 计算成本——材料 = 体积/2、墨水 = 表面积/6、noclip 有倍率（[`PrintData.scala#L147`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/item/data/PrintData.scala#L147)）；足够则扣除并生成 `output = Option(data.createItemStack())`（[`#L238-L256`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L238)）。
2. **能量推进**：每 tick 从节点缓冲扣电推进 `requiredEnergy`，归零时把 `output` 放入输出槽（可合并时叠加，[`#L258-L278`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L258)）。
3. **耗材补给**：材料槽按 `PrintData.materialValue`（变色材料 Chamelium 或旧打印件回收）累加 `amountMaterial`，墨水槽按 `inkValue`（IMC 注册的墨水提供者）累加 `amountInk`（[`#L280-L297`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L280)，[`materialValue`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/item/data/PrintData.scala#L166)）。

产出的物品即 `data.createItemStack()`——见下节。

---

## 二、保存逻辑（PrintData ↔ NBT）

### 2.1 物品形态（ItemStack 的 NBT）

`PrintData` 继承 [`ItemData`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/item/data/ItemData.scala#L8)，产出物品的三行核心：

```scala
24:31:tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/item/data/ItemData.scala
  def createItemStack() = {
    if (itemName == null) null
    else {
      val stack = api.Items.get(itemName).createItemStack(1)
      save(stack)
      stack
    }
  }
```

即：取 `Print` 方块的 ItemBlock → `save(stack)` 把整个 `PrintData` 序列化进物品 NBT（加载时 `load` 会 copy 一份 tag，避免 ItemStack 共享 NBT 的 bug，[`ItemData.scala#L9-L15`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/item/data/ItemData.scala#L9)）。

### 2.2 NBT 字段表

[`PrintData.save/load`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/item/data/PrintData.scala#L60)：

| NBT 键 | 类型 | 含义 |
|--------|------|------|
| `label` / `tooltip` | String（可选） | 自定义名称/提示 |
| `isButtonMode` | Boolean | 按钮模式 |
| `redstoneLevel` | Int（0~15） | 红石输出强度 |
| `pressurePlate` | Boolean | 压力板模式 |
| `stateOff` / `stateOn` | NBT 列表 | 两态的 Shape 列表 |
| `isBeaconBase` | Boolean | 可作信标基座 |
| `lightLevel` | Byte（0~15） | 发光等级 |
| `noclipOff` / `noclipOn` | Boolean | 两态是否无碰撞 |

单个 Shape 的序列化（[`shapeToNBT`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/item/data/PrintData.scala#L216) / [`nbtToShape`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/item/data/PrintData.scala#L189)）：

- `bounds`：6 字节数组，坐标 ×16 取整存 byte（最小分辨率 1/16 格，即 16×16×16 体素网格）；
- `texture`：贴图资源名字符串（最长 64），可为**任意已注册的方块贴图**；
- `tint`：可选 Int 颜色；
- `nbtToShape` 还兼容旧开发版的 `minX/maxX...` 单字节键格式。

**排序一致性**：Shape 存在 `mutable.Set` 中无序，而 NBT 列表比较关心顺序。序列化前先按 `minX/Y/Z、maxX/Y/Z、tint、texture` 排序（[`setNewShapeSet`/`compareShape`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/item/data/PrintData.scala#L97)），保证两个内容相同的打印件 NBT 完全一致（否则原版会判定为不同物品，影响堆叠/比较）。

### 2.3 TileEntity 形态（存档与客户端同步）

放置后数据转入 `tileentity.Print`，同样以 `PrintData` 为载体，分三路 NBT：

| 路径 | 时机 | 内容 | 源代码位置 |
|------|------|------|-----------|
| 服务器存档 | 区块保存/加载 | `data`（子 Compound）+ `state` | [`Print.scala#L69-L80`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Print.scala#L69) |
| 客户端同步 | `markBlockForUpdate` 描述包 | 同上 `data` + `state` | [`Print.scala#L82-L95`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Print.scala#L82) |
| 朝向同步 | `Rotatable` 基类 | `pitch` + `yaw` | [`Rotatable.scala#L134-L147`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/traits/Rotatable.scala#L134) |

客户端 `readFromNBTForClient` 读取后会 `updateBounds()` + `world.markBlockForUpdate(x, y, z)` 触发重渲染。打印机自身的配置/进度/材料也持久化（[`Printer.scala#L300-L324`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Printer.scala#L300)）。

---

## 三、显示逻辑（渲染）

### 3.1 渲染器注册与分发

打印件方块继承 `SimpleBlock`，`getRenderType` 返回自定义渲染 ID（[`SimpleBlock.scala#L47`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/SimpleBlock.scala#L47)），客户端启动时注册 ISBRH：

```scala
50:51:tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/client/Proxy.scala
    Settings.blockRenderId = RenderingRegistry.getNextAvailableRenderId
    RenderingRegistry.registerBlockHandler(BlockRenderer)
```

世界渲染时 [`BlockRenderer.renderWorldBlock`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/client/renderer/block/BlockRenderer.scala#L86) 按 TileEntity 类型分发，打印件分支：

```scala
106:109:tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/client/renderer/block/BlockRenderer.scala
      case print: common.tileentity.Print =>
        Print.render(print.data, print.state, print.facing, x, y, z, block, renderer)
        true
```

### 3.2 核心渲染：逐盒子 + 原版标准管线

[`client.renderer.block.Print.render`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/client/renderer/block/Print.scala#L14) 是显示自定义方块的全部秘密——**不生成任何网格/模型，把每个 Shape 当作一个"子方块"，直接复用原版 `RenderBlocks.renderStandardBlock`**（自动获得 AO、光照、面剔除）：

```scala
14:35:tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/client/renderer/block/Print.scala
  def render(data: PrintData, state: Boolean, facing: ForgeDirection, x: Int, y: Int, z: Int, block: Block, renderer: RenderBlocks): Unit = {
    val printBlock = block.asInstanceOf[li.cil.oc.common.block.Print].getPrintBlock  // ThreadLocal 克隆
    val shapes = if (state) data.stateOn else data.stateOff
    printBlock.isSingleShape = shapes.size == 1
    if (shapes.isEmpty) { /* 贴 missingno 渲染整格占位 */ }
    else for (shape <- shapes if !Strings.isNullOrEmpty(shape.texture)) {
      val bounds = shape.bounds.rotateTowards(facing)        // 按方块朝向旋转（Y 轴）
      printBlock.colorMultiplierOverride = shape.tint         // 逐形状染色
      printBlock.textureOverride = Option(resolveTexture(shape.texture)) // 逐形状贴图
      renderer.setRenderBounds(bounds.minX, ..., bounds.maxZ) // 本盒子的渲染包围盒
      renderer.renderStandardBlock(printBlock, x, y, z)       // 原版标准方块渲染
    }
    /* 复位 override */
  }
```

三个支撑机制：

1. **贴图解析** [`resolveTexture`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/client/renderer/block/Print.scala#L37)：从方块贴图表 `TextureMap` 按资源名取 `IIcon`，找不到回落 `missingno`——因此任意已注册方块贴图都能当打印件材质。
2. **override 通道**（[`common/block/Print.scala#L43-L67`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Print.scala#L43)）：`renderStandardBlock` 渲染时会回调方块的 `getIcon`/`colorMultiplier`，打印件方块覆写这两个方法返回 `textureOverride`/`colorMultiplierOverride`，从而实现每个盒子独立贴图与染色；为线程安全，渲染用方块是 `printBlockThreadLocal` 中的克隆实例（[`#L58-L62`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Print.scala#L58)）。
3. **朝向旋转** [`ExtendedAABB.rotateTowards`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/util/ExtendedAABB.scala#L29)：按 `facing` 做 0/90/180/270° 的 Y 轴旋转变换坐标。

### 3.3 光照与不透明度

- 发光：`getLightValue` 直接返回 `data.lightLevel`（[`common/block/Print.scala#L92`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Print.scala#L92)）；
- 遮光：按 4×4×4 子空间采样估算体积占比（[`computeApproximateOpacity`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/item/data/PrintData.scala#L134)），缓存于 `PrintData.opacity`，方块 `getLightOpacity` 返回 `opacity × 4`（[`Print.scala#L98`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Print.scala#L98)）。

### 3.4 物品形态渲染（物品栏/手持/掉落）

走 `IItemRenderer`：[`ItemRenderer.renderItem`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/client/renderer/item/ItemRenderer.scala#L123) 从物品栈重建 `PrintData`，按当前状态（按住 Shift 显示激活态）对每个 Shape 调 [`drawShape`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/client/renderer/item/ItemRenderer.scala#L176)——直接 `GL11.glBegin(GL_QUADS)` 手绘 6 个面（含 UV 插值、tint 染色、无贴图形状半透明混合）。物品显示名取 `data.label`（[`common/block/Item.scala#L56-L61`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Item.scala#L56)）。

### 3.5 GTNH 专属补丁

- `BlockRenderer` 标注 `@ThreadSafeISBRH(perThread = false)` 兼容 Angelica 多线程渲染（[`BlockRenderer.scala#L16`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/client/renderer/block/BlockRenderer.scala#L16)）；
- `PatchedRenderBlocks` 翻转 X+/Z- 面贴图，修复自定义渲染器方块的贴图镜像问题（[`#L171-L183`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/client/renderer/block/BlockRenderer.scala#L171)），打印件在 `needsFlipping` 列表中（[`#L133-L138`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/client/renderer/block/BlockRenderer.scala#L133)）。
- ForgeMultipart 环境下打印件可转为部件 `PrintPart`，其 `renderStatic` 复用同一个 `Print.render`（[`PrintPart.scala#L325-L331`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/integration/fmp/PrintPart.scala#L325)）。

---

## 四、放置到世界的逻辑

### 4.1 放置流程

玩家右键放置时，走 OC 自定义 ItemBlock [`Item.placeBlockAt`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Item.scala#L81)：

```scala
87:100:tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Item.scala
    if (super.placeBlockAt(stackToUse, player, world, x, y, z, side, hitX, hitY, hitZ, metadata)) {
      // If it's a rotatable block try to make it face the player.
      world.getTileEntity(x, y, z) match {
        ...
        case rotatable: tileentity.traits.Rotatable =>
          rotatable.setFromEntityPitchAndYaw(player)      // 取玩家朝向
          if (!rotatable.validFacings.contains(rotatable.pitch)) {
            rotatable.pitch = rotatable.validFacings.headOption.getOrElse(ForgeDirection.NORTH)
          }
          if (!rotatable.isInstanceOf[tileentity.RobotProxy]) {
            rotatable.invertRotation()                    // 反转，使方块"面对"玩家
          }
```

完整时序：

| 步骤 | 动作 | 源代码位置 |
|------|------|-----------|
| 1 | `super.placeBlockAt` → 原版 `setBlock` 放置 Print 方块 | [`Item.scala#L87`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Item.scala#L87) |
| 2 | 方块 `hasTileEntity=true`，`createTileEntity` 创建 `tileentity.Print` | [`common/block/Print.scala#L220-L222`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Print.scala#L220) |
| 3 | `onBlockPlacedBy` 匹配 TileEntity 类型，调 `doCustomInit` | [`CustomDrops.scala#L32-L39`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/traits/CustomDrops.scala#L32) |
| 4 | **`doCustomInit`：`data.load(stack)` 把物品 NBT 灌入 TileEntity**，`updateBounds()` 计算总包围盒，`func_147451_t`（markBlockForRenderUpdate）触发重渲染 | [`common/block/Print.scala#L233-L238`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Print.scala#L233) |
| 5 | 按玩家朝向设置 facing（pitch/yaw）并反转 | [`Item.scala#L93-L100`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Item.scala#L93)、[`Rotatable.scala#L54-L71`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/traits/Rotatable.scala#L54) |
| 6 | 朝向变更回调 `onRotationChanged` → 再次 `updateBounds` + 服务端发包同步 | [`tileentity/Print.scala#L110-L113`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Print.scala#L110) |
| 7 | 客户端 `readFromNBTForClient` 收到 `data`+`state`+`pitch/yaw`，`updateBounds` 后 `markBlockForUpdate` 重渲染 | [`tileentity/Print.scala#L82-L89`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Print.scala#L82)、[`Rotatable.scala#L134-L141`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/traits/Rotatable.scala#L134) |

其中 `updateBounds` 把当前状态所有 Shape 的 AABB 取并集、再 `rotateTowards(facing)` 旋转，得到整格的 `boundsOff`/`boundsOn`（空集回落单位立方体），供 `setBlockBoundsBasedOnState` 等使用（[`tileentity/Print.scala#L97-L108`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Print.scala#L97)）。

### 4.2 放置后的数据驱动行为

方块本体是"哑"的 `Print`，以下全部由 `PrintData` 计算：

| 行为 | 实现方式 | 源代码位置 |
|------|---------|-----------|
| 碰撞箱 | 逐 Shape（考虑 noclip）`rotateTowards(facing)` 后加入碰撞列表 | [`common/block/Print.scala#L151-L166`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Print.scala#L151) |
| 射线选中 | 逐 Shape 求交，取最近命中（决定准星与选中描边） | [`#L168-L187`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Print.scala#L168) |
| 面实心/贴面剔除 | `isSideSolid` 判断某面是否有贴满整面的 Shape | [`#L120-L142`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Print.scala#L120) |
| 右键激活 | `activate()` → 有激活态则 `toggleState()`（播音效、红石输出、按钮模式定时弹回） | [`#L226-L231`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Print.scala#L226)、[`tileentity/Print.scala#L43-L53`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/tileentity/Print.scala#L43) |
| 中键拾取 | `getPickBlock` 返回 `data.createItemStack()`（保留全部数据） | [`#L144-L149`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Print.scala#L144) |
| 破坏掉落 | `doCustomDrops` 掉落 `data.createItemStack()`（非创造模式） | [`#L240-L245`](../../tools/OpenComputers-1.12.48-GTNH/src/main/scala/li/cil/oc/common/block/Print.scala#L240) |

即：**放置 = NBT 从物品转移到 TileEntity；破坏 = NBT 从 TileEntity 转回物品**，打印件数据在生命周期中始终以 `PrintData` 单一载体流转。
