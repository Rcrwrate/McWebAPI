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
            {
                ObjectNode wNode = root.putObject(dimId.toString());
                WorldServer worldServer = DimensionManager.getWorld(dimId.intValue());
                WorldInfo worldInfo = worldServer.getWorldInfo();
                ClassUtils.getClassInfo(worldServer, wNode, "WorldServerClass");
                ClassUtils.getClassInfo(worldInfo, wNode, "WorldInfoClass");
                wNode.set("WorldInfo", mapper.valueToTree(worldInfo));
            }
        }

        sendResponse(exchange, root);
    }
}
