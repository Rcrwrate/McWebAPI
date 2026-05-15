package love.shirokasoke.webapi.server.handlers.item;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.RouteHandler;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.NBT;

public class ItemIconHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/item/icon";
    }

    @Override
    public String getDescription() {
        return "Returns item icon PNG by base64-encoded ItemStack NBT JSON. Query: ?id=<base64>";
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
        if (stack == null || stack.getItem() == null) {
            throw new Error(400, "failed to restore ItemStack from NBT");
        }

        String fileName = Items.getFileName(stack) + ".png";
        File iconFile = new File(Config.ItemIconFolder, fileName);

        if (!iconFile.exists() || !iconFile.isFile()) {
            MyMod.LOG.warn("[ItemIconHandler] Icon not found: {}", iconFile.getAbsolutePath());
            throw new Error(404, "icon not found");
        }

        byte[] imageData = Files.readAllBytes(iconFile.toPath());
        exchange.getResponseHeaders()
            .set("Content-Type", "image/png");
        setCache(exchange, 86400);
        sendResponse(exchange, imageData);
    }
}
