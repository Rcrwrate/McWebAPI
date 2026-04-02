package love.shirokasoke.webapi.utils;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import love.shirokasoke.webapi.Constant;

public class Blocks {

    private static final ObjectMapper mapper = Constant.mapper;

    public static ObjectNode dump(Block block, ObjectNode data) {
        ClassUtils.getClassInfo(block, data);
        data.put("id", Block.getIdFromBlock(block));
        data.put("registryName", Block.blockRegistry.getNameForObject(block));
        data.put("unlocalizedName", block.getUnlocalizedName());
        data.put("localizedName", block.getLocalizedName());

        data.put("resistance", block.getExplosionResistance(null) * 5.0f);
        data.put("lightLevel", block.getLightValue());
        data.put("isOpaqueCube", block.isOpaqueCube());
        data.put("isNormalCube", block.isNormalCube());

        data.put("slipperiness", block.slipperiness);
        data.put("renderType", block.getRenderType());

        material(block.getMaterial(), data);
        return data;
    }

    private static void material(Material material, ObjectNode data) {
        ObjectNode materialData = mapper.createObjectNode();
        ClassUtils.getClassInfo(material, data);
        materialData.put("isLiquid", material.isLiquid());
        materialData.put("isSolid", material.isSolid());
        materialData.put("blocksMovement", material.blocksMovement());
        materialData.put("isOpaque", material.isOpaque());
        materialData.put("isFlammable", material.getCanBurn());
        materialData.put("isReplaceable", material.isReplaceable());
        materialData.put("requiresNoTool", material.isToolNotRequired());
        materialData.put("mobilityFlag", material.getMaterialMobility());
        materialData.put("isAdventureModeExempt", material.isAdventureModeExempt());
        data.set("material", materialData);
    }

    public static ObjectNode dump(Block block) {
        return dump(block, mapper.createObjectNode());
    }
}
