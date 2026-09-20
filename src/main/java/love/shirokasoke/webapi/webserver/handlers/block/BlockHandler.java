package love.shirokasoke.webapi.webserver.handlers.block;

import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.Blocks;
import love.shirokasoke.webapi.utils.ClassUtils;
import love.shirokasoke.webapi.utils.Fluids;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.McAccessor;
import love.shirokasoke.webapi.utils.NBT;
import love.shirokasoke.webapi.webserver.Context;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class BlockHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/block";
    }

    @Override
    public String getDescription() {
        return "Get block information at specified coordinates. Query params: x, y, z, dim (optional, default=0)";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI()
            .getQuery();
        if (query == null) {
            throw new ApiException(400, "Missing query parameters. Required: x, y, z");
        }
        coordinates co = getCoordinates(exchange);
        Context context = new Context(co).initServer()
            .initWorld()
            .checkblockExists();
        WorldServer world = context.world;

        Block block = world.getBlock(co.posX, co.posY, co.posZ);
        int metadata = world.getBlockMetadata(co.posX, co.posY, co.posZ);

        ObjectNode data = mapper.createObjectNode();

        ObjectNode b = data.putObject("block");
        Blocks.dump(block, b);
        b.put("hardness", block.getBlockHardness(world, co.posX, co.posY, co.posZ));
        b.put("isReplaceable", block.isReplaceable(world, co.posX, co.posY, co.posZ));
        b.put("isPassable", !block.getBlocksMovement(world, co.posX, co.posY, co.posZ));

        data.set(
            "coordinates",
            mapper.createObjectNode()
                .put("posX", co.posX)
                .put("posY", co.posY)
                .put("posZ", co.posZ)
                .put("dimension", co.dimension));
        data.put("metadata", metadata);
        data.put("isAir", block.isAir(world, co.posX, co.posY, co.posZ));

        // 检查是否有TileEntity（如箱子、熔炉等）
        if (block.hasTileEntity(metadata)) {
            TileEntity tileEntity = McAccessor.getTileEntity(world, co.posX, co.posY, co.posZ);
            if (tileEntity != null) {
                ObjectNode tileEntityData = mapper.createObjectNode();
                ClassUtils.getClassInfo(tileEntity, tileEntityData);

                NBTTagCompound nbt = new NBTTagCompound();
                tileEntity.writeToNBT(nbt);
                NBT.dump(nbt, tileEntityData, "nbt");

                // 如果是箱子或其他容器，读取物品内容
                if (tileEntity instanceof IInventory inventory) {
                    int size = inventory.getSizeInventory();
                    tileEntityData.put("inventorySize", size);

                    ArrayNode items = mapper.createArrayNode();
                    for (int i = 0; i < size; i++) {
                        ItemStack stack = inventory.getStackInSlot(i);
                        if (stack != null) {
                            ObjectNode item = Items.dump(stack);
                            item.put("slot", i);
                            item.put("stackSize", stack.stackSize);
                            items.add(item);
                        }
                    }
                    tileEntityData.set("items", items);
                }

                // 如果是流体容器（如储罐、流体机器），读取流体存储内容
                if (tileEntity instanceof IFluidHandler fluidHandler) {
                    FluidTankInfo[] tanks = fluidHandler.getTankInfo(ForgeDirection.UNKNOWN);

                    if (tanks != null) {
                        ArrayNode fluids = mapper.createArrayNode();
                        for (int i = 0; i < tanks.length; i++) {
                            FluidTankInfo tank = tanks[i];
                            if (tank == null) {
                                continue;
                            }
                            FluidStack fluid = tank.fluid;
                            if (fluid != null && fluid.getFluid() != null) {
                                ObjectNode fluidNode = Fluids.dump(fluid.getFluid());
                                fluidNode.put("index", i);
                                fluidNode.put("amount", fluid.amount);
                                fluidNode.put("capacity", tank.capacity);
                                fluids.add(fluidNode);
                            }
                        }
                        tileEntityData.set("fluids", fluids);
                    }
                }
                data.set("tileEntity", tileEntityData);
            }
        }

        sendResponse(exchange, data);
    }
}
