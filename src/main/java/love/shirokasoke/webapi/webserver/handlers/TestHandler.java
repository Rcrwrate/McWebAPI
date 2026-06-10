package love.shirokasoke.webapi.webserver.handlers;

import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.NBT;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class TestHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/test";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        if (params == null || !params.containsKey("id")) {
            throw new Error(400, "missing query param 'id'");
        }

        if (Config.ItemIconFolder == null || Config.ItemIconFolder.isEmpty()) {
            throw new Error(500, "ItemIconFolder not configured");
        }

        NBTTagCompound nbt = new NBTTagCompound();

        try {
            nbt.setShort("id", Short.parseShort(params.get("id")));
        } catch (NumberFormatException e) {
            throw new Error(400, "invalid query param 'id'");
        }

        nbt.setByte("Count", (byte) 1);

        String damageStr = params.get("damage");
        if (damageStr != null) {
            try {
                nbt.setShort("Damage", Short.parseShort(damageStr));
            } catch (NumberFormatException e) {
                throw new Error(400, "invalid query param 'damage'");
            }
        } else {
            nbt.setShort("Damage", (short) 0);
        }

        if (params.containsKey("tag")) {
            NBTTagCompound tagNbt = NBT.readFromBase64(params.get("tag"));
            if (tagNbt != null) {
                nbt.setTag("tag", tagNbt);
            }
        }

        ItemStack stack = ItemStack.loadItemStackFromNBT(nbt);
        String fileName = Items.getFileName(stack) + ".png";
        throw new Error(200, fileName);
    }
}
