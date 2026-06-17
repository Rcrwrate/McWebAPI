package love.shirokasoke.webapi.webserver.handlers.gt5;

import java.io.IOException;

import net.minecraft.tileentity.TileEntity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import love.shirokasoke.webapi.utils.ClassUtils;
import love.shirokasoke.webapi.utils.GT5Utils;
import love.shirokasoke.webapi.utils.GT5Utils.MachineType;
import love.shirokasoke.webapi.webserver.handlers.block.BlockHandler;

public class GT5BaseHandler extends BlockHandler {

    protected IGregTechTileEntity igte;
    protected MetaTileEntity mte;
    protected coordinates co;

    @Override
    public String getPath() {
        return "/gt5";
    }

    @Override
    public String getDescription() {
        return "GT5 machine base endpoint. Query params: x, y, z, dim (optional, default=0). Detects if a block is a GT5 machine (multiblock/single-block/hatch) and returns its working state.";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        GT5init(exchange);

        ObjectNode data = buildMachineInfo();
        sendResponse(exchange, data);
    }

    protected void GT5init(HttpExchange exchange) throws Error {
        String query = exchange.getRequestURI()
            .getQuery();
        co = checklist(query);

        TileEntity tileEntity = world.getTileEntity(co.posX, co.posY, co.posZ);
        if (!(tileEntity instanceof IGregTechTileEntity)) {
            throw new Error(404, "Block at given coordinates is not a GT5 machine");
        }

        igte = (IGregTechTileEntity) tileEntity;
        if (!igte.canAccessData()) {
            throw new Error(404, "GT5 machine has no valid MetaTileEntity");
        }

        mte = (MetaTileEntity) igte.getMetaTileEntity();
        if (mte == null) {
            throw new Error(404, "GT5 machine MetaTileEntity is null");
        }
    }

    protected ObjectNode buildMachineInfo() {
        ObjectNode data = mapper.createObjectNode();

        data.set(
            "coordinates",
            mapper.createObjectNode()
                .put("posX", co.posX)
                .put("posY", co.posY)
                .put("posZ", co.posZ)
                .put("dimension", co.dimension));

        GT5Utils.writeBasicMachineInfo(igte, mte, data);
        GT5Utils.writeState(igte, data.putObject("state"));

        // 类型特有信息
        MachineType type = GT5Utils.getMachineType(mte);
        switch (type) {
            case MULTIBLOCK:
                GT5Utils.writeMultiBlockInfo((MTEMultiBlockBase) mte, data.putObject("multi"));
                break;
            case HATCH:
                GT5Utils.writeHatchInfo((MTEHatch) mte, data.putObject("hatch"));
                break;
            case SINGLE:
                GT5Utils.writeSingleBlockInfo((MTEBasicMachine) mte, data.putObject("single"));
                break;
            default:
                break;
        }

        ClassUtils.getClassInfo(mte, data);
        return data;
    }
}
