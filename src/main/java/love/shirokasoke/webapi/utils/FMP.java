package love.shirokasoke.webapi.utils;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import appeng.api.parts.IFacadeContainer;
import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPart;
import appeng.api.parts.PartItemStack;
import appeng.api.util.AEColor;
import appeng.fmp.CableBusPart;
import appeng.parts.CableBusContainer;
import codechicken.lib.vec.Cuboid6;
import codechicken.multipart.TMultiPart;
import love.shirokasoke.webapi.Constant;

public class FMP {

    private static final ObjectMapper mapper = Constant.mapper;

    public static ObjectNode dump(TMultiPart part, ObjectNode data) {
        if (part == null) {
            data.putNull("type");
            return data;
        }

        ClassUtils.getClassInfo(part, data);
        data.put("type", part.getType());
        data.put("lightValue", part.getLightValue());
        data.put("doesTick", part.doesTick());

        Cuboid6 bounds = part.getRenderBounds();
        if (bounds != null) {
            ObjectNode boundsNode = mapper.createObjectNode();
            boundsNode.put("minX", bounds.min.x);
            boundsNode.put("minY", bounds.min.y);
            boundsNode.put("minZ", bounds.min.z);
            boundsNode.put("maxX", bounds.max.x);
            boundsNode.put("maxY", bounds.max.y);
            boundsNode.put("maxZ", bounds.max.z);
            data.set("bounds", boundsNode);
        }

        ArrayNode collisionBoxes = mapper.createArrayNode();
        for (Cuboid6 c : part.getCollisionBoxes()) {
            ObjectNode cNode = mapper.createObjectNode();
            cNode.put("minX", c.min.x);
            cNode.put("minY", c.min.y);
            cNode.put("minZ", c.min.z);
            cNode.put("maxX", c.max.x);
            cNode.put("maxY", c.max.y);
            cNode.put("maxZ", c.max.z);
            collisionBoxes.add(cNode);
        }
        data.set("collisionBoxes", collisionBoxes);

        if (part instanceof CableBusPart) {
            dumpCableBusPart((CableBusPart) part, data);
        } else {
            ArrayNode drops = data.putArray("drops");
            for (ItemStack drop : part.getDrops()) {
                drops.add(Items.dump(drop));
            }
        }

        return data;
    }

    private static void dumpCableBusPart(CableBusPart part, ObjectNode data) {
        ObjectNode aeNode = data.putObject("ae2");
        try {
            CableBusContainer cbc = part.getCableBus();

            AEColor color = cbc.getColor();
            aeNode.put("color", color != null ? color.name() : "null");
            aeNode.put("isEmpty", cbc.isEmpty());
            aeNode.put("lightValue", cbc.getLightValue());

            ArrayNode sides = aeNode.putArray("parts");
            for (ForgeDirection dir : ForgeDirection.values()) {
                IPart sidePart = cbc.getPart(dir);
                if (sidePart != null) {
                    ObjectNode sideNode = mapper.createObjectNode();
                    sideNode.put("direction", dir.name());
                    ItemStack stack = sidePart.getItemStack(PartItemStack.Break);
                    if (stack != null) {
                        sideNode.set("item", Items.dump(stack));
                    }
                    sides.add(sideNode);
                }
            }

            IFacadeContainer facadeContainer = cbc.getFacadeContainer();
            ArrayNode facades = mapper.createArrayNode();
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                IFacadePart facade = facadeContainer.getFacade(dir);
                if (facade != null) {
                    ObjectNode facadeNode = aeNode.putObject("facades");
                    facadeNode.put("direction", dir.name());
                    ItemStack stack = facade.getItemStack();
                    if (stack != null) {
                        facadeNode.set("item", Items.dump(stack));
                    }
                    facades.add(facadeNode);
                }
            }
        } catch (Throwable e) {
            log.e(e);
            aeNode.put("error", e.getMessage());
        }
    }

    public static ObjectNode dump(TMultiPart part) {
        return dump(part, mapper.createObjectNode());
    }
}
