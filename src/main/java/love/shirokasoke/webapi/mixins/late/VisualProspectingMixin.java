package love.shirokasoke.webapi.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.sinthoras.visualprospecting.database.DimensionCache;
import com.sinthoras.visualprospecting.database.WorldCache;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import love.shirokasoke.webapi.utils.Accessor;

@Mixin(Accessor.class)
public class VisualProspectingMixin {

    /**
     * @author shirokasoke
     * @reason 替代反射
     */
    @Overwrite(remap = false)
    public static Int2ObjectMap<DimensionCache> WorldCache_dimensions(WorldCache cache) {
        return ((WorldCacheAccess) cache).$getDimensions();
    }
}
