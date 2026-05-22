# BUGs

## 1.<已修复> NBT

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

## 2.<已修复> NBT -> JSON -> NBT

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

## 3. <已修复> 客户端与服务端直接的物品数据ID不一致

~~预期向下兼容，新增通过regName确保唯一，但保留ID获取的功能~~

在数据导入时，转换客户端id为服务端id

服务端数据：

[ItemHandler.java](src/main/java/love/shirokasoke/webapi/server/handlers/item/ItemHandler.java)

[ItemsHandler.java](src/main/java/love/shirokasoke/webapi/server/handlers/item/ItemsHandler.java)

客户端数据：

[ItemStaticHandler.java](src/main/java/love/shirokasoke/webapi/server/handlers/item/ItemStaticHandler.java)

## 4. <未修复> 部分客户端导出的物品icon显示空白

正在调查，发现的有**液滴**，2.9.0将被移除？，后续处理

**粉尘**