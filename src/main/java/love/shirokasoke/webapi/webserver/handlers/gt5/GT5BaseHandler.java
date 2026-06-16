package love.shirokasoke.webapi.webserver.handlers.gt5;

import java.io.IOException;

import net.minecraft.tileentity.TileEntity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.shutdown.ShutDownReason;
import love.shirokasoke.webapi.utils.ClassUtils;
import love.shirokasoke.webapi.webserver.handlers.block.BlockHandler;

public class GT5BaseHandler extends BlockHandler {

    protected IGregTechTileEntity igte;
    protected MetaTileEntity mte;
    protected coordinates co;

    public enum MachineType {
        /** 多方块机器核心方块 */
        MULTIBLOCK,
        SINGLE,
        /** 多方块机器附属方块 */
        HATCH,
        UNKNOWN
    }

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

    protected MachineType getMachineType() {
        if (mte instanceof MTEMultiBlockBase) {
            return MachineType.MULTIBLOCK;
        } else if (mte instanceof MTEHatch) {
            return MachineType.HATCH;
        } else if (mte instanceof MTEBasicMachine) {
            return MachineType.SINGLE;
        }
        return MachineType.UNKNOWN;
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

        MachineType type = getMachineType();
        data.put("machineType", type.name());
        data.put("owner", igte.getOwnerName());
        data.put("metaTileID", igte.getMetaTileID());
        data.put("localName", mte.getLocalName());
        data.put("internalName", mte.mName);

        // 通用工作状态
        ObjectNode state = data.putObject("state");
        state.put("isActive", igte.isActive());
        state.put("isAllowedToWork", igte.isAllowedToWork());

        // 停机信息（仅 BaseMetaTileEntity 可访问）
        if (igte instanceof BaseMetaTileEntity) {
            BaseMetaTileEntity bmte = (BaseMetaTileEntity) igte;
            state.put("wasShutdown", bmte.mWasShutdown);

            ShutDownReason reason = bmte.lastShutDownReason;
            ObjectNode reasonNode = state.putObject("lastShutDownReason");
            reasonNode.put("id", reason.getID());
            reasonNode.put("displayString", reason.getDisplayString());
            reasonNode.put("wasCritical", reason.wasCritical());
        }

        // 类型特有信息
        switch (type) {
            case MULTIBLOCK:
                appendMultiBlockInfo(data);
                break;
            case HATCH:
                appendHatchInfo(data);
                break;
            case SINGLE:
                appendSingleBlockInfo(data);
                break;
            default:
                break;
        }

        ClassUtils.getClassInfo(mte, data);

        return data;
    }

    protected void appendHatchInfo(ObjectNode data) {
        MTEHatch hatch = (MTEHatch) mte;

        ObjectNode hatchNode = data.putObject("hatch");
        hatchNode.put("tier", hatch.mTier);
    }

    protected void appendMultiBlockInfo(ObjectNode data) {
        MTEMultiBlockBase multi = (MTEMultiBlockBase) mte;

        ObjectNode multiNode = data.putObject("multiblock");
        multiNode.put("structureValid", multi.mMachine);
        multiNode.put("progressTime", multi.mProgresstime);
        multiNode.put("maxProgressTime", multi.mMaxProgresstime);
        multiNode.put("euT", multi.mEUt);
        multiNode.put("efficiency", multi.mEfficiency);
        multiNode.put("pollution", multi.mPollution);

        // 维护状态
        ObjectNode maintenance = multiNode.putObject("maintenance");
        maintenance.put("wrench", multi.mWrench);
        maintenance.put("screwdriver", multi.mScrewdriver);
        maintenance.put("softMallet", multi.mSoftMallet);
        maintenance.put("hardHammer", multi.mHardHammer);
        maintenance.put("solderingTool", multi.mSolderingTool);
        maintenance.put("crowbar", multi.mCrowbar);

        // Hatch 数量
        ObjectNode hatches = multiNode.putObject("hatches");
        hatches.put("inputBus", multi.mInputBusses.size());
        hatches.put("outputBus", multi.mOutputBusses.size());
        hatches.put("inputHatch", multi.mInputHatches.size());
        hatches.put("outputHatch", multi.mOutputHatches.size());
        hatches.put("energyHatch", multi.mEnergyHatches.size());
        hatches.put("dynamoHatch", multi.mDynamoHatches.size());
        hatches.put("maintenanceHatch", multi.mMaintenanceHatches.size());
        hatches.put("mufflerHatch", multi.mMufflerHatches.size());
        hatches.put("dualInputHatch", multi.mDualInputHatches.size());
    }

    protected void appendSingleBlockInfo(ObjectNode data) {
        MTEBasicMachine single = (MTEBasicMachine) mte;

        ObjectNode singleNode = data.putObject("singleBlock");
        singleNode.put("progressTime", single.mProgresstime);
        singleNode.put("maxProgressTime", single.mMaxProgresstime);
        singleNode.put("euT", single.mEUt);
        singleNode.put("inputSlotCount", single.mInputSlotCount);
        singleNode.put("amperage", single.mAmperage);
        singleNode.put("mainFacing", single.mMainFacing.name());
    }
}
