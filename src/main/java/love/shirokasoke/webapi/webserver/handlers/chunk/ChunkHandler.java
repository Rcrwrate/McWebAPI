package love.shirokasoke.webapi.webserver.handlers.chunk;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.Chunks;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class ChunkHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/chunk";
    }

    @Override
    public String getDescription() {
        return "Get chunk information at specified coordinates. Query params: x, y, z, dim (optional, default=0) or chunkX, chunkZ, dim (optional, default=0)";
    }

    protected int chunkX;
    protected int chunkZ;
    protected int dimension = 0;

    protected void getCo(HttpExchange exchange) throws ApiException {
        getCo(parseQueryParams(exchange));
    }

    protected void getCo(java.util.Map<String, String> params) throws ApiException {
        if (params.containsKey("chunkX") && params.containsKey("chunkZ")) {
            chunkX = Integer.parseInt(params.get("chunkX"));
            chunkZ = Integer.parseInt(params.get("chunkZ"));
        } else if (params.containsKey("x") && params.containsKey("z")) {
            int worldX = Integer.parseInt(params.get("x"));
            int worldZ = Integer.parseInt(params.get("z"));
            chunkX = worldX >> 4;
            chunkZ = worldZ >> 4;
        } else {
            throw new ApiException(400, "Missing required parameters. Provide either chunkX & chunkZ, or x & z");
        }
        if (params.containsKey("dim") || params.containsKey("dimension")) {
            dimension = Integer.parseInt(params.get("dim") != null ? params.get("dim") : params.get("dimension"));
        }
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        getCo(exchange);

        MinecraftServer server = getServer();
        WorldServer world = server.worldServerForDimension(dimension);
        if (world == null) {
            throw new ApiException(404, "Invalid dimension: " + dimension);
        }

        if (!world.theChunkProviderServer.chunkExists(chunkX, chunkZ)) {
            throw new ApiException(
                404,
                "Chunk not loaded at coordinates: chunkX=" + chunkX
                    + ", chunkZ="
                    + chunkZ
                    + ", dimension="
                    + dimension);
        }

        Chunk chunk = world.theChunkProviderServer.loadChunk(chunkX, chunkZ);
        if (chunk == null) {
            throw new ApiException(404, "Chunk not found at coordinates: chunkX=" + chunkX + ", chunkZ=" + chunkZ);
        }

        ObjectNode data = mapper.createObjectNode();
        data.put("dimension", dimension);
        Chunks.dump(chunk, data, 2);
        sendResponse(exchange, data);
    }
}
