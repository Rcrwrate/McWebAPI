package love.shirokasoke.webapi.webserver.handlers.entity;

import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.Entitys;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class EntityHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/entity";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        int entityId = Integer.parseInt(params.get("id"));

        Entity target = null;
        for (Integer dimId : DimensionManager.getIDs()) {
            WorldServer world = DimensionManager.getWorld(dimId.intValue());
            if (world != null) {
                for (Entity entity : world.loadedEntityList) {
                    if (entity.getEntityId() == entityId) {
                        target = entity;
                        break;
                    }
                }
            }
            if (target != null) break;
        }

        if (target == null) {
            throw new ApiException(404, "Entity not found with id: " + entityId);
        }

        ObjectNode result = Entitys.dump(target, true);
        sendResponse(exchange, result);
    }
}
