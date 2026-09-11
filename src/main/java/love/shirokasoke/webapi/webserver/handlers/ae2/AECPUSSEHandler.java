package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import love.shirokasoke.webapi.utils.Logs;
import love.shirokasoke.webapi.webserver.Context;
import love.shirokasoke.webapi.webserver.SSEClient;
import love.shirokasoke.webapi.webserver.SSEHandler;

public class AECPUSSEHandler implements SSEHandler {

    @Override
    public String getPath() {
        return "/ae/cpu/sse";
    }

    @Override
    public String getDescription() {
        return "AE Crafting CPUs";
    }

    @Override
    public void run(HttpExchange exchange, SSEClient client) throws Exception {
        final Map<String, String> params = parseQueryParams(exchange);
        final int sleep = Integer.valueOf(params.getOrDefault("interval", "5"));

        Context context = new Context(getCoordinates(params)).initServer()
            .initWorld()
            .checkblockExists()
            .initTileEntity()
            .initAE();

        // 从网格缓存中获取合成网格接口
        ICraftingGrid craftingGrid = context.grid.getCache(ICraftingGrid.class);
        if (craftingGrid == null) {
            throw new ApiException(500, "Crafting grid not available");
        }

        while (client.isOpen()) {
            ArrayNode cpus = mapper.createArrayNode();
            for (ICraftingCPU cpu : craftingGrid.getCpus()) {
                ObjectNode cpuNode = mapper.createObjectNode();

                // 理论上 CPU 只可能是 CraftingCPUCluster
                if (cpu instanceof CraftingCPUCluster cluster) {
                    AECPUHandler.dumpCraftingTasks(cluster, cpuNode);
                    AECPUHandler.dumpBasicInfo(cluster, cpuNode);
                } else {
                    Logs.debugFields(cpus);
                }
                cpus.add(cpuNode);
            }

            client.eventJson("aecpu", cpus);
            Thread.sleep(sleep);
        }
    }
}
