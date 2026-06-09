# BUGs

- [x] [1.NBT](#1nbt)

- [x] [2.NBT -> JSON -> NBT](#2nbt---json---nbt)

- [x] [3.客户端与服务端直接的物品数据ID不一致](#3客户端与服务端直接的物品数据id不一致)

- [x] [4. 部分客户端导出的物品icon显示空白](#4部分客户端导出的物品icon显示空白)

- [ ] [5.AE CPU下单必须使用非部件形式的接口](#5-ae-cpu下单必须使用非部件形式的接口)

- [ ] [6. Hodgepodge 警告](#6-hodgepodge-警告)

## 1.NBT

[ItemIconDumperThread.java](src/main/java/love/shirokasoke/webapi/client/thread/ItemIconDumperThread.java#L79)

IconDump会跳过`item.microblock`的大量物品（应该是伪装板）

已确认：damage相同，依靠NBT进行材质区分，暂时不处理（涉及不到）

(顺手修复了，通过计算NBT hash区分，仅4位的hash就可以实现初步的完整导出(无重复))

```json
{
  "id" : 4145,
  "registryName" : "ForgeMicroblock:microblock",
  "UnlocalizedName" : "item.microblock",
  "localizedName" : "Stone Nook",
  "HasSubtypes" : true,
  "MaxStackSize" : 64,
  "damageable" : false,
  "damage" : 513,
  "AttributeModifiers" : {
    "empty" : true
  },
  "nbtstr" : "{mat:\"minecraft:stone\"}",
  "nbt" : {
    "mat" : "minecraft:stone"
  }
}, {
  "id" : 4145,
  "registryName" : "ForgeMicroblock:microblock",
  "UnlocalizedName" : "item.microblock",
  "localizedName" : "Stone Bricks Nook",
  "HasSubtypes" : true,
  "MaxStackSize" : 64,
  "damageable" : false,
  "damage" : 513,
  "AttributeModifiers" : {
    "empty" : true
  },
  "nbtstr" : "{mat:\"minecraft:stonebrick\"}",
  "nbt" : {
    "mat" : "minecraft:stonebrick"
  }
}
```

[`/item/ae`](src/main/java/love/shirokasoke/webapi/server/handlers/item/AEHandler.java#L106)

## 2.NBT -> JSON -> NBT

不可行，java中byte/short/int/long/float/double转向JSON直接丢失精度且不方便处理

选择使用`NBTBase.write`转为Base64处理

但是客户端/服务端各自生成的Base64**不一致**，导致hash生成的icon文件名不一致（暂时没有好的解决办法，目前打算在SDK中处理）

客户端

```json
{
  "id": 4138,
  "registryName": "appliedenergistics2:item.ItemFacade",
  "UnlocalizedName": "item.appliedenergistics2.ItemFacade",
  "localizedName": "Cable Facade - Certus Quartz Pillar Slab",
  "HasSubtypes": true,
  "MaxStackSize": 64,
  "damageable": false,
  "damage": 0,
  "AttributeModifiers": {
    "empty": true
  },
  "nbtstr": "{modid:\"appliedenergistics2\",itemname:\"tile.QuartzPillarSlabBlock.double\",x:[243,0,]}",
  "nbtWrite": "CAAFbW9kaWQAE2FwcGxpZWRlbmVyZ2lzdGljczIIAAhpdGVtbmFtZQAhdGlsZS5RdWFydHpQaWxsYXJTbGFiQmxvY2suZG91YmxlCwABeAAAAAIAAADzAAAAAAA=",
  "nbt": {
    "modid": "appliedenergistics2",
    "itemname": "tile.QuartzPillarSlabBlock.double",
    "x": [243, 0]
  }
}
```

服务端

```json
{
  "id": 4138,
  "registryName": "appliedenergistics2:item.ItemFacade",
  "UnlocalizedName": "item.appliedenergistics2.ItemFacade",
  "localizedName": "Cable Facade - Certus Quartz Pillar Slab",
  "HasSubtypes": true,
  "MaxStackSize": 64,
  "damageable": false,
  "damage": 0,
  "AttributeModifiers": {
    "empty": true
  },
  "nbtstr": "{itemname:\"tile.QuartzPillarSlabBlock.double\",x:[243,0,],modid:\"appliedenergistics2\"}",
  "nbtWrite": "CAAIaXRlbW5hbWUAIXRpbGUuUXVhcnR6UGlsbGFyU2xhYkJsb2NrLmRvdWJsZQsAAXgAAAACAAAA8wAAAAAIAAVtb2RpZAATYXBwbGllZGVuZXJnaXN0aWNzMgA=",
  "nbt": {
    "itemname": "tile.QuartzPillarSlabBlock.double",
    "x": [243, 0],
    "modid": "appliedenergistics2"
  },
  "stackSize": 0
}
```

## 3.客户端与服务端直接的物品数据ID不一致

~~预期向下兼容，新增通过regName确保唯一，但保留ID获取的功能~~

在数据导入时，转换客户端id为服务端id

服务端数据：

[ItemHandler.java](src/main/java/love/shirokasoke/webapi/server/handlers/item/ItemHandler.java)

[ItemsHandler.java](src/main/java/love/shirokasoke/webapi/server/handlers/item/ItemsHandler.java)

客户端数据：

[ItemStaticHandler.java](src/main/java/love/shirokasoke/webapi/server/handlers/item/ItemStaticHandler.java)

## 4.部分客户端导出的物品icon显示空白

~~正在调查，发现的有**液滴**，2.9.0将被移除？，后续处理~~

**粉尘**

采用了NEI的渲染，但是未进入游戏真正激活NEI导致的渲染异常，改成游戏内指令形式的触发

## 5. AE CPU下单必须使用非部件形式的接口

[AECPUTaskHandler.java](src/main/java/love/shirokasoke/webapi/server/handlers/ae2/AECPUTaskHandler.java)

FMP形式的接口尚未支持

## 6. Hodgepodge 警告

**Hodgepodge 相关源码：**

- `Hodgepodge/src/main/java/com/mitchej123/hodgepodge/util/ServerThreadLongHashMap.java`
- `Hodgepodge/src/main/java/com/mitchej123/hodgepodge/core/fml/transformers/mc/SpeedupLongIntHashMapTransformer.java`

HTTP 线程池直接调用 `world.blockExists()`、`world.getBlock()`、`world.getTileEntity()`、`ChunkProviderServer.chunkExists()` 等 API，Hodgepodge 的 `ServerThreadLongHashMap` 检测到 off-thread 访问后打印 warn

```log
[ServerThreadLongHashMap]: Off-thread read from pool-3-thread-3 - serving from snapshot
java.lang.Throwable: Caller stacktrace
    at ...ChunkProviderServer.chunkExists(...)
    at ...World.blockExists(...)
    at ...BlockHandler.checklist(...)
```

**原因**

所有 handler 的逻辑都在线程池中执行。Minecraft 的 World/Chunk 数据结构不是线程安全的，Hodgepodge 用 ASM 将 `ChunkProviderServer.loadedChunkHashMap` 替换为 `ServerThreadLongHashMap`

- **Server thread**：直接读写底层 fastutil `Long2ObjectOpenHashMap`（无锁，零开销）
- **Off-thread**：只能读 snapshot（定期刷新的副本），且首次访问时打印 warn

虽然 Hodgepodge 会返回 snapshot 结果避免崩溃，但：

1. 读到的是最多落后 1 秒的旧数据
2. 每次新 HTTP 线程都会触并发一次 warn（按线程名去重）
3. `getBlock()` / `getTileEntity()` / `IInventory` 等后续操作仍暴露在发风险下，没有任何线程安全保证

**直接解决**可以通过 `CommonProxy.runOnServerThread()` 将世界操作投递到 Server thread

**性能评估**

- snapshot fast path：~0.3μs（纳秒级）
- `runOnServerThread`：~25ms（平均等待半个 tick，tick 周期 50ms）

根据性能评估，**不处理**只读的请求，后续允许用户自行选择是否使用线程安全的调用方式