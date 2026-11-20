package love.shirokasoke.webapi.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sinthoras.visualprospecting.database.OreVeinPosition;
import com.sinthoras.visualprospecting.database.veintypes.VeinType;

import gregtech.api.interfaces.IOreMaterial;
import gregtech.api.util.StringUtils;
import gregtech.common.WorldgenGTOreLayer;
import love.shirokasoke.webapi.Constant;
import love.shirokasoke.webapi.webserver.RouteHandler.ApiException;

/**
 * GT5 矿脉元数据
 */
public final class GT5Veins {

    private GT5Veins() {}

    // region 查询

    /** 该维度可生成的矿脉，顺序与 GT 的加权抽取表一致 */
    public static List<WorldgenGTOreLayer> candidates(String dimensionName) {
        if (dimensionName == null) return Collections.emptyList();
        List<WorldgenGTOreLayer> layers = new ArrayList<>();
        for (WorldgenGTOreLayer layer : WorldgenGTOreLayer.sList) {
            if (layer.canGenerateIn(dimensionName)) layers.add(layer);
        }
        return layers;
    }

    /** 按注册名查找矿脉，未注册时返回 null */
    public static WorldgenGTOreLayer byName(String name) {
        if (name == null) return null;
        for (WorldgenGTOreLayer layer : WorldgenGTOreLayer.sList) {
            if (layer.mWorldGenName.equals(name)) return layer;
        }
        return null;
    }

    /** 大小写不敏感地按注册名查找矿脉，未注册时返回 null；只查全局注册表，与维度无关 */
    public static WorldgenGTOreLayer byNameIgnoreCase(String query) {
        if (query == null) return null;
        for (WorldgenGTOreLayer layer : WorldgenGTOreLayer.sList) {
            if (layer.mWorldGenName.equalsIgnoreCase(query)) return layer;
        }
        return null;
    }

    // endregion

    // region 本地化

    /** 脉型注册名 -> 本地化名 */
    private static final Map<String, String> LOCALIZED_NAMES = new ConcurrentHashMap<>();

    public static String localizedName(OreVeinPosition position) {
        VeinType type = position.veinType;
        if (type == null) return null;
        return LOCALIZED_NAMES.computeIfAbsent(type.name, name -> {
            WorldgenGTOreLayer layer = byName(name);
            return layer == null ? type.getVeinName() : localizedName(layer);
        });
    }

    public static String localizedName(WorldgenGTOreLayer layer) {
        Set<String> names = new LinkedHashSet<>();
        collectName(names, layer.mPrimary);
        collectName(names, layer.mSecondary);
        collectName(names, layer.mBetween);
        collectName(names, layer.mSporadic);
        return StringUtils.formatList(names.toArray(new String[0]));
    }

    private static void collectName(Set<String> names, IOreMaterial material) {
        if (material == null) return;
        String name = material.getLocalizedName();
        if (name != null && !name.isEmpty()) names.add(name);
    }

    // endregion

    // region JSON 输出

    public static ObjectNode veinNode(OreVeinPosition entry) {
        ObjectNode node = Constant.mapper.createObjectNode();
        VeinType type = entry.veinType;
        node.put("vein", type.name);
        node.put("localizedName", localizedName(entry));
        node.put("chunkX", entry.chunkX);
        node.put("chunkZ", entry.chunkZ);
        node.put("x", entry.getBlockX());
        node.put("z", entry.getBlockZ());
        node.put("blockSize", type.blockSize);
        node.put("depleted", entry.isDepleted());
        node.put(
            "source",
            entry.getSource()
                .name());
        writeOres(node, type.primaryOre, type.secondaryOre, type.inBetweenOre, type.sporadicOre);
        return node;
    }

    public static void writeOres(ObjectNode node, IOreMaterial primary, IOreMaterial secondary, IOreMaterial between,
        IOreMaterial sporadic) {
        ObjectNode ores = node.putObject("ores");
        ObjectNode oreIds = node.putObject("oreIds");
        putOre(ores, oreIds, "primary", primary);
        putOre(ores, oreIds, "secondary", secondary);
        putOre(ores, oreIds, "between", between);
        putOre(ores, oreIds, "sporadic", sporadic);
    }

    private static void putOre(ObjectNode ores, ObjectNode oreIds, String key, IOreMaterial material) {
        if (material == null) return;
        String localized = material.getLocalizedName();
        if (localized == null) return;
        ores.put(key, localized);
        String internal = material.getInternalName();
        if (internal != null) oreIds.put(key, internal);
    }

    // endregion

    // region 工具

    public static double round(double value, int digits) {
        double scale = Math.pow(10, digits);
        return Math.round(value * scale) / scale;
    }

    public static Integer parseDimension(Map<String, String> params) throws ApiException {
        String raw = params.get("dim");
        if (raw == null || raw.isEmpty()) raw = params.get("dimension");
        if (raw == null || raw.isEmpty()) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Invalid dimension: " + raw);
        }
    }

    // endregion
}
