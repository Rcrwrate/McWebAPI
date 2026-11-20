package love.shirokasoke.webapi.webserver.handlers.gt5;

import java.util.List;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import galacticgreg.api.enums.DimensionDef;
import gregtech.common.WorldgenGTOreLayer;
import love.shirokasoke.webapi.utils.GT5Veins;
import love.shirokasoke.webapi.webserver.RouteHandler;

/**
 * 按维度列出可生成的 GT 矿脉定义 {@link WorldgenGTOreLayer#sList}
 */
public class GT5VeinDefHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/gt5/veindefs";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        final ObjectNode data = mapper.createObjectNode();
        ObjectNode dimensions = data.putObject("dimensions");
        for (Integer dimId : DimensionManager.getIDs()) {
            WorldServer world = DimensionManager.getWorld(dimId);
            if (world == null) continue;
            dimensions.set(dimId.toString(), buildDimension(world));
        }

        setCache(exchange, 86400);
        sendResponse(exchange, data);
    }

    private ObjectNode buildDimension(WorldServer world) {
        int dimension = world.provider.dimensionId;
        String dimensionName = DimensionDef.getDimensionName(world);

        List<WorldgenGTOreLayer> layers = GT5Veins.candidates(dimensionName);
        int totalWeight = 0;
        for (WorldgenGTOreLayer layer : layers) {
            totalWeight += layer.getWeight();
        }

        ObjectNode node = mapper.createObjectNode();
        node.put("dimension", dimension);
        node.put("dimensionName", dimensionName);

        ArrayNode veins = node.putArray("veins");
        for (WorldgenGTOreLayer layer : layers) {
            ObjectNode vein = veins.addObject();
            vein.put("vein", layer.mWorldGenName);
            vein.put("localizedName", GT5Veins.localizedName(layer));
            vein.put("weight", layer.getWeight());
            vein.put("chance", totalWeight <= 0 ? 0 : GT5Veins.round(layer.getWeight() / (double) totalWeight, 4));
            vein.put("size", layer.mSize);
            vein.put("density", layer.mDensity);
            vein.put("minY", layer.getMinY(dimensionName));
            vein.put("maxY", layer.getMaxY(dimensionName));

            GT5Veins.writeOres(vein, layer.mPrimary, layer.mSecondary, layer.mBetween, layer.mSporadic);
        }
        return node;
    }
}
