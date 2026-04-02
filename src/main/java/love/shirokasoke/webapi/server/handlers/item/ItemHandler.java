package love.shirokasoke.webapi.server.handlers.item;

import java.util.Map;

import net.minecraft.item.Item;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.RouteHandler;
import love.shirokasoke.webapi.utils.Items;

public class ItemHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/item";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        if (params == null) throw new Error(400, "missing query");

        int id = Integer.parseInt(params.get("id"));
        Item item = Item.getItemById(id);

        ObjectNode data = mapper.createObjectNode();
        Items.dump(item, data);
        if (item.getHasSubtypes()) {
            ArrayNode subs = mapper.createArrayNode();
            Items.getPermutations(item)
                .forEach(t -> { subs.add(Items.dump(t)); });
            data.set("subs", subs);

        }
        sendResponse(exchange, 200, data);
    }

}
