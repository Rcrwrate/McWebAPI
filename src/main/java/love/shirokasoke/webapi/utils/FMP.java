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

/**
 * 用于将 Forge Multipart (FMP) 及 AE2 CableBus 部件序列化为 JSON 的工具类。
 *
 * <p>
 * 该类提供方法将 {@link TMultiPart}（或其 AE2 专用子类 {@link CableBusPart}）的状态
 * 导出为 Jackson {@link ObjectNode}，以便通过 WebAPI 发送或记录日志。
 * </p>
 *
 * <p>
 * 生成的数据包括基础的多部件元数据、渲染/碰撞边界、掉落物，以及 AE2 专属信息，
 * 如线缆颜色、各面附着的部件和伪装板（Facade）。
 * </p>
 */
public final class FMP {

    private static final ObjectMapper mapper = Constant.mapper;

    private FMP() {}

    public static ObjectNode dump(TMultiPart part, ObjectNode data) {
        if (part == null) {
            data.putNull("type");
            return data;
        }

        ClassUtils.getClassInfo(part, data);
        data.put("type", part.getType());
        data.put("lightValue", part.getLightValue());
        data.put("doesTick", part.doesTick());

        // 渲染边界
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

        // 碰撞箱。
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

        // 对 AE2 的 CableBusPart 进行特殊处理，以暴露线缆和部件状态。
        if (part instanceof CableBusPart) {
            dumpCableBusPart((CableBusPart) part, data);
        } else {
            // 普通部件仅列出其掉落物。
            ArrayNode drops = data.putArray("drops");
            for (ItemStack drop : part.getDrops()) {
                drops.add(Items.dump(drop));
            }
        }

        return data;
    }

    /**
     * 将 {@link CableBusPart} 的 AE2 专属数据序列化到提供的 JSON 节点中。
     *
     * <p>
     * 在 {@code "ae2"} 键下写入的信息包括：
     * </p>
     * <ul>
     * <li>线缆颜色及是否为空的状态</li>
     * <li>线缆总线发出的光照等级</li>
     * <li>每个 {@link ForgeDirection} 面上附着的部件</li>
     * <li>每个有效方向上应用的伪装板（Facade）</li>
     * </ul>
     */
    private static void dumpCableBusPart(CableBusPart part, ObjectNode data) {
        ObjectNode aeNode = data.putObject("ae2");
        try {
            // 获取内部容器，该容器保存了所有线缆/部件状态。
            CableBusContainer cbc = part.getCableBus();

            // 导出线缆颜色、是否为空以及光照等级。
            AEColor color = cbc.getColor();
            aeNode.put("color", color != null ? color.name() : "null");
            aeNode.put("isEmpty", cbc.isEmpty());
            aeNode.put("lightValue", cbc.getLightValue());

            // 遍历所有 ForgeDirection 并序列化附着的部件。
            ArrayNode sides = aeNode.putArray("parts");
            for (ForgeDirection dir : ForgeDirection.values()) {
                IPart sidePart = cbc.getPart(dir);
                if (sidePart != null) {
                    ObjectNode sideNode = mapper.createObjectNode();
                    sideNode.put("direction", dir.name());
                    // PartItemStack.Break 表示被破坏时的物品形态。
                    ItemStack stack = sidePart.getItemStack(PartItemStack.Break);
                    if (stack != null) {
                        sideNode.set("item", Items.dump(stack));
                    }
                    sides.add(sideNode);
                }
            }

            // 遍历伪装板
            IFacadeContainer facadeContainer = cbc.getFacadeContainer();
            ArrayNode facades = aeNode.putArray("facades");
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                IFacadePart facade = facadeContainer.getFacade(dir);
                if (facade != null) {
                    ObjectNode facadeNode = facades.addObject();
                    facadeNode.put("direction", dir.name());
                    ItemStack stack = facade.getItemStack();
                    if (stack != null) {
                        facadeNode.set("item", Items.dump(stack));
                    }
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
