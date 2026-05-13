package love.shirokasoke.webapi.server.handlers.entity;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.RouteHandler;
import love.shirokasoke.webapi.utils.Entitys;

public class EntitiesHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/entities";
    }

    @Override
    public String getDescription() {
        return "获取服务器中所有已加载实体列表";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        @SuppressWarnings("unused")
        MinecraftServer server = getServer();

        ObjectNode root = mapper.createObjectNode();

        for (Integer dimId : DimensionManager.getIDs()) {
            WorldServer world = DimensionManager.getWorld(dimId.intValue());
            if (world != null) {
                ObjectNode data = root.putObject(dimId.toString());
                String worldName = world.provider.getDimensionName();
                data.put("WorldName", worldName);

                ArrayNode loaded = data.putArray("loadedEntityList");
                for (Entity obj : world.loadedEntityList) {
                    loaded.add(Entitys.dump(obj, world.loadedEntityList.size() < 50));
                }
            }
        }
        sendResponse(exchange, root);
    }
}
