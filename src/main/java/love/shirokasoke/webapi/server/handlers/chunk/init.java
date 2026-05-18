package love.shirokasoke.webapi.server.handlers.chunk;

import java.io.File;
import java.util.List;

import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.RouteRegistry;
import love.shirokasoke.webapi.server.handlers.block.BlockTileHandler;

public class init {

    public static void i() {
        RouteRegistry.register(new ChunksHandler());
        RouteRegistry.register(new ChunkHandler());
        RouteRegistry.register(new ChunkForceHandler());

        // 检查 ChunkMapHandler 所需配置是否有效
        File blocksJson = (Config.BlockFile != null && !Config.BlockFile.isEmpty()) ? new File(Config.BlockFile)
            : new File("dumps/blocks.json");
        File blockTileDir = (Config.BlockTileFolder != null && !Config.BlockTileFolder.isEmpty())
            ? new File(Config.BlockTileFolder)
            : new File("dumps/block_tiles");

        if (blocksJson.exists() && blockTileDir.exists() && blockTileDir.isDirectory()) {
            RouteRegistry.register(new ChunkMapHandler(blocksJson, blockTileDir, Config.blockTileSize));
            RouteRegistry.register(new BlockTileHandler(blocksJson, blockTileDir));
        } else {
            MyMod.LOG.warn(
                "ChunkMapHandler 未注册：blocks.json 或 block_tiles 目录不存在。"
                    + " 请配置 BlockFile 和 BlockTileFolder，或确保 dumps/blocks.json 和 dumps/block_tiles/ 存在。");
        }

        // 注册 chunk loading 回调
        ForgeChunkManager.setForcedChunkLoadingCallback(MyMod.INST, new LoadingCallback() {

            @Override
            public void ticketsLoaded(List<Ticket> tickets, World world) {
                MyMod.LOG.info("Chunk loading callback triggered for dimension: " + world.provider.dimensionId);

                if (tickets != null && !tickets.isEmpty()) {
                    for (Ticket ticket : tickets) {
                        MyMod.LOG.info(
                            "Processing saved ticket: " + ticket.getModId()
                                + " in dimension "
                                + world.provider.dimensionId);

                        // 因为这些都是临时加载的，服务器重启后应该释放
                        // 所以这里直接释放 ticket
                        ForgeChunkManager.releaseTicket(ticket);
                        MyMod.LOG.info("Released saved ticket for dimension: " + world.provider.dimensionId);
                    }
                }
            }
        });

        MyMod.LOG.info("Chunk loading callback registered successfully");
    }
}
