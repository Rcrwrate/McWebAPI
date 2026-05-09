package love.shirokasoke.webapi.server.handlers.ae2;

import java.io.IOException;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.sun.net.httpserver.HttpExchange;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import love.shirokasoke.webapi.server.handlers.block.BlockHandler;

public class AEBaseHandler extends BlockHandler {

    protected IGridHost host;
    protected IGridNode aenode;
    protected IGrid grid;

    @Override
    public String getPath() {
        return "/ae";
    }

    @Override
    public String getDescription() {
        return "AE Base";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        AEinit(exchange);
        String response = "{\"message\": \"AE HIT\"}";

        setCache(exchange, 86400);
        sendResponse(exchange, 200, response);
    }

    protected void AEinit(HttpExchange exchange) throws Error {
        String query = exchange.getRequestURI()
            .getQuery();
        coordinates co = checklist(query);
        TileEntity tileEntity = world.getTileEntity(co.posX, co.posY, co.posZ);
        if (tileEntity instanceof IGridHost) {
            host = (IGridHost) tileEntity;

        } else {
            throw new Error(401, "Not belong to AE");
        }
        aenode = host.getGridNode(ForgeDirection.UNKNOWN);
        if (aenode == null || !aenode.isActive()) {
            throw new Error(500, "AE Netowrk is not active!");
        }
        grid = aenode.getGrid();
    }
}
