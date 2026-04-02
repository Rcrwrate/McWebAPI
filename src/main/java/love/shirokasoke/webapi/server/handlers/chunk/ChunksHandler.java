package love.shirokasoke.webapi.server.handlers.chunk;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.RouteHandler;
import love.shirokasoke.webapi.utils.Chunks;
import love.shirokasoke.webapi.utils.ClassUtils;

public class ChunksHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/chunks";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        MinecraftServer server = getServer();
        ObjectNode data = mapper.createObjectNode();
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;
            ObjectNode dimNode = data.putObject(String.valueOf(world.provider.dimensionId));

            IChunkProvider pro = world.getChunkProvider();
            ClassUtils.getClassInfo(pro, dimNode);

            if (pro instanceof ChunkProviderServer) {
                ArrayNode chunksArrayNode = mapper.createArrayNode();
                ChunkProviderServer proS = (ChunkProviderServer) pro;
                for (Chunk chunk : proS.func_152380_a()) {
                    chunksArrayNode.add(Chunks.dump(chunk));
                }
                dimNode.set("chunks", chunksArrayNode);
            }
            dimNode.put("count", pro.getLoadedChunkCount());
        }
        sendResponse(exchange, 200, data);
    }

}
