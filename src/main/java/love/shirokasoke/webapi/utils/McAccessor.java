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
     * {@link WorldServer#getTileEntity}会通过{@link WorldServer#getChunkFromChunkCoords}
     * 
     * 访问到{@link net.minecraft.world.gen.ChunkProviderServer#provideChunk} ,
     * 
     * 存在不安全的可能
     */
    public static TileEntity getTileEntity(WorldServer world, int x, int y, int z) throws ApiException {
        final TileEntity te;
        if (world.theChunkProviderServer.loadedChunkHashMap instanceof ServerThreadLongHashMap) {
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
}
