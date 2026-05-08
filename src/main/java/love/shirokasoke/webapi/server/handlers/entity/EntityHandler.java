package love.shirokasoke.webapi.server.handlers.entity;

import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.RouteHandler;
import love.shirokasoke.webapi.utils.Entitys;

public class EntityHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/entity";
    }

    @Override
    public String getDescription() {
        return "根据 entityId 查询单个实体详情，Query 参数: id";
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
            throw new Error(404, "Entity not found with id: " + entityId);
        }

        ObjectNode result = Entitys.dump(target, true);
        sendResponse(exchange, 200, result);
    }
}
