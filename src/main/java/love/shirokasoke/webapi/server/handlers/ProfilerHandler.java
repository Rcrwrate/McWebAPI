package love.shirokasoke.webapi.server.handlers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.RouteHandler;

/**
 * Profiler Handler - Returns detailed server performance profiling information
 * Similar to Spark mod functionality
 */
public class ProfilerHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/profiler";
    }

    @Override
    public String getDescription() {
        return "Returns detailed server performance profiling information including dimensions, chunks, entities, and tile entities";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        MinecraftServer server = getServer();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        // Add server overview
        addServerOverview(root, server);

        // Add dimension information
        addDimensionInfo(root, server);

        // Add profiler data if available
        addProfilerData(root, server);

        String response = mapper.writeValueAsString(root);

        sendResponse(exchange, 200, response);
    }

    private void addServerOverview(ObjectNode root, MinecraftServer server) {
        ObjectNode serverNode = root.putObject("server");
        serverNode.put(
            "totalMemoryMB",
            Runtime.getRuntime()
                .totalMemory() / 1024
                / 1024);
        serverNode.put(
            "freeMemoryMB",
            Runtime.getRuntime()
                .freeMemory() / 1024
                / 1024);
        serverNode.put(
            "maxMemoryMB",
            Runtime.getRuntime()
                .maxMemory() / 1024
                / 1024);
    }

    private void addDimensionInfo(ObjectNode root, MinecraftServer server) {
        ObjectNode dimensionsNode = root.putObject("dimensions");

        WorldServer[] worlds = server.worldServers;
        for (WorldServer world : worlds) {
            if (world == null) continue;

            ObjectNode worldNode = dimensionsNode.putObject(String.valueOf(world.provider.dimensionId));

            // Basic world info
            worldNode.put("name", world.provider.getDimensionName());
            worldNode.put(
                "loadedChunks",
                world.getChunkProvider()
                    .getLoadedChunkCount());
            worldNode.put("totalEntities", world.loadedEntityList.size());
            worldNode.put("totalTileEntities", world.loadedTileEntityList.size());

            // Find laggy chunks
            addLaggyChunks(worldNode, world);
        }
    }

    private void addLaggyChunks(ObjectNode worldNode, WorldServer world) {
        ArrayNode laggyChunksArray = worldNode.putArray("laggyChunks");

        Map<Chunk, Integer> entityCountMap = new HashMap<>();

        // Count entities per chunk
        for (Object obj : world.loadedEntityList) {
            if (obj instanceof Entity) {
                Entity entity = (Entity) obj;
                Chunk chunk = world.getChunkFromChunkCoords(entity.chunkCoordX, entity.chunkCoordZ);
                entityCountMap.put(chunk, entityCountMap.getOrDefault(chunk, 0) + 1);
            }
        }

        // Find chunks with most entities
        entityCountMap.entrySet()
            .stream()
            .sorted(
                (a, b) -> b.getValue()
                    .compareTo(a.getValue()))
            .limit(5) // Top 5 laggiest chunks
            .forEach(entry -> {
                Chunk chunk = entry.getKey();
                int entityCount = entry.getValue();

                ObjectNode chunkNode = laggyChunksArray.addObject();
                chunkNode.put("chunkX", chunk.xPosition);
                chunkNode.put("chunkZ", chunk.zPosition);
                chunkNode.put("entityCount", entityCount);
            });
    }

    private void addProfilerData(ObjectNode root, MinecraftServer server) {
        try {
            Profiler profiler = server.theProfiler;
            if (profiler == null) {
                root.putNull("profiler");
                return;
            }

            ObjectNode profilerNode = root.putObject("profiler");
            profilerNode.put("enabled", profiler.profilingEnabled);
        } catch (Exception e) {
            MyMod.LOG.error("Failed to get profiler data", e);
            root.putNull("profiler");
        }
    }

}
