package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;

/**
 * 取消 AE 合成 CPU 上正在执行的合成任务
 */
public class AECPUCancelHandler extends AEBaseHandler {

    @Override
    public String getPath() {
        return "/ae/cpu/cancel";
    }

    @Override
    public String getDescription() {
        return "Cancel crafting task on AE CPU. DELETE body: {name?} or {id?}";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod()
            .equals("DELETE")) {
            throw new ApiException(400, "Method must be DELETE");
        }

        AEinit(exchange);

        JsonNode body = getBody(exchange);
        String targetName = body.path("name")
            .asText(null);
        int targetId = body.path("id")
            .asInt(-1);

        boolean hasName = targetName != null && !targetName.isEmpty();

        if (!hasName && targetId < 0) {
            throw new ApiException(400, "Request body must contain 'name' or 'id'");
        }

        ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);
        if (craftingGrid == null) {
            throw new ApiException(500, "Crafting grid not available");
        }

        // 将 CPUs 转为 List 以便按索引访问
        List<ICraftingCPU> cpuList = craftingGrid.getCpus()
            .stream()
            .toList();

        ICraftingCPU targetCpu = null;

        if (hasName) {
            for (ICraftingCPU cpu : cpuList) {
                if (targetName.equals(cpu.getName())) {
                    targetCpu = cpu;
                    break;
                }
            }
            if (targetCpu == null) {
                throw new ApiException(404, "CPU not found with name: " + targetName);
            }
        }

        if (targetCpu == null && targetId >= 0) {
            if (targetId >= cpuList.size()) {
                throw new ApiException(404, "CPU index out of range: " + targetId + " (total: " + cpuList.size() + ")");
            }
            targetCpu = cpuList.get(targetId);
        }

        if (targetCpu == null) {
            throw new ApiException(404, "Target CPU not found");
        }

        // 取消任务。ICraftingCPU 接口没有 cancel()，实际实现类 CraftingCPUCluster 才有
        boolean wasBusy = targetCpu.isBusy();
        if (targetCpu instanceof CraftingCPUCluster cluster) {
            try {
                ServerThreadDispatcher.runOnServerThread(cluster::cancel);
            } catch (Exception e) {
                throw new ApiException(500, "Cancel failed: " + e.getMessage());
            }
        } else {
            throw new ApiException(500, "Target CPU does not support cancel operation");
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("cpu", targetCpu.getName());
        response.put("wasBusy", wasBusy);
        sendResponse(exchange, response);
    }
}
