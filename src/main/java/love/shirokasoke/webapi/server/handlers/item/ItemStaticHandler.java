package love.shirokasoke.webapi.server.handlers.item;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.RouteHandler;
import love.shirokasoke.webapi.server.WebServer;

public class ItemStaticHandler implements RouteHandler {

    private String ItemFile;
    private JsonNode data;

    public ItemStaticHandler(String ItemFile) {
        this.ItemFile = ItemFile;
    }

    public boolean isValid() {
        if (ItemFile == null || ItemFile.isEmpty()) {
            return false;
        }

        File file = new File(Config.ItemFile);
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            return false;
        }

        try {
            data = mapper.readTree(file);
            if (!data.isArray()) {
                MyMod.LOG.warn("[ItemStatic] ItemFile is not a JSON array: {}", Config.ItemFile);
                return false;
            }
            if (data.size() == 0) {
                MyMod.LOG.warn("[ItemStatic] ItemFile array is empty: {}", Config.ItemFile);
                return false;
            }
            if (!data.get(0)
                .has("registryName")) {
                MyMod.LOG.warn("[ItemStatic] ItemFile missing 'registryName' field: {}", Config.ItemFile);
                return false;
            }
            MyMod.LOG.info("[ItemStatic] ItemFile validated successfully: {} ({} items)", Config.ItemFile, data.size());
            return true;
        } catch (IOException e) {
            MyMod.LOG.error("[ItemStatic] Failed to parse ItemFile: {}", Config.ItemFile);
            return false;
        }
    }

    public void inject() {
        WebServer.remove("/item");
        WebServer.addRoute(this);
    }

    @Override
    public String getPath() {
        return "/item";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        if (params == null) throw new Error(400, "missing query");

        int id = Integer.parseInt(params.get("id"));

        ArrayNode matches = mapper.createArrayNode();
        for (JsonNode node : data) {
            if (node.has("id") && node.get("id")
                .asInt() == id) {
                matches.add(node);
            }
        }

        if (matches.size() == 0) {
            throw new Error(404, "item not found");
        }

        ObjectNode result = ((ObjectNode) matches.get(0)).deepCopy();
        if (matches.size() > 1) {
            ArrayNode subs = mapper.createArrayNode();
            for (int i = 1; i < matches.size(); i++) {
                subs.add(matches.get(i));
            }
            result.set("subs", subs);
        }

        setCache(exchange, 86400);
        sendResponse(exchange, result);
    }
}
