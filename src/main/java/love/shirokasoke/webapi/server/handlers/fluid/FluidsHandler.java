package love.shirokasoke.webapi.server.handlers.fluid;

import java.util.Map;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.RouteHandler;
import love.shirokasoke.webapi.utils.Fluids;

public class FluidsHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/fluids";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Map<String, Fluid> maps = FluidRegistry.getRegisteredFluids();

        ArrayNode data = mapper.createArrayNode();
        for (Fluid fluid : maps.values()) {
            Fluids.dump(fluid, data.addObject());
        }
        setCache(exchange, 86400);
        sendResponse(exchange, data);
    }
}
