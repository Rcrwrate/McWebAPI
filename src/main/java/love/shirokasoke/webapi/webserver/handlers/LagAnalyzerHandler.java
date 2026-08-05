package love.shirokasoke.webapi.webserver.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.webserver.RouteHandler;

/**
 * Lag Analyzer Handler - Provides detailed lag source analysis
 * Similar to Spark mod's entity profiling
 */
public class LagAnalyzerHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/lag-analyzer";
    }

    @Override
    public String getDescription() {
        return "Analyzes potential lag sources: entities, tile entities, items, etc.";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        MinecraftServer server = getServer();

        ObjectNode root = mapper.createObjectNode();

        // Analyze entities by type
        root.set("entities", buildEntityAnalysis(server));

        // Analyze tile entities
        root.set("tileEntities", buildTileEntityAnalysis(server));

        // Memory and GC info
        root.set("memory", buildMemoryInfo());

        sendResponse(exchange, root);
    }

    private ObjectNode buildEntityAnalysis(MinecraftServer server) {
        Map<String, EntityStats> entityStats = new HashMap<>();
        Map<String, Integer> dimensionEntityCounts = new HashMap<>();

        // Collect statistics
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;

            String dimName = world.provider.getDimensionName();
            int dimEntityCount = 0;

            for (Object obj : world.loadedEntityList) {
                if (obj instanceof Entity entity) {
                    String entityName = EntityList.getEntityString(entity);

                    if (entityName == null) {
                        entityName = entity.getClass()
                            .getSimpleName();
                    }

                    EntityStats stats = entityStats.computeIfAbsent(entityName, k -> new EntityStats());
                    stats.count++;
                    stats.totalCount++;
                    dimEntityCount++;

                    // Check for potential lag sources
                    if (entity instanceof EntityItem) {
                        stats.itemCount++;
                    } else if (entity instanceof EntityXPOrb) {
                        stats.xpOrbCount++;
                    }
                }
            }

            dimensionEntityCounts.put(dimName, dimEntityCount);
        }

        // Sort by count descending
        List<Map.Entry<String, EntityStats>> sortedStats = new ArrayList<>(entityStats.entrySet());
        sortedStats.sort((a, b) -> Integer.compare(b.getValue().count, a.getValue().count));

        ObjectNode entityNode = mapper.createObjectNode();

        // Top 20 entity types
        ArrayNode byType = mapper.createArrayNode();
        int count = 0;
        for (Map.Entry<String, EntityStats> entry : sortedStats) {
            if (count >= 20) break; // Top 20 entity types
            EntityStats stats = entry.getValue();
            byType.add(
                mapper.createObjectNode()
                    .put("name", entry.getKey())
                    .put("count", stats.count)
                    .put("items", stats.itemCount)
                    .put("xpOrbs", stats.xpOrbCount));
            count++;
        }
        entityNode.set("byType", byType);

        // Dimension breakdown
        ObjectNode byDimension = mapper.createObjectNode();
        for (Map.Entry<String, Integer> entry : dimensionEntityCounts.entrySet()) {
            byDimension.put(entry.getKey(), entry.getValue());
        }
        entityNode.set("byDimension", byDimension);

        return entityNode;
    }

    private ObjectNode buildTileEntityAnalysis(MinecraftServer server) {
        Map<String, TileEntityStats> teStats = new HashMap<>();
        Map<String, Integer> dimensionTECounts = new HashMap<>();

        // Collect statistics
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;

            String dimName = world.provider.getDimensionName();
            int dimTECount = 0;

            for (Object obj : world.loadedTileEntityList) {
                if (obj instanceof TileEntity te) {
                    String teName = te.getClass()
                        .getSimpleName();

                    TileEntityStats stats = teStats.computeIfAbsent(teName, k -> new TileEntityStats());
                    stats.count++;
                    dimTECount++;

                    // Track positions for potential lag sources
                    if (stats.count <= 10) { // Track first 10 positions
                        stats.positions.add(String.format("%d,%d,%d", te.xCoord, te.yCoord, te.zCoord));
                    }
                }
            }

            dimensionTECounts.put(dimName, dimTECount);
        }

        // Sort by count descending
        List<Map.Entry<String, TileEntityStats>> sortedStats = new ArrayList<>(teStats.entrySet());
        sortedStats.sort((a, b) -> Integer.compare(b.getValue().count, a.getValue().count));

        ObjectNode teNode = mapper.createObjectNode();

        // Top 20 TE types
        ArrayNode byType = mapper.createArrayNode();
        int count = 0;
        for (Map.Entry<String, TileEntityStats> entry : sortedStats) {
            if (count >= 20) break; // Top 20 TE types
            TileEntityStats stats = entry.getValue();
            ArrayNode positions = mapper.createArrayNode();
            for (String pos : stats.positions) {
                positions.add(pos);
            }
            byType.add(
                mapper.createObjectNode()
                    .put("name", entry.getKey())
                    .put("count", stats.count)
                    .set("samplePositions", positions));
            count++;
        }
        teNode.set("byType", byType);

        // Dimension breakdown
        ObjectNode byDimension = mapper.createObjectNode();
        for (Map.Entry<String, Integer> entry : dimensionTECounts.entrySet()) {
            byDimension.put(entry.getKey(), entry.getValue());
        }
        teNode.set("byDimension", byDimension);

        return teNode;
    }

    private ObjectNode buildMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();

        return mapper.createObjectNode()
            .put("totalMB", runtime.totalMemory() / 1024 / 1024)
            .put("freeMB", runtime.freeMemory() / 1024 / 1024)
            .put("usedMB", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024)
            .put("maxMB", runtime.maxMemory() / 1024 / 1024)
            .put("availableProcessors", runtime.availableProcessors());
    }

    private static class EntityStats {

        int count = 0;
        int totalCount = 0;
        int itemCount = 0;
        int xpOrbCount = 0;
    }

    private static class TileEntityStats {

        int count = 0;
        List<String> positions = new ArrayList<>();
    }
}
