package love.shirokasoke.webapi.webserver.handlers.item;

import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class ItemHandler implements RouteHandler {

    public static ItemHandler INSTANCE = new ItemHandler();

    private ItemHandler() {}

    @Override
    public String getPath() {
        return "/item";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        if (params == null) throw new ApiException(400, "missing query");

        int id = Integer.parseInt(params.get("id"));
        Item item = Item.getItemById(id);

        ObjectNode data = mapper.createObjectNode();
        Items.dump(new ItemStack(item, 1, 0), data);
        if (item.getHasSubtypes()) {
            ArrayNode subs = mapper.createArrayNode();
            Items.getPermutations(item)
                .forEach(t -> { subs.add(Items.dump(t)); });
            data.set("subs", subs);
        }
        setCache(exchange, 86400);
        sendResponse(exchange, data);
    }

}
