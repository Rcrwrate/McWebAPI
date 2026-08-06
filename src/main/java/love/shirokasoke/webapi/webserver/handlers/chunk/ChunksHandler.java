package love.shirokasoke.webapi.webserver.handlers.chunk;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.Chunks;
import love.shirokasoke.webapi.utils.ClassUtils;
import love.shirokasoke.webapi.utils.McAccessor;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class ChunksHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/chunks";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        MinecraftServer server = McAccessor.getServer();
        ObjectNode data = mapper.createObjectNode();
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;
            ObjectNode dimNode = data.putObject(String.valueOf(world.provider.dimensionId));
            dimNode.put("name", world.provider.getDimensionName());

            IChunkProvider pro = world.getChunkProvider();

            ClassUtils.getClassInfo(pro, dimNode);

            if (pro instanceof ChunkProviderServer proS) {
                ArrayNode chunksArrayNode = mapper.createArrayNode();
                for (Chunk chunk : proS.func_152380_a()) {
                    chunksArrayNode.add(Chunks.dump(chunk, pro.getLoadedChunkCount() > 512 ? 0 : 1));
                }
                dimNode.set("chunks", chunksArrayNode);
            }
            dimNode.put("count", pro.getLoadedChunkCount());
        }
        sendResponse(exchange, data);
    }

}
