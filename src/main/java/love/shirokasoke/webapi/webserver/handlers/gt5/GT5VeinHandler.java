package love.shirokasoke.webapi.webserver.handlers.gt5;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.world.WorldServer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sinthoras.visualprospecting.database.OreVeinPosition;
import com.sun.net.httpserver.HttpExchange;

import galacticgreg.api.enums.DimensionDef;
import gregtech.common.WorldgenGTOreLayer;
import love.shirokasoke.webapi.utils.GT5Veins;
import love.shirokasoke.webapi.utils.McAccessor;
import love.shirokasoke.webapi.utils.VeinProspection;
import love.shirokasoke.webapi.webserver.RouteHandler;

/**
 * 按坐标搜索最近的已知 GT5 矿脉
 */
public class GT5VeinHandler implements RouteHandler {

    private static final int DEFAULT_RADIUS = 2048;
    private static final int MIN_RADIUS = 16;
    private static final int MAX_RADIUS = 8192;
    private static final int DEFAULT_LIMIT = 32;
    private static final int MAX_LIMIT = 512;
    private static final int MAX_RADIUS_CHUNKS = 512;

    @Override
    public String getPath() {
        return "/gt5/vein";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        coordinates co = getCoordinates(params);

        int radius = parseInt(params.get("radius"), DEFAULT_RADIUS, MIN_RADIUS, MAX_RADIUS);
        int limit = parseInt(params.get("limit"), DEFAULT_LIMIT, 1, MAX_LIMIT);
        boolean excludeDepleted = !"false".equalsIgnoreCase(params.get("excludeDepleted"));

        WorldServer world = McAccessor.getWorld(co.dimension);
        String dimensionName = DimensionDef.getDimensionName(world);

        int radiusChunks = Math.min(MAX_RADIUS_CHUNKS, (radius + 15) >> 4);
        int centerChunkX = co.posX >> 4;
        int centerChunkZ = co.posZ >> 4;

        long start = System.nanoTime();
        List<OreVeinPosition> entries = VeinProspection.scan(
            co.dimension,
            centerChunkX - radiusChunks,
            centerChunkZ - radiusChunks,
            centerChunkX + radiusChunks,
            centerChunkZ + radiusChunks);

        String requested = params.get("vein");
        String target = null;
        if (requested != null && !requested.trim()
            .isEmpty()) {
            WorldgenGTOreLayer layer = GT5Veins.byNameIgnoreCase(requested.trim());
            if (layer == null) {
                throw new ApiException(
                    404,
                    "Unknown vein: " + requested + ", see /gt5/veindefs?dim=" + co.dimension + " for " + dimensionName);
            }
            target = layer.mWorldGenName;
        }

        List<OreVeinPosition> picked = new ArrayList<>();
        for (OreVeinPosition entry : entries) {
            if (target != null && !target.equals(entry.veinType.name)) continue;
            if (excludeDepleted && entry.isDepleted()) continue;
            picked.add(entry);
        }
        picked.sort(Comparator.comparingDouble(entry -> distanceSq(entry, co.posX, co.posZ)));

        int matched = picked.size();
        if (matched > limit) picked = picked.subList(0, limit);

        ObjectNode data = mapper.createObjectNode();
        data.put("x", co.posX);
        data.put("y", co.posY);
        data.put("z", co.posZ);
        data.put("dimension", co.dimension);
        data.put("dimensionName", dimensionName);
        data.put("target", target);
        data.put("radius", radius);
        data.put("radiusChunks", radiusChunks);
        data.put("limit", limit);
        data.put("excludeDepleted", excludeDepleted);
        data.put("matched", matched);
        data.put("elapsedMs", GT5Veins.round((System.nanoTime() - start) / 1_000_000.0, 2));

        ArrayNode veins = data.putArray("veins");
        for (OreVeinPosition entry : picked) {
            ObjectNode node = GT5Veins.veinNode(entry);
            node.put("distance", GT5Veins.round(Math.sqrt(distanceSq(entry, co.posX, co.posZ)), 2));
            veins.add(node);
        }
        sendResponse(exchange, data);
    }

    private static double distanceSq(OreVeinPosition entry, int blockX, int blockZ) {
        double dx = entry.getBlockX() - blockX;
        double dz = entry.getBlockZ() - blockZ;
        return dx * dx + dz * dz;
    }

    private static int parseInt(String value, int defaultValue, int min, int max) throws ApiException {
        if (value == null || value.trim()
            .isEmpty()) return defaultValue;
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) {
                throw new ApiException(400, "value must be between " + min + " and " + max + ": " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Invalid number: " + value);
        }
    }
}
