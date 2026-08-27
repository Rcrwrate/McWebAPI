package love.shirokasoke.webapi.webserver.handlers.block;

import java.io.IOException;

import net.minecraft.block.Block;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.webserver.Context;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class SetBlockHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/setblock";
    }

    @Override
    public String getDescription() {
        return "Setblock";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod()
            .equals("POST")) {
            throw new ApiException(400, "Method must be POST");
        }
        JsonNode data = getBody(exchange);
        coordinates co = getCoordinates(exchange);
        Context context = new Context(co).initServer()
            .initWorld()
            .checkblockExists();

        int id = data.get("id")
            .asInt();
        int metadataIn = data.path("metadataIn")
            .asInt(0);
        int flag = data.path("flag")
            .asInt(2);

        Block block = Block.getBlockById(id);
        if (block == null) {
            throw new ApiException(404, "block id not found");
        }

        boolean changed = false;
        try {
            changed = ServerThreadDispatcher
                .callOnServerThread(() -> context.world.setBlock(co.posX, co.posY, co.posZ, block, metadataIn, flag));
        } catch (Exception e) {
            throw new IOException(e);
        }

        ObjectNode rep = mapper.createObjectNode()
            .put("success", changed)
            .putNull("data");
        sendResponse(exchange, 200, rep, true);
    }
}
