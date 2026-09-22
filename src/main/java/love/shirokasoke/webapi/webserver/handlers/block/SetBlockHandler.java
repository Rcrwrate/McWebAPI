package love.shirokasoke.webapi.webserver.handlers.block;

import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.NBT;
import love.shirokasoke.webapi.webserver.Context;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class SetBlockHandler implements RouteHandler {

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
            throw new ApiException(400, "Method must be POST");
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

        boolean success = false;
        try {
            success = ServerThreadDispatcher.callOnServerThread(() -> {
                boolean changed = context.world.setBlock(co.posX, co.posY, co.posZ, block, metadataIn, flag);
                if (nbt == null) {
                    return changed;
                }
                TileEntity te = context.world.getTileEntity(co.posX, co.posY, co.posZ);
                if (te == null) {
                    return false;
                }
                nbt.removeTag("id");
                nbt.setInteger("x", co.posX);
                nbt.setInteger("y", co.posY);
                nbt.setInteger("z", co.posZ);
                te.readFromNBT(nbt);
                te.markDirty();
                context.world.markBlockForUpdate(co.posX, co.posY, co.posZ);
                // 方块未变化时 setBlock 返回 false，但 NBT 已写入同样算成功
                return true;
            });
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }

        ObjectNode rep = mapper.createObjectNode()
            .put("success", success)
            .putNull("data");
        sendResponse(exchange, 200, rep, true);
    }
}
