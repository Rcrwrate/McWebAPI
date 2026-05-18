package love.shirokasoke.webapi.server.handlers.block;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.RouteHandler;

/**
 * 返回单个方块顶面纹理图片（PNG）。
 *
 * <p>
 * Query 参数：
 * <ul>
 * <li>{@code id} / {@code registryName} - 方块注册名（必填）</li>
 * <li>{@code meta} - 元数据（可选，默认 0）</li>
 * </ul>
 */
public class BlockTileHandler implements RouteHandler {

    private final Map<String, String> blockTileMap = new HashMap<>();
    private final File blockTileDir;
    private boolean mapLoaded = false;
    private final File blocksJsonFile;

    public BlockTileHandler(File blocksJsonFile, File blockTileDir) {
        this.blocksJsonFile = blocksJsonFile;
        this.blockTileDir = blockTileDir;
    }

    @Override
    public String getPath() {
        return "/block/tile";
    }

    @Override
    public String getDescription() {
        return "Get block top-layer tile image (PNG). Query: id/registryName, meta (optional, default=0)";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);

        String registryName = null;
        if (params.containsKey("id")) {
            registryName = Block.blockRegistry
                .getNameForObject(Block.blockRegistry.getObjectById(Integer.parseInt(params.get("id"))));
        }
        if (registryName == null || registryName.isEmpty()) {
            registryName = params.get("regName");
        }
        if (registryName == null || registryName.isEmpty()) {
            throw new Error(400, "Missing required parameter 'id' or 'registryName'");
        }

        ensureBlockTileMapLoaded();

        int meta = 0;
        if (params.containsKey("meta")) {
            try {
                meta = Integer.parseInt(params.get("meta"));
            } catch (NumberFormatException e) {
                throw new Error(400, "Invalid 'meta' parameter");
            }
        }

        String fileName = lookupFileName(registryName, meta);
        if (fileName == null) {
            throw new Error(404, "Block tile not found: " + registryName + ":" + meta);
        }

        File tileFile = new File(blockTileDir, fileName + ".png");
        if (!tileFile.exists() || !tileFile.isFile()) {
            throw new Error(404, "Block tile image not found: " + tileFile.getName());
        }

        byte[] imageData = Files.readAllBytes(tileFile.toPath());
        exchange.getResponseHeaders()
            .set("Content-Type", "image/png");
        setCache(exchange, 86400);
        sendResponse(exchange, imageData);
    }

    private synchronized void ensureBlockTileMapLoaded() {
        if (mapLoaded) {
            return;
        }
        mapLoaded = true;

        if (!blocksJsonFile.exists()) {
            MyMod.LOG.warn("blocks.json 不存在: {}", blocksJsonFile);
            return;
        }

        try {
            ArrayNode array = (ArrayNode) mapper.readTree(blocksJsonFile);
            for (JsonNode node : array) {
                String regName = node.path("registryName")
                    .asText(null);
                int meta = node.path("meta")
                    .asInt(0);
                String fileName = node.path("fileName")
                    .asText(null);
                if (regName != null && fileName != null) {
                    blockTileMap.put(regName + ":" + meta, fileName);
                }
            }
            MyMod.LOG.info("已加载 {} 条方块纹理映射", blockTileMap.size());
        } catch (IOException e) {
            MyMod.LOG.error("加载 blocks.json 失败");
        }
    }

    private String lookupFileName(String registryName, int meta) {
        String key = registryName + ":" + meta;
        String fileName = blockTileMap.get(key);

        if (fileName == null && meta != 0) {
            fileName = blockTileMap.get(registryName + ":0");
        }

        return fileName;
    }

}
