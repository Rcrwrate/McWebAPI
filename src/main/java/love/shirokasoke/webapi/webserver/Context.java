package love.shirokasoke.webapi.webserver;

import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import love.shirokasoke.webapi.utils.McAccessor;
import love.shirokasoke.webapi.webserver.RouteHandler.ApiException;
import love.shirokasoke.webapi.webserver.RouteHandler.coordinates;

public class Context {

    public coordinates co;
    public MinecraftServer server = null;
    public WorldServer world = null;
    public TileEntity tileEntity = null;
    public IGridHost host = null;
    public IGridNode aenode = null;
    public IGrid grid = null;
    public IGregTechTileEntity igte = null;
    public MetaTileEntity mte = null;

    public Context(coordinates co) {
        this.co = co;
    }

    public Context initServer() throws ApiException {
        if (server == null) {
            server = McAccessor.getServer();
        }
        return this;
    }

    public Context initWorld() throws ApiException {
        if (world == null) {
            world = McAccessor.getWorld(server, co.dimension);
        }
        return this;
    }

    public Context checkblockExists() throws ApiException {
        if (!McAccessor.blockExists(world, co.posX, co.posY, co.posZ)) {
            throw new ApiException(404, "Chunk not loaded at coordinates: " + co.toString());
        }
        return this;
    }

    /**
     * 调用之前必须先调用{@link Context#checkblockExists}
     */
    public Context initTileEntity() throws ApiException {
        if (tileEntity == null) {
            tileEntity = McAccessor.getTileEntity(world, co.posX, co.posY, co.posZ);
        }
        return this;
    }

    public Context initAE() throws ApiException {
        if (tileEntity instanceof IGridHost) {
            host = (IGridHost) tileEntity;
        } else {
            throw new ApiException(401, "Not belong to AE");
        }
        aenode = host.getGridNode(ForgeDirection.UNKNOWN);
        if (aenode == null || !aenode.isActive()) {
            throw new ApiException(500, "AE Network is not active!");
        }
        grid = aenode.getGrid();
        return this;
    }

    public Context initGT() throws ApiException {
        if (!(tileEntity instanceof IGregTechTileEntity)) {
            throw new ApiException(404, "Block at given coordinates is not a GT5 machine");
        }
        igte = (IGregTechTileEntity) tileEntity;
        if (!igte.canAccessData()) {
            throw new ApiException(404, "GT5 machine has no valid MetaTileEntity");
        }
        mte = (MetaTileEntity) igte.getMetaTileEntity();
        if (mte == null) {
            throw new ApiException(404, "GT5 machine MetaTileEntity is null");
        }
        return this;
    }
}
