package love.shirokasoke.webapi.utils;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import love.shirokasoke.webapi.Constant;

public class Fluids {

    private static final ObjectMapper mapper = Constant.mapper;

    public static ObjectNode dump(Fluid fluid) {
        return dump(fluid, mapper.createObjectNode());
    }

    public static ObjectNode dump(Fluid fluid, ObjectNode data) {
        ClassUtils.getClassInfo(fluid, data);
        data.put("name", fluid.getName());
        data.put("defaultName", FluidRegistry.getDefaultFluidName(fluid));
        data.put("unlocalizedName", fluid.getUnlocalizedName());
        try {
            data.put("localizedName", fluid.getLocalizedName());
        } catch (Throwable ignored) {}
        data.put("fluidID", fluid.getID());
        data.put("color", fluid.getColor());
        data.put("luminosity", fluid.getLuminosity());
        data.put("density", fluid.getDensity());
        data.put("temperature", fluid.getTemperature());
        data.put("viscosity", fluid.getViscosity());
        data.put("gaseous", fluid.isGaseous());
        if (fluid.getBlock() != null) {
            data.put("block", net.minecraft.block.Block.blockRegistry.getIDForObject(fluid.getBlock()));
        }
        return data;
    }

    public static String getFileName(Fluid fluid) {
        String defaultName = FluidRegistry.getDefaultFluidName(fluid);
        if (defaultName == null) {
            defaultName = "unknown:" + fluid.getName();
        }
        return Items.cleanFileName(defaultName.replace(":", "_"));
    }
}
