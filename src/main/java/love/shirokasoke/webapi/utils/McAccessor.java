package love.shirokasoke.webapi.utils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import com.mitchej123.hodgepodge.util.ServerThreadLongHashMap;

import cpw.mods.fml.common.FMLCommonHandler;
import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.webserver.RouteHandler.ApiException;

/**
 * 统一管理MC相关对象的获取
 * 
 * @apiNote 用于规避链表的不安全访问
 */
public final class McAccessor {

    private McAccessor() {}

    public static MinecraftServer getServer() throws ApiException {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) {
            throw new ApiException(503, "Server not available");
        }
        return server;
    }

    public static WorldServer getWorld(MinecraftServer server, int dim) throws ApiException {
        WorldServer world = server.worldServerForDimension(dim);
        if (world == null) {
            throw new ApiException(404, "Invalid dimension: " + dim);
        }
        return world;
    }

    public static WorldServer getWorld(int dim) throws ApiException {
        return getWorld(getServer(), dim);
    }

    /**
     * 绕过原版的逻辑，直接从安全的HashMap副本取出
     * </p>
     * 涉及的堆栈:
     * </p>
     * {@link net.minecraft.world.gen.ChunkProviderServer#loadChunk(int, int, Runnable)}
     * </p>
     * 避免操作LongHashMap: {@link net.minecraft.world.gen.ChunkProviderServer#chunksToUnload}
     * </p>
     * 受影响导致NPE的Mixin:
     * {@link com.mitchej123.hodgepodge.mixins.early.minecraft.fastload.MixinChunkProviderServer_FastUnload#unloadQueuedChunks}
     */
    public static Chunk loadChunk(WorldServer world, int chunkX, int chunkZ) throws ApiException {
        long k = ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ);
        final Chunk chunk;
        if (world.theChunkProviderServer.loadedChunkHashMap instanceof ServerThreadLongHashMap chunkMap) {
            MyMod.LOG.debug("ServerThreadLongHashMap Snap hit");
            chunk = (Chunk) chunkMap.getValueByKey(k);
        } else if (Config.chunkSafe) {
            try {
                chunk = (Chunk) ServerThreadDispatcher
                    .callOnServerThread(() -> world.theChunkProviderServer.loadedChunkHashMap.getValueByKey(k));
            } catch (Exception e) {
                Logs.e(e);
                throw new ApiException(500, "Error load chunk: " + e.getMessage());
            }
        } else {
            chunk = (Chunk) world.theChunkProviderServer.loadedChunkHashMap.getValueByKey(k);
        }
        if (chunk == null) {
            throw new ApiException(
                404,
                "Chunk not found: chunkX=" + chunkX + ", chunkZ=" + chunkZ + ", dim=" + world.provider.dimensionId);
        }
        return chunk;
    }

    public static Chunk loadChunk(int dim, int chunkX, int chunkZ) throws ApiException {
        return loadChunk(getWorld(dim), chunkX, chunkZ);
    }

    /**
     * 涉及的堆栈:
     * </p>
     * {@link net.minecraft.world.World#getTileEntity} ->
     * {@link net.minecraft.world.World#addedTileEntityList} 为ArrayList(线程不安全，但是无需关注)
     * </p>
     * {@link net.minecraft.world.World#getChunkFromChunkCoords} ->
     * {@link net.minecraft.world.gen.ChunkProviderServer#provideChunk} 调用之前<b>必须</b>判断区块存在！否则不安全
     * </p>
     * provideChunk涉及Mixin,同样是调用之前<b>必须</b>判断区块存在！否则不安全
     * {@link com.mitchej123.hodgepodge.mixins.early.minecraft.fastload.MixinChunkProviderServer_EntityGuard#provideChunk}
     * 
     * @apiNote 存在不安全的可能
     */
    public static TileEntity getTileEntity(WorldServer world, int x, int y, int z) throws ApiException {
        final TileEntity te;
        if (world.theChunkProviderServer.loadedChunkHashMap instanceof ServerThreadLongHashMap) {
            MyMod.LOG.debug("ServerThreadLongHashMap Snap hit");
            te = world.getTileEntity(x, y, z);
        } else if (Config.chunkSafe) {
            try {
                te = ServerThreadDispatcher.callOnServerThread(() -> world.getTileEntity(x, y, z));
            } catch (Exception e) {
                Logs.e(e);
                throw new ApiException(500, "Error load TileEntity: " + e.getMessage());
            }
        } else {
            te = world.getTileEntity(x, y, z);
        }
        return te;
    }

    /**
     * 涉及的堆栈:
     * </p>
     * {@link net.minecraft.world.World#blockExists} ->
     * {@link net.minecraft.world.gen.ChunkProviderServer#chunkExists}
     */
    public static boolean blockExists(WorldServer world, int x, int y, int z) throws ApiException {
        if (world.theChunkProviderServer.loadedChunkHashMap instanceof ServerThreadLongHashMap) {
            MyMod.LOG.debug("ServerThreadLongHashMap Snap hit");
            return world.blockExists(x, y, z);
        } else if (Config.chunkSafe) {
            try {
                return ServerThreadDispatcher.callOnServerThread(() -> world.blockExists(x, y, z));
            } catch (Exception e) {
                Logs.e(e);
                throw new ApiException(500, "Error checking block existence: " + e.getMessage());
            }
        } else {
            return world.blockExists(x, y, z);
        }
    }
}
