package love.shirokasoke.webapi.webserver.handlers.chunk;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.Chunks;
import love.shirokasoke.webapi.utils.McAccessor;
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

    public class ChunkCoord extends ChunkCoordIntPair {

        public int dimension;

        public ChunkCoord(int chunkX, int chunkZ, int dimension) {
            super(chunkX, chunkZ);
            this.dimension = dimension;
        }
    }

    protected ChunkCoord getCo(HttpExchange exchange) throws ApiException {
        return getCo(parseQueryParams(exchange));
    }

    protected ChunkCoord getCo(java.util.Map<String, String> params) throws ApiException {
        int chunkX;
        int chunkZ;
        int dimension = 0;
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
        return new ChunkCoord(chunkX, chunkZ, dimension);
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        ChunkCoord cc = getCo(exchange);
        Chunk chunk = McAccessor.loadChunk(cc.dimension, cc.chunkXPos, cc.chunkZPos);
        ObjectNode data = mapper.createObjectNode();
        data.put("dimension", cc.dimension);
        Chunks.dump(chunk, data, 2);
        sendResponse(exchange, data);
    }
}
