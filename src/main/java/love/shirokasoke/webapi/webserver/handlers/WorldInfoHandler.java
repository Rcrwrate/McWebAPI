package love.shirokasoke.webapi.webserver.handlers;

import java.io.IOException;

import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.ClassUtils;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class WorldInfoHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/WorldInfo";
    }

    @Override
    public String getDescription() {
        return "Returns WorldInfo";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        ObjectNode root = mapper.createObjectNode();

        for (Integer dimId : DimensionManager.getIDs()) {
            WorldServer worldServer = DimensionManager.getWorld(dimId.intValue());
            if (worldServer == null) {
                // 维度未加载，跳过以避免 NPE（参考 TPSHandler / EntityHandler 的判空写法）
                continue;
            }
            ObjectNode wNode = root.putObject(dimId.toString());
            WorldInfo worldInfo = worldServer.getWorldInfo();
            ClassUtils.getClassInfo(worldServer, wNode, "WorldServerClass");
            ClassUtils.getClassInfo(worldInfo, wNode, "WorldInfoClass");
            wNode.set("WorldInfo", mapper.valueToTree(worldInfo));
        }

        sendResponse(exchange, root);
    }
}
