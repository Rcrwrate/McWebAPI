package love.shirokasoke.webapi.server.handlers;

import java.io.IOException;

import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.RouteHandler;
import love.shirokasoke.webapi.utils.ClassUtils;

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
            {
                ObjectNode wNode = mapper.createObjectNode();
                WorldServer worldServer = DimensionManager.getWorld(dimId.intValue());
                WorldInfo WorldInfo = worldServer.getWorldInfo();
                ClassUtils.getClassInfo(worldServer, wNode, "worldServer");
                ClassUtils.getClassInfo(WorldInfo, wNode, "WorldInfo");
                wNode.set("WorldInfo", mapper.valueToTree(WorldInfo));
                root.set(dimId.toString(), wNode);
            }
        }

        sendResponse(exchange, 200, root);
    }
}
