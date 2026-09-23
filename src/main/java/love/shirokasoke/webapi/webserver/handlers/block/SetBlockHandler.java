package love.shirokasoke.webapi.webserver.handlers.block;

import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.NBT;
import love.shirokasoke.webapi.webserver.Context;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class SetBlockHandler implements RouteHandler {

    /** {@link #setblock} 返回位掩码：方块本身发生变化 */
    public static final int RESULT_CHANGED = 1;
    /** {@link #setblock} 返回位掩码：TileEntity 的 NBT 已写入 */
    public static final int RESULT_NBT_CHANGED = 1 << 1;

    @Override
    public String getPath() {
        return "/setblock";
    }

    @Override
    public String getDescription() {
        return "Setblock";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod()
            .equals("POST")) {
            throw new ApiException(405, "Method must be POST");
        }
        JsonNode data = getBody(exchange);
        coordinates co = getCoordinates(exchange);
        Context context = new Context(co).initServer()
            .initWorld()
            .checkblockExists();

        int id = data.get("id")
            .asInt();
        int metadataIn = data.path("metadataIn")
            .asInt(0);
        int flag = data.path("flag")
            .asInt(2);

        Block block = Block.getBlockById(id);
        if (block == null) {
            throw new ApiException(404, "block id not found");
        }
        final NBTTagCompound nbt;
        if (data.has("nbt")) {
            String nbtBase64 = data.path("nbt")
                .asText();
            nbt = NBT.readFromBase64(nbtBase64);
        } else {
            nbt = null;
        }

        int result = 0;
        try {
            result = ServerThreadDispatcher
                .callOnServerThread(() -> setblock(context.world, co, block, metadataIn, flag, nbt));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }

        boolean changed = (result & RESULT_CHANGED) != 0;
        boolean nbtchanged = (result & RESULT_NBT_CHANGED) != 0;

        sendResponse(
            exchange,
            mapper.createObjectNode()
                .put("changed", changed)
                .put("nbtchanged", nbtchanged));
    }

    public static int setblock(WorldServer world, coordinates co, Block block, int metadataIn, int flag,
        NBTTagCompound nbt) {
        boolean changed = world.setBlock(co.posX, co.posY, co.posZ, block, metadataIn, flag);
        if (nbt == null) {
            return changed ? RESULT_CHANGED : 0;
        }
        TileEntity te = world.getTileEntity(co.posX, co.posY, co.posZ);
        if (te == null) {
            return changed ? RESULT_CHANGED : 0;
        }
        nbt.removeTag("id");
        nbt.setInteger("x", co.posX);
        nbt.setInteger("y", co.posY);
        nbt.setInteger("z", co.posZ);
        te.readFromNBT(nbt);
        te.markDirty();
        world.markBlockForUpdate(co.posX, co.posY, co.posZ);
        // 方块未变化时 setBlock 返回 false，但 NBT 已写入同样算成功
        return changed ? RESULT_CHANGED | RESULT_NBT_CHANGED : RESULT_NBT_CHANGED;
    }
}
