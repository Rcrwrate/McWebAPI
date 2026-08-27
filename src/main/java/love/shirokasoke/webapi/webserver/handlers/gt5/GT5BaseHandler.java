package love.shirokasoke.webapi.webserver.handlers.gt5;

import java.io.IOException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.GT5Utils;
import love.shirokasoke.webapi.webserver.Context;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class GT5BaseHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/gt5";
    }

    @Override
    public String getDescription() {
        return "GT5 machine base endpoint. Query params: x, y, z, dim (optional, default=0). Detects if a block is a GT5 machine (multiblock/single-block/hatch) and returns its working state.";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        ObjectNode data = buildMachineInfo(GT5init(exchange));
        sendResponse(exchange, data);
    }

    protected Context GT5init(HttpExchange exchange) throws ApiException {
        coordinates co = getCoordinates(exchange);
        return new Context(co).initServer()
            .initWorld()
            .checkblockExists()
            .initTileEntity()
            .initGT();
    }

    protected ObjectNode buildMachineInfo(Context context) {
        ObjectNode data = mapper.createObjectNode();

        data.put("x", context.co.posX)
            .put("y", context.co.posY)
            .put("z", context.co.posZ)
            .put("dimension", context.co.dimension);

        GT5Utils.writeBasicMachineInfo(context.igte, context.mte, data);
        GT5Utils.write(context.mte, data);
        GT5Utils.writeState(context.igte, data.putObject("state"));
        return data;
    }
}
