package love.shirokasoke.webapi.server.handlers.ae2;

import java.io.IOException;

import net.minecraft.item.ItemStack;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import love.shirokasoke.webapi.utils.Items;

public class AEItemHandler extends AEBaseHandler {

    @Override
    public String getPath() {
        return "/ae/item";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        AEinit(exchange);
        // 获取存储网格
        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        // 访问物品库存
        IMEMonitor<IAEItemStack> itemInventory = storageGrid.getItemInventory();
        // 获取完整的物品列表
        IItemList<IAEItemStack> itemList = itemInventory.getStorageList();
        ArrayNode items = mapper.createArrayNode();
        for (IAEItemStack stack : itemList) {
            if (stack != null) {
                ItemStack minecraftStack = stack.getItemStack();
                if (minecraftStack != null) {
                    items.add(
                        Items.dump(minecraftStack)
                            .put("stackSize", stack.getStackSize()));

                }
            }
        }

        ObjectNode response = mapper.createObjectNode();
        response.set("items", items);
        if (storageGrid instanceof GridStorageCache) {
            GridStorageCache cache = (GridStorageCache) storageGrid;
            response.put("totalBytes", cache.getItemBytesTotal());
            response.put("usedBytes", cache.getItemBytesUsed());
            response.put("totalTypes", cache.getItemTypesTotal());
            response.put("usedTypes", cache.getItemTypesUsed());

            ObjectNode cellStatus = response.putObject("cellStatus");
            cellStatus.put("all", cache.getItemCellCount());
            cellStatus.put("green", cache.getItemCellG());
            cellStatus.put("blue", cache.getItemCellB());
            cellStatus.put("orange", cache.getItemCellO());
            cellStatus.put("red", cache.getItemCellR());
        }
        sendResponse(exchange, response);
    }
}
