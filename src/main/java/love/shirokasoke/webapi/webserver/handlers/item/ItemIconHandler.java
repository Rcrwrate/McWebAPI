package love.shirokasoke.webapi.webserver.handlers.item;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.NBT;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class ItemIconHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/item/icon";
    }

    @Override
    public String getDescription() {
        return "Returns item icon PNG by base64-encoded ItemStack NBT JSON. Query: ?id=<base64>";
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
        if (stack == null || stack.getItem() == null) {
            throw new ApiException(400, "failed to restore ItemStack from NBT");
        }

        String fileName = Items.getFileName(stack) + ".png";
        File iconFile = new File(Config.ItemIconFolder, fileName);

        if (!iconFile.exists() || !iconFile.isFile()) {
            MyMod.LOG.warn("[ItemIconHandler] Icon not found: {}", iconFile.getAbsolutePath());
            throw new ApiException(404, "icon not found");
        }

        byte[] imageData = Files.readAllBytes(iconFile.toPath());
        exchange.getResponseHeaders()
            .set("Content-Type", "image/png");
        setCache(exchange, 86400);
        sendResponse(exchange, imageData);
    }
}
