package love.shirokasoke.webapi.utils;

import static gregtech.api.util.GTUtility.validMTEList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicGenerator;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.shutdown.ShutDownReason;
import kekztech.common.tileentities.MTELapotronicSuperCapacitor;
import love.shirokasoke.webapi.Constant;

public final class GT5Utils {

    private static final ObjectMapper mapper = Constant.mapper;

    private GT5Utils() {}

    public enum MachineType {
        /** 多方块机器核心方块 */
        MULTIBLOCK,
        SINGLE,
        /** 单方块发电机 */
        GENERATOR,
        /** 多方块机器附属方块 */
        HATCH,
        UNKNOWN
    }

    public static MachineType getMachineType(MetaTileEntity mte) {
        if (mte instanceof MTEMultiBlockBase) {
            return MachineType.MULTIBLOCK;
        } else if (mte instanceof MTEHatch) {
            return MachineType.HATCH;
        } else if (mte instanceof MTEBasicGenerator) {
            return MachineType.GENERATOR;
        } else if (mte instanceof MTEBasicMachine) {
            return MachineType.SINGLE;
        }
        return MachineType.UNKNOWN;
    }

    /**
     * 验证 TileEntity 是否为合法的 GT5 机器，并返回其 MetaTileEntity。
     * 若不是合法 GT5 机器则返回 null。
     */
    public static MetaTileEntity extractValidMTE(TileEntity te) {
        if (!(te instanceof IGregTechTileEntity)) return null;
        IGregTechTileEntity igte = (IGregTechTileEntity) te;
        if (!igte.canAccessData()) return null;
        Object rawMte = igte.getMetaTileEntity();
        // 排除GT线缆
        if (!(rawMte instanceof MetaTileEntity)) return null;
        return (MetaTileEntity) rawMte;
    }

    /**
     * GT5 机器的基础信息
     */
    public static void writeBasicMachineInfo(IGregTechTileEntity igte, MetaTileEntity mte, ObjectNode node) {
        node.put("machineType", getMachineType(mte).name());
        node.put("localName", mte.getLocalName());
        node.put("internalName", mte.mName);
        node.put("metaTileID", igte.getMetaTileID());
        node.put("owner", igte.getOwnerName());
    }

    /**
     * 大部分机器一般会重写{@link BaseMetaTileEntity#getInfoData}和{@link BaseMetaTileEntity#getInfoMap}
     * <br>
     * 直接通过该接口获取数据比较快
     * 
     * @apiNote 例外：兰波顿没有重写{@link MTELapotronicSuperCapacitor#getInfoMap}
     */
    public static void writeIO(BaseMetaTileEntity bmte, ObjectNode data) {
        data.put("storedEU", bmte.getStoredEU());
        data.put("euCapacity", bmte.getEUCapacity());
        ArrayNode info = data.putArray("Info");
        for (String i : bmte.getInfoData()) {
            info.add(i);
        }
        ObjectNode rawInfo = data.putObject("rawInfo");
        for (Entry<String, String> e : bmte.getInfoMap()
            .entrySet()) {
            rawInfo.put(e.getKey(), e.getValue());
        }
        if (bmte.isEnetInput()) {
            data.put("inputVoltage", bmte.getInputVoltage());
            data.put("inputAmperage", bmte.getInputAmperage());
        }
        if (bmte.isEnetOutput()) {
            data.put("outputVoltage", bmte.getOutputVoltage());
            data.put("outputAmperage", bmte.getOutputAmperage());
        }
    }

    public static void writeState(IGregTechTileEntity igte, ObjectNode state) {
        state.put("isActive", igte.isActive());
        state.put("isAllowedToWork", igte.isAllowedToWork());
        BaseMetaTileEntity bmte = (BaseMetaTileEntity) igte;
        state.put("wasShutdown", bmte.mWasShutdown);
        ShutDownReason reason = bmte.lastShutDownReason;
        ObjectNode reasonNode = state.putObject("lastShutDownReason");
        reasonNode.put("id", reason.getID());
        reasonNode.put("displayString", reason.getDisplayString());
        reasonNode.put("wasCritical", reason.wasCritical());
        writeIO(bmte, state);
    }

    public static void write(MetaTileEntity mte, ObjectNode data) {
        ClassUtils.getClassInfo(mte, data);
        MachineType type = GT5Utils.getMachineType(mte);
        NBTTagCompound nbt = new NBTTagCompound();
        mte.saveNBTData(nbt);
        NBT.dump(nbt, data, "nbt");
        switch (type) {
            case MULTIBLOCK:
                writeMultiBlockInfo((MTEMultiBlockBase) mte, data.putObject("multi"));
                break;
            case HATCH:
                writeHatchInfo((MTEHatch) mte, data.putObject("hatch"));
                break;
            case SINGLE:
                writeSingleBlockInfo((MTEBasicMachine) mte, data.putObject("single"));
                break;
            case GENERATOR:
                writeGeneratorInfo((MTEBasicGenerator) mte, data.putObject("generator"));
                break;
            default:
                break;
        }
    }

    public static void writeHatchInfo(MTEHatch hatch, ObjectNode data) {
        data.put("tier", hatch.mTier);
    }

    public static void writeMultiBlockInfo(MTEMultiBlockBase multi, ObjectNode data) {
        data.put("structureValid", multi.mMachine);
        data.put("progressTime", multi.mProgresstime);
        data.put("maxProgressTime", multi.mMaxProgresstime);
        data.put("euT", multi.mEUt);
        data.put("efficiency", multi.mEfficiency);
        data.put("pollution", multi.mPollution);
        data.put("inputVoltageTier", multi.getAverageInputVoltage());
        data.put("maxInputEu", multi.getMaxInputEu());
        data.put("maxInputAmps", multi.getMaxInputAmps());
        long storedEnergy = 0;
        long maxEnergy = 0;
        for (MTEHatchEnergy tHatch : validMTEList(multi.mEnergyHatches)) {
            final IGregTechTileEntity baseMetaTileEntity = tHatch.getBaseMetaTileEntity();
            storedEnergy += baseMetaTileEntity.getStoredEU();
            maxEnergy += baseMetaTileEntity.getEUCapacity();
        }
        data.put("storedEnergy", storedEnergy);
        data.put("maxEnergy", maxEnergy);
        // multi.getActualEnergyUsage();
        data.put("maxParallelRecipes", multi.getMaxParallelRecipes());
        data.put("trueParallel", multi.getTrueParallel());

        // 维护状态
        ObjectNode maintenance = data.putObject("maintenance");
        maintenance.put("wrench", multi.mWrench);
        maintenance.put("screwdriver", multi.mScrewdriver);
        maintenance.put("softMallet", multi.mSoftMallet);
        maintenance.put("hardHammer", multi.mHardHammer);
        maintenance.put("solderingTool", multi.mSolderingTool);
        maintenance.put("crowbar", multi.mCrowbar);

        // Hatch 坐标
        ObjectNode hatches = data.putObject("hatches");
        hatches.set("inputBus", writeHatchCoords(multi.mInputBusses));
        hatches.set("outputBus", writeHatchCoords(multi.mOutputBusses));
        hatches.set("inputHatch", writeHatchCoords(multi.mInputHatches));
        hatches.set("outputHatch", writeHatchCoords(multi.mOutputHatches));
        hatches.set("energyHatch", writeHatchCoords(multi.mEnergyHatches));
        hatches.set("dynamoHatch", writeHatchCoords(multi.mDynamoHatches));
        hatches.set("maintenanceHatch", writeHatchCoords(multi.mMaintenanceHatches));
        hatches.set("mufflerHatch", writeHatchCoords(multi.mMufflerHatches));
        hatches.set("dualInputHatch", writeHatchCoords(multi.mDualInputHatches));
        hatches.set("smartInputHatch", writeHatchCoords(Accessor.MTEMultiBlockBase_mSmartInputHatches(multi)));
    }

    public static void writeSingleBlockInfo(MTEBasicMachine single, ObjectNode data) {
        data.put("tier", single.mTier);
        data.put("progressTime", single.mProgresstime);
        data.put("maxProgressTime", single.mMaxProgresstime);
        data.put("euT", single.mEUt);
        data.put("inputSlotCount", single.mInputSlotCount);
        data.put("amperage", single.mAmperage);
        data.put("mainFacing", single.mMainFacing.name());
    }

    public static void writeGeneratorInfo(MTEBasicGenerator gen, ObjectNode data) {
        data.put("tier", gen.mTier);
        data.put("storedEU", gen.getEUVar());
        data.put("maxEUStore", gen.maxEUStore());
        data.put("pollution", gen.getPollution());
        data.put("efficiency", gen.getEfficiency());
        RecipeMap<?> recipeMap = gen.getRecipeMap();
        if (recipeMap != null) {
            data.put("recipeMap", recipeMap.unlocalizedName);
        }
        data.put("maxEUOutput", gen.maxEUOutput());
        data.put("capacity", gen.getCapacity());
        FluidStack fluid = gen.mFluid;
        if (fluid != null && fluid.getFluid() != null) {
            Fluids.dump(fluid.getFluid(), data.putObject("fluid"));
        }
    }

    private static List<MTEHatch> validHatches(List<?> hatchList) {
        List<MTEHatch> result = new ArrayList<>();
        for (Object hatch : hatchList) {
            if (hatch instanceof MTEHatch) {
                result.add((MTEHatch) hatch);
            }
        }
        return result;
    }

    public static ArrayNode writeHatchCoords(List<?> hatchList) {
        ArrayNode arr = mapper.createArrayNode();
        for (MTEHatch hatch : validHatches(hatchList)) {
            IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
            if (base != null) {
                arr.add(
                    mapper.createObjectNode()
                        .put("x", base.getXCoord())
                        .put("y", base.getYCoord())
                        .put("z", base.getZCoord()));
            }
        }
        return arr;
    }
}
