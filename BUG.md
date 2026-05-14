# BUGs

[ItemIconDumperThread.java](src/main/java/love/shirokasoke/webapi/client/thread/ItemIconDumperThread.java#L79)

IconDump会跳过`item.microblock`的大量物品（应该是伪装板）

[`/item/ae`](src/main/java/love/shirokasoke/webapi/server/handlers/item/AEHandler.java#L106)
