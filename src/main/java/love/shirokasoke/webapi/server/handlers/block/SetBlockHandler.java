package love.shirokasoke.webapi.server.handlers.block;

import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

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
        if (block.equals(Blocks.air) && id != 0) {
            throw new Error(404, "block id not found");
        }
        boolean success = world.setBlock(co.posX, co.posY, co.posZ, block, metadataIn, flag);
        ObjectNode rep = mapper.createObjectNode();
        rep.put("success", success);
        sendResponse(exchange, 200, rep);
    }
}
