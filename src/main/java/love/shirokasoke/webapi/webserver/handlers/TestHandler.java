package love.shirokasoke.webapi.webserver.handlers;

import java.util.Map;

import net.minecraft.item.ItemStack;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.NBT;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class TestHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/test";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        if (params == null || !params.containsKey("id")) {
            throw new ApiException(400, "missing query param 'id'");
        }

        if (Config.ItemIconFolder == null || Config.ItemIconFolder.isEmpty()) {
            throw new ApiException(500, "ItemIconFolder not configured");
        }

        int id;
        try {
            id = Short.parseShort(params.get("id"));
        } catch (NumberFormatException e) {
            throw new ApiException(400, "invalid query param 'id'");
        }

        int damage = 0;
        String damageStr = params.get("damage");
        if (damageStr != null) {
            try {
                damage = Short.parseShort(damageStr);
            } catch (NumberFormatException e) {
                throw new ApiException(400, "invalid query param 'damage'");
            }
        }

        ItemStack stack = NBT.toItemStack(id, damage, params.get("tag"));
        String fileName = Items.getFileName(stack) + ".png";
        throw new ApiException(200, fileName);
    }
}
