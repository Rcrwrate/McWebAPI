package love.shirokasoke.webapi.webserver.handlers.test;

import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.NBT;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class JsonTestHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/test/json2nbt";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        if (!exchange.getRequestMethod()
            .equals("POST")) {
            throw new ApiException(405, "method must be POST");
        }

        String req;
        JsonNode body = getBody(exchange);
        if (body.has("data")) {
            req = body.path("data")
                .asText();
        } else {
            throw new ApiException(400, "data missing");
        }

        if (req != null) {
            NBTTagCompound nbt = (NBTTagCompound) JsonToNBT.func_150315_a(req);
            ObjectNode fin = mapper.createObjectNode();
            NBT.dump(nbt, fin);
            sendResponse(exchange, fin);
        }
        throw new ApiException(400, "data missing");
    }
}
