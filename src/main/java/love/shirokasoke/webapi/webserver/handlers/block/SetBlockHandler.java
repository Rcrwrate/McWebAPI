package love.shirokasoke.webapi.webserver.handlers.block;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.block.Block;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.CommonProxy;

public class SetBlockHandler extends BlockHandler {

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
            throw new Error(400, "Method must be POST");
        }
        JsonNode data = getBody(exchange);
        String query = exchange.getRequestURI()
            .getQuery();
        coordinates co = checklist(query);

        int id = data.get("id")
            .asInt();
        int metadataIn = data.path("metadataIn")
            .asInt(0);
        int flag = data.path("flag")
            .asInt(2);

        Block block = Block.getBlockById(id);
        if (block == null) {
            throw new Error(404, "block id not found");
        }

        AtomicBoolean changed = new AtomicBoolean();
        try {
            CommonProxy.runOnServerThread(
                () -> { changed.set(world.setBlock(co.posX, co.posY, co.posZ, block, metadataIn, flag)); });
        } catch (Exception e) {
            throw new IOException(e);
        }

        ObjectNode rep = mapper.createObjectNode()
            .put("success", changed.get())
            .putNull("data");
        sendResponse(exchange, 200, rep, true);
    }
}
