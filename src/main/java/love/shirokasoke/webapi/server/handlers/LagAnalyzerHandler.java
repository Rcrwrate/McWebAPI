package love.shirokasoke.webapi.server.handlers;

import java.io.IOException;
import java.io.OutputStream;
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

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.RouteHandler;

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

        StringBuilder response = new StringBuilder();
        response.append("{");

        // Analyze entities by type
        appendEntityAnalysis(server, response);
        response.append(", ");

        // Analyze tile entities
        appendTileEntityAnalysis(server, response);
        response.append(", ");

        // Memory and GC info
        appendMemoryInfo(response);

        response.append("}");

        exchange.getResponseHeaders()
            .set("Content-Type", "application/json");
        exchange.sendResponseHeaders(
            200,
            response.toString()
                .getBytes().length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(
                response.toString()
                    .getBytes());
        }
    }

    private void appendEntityAnalysis(MinecraftServer server, StringBuilder response) {
        response.append("\"entities\": {");

        Map<String, EntityStats> entityStats = new HashMap<>();
        Map<String, Integer> dimensionEntityCounts = new HashMap<>();

        // Collect statistics
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;

            String dimName = world.provider.getDimensionName();
            int dimEntityCount = 0;

            for (Object obj : world.loadedEntityList) {
                if (obj instanceof Entity) {
                    Entity entity = (Entity) obj;
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

        response.append("\"byType\": [");
        int count = 0;
        for (Map.Entry<String, EntityStats> entry : sortedStats) {
            if (count >= 20) break; // Top 20 entity types

            if (count > 0) response.append(", ");

            EntityStats stats = entry.getValue();
            response.append(
                String.format(
                    "{\"name\": \"%s\", \"count\": %d, \"items\": %d, \"xpOrbs\": %d}",
                    entry.getKey(),
                    stats.count,
                    stats.itemCount,
                    stats.xpOrbCount));
            count++;
        }
        response.append("], ");

        // Dimension breakdown
        response.append("\"byDimension\": {");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : dimensionEntityCounts.entrySet()) {
            if (!first) response.append(", ");
            response.append(String.format("\"%s\": %d", entry.getKey(), entry.getValue()));
            first = false;
        }
        response.append("}");

        response.append("}");
    }

    private void appendTileEntityAnalysis(MinecraftServer server, StringBuilder response) {
        response.append("\"tileEntities\": {");

        Map<String, TileEntityStats> teStats = new HashMap<>();
        Map<String, Integer> dimensionTECounts = new HashMap<>();

        // Collect statistics
        for (WorldServer world : server.worldServers) {
            if (world == null) continue;

            String dimName = world.provider.getDimensionName();
            int dimTECount = 0;

            for (Object obj : world.loadedTileEntityList) {
                if (obj instanceof TileEntity) {
                    TileEntity te = (TileEntity) obj;
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

        response.append("\"byType\": [");
        int count = 0;
        for (Map.Entry<String, TileEntityStats> entry : sortedStats) {
            if (count >= 20) break; // Top 20 TE types

            if (count > 0) response.append(", ");

            TileEntityStats stats = entry.getValue();
            response.append(
                String.format(
                    "{\"name\": \"%s\", \"count\": %d, \"samplePositions\": [\"%s\"]}",
                    entry.getKey(),
                    stats.count,
                    String.join("\", \"", stats.positions)));
            count++;
        }
        response.append("], ");

        // Dimension breakdown
        response.append("\"byDimension\": {");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : dimensionTECounts.entrySet()) {
            if (!first) response.append(", ");
            response.append(String.format("\"%s\": %d", entry.getKey(), entry.getValue()));
            first = false;
        }
        response.append("}");

        response.append("}");
    }

    private void appendMemoryInfo(StringBuilder response) {
        Runtime runtime = Runtime.getRuntime();

        response.append("\"memory\": {");
        response.append(String.format("\"totalMB\": %d, ", runtime.totalMemory() / 1024 / 1024));
        response.append(String.format("\"freeMB\": %d, ", runtime.freeMemory() / 1024 / 1024));
        response
            .append(String.format("\"usedMB\": %d, ", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024));
        response.append(String.format("\"maxMB\": %d, ", runtime.maxMemory() / 1024 / 1024));
        response.append(String.format("\"availableProcessors\": %d", runtime.availableProcessors()));
        response.append("}");
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
