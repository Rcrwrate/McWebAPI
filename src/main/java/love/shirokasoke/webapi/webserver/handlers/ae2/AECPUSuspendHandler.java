package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.Accessor;
import love.shirokasoke.webapi.utils.IAEStackSafe;

/**
 * 暂停 AE 合成 CPU 上正在执行的合成任务
 */
public class AECPUSuspendHandler extends AEBaseHandler {

    @Override
    public String getPath() {
        return "/ae/cpu/suspend";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        final boolean action;
        switch (exchange.getRequestMethod()) {
            case "POST" -> action = true;
            case "DELETE" -> action = false;
            default -> throw new ApiException(405, "Method must be POST or DELETE");
        }

        IGrid grid = AEinit(exchange).grid;

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
        // .put("busy", cpu.isBusy()) isBusy会对task进行写入，规避
        // boolean wasBusy = targetCpu.isBusy();
        boolean wasBusy = false;
        if (targetCpu instanceof CraftingCPUCluster cluster) {
            wasBusy = Accessor.CraftingCPUCluster_tasks(cluster)
                .isEmpty() ? false : IAEStackSafe.hasAny(Accessor.CraftingCPUCluster_waitingFor(cluster));
            try {
                ServerThreadDispatcher.runOnServerThread(() -> cluster.setSuspended(action));
            } catch (Exception e) {
                throw new ApiException(500, "Cancel failed: " + e.getMessage());
            }
        } else {
            throw new ApiException(500, "Target CPU does not support suspend operation");
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("cpu", targetCpu.getName());
        response.put("wasBusy", wasBusy);
        response.put("Suspended", action);
        sendResponse(exchange, response);
    }
}
