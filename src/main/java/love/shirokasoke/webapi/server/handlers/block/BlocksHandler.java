package love.shirokasoke.webapi.server.handlers.block;

import java.io.IOException;

import net.minecraft.block.Block;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.RouteHandler;
import love.shirokasoke.webapi.utils.Blocks;

/**
 * 获取所有已注册的物品列表
 */
public class BlocksHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/blocks";
    }

    @Override
    public String getDescription() {
        return "获取服务器中所有已注册的物品列表";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        getServer();

        ArrayNode items = mapper.createArrayNode();

        for (Object obj : Block.blockRegistry) {
            if (obj instanceof Block) {
                items.add(Blocks.dump((Block) obj));
            }
        }
        setCache(exchange, 86400);
        sendResponse(exchange, 200, items);
    }
}
