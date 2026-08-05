package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import love.shirokasoke.webapi.webserver.handlers.block.BlockHandler;

public class AEBaseHandler extends BlockHandler {

    protected IGridHost host;
    protected IGridNode aenode;
    protected IGrid grid;
    protected coordinates co;

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
        ObjectNode response = mapper.createObjectNode()
            .put("message", "AE HIT");

        setCache(exchange, 86400);
        sendResponse(exchange, response);
    }

    protected void AEinit(HttpExchange exchange) throws ApiException {
        String query = exchange.getRequestURI()
            .getQuery();
        co = checklist(query);
        TileEntity tileEntity = world.getTileEntity(co.posX, co.posY, co.posZ);
        if (tileEntity instanceof IGridHost) {
            host = (IGridHost) tileEntity;

        } else {
            throw new ApiException(401, "Not belong to AE");
        }
        aenode = host.getGridNode(ForgeDirection.UNKNOWN);
        if (aenode == null || !aenode.isActive()) {
            throw new ApiException(500, "AE Netowrk is not active!");
        }
        grid = aenode.getGrid();
    }
}
