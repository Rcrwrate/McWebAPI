package love.shirokasoke.webapi.webserver.handlers.ThreeD;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.webserver.RouteHandler;

/**
 * 请求体为一张图片（png/jpg 等），转换为 OC 3D 打印件后投递到玩家背包，
 * 背包放不下的部分掉落在玩家附近。可选 Query 参数: label/tooltip（支持 %d,%d 块坐标占位）。
 */
public class PlayerPrintHandler implements RouteHandler {

    /** 请求体大小上限 32MB */
    private static final int MAX_BODY_BYTES = 32 * 1024 * 1024;

    @Override
    public String getPath() {
        return "/3d/player";
    }

    @Override
    public String getDescription() {
        return "PUT 上传图片 → OC 3D 打印件，投递到玩家背包（多余掉落在玩家附近）";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        if (!"PUT".equals(exchange.getRequestMethod())) {
            throw new ApiException(400, "Method must be PUT");
        }
        Map<String, String> params = parseQueryParams(exchange);

        final EntityPlayerMP player;
        if (params.containsKey("id")) {
            final int entityId;
            try {
                entityId = Integer.parseInt(params.get("id"));
            } catch (NumberFormatException e) {
                throw new ApiException(400, "Invalid entity id");
            }
            player = findPlayer(entityId);
        } else if (params.containsKey("name")) {
            player = findPlayer(params.get("name"));
        } else {
            player = null;
        }

        if (player == null) {
            throw new ApiException(404, "Player not found");
        }

        byte[] imageData = {};
        try (InputStream is = exchange.getRequestBody()) {
            imageData = is.readAllBytes();
        }
        if (imageData.length == 0) {
            throw new ApiException(400, "Empty request body");
        }
        if (imageData.length > MAX_BODY_BYTES) {
            throw new ApiException(400, "Image too large: " + imageData.length + " bytes");
        }

        PrintUtils.checkImage(imageData);

        String label = params.getOrDefault("label", "3d-print %d,%d");
        String tooltip = params.getOrDefault("tooltip", "created by love.shirokasoke.webapi");

        // 图片解码与形状生成为纯 CPU 操作，在 HTTP 线程完成，不占用主线程 tick。
        final List<ItemStack> prints = PrintUtils.createPrints(imageData, label, tooltip);

        // 背包操作必须在服务器主线程执行
        int[] counts = ServerThreadDispatcher.callOnServerThread(() -> {
            int added = 0;
            int dropped = 0;
            for (ItemStack stack : prints) {
                if (player.inventory.addItemStackToInventory(stack)) {
                    added++;
                } else {
                    player.dropPlayerItemWithRandomChoice(stack, false);
                    dropped++;
                }
            }
            return new int[] { added, dropped };
        });

        if (counts == null) {
            throw new ApiException(404, "Player not found with entity id: ");
        }

        ObjectNode rep = mapper.createObjectNode()
            .put("total", prints.size())
            .put("added", counts[0])
            .put("dropped", counts[1]);
        sendResponse(exchange, rep);
    }

    /** 跨维度按 entityId 查找在线玩家 */
    private static EntityPlayerMP findPlayer(int entityId) {
        for (Integer dimId : DimensionManager.getIDs()) {
            WorldServer world = DimensionManager.getWorld(dimId.intValue());
            if (world == null) continue;
            for (Entity obj : world.loadedEntityList) {
                if (obj instanceof EntityPlayerMP mp && obj.getEntityId() == entityId) {
                    return mp;
                }
            }
        }
        return null;
    }

    private static EntityPlayerMP findPlayer(String name) {
        for (Integer dimId : DimensionManager.getIDs()) {
            WorldServer world = DimensionManager.getWorld(dimId.intValue());
            if (world == null) continue;
            for (Entity obj : world.loadedEntityList) {
                if (obj instanceof EntityPlayerMP mp && mp.getCommandSenderName()
                    .equals(name)) {
                    return mp;
                }
            }
        }
        return null;
    }
}
