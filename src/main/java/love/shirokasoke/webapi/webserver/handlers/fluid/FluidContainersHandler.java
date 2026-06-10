package love.shirokasoke.webapi.webserver.handlers.fluid;

import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidContainerRegistry.FluidContainerData;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.Fluids;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class FluidContainersHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/fluidContainers";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        FluidContainerData[] datas = FluidContainerRegistry.getRegisteredFluidContainerData();

        ArrayNode data = mapper.createArrayNode();
        for (FluidContainerData entry : datas) {
            ObjectNode obj = data.addObject();
            if (entry.fluid != null && entry.fluid.getFluid() != null) {
                obj.set("fluid", Fluids.dump(entry.fluid.getFluid()));
                obj.put("amount", entry.fluid.amount);
            }
            if (entry.filledContainer != null) {
                obj.set("filledContainer", Items.dump(entry.filledContainer));
            }
            if (entry.emptyContainer != null) {
                obj.set("emptyContainer", Items.dump(entry.emptyContainer));
            }
        }
        setCache(exchange, 86400);
        sendResponse(exchange, data);
    }
}
