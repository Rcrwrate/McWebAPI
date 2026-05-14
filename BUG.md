# BUGs

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
