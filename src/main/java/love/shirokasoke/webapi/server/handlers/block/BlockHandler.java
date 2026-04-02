package love.shirokasoke.webapi.server.handlers.block;

import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.RouteHandler;
import love.shirokasoke.webapi.utils.Blocks;
import love.shirokasoke.webapi.utils.ClassUtils;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.log;

public class BlockHandler implements RouteHandler {

    protected MinecraftServer server;
    protected WorldServer world;

    @Override
    public String getPath() {
        return "/block";
    }

    @Override
    public String getDescription() {
        return "Get block information at specified coordinates. Query params: x, y, z, dim (optional, default=0)";
    }

    protected coordinates checklist(String query) throws Error {
        if (query == null) {
            throw new Error(400, "Missing query parameters. Required: x, y, z");
        }
        coordinates co = getCoordinates(query);
        server = getServer();
        world = server.worldServerForDimension(co.dimension);
        if (world == null) {
            throw new Error(404, "Invalid dimension: " + co.dimension);
        }
        if (!world.blockExists(co.posX, co.posY, co.posZ)) {
            throw new Error(404, "Chunk not loaded at coordinates: " + co.toString());
        }
        return co;
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI()
            .getQuery();
        coordinates co = checklist(query);

        Block block = world.getBlock(co.posX, co.posY, co.posZ);
        int metadata = world.getBlockMetadata(co.posX, co.posY, co.posZ);

        ObjectNode data = mapper.createObjectNode();
        try {
            data.set("blockAuto", mapper.valueToTree(block));
        } catch (NoClassDefFoundError e) {
            log.e(e);
        } finally {
            ObjectNode b = mapper.createObjectNode();
            Blocks.dump(block, b);
            b.put("hardness", block.getBlockHardness(world, co.posX, co.posY, co.posZ));
            b.put("isReplaceable", block.isReplaceable(world, co.posX, co.posY, co.posZ));
            b.put("isPassable", !block.getBlocksMovement(world, co.posX, co.posY, co.posZ));
            data.set("block", b);
        }

        data.set("coordinates", mapper.valueToTree(co));
        data.put("metadata", metadata);
        data.put("isAir", block.isAir(world, co.posX, co.posY, co.posZ));

        // 检查是否有TileEntity（如箱子、熔炉等）
        if (block.hasTileEntity(metadata)) {
            TileEntity tileEntity = world.getTileEntity(co.posX, co.posY, co.posZ);
            if (tileEntity != null) {
                ObjectNode tileEntityData = mapper.createObjectNode();
                ClassUtils.getClassInfo(tileEntity, tileEntityData);

                // 如果是箱子或其他容器，读取物品内容
                if (tileEntity instanceof IInventory) {
                    IInventory inventory = (IInventory) tileEntity;
                    int size = inventory.getSizeInventory();
                    tileEntityData.put("inventorySize", size);

                    ArrayNode items = mapper.createArrayNode();
                    for (int i = 0; i < size; i++) {
                        ItemStack stack = inventory.getStackInSlot(i);
                        if (stack != null) {
                            ObjectNode item = Items.dump(stack);
                            item.put("slot", i);
                            items.add(item);
                        }
                    }
                    tileEntityData.set("items", items);
                }
                data.set("tileEntity", tileEntityData);
            }
        }

        sendResponse(exchange, 200, data);
    }
}
