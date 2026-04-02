package love.shirokasoke.webapi.server.handlers.block;

import java.io.IOException;
import java.util.Set;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IInterfaceViewable;
import appeng.core.features.registries.InterfaceTerminalRegistry;

public class AEHandler extends BlockHandler {

    @Override
    public String getPath() {
        return "/block/ae2";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI()
            .getQuery();
        coordinates co = checklist(query);
        TileEntity tileEntity = world.getTileEntity(co.posX, co.posY, co.posZ);
        if (tileEntity instanceof IGridHost) {
            IGridHost host = (IGridHost) tileEntity;
            IGridNode aenode = host.getGridNode(ForgeDirection.UNKNOWN);
            if (aenode != null && aenode.isActive()) {
                IGrid grid = aenode.getGrid();

                // 获取存储网格
                IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);

                // 访问物品库存
                IMEMonitor<IAEItemStack> itemInventory = storageGrid.getItemInventory();

                // 访问流体库存
                IMEMonitor<IAEFluidStack> fluidInventory = storageGrid.getFluidInventory();

                // 获取完整的物品列表
                IItemList<IAEItemStack> itemList = itemInventory.getStorageList();

                ObjectNode root = mapper.createObjectNode();

                // 构建物品库存信息
                ArrayNode items = mapper.createArrayNode();
                // 遍历库存
                for (IAEItemStack stack : itemList) {
                    if (stack != null) {
                        ItemStack minecraftStack = stack.getItemStack();
                        long count = stack.getStackSize();
                        if (minecraftStack != null) {
                            ObjectNode item = mapper.createObjectNode();
                            item.put("itemId", Item.getIdFromItem(minecraftStack.getItem()));
                            item.put(
                                "itemName",
                                net.minecraft.item.Item.itemRegistry.getNameForObject(minecraftStack.getItem()));
                            item.put("displayName", minecraftStack.getDisplayName());
                            item.put("stackSize", count);
                            item.put("damage", minecraftStack.getItemDamage());
                            item.put("maxStackSize", minecraftStack.getMaxStackSize());
                            items.add(item);
                        }
                    }
                }
                root.set("items", items);

                // 构建ME接口列表
                ArrayNode interfaces = mapper.createArrayNode();
                Set<Class<? extends IInterfaceViewable>> supportedClasses = InterfaceTerminalRegistry.instance()
                    .getSupportedClasses();

                for (Class<? extends IInterfaceViewable> clazz : supportedClasses) {
                    for (IGridNode node : grid.getMachines(clazz)) {
                        IInterfaceViewable machine = (IInterfaceViewable) node.getMachine();
                        if (!machine.shouldDisplay()) continue;

                        ObjectNode iface = mapper.createObjectNode();
                        iface.put("name", machine.getName());
                        iface.put("online", node.isActive());

                        DimensionalCoord loc = machine.getLocation();
                        ObjectNode location = mapper.createObjectNode();
                        location.put("x", loc.x);
                        location.put("y", loc.y);
                        location.put("z", loc.z);
                        location.put("dimension", loc.getDimension());
                        iface.set("location", location);

                        // 添加样板信息
                        ArrayNode patterns = mapper.createArrayNode();
                        IInventory patternInv = machine.getPatterns();
                        if (patternInv != null) {
                            for (int i = 0; i < patternInv.getSizeInventory(); i++) {
                                ItemStack patternStack = patternInv.getStackInSlot(i);
                                if (patternStack != null) {
                                    ObjectNode pattern = mapper.createObjectNode();
                                    pattern.put("slot", i);
                                    pattern.put(
                                        "itemName",
                                        net.minecraft.item.Item.itemRegistry.getNameForObject(patternStack.getItem()));
                                    pattern.put("displayName", patternStack.getDisplayName());
                                    patterns.add(pattern);
                                }
                            }
                        }
                        iface.set("patterns", patterns);

                        interfaces.add(iface);
                    }
                }
                root.set("interfaces", interfaces);

                // 添加网格信息
                ObjectNode gridInfo = mapper.createObjectNode();
                gridInfo.put(
                    "gridId",
                    grid.getId()
                        .toString());
                gridInfo.put(
                    "nodeCount",
                    grid.getNodes()
                        .size());
                gridInfo.put("isPowered", aenode.isActive());
                root.set("gridInfo", gridInfo);

                sendResponse(exchange, 200, root);
                return;
            } else {
                sendErrorResponse(exchange, 400, "AE block is not connected to a grid or grid node is inactive");
                return;
            }
        }

        sendErrorResponse(exchange, 404, "Not an AE grid block");
    }

}
