package love.shirokasoke.webapi.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.sinthoras.visualprospecting.database.DimensionCache;
import com.sinthoras.visualprospecting.database.WorldCache;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

@Mixin(WorldCache.class)
public interface WorldCacheAccess {

    /** 暴露 {@link WorldCache#dimensions}（维度 id -> 该维度的矿脉/流体缓存） */
    @Accessor(value = "dimensions", remap = false)
    Int2ObjectMap<DimensionCache> $getDimensions();
}
