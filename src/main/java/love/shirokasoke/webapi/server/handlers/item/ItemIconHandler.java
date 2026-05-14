package love.shirokasoke.webapi.server.handlers.item;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.Constant;
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

        String base64 = params.get("id");
        String jsonStr;
        try {
            jsonStr = new String(
                    Base64.getDecoder()
                            .decode(base64),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new Error(400, "invalid base64 encoding");
        }

        JsonNode json;
        try {
            json = Constant.mapper.readTree(jsonStr);
        } catch (IOException e) {
            throw new Error(400, "invalid json: " + e.getMessage());
        }
        if (!json.isObject()) {
            throw new Error(400, "json must be an object");
        }

        NBTTagCompound nbt = new NBTTagCompound();
        if (json.has("id")) {
            nbt.setShort(
                    "id",
                    (short) json.get("id")
                            .asInt());
        } else {
            throw new Error(400, "missing field 'id'");
        }
        if (json.has("Count")) {
            nbt.setByte(
                    "Count",
                    (byte) json.get("Count")
                            .asInt());
        } else {
            nbt.setByte("Count", (byte) 1);
        }
        if (json.has("Damage")) {
            nbt.setShort(
                    "Damage",
                    (short) json.get("Damage")
                            .asInt());
        } else {
            nbt.setShort("Damage", (short) 0);
        }
        if (json.has("tag") && json.get("tag")
                .isObject()) {
            NBTTagCompound tagNbt = NBT.fromJson(json.get("tag"));
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
