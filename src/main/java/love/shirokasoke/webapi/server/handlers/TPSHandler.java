package love.shirokasoke.webapi.server.handlers;

import java.io.IOException;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.RouteHandler;

/**
 * TPS Handler - Returns server TPS (Ticks Per Second) information
 */
public class TPSHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/tps";
    }

    @Override
    public String getDescription() {
        return "Returns server TPS (Ticks Per Second) information";
    }

    public static long mean(long[] values) {
        long sum = 0l;
        for (long v : values) {
            sum += v;
        }

        return sum / values.length;
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        MinecraftServer server = getServer();

        ObjectNode root = mapper.createObjectNode();

        for (Integer dimId : DimensionManager.getIDs()) {
            {
                double worldTickTime = mean(server.worldTickTimes.get(dimId)) * 1.0E-6D;
                double worldTPS = Math.min(1000.0 / worldTickTime, 20);
                ObjectNode tpsNode = mapper.createObjectNode();
                WorldServer world = DimensionManager.getWorld(dimId.intValue());
                if (world != null) {
                    String worldName = world.provider.getDimensionName();
                    tpsNode.put("WorldName", worldName);
                }
                tpsNode.put("TickTime", worldTickTime);
                tpsNode.put("TPS", worldTPS);
                root.set(dimId.toString(), tpsNode);
            }
        }

        sendResponse(exchange, 200, root);
    }
}
