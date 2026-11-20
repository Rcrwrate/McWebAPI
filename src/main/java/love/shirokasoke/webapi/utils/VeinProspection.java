package love.shirokasoke.webapi.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.sinthoras.visualprospecting.database.DimensionCache;
import com.sinthoras.visualprospecting.database.OreVeinPosition;
import com.sinthoras.visualprospecting.database.ServerCache;
import com.sinthoras.visualprospecting.database.veintypes.VeinType;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * 直接读取 VisualProspecting 缓存
 *
 * <p>
 * 涉及相关数据类型
 * <p>
 * {@link ServerCache#dimensions} -> {@link it.unimi.dsi.fastutil.ints.Int2ObjectMap}
 * <p>
 * {@link DimensionCache#oreChunks} -> {@link it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap}
 * <p>
 * 部分只读情况下安全
 */
public final class VeinProspection {

    private VeinProspection() {}

    /**
     * 读取矩形区块范围内的所有已知矿脉
     */
    public static List<OreVeinPosition> scan(int dimensionId, int minChunkX, int minChunkZ, int maxChunkX,
        int maxChunkZ) {
        return copy(ServerCache.instance.prospectOreChunks(dimensionId, minChunkX, minChunkZ, maxChunkX, maxChunkZ));
    }

    /**
     * 读取整个维度的所有已知矿脉
     */
    public static List<OreVeinPosition> scanAll(int dimensionId) {
        DimensionCache cache = dimensionCache(dimensionId);
        if (cache == null) return new ArrayList<>();
        return copy(cache.getAllOreVeins());
    }

    // region tools

    /** 过滤NULL */
    private static List<OreVeinPosition> copy(Collection<OreVeinPosition> raw) {
        List<OreVeinPosition> positions = new ArrayList<>(raw.size());
        for (OreVeinPosition position : raw) {
            if (position == null) continue;
            VeinType type = position.veinType;
            if (type == null || type == VeinType.NO_VEIN) continue;
            positions.add(position);
        }
        return positions;
    }

    /** 维度缓存 */
    private static DimensionCache dimensionCache(int dimensionId) {
        Int2ObjectMap<DimensionCache> dimensions = Accessor.WorldCache_dimensions(ServerCache.instance);
        return dimensions == null ? null : dimensions.get(dimensionId);
    }
}
