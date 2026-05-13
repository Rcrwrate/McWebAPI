package love.shirokasoke.webapi.server.handlers.ae2;

import java.io.IOException;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPart;
import appeng.api.util.DimensionalCoord;
import love.shirokasoke.webapi.utils.ClassUtils;

/**
 * 查看 AE 网络中的所有节点信息
 */
public class AENodesHandler extends AEBaseHandler {

    @Override
    public String getPath() {
        return "/ae/nodes";
    }

    @Override
    public String getDescription() {
        return "List all nodes in the AE network";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        AEinit(exchange);

        ArrayNode nodes = mapper.createArrayNode();
        int activeCount = 0;
        int totalCount = 0;

        for (IGridNode node : grid.getNodes()) {
            totalCount++;
            ObjectNode nodeData = nodes.addObject();

            nodeData.put("active", node.isActive());
            nodeData.put("meetsChannel", node.meetsChannelRequirements());
            nodeData.put("playerID", node.getPlayerID());

            if (node.isActive()) {
                activeCount++;
            }

            // 节点对应的机器（TileEntity 或 IPart）信息
            IGridHost machine = node.getMachine();
            if (machine != null) {
                ClassUtils.getClassInfo(machine, nodeData, "machineClass");
                // 判断是否为线缆上的部件（如输入/输出总线、存储/合成面板等）
                nodeData.put("isPart", machine instanceof IPart);
                // 判断是否为可操作主机（用于安全权限校验）
                nodeData.put("isIActionHost", machine instanceof IActionHost);
            }

            IGridBlock block = node.getGridBlock();
            DimensionalCoord loc = block.getLocation();
            if (loc != null) {
                nodeData.putObject("location")
                    .put("x", loc.x)
                    .put("y", loc.y)
                    .put("z", loc.z)
                    .put("dimension", loc.getDimension());
            }
            nodeData.put("idlePowerUsage", block.getIdlePowerUsage());

            ArrayNode flagsArray = nodeData.putArray("flags");
            for (appeng.api.networking.GridFlags flag : block.getFlags()) {
                flagsArray.add(flag.name());
            }
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("total", totalCount);
        response.put("active", activeCount);
        response.set("nodes", nodes);

        setCache(exchange, 5);
        sendResponse(exchange, response);
    }
}
