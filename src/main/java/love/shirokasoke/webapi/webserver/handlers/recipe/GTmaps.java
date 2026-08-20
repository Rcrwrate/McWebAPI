package love.shirokasoke.webapi.webserver.handlers.recipe;

import net.minecraft.util.StatCollector;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpExchange;

import gregtech.api.recipe.RecipeMap;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class GTmaps implements RouteHandler {

    @Override
    public String getPath() {
        return "/recipes/gt/maps";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        ArrayNode data = mapper.createArrayNode();
        for (String i : RecipeMap.ALL_RECIPE_MAPS.keySet()) {
            data.addObject()
                .put("unlocalizedName", i)
                .put("name", StatCollector.translateToLocal(i));
        }
        setCache(exchange, 86400);
        sendResponse(exchange, data);
    }

}
