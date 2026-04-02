package love.shirokasoke.webapi.server.handlers.item;

import net.minecraft.item.Item;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.RouteHandler;
import love.shirokasoke.webapi.utils.Items;

public class ItemsHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/items";
    }

    @Override
    public String getDescription() {
        return "获取服务器中所有已注册的物品列表";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        getServer();

        ArrayNode data = mapper.createArrayNode();

        for (Object obj : Item.itemRegistry) {
            if (obj instanceof Item) {
                Item item = (Item) obj;
                ObjectNode itemInfo = mapper.createObjectNode();
                Items.dump(item, itemInfo);
                data.add(itemInfo);
            }
        }

        sendResponse(exchange, 200, data);
    }
}
