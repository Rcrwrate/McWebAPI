package love.shirokasoke.webapi.server.handlers.chunk;

import java.util.List;

import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.RouteRegistry;

public class init {

    public static void i() {
        RouteRegistry.register(new ChunksHandler());
        RouteRegistry.register(new ChunkForceHandler());

        // 注册 chunk loading 回调
        ForgeChunkManager.setForcedChunkLoadingCallback(MyMod.INST, new LoadingCallback() {

            @Override
            public void ticketsLoaded(List<Ticket> tickets, World world) {
                MyMod.LOG
                    .info("[WebAPI] Chunk loading callback triggered for dimension: " + world.provider.dimensionId);

                if (tickets != null && !tickets.isEmpty()) {
                    for (Ticket ticket : tickets) {
                        MyMod.LOG.info(
                            "[WebAPI] Processing saved ticket: " + ticket.getModId()
                                + " in dimension "
                                + world.provider.dimensionId);

                        // 因为这些都是临时加载的，服务器重启后应该释放
                        // 所以这里直接释放 ticket
                        ForgeChunkManager.releaseTicket(ticket);
                        MyMod.LOG.info("[WebAPI] Released saved ticket for dimension: " + world.provider.dimensionId);
                    }
                }
            }
        });

        MyMod.LOG.info("[WebAPI] Chunk loading callback registered successfully");
    }
}
