package love.shirokasoke.webapi.webserver.handlers.gt5;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.world.WorldServer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sinthoras.visualprospecting.database.OreVeinPosition;
import com.sun.net.httpserver.HttpExchange;

import galacticgreg.api.enums.DimensionDef;
import love.shirokasoke.webapi.utils.GT5Veins;
import love.shirokasoke.webapi.utils.McAccessor;
import love.shirokasoke.webapi.utils.VeinProspection;
import love.shirokasoke.webapi.webserver.RouteHandler;

/**
 * 列出指定维度中所有已生成的 GT5 矿脉
 */
public class GT5VeinListHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/gt5/veins";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        Integer dimension = GT5Veins.parseDimension(params);
        if (dimension == null) throw new ApiException(400, "Missing DimensionID");

        WorldServer world = McAccessor.getWorld(dimension);
        String dimensionName = DimensionDef.getDimensionName(world);

        List<OreVeinPosition> entries = VeinProspection.scanAll(dimension);
        // 底层按 long 键散列，顺序不稳定，按区块排序让同一份数据每次输出一致
        entries.sort(
            Comparator.comparingInt((OreVeinPosition entry) -> entry.chunkX)
                .thenComparingInt(entry -> entry.chunkZ));

        ObjectNode data = mapper.createObjectNode();
        data.put("dimension", dimension);
        data.put("dimensionName", dimensionName);

        ArrayNode veins = data.putArray("veins");
        for (OreVeinPosition entry : entries) {
            veins.add(GT5Veins.veinNode(entry));
        }
        sendResponse(exchange, data);
    }
}
