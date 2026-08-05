package love.shirokasoke.webapi.webserver.handlers.block;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.webserver.RouteHandler;

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

    private final Map<String, String> blockTileMap;
    private final File blockTileDir;

    public BlockTileHandler(File blocksJsonFile, File blockTileDir) {
        this.blockTileDir = blockTileDir;
        this.blockTileMap = BlockTileHandler.ensureBlockTileMapLoaded(blocksJsonFile);
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
            throw new ApiException(400, "Missing required parameter 'id' or 'registryName'");
        }

        int meta = 0;
        if (params.containsKey("meta")) {
            try {
                meta = Integer.parseInt(params.get("meta"));
            } catch (NumberFormatException e) {
                throw new ApiException(400, "Invalid 'meta' parameter");
            }
        }

        String fileName = lookupFileName(registryName, meta);
        if (fileName == null) {
            throw new ApiException(404, "Block tile not found: " + registryName + ":" + meta);
        }

        File tileFile = new File(blockTileDir, fileName + ".png");
        if (!tileFile.exists() || !tileFile.isFile()) {
            throw new ApiException(404, "Block tile image not found: " + tileFile.getName());
        }

        byte[] imageData = Files.readAllBytes(tileFile.toPath());
        exchange.getResponseHeaders()
            .set("Content-Type", "image/png");
        setCache(exchange, 86400);
        sendResponse(exchange, imageData);
    }

    /**
     * 从 blocks.json 加载 blockTileMap(预导出 PNG 文件名（不含扩展名）)
     */
    static public Map<String, String> ensureBlockTileMapLoaded(File blocksJsonFile) {
        if (!blocksJsonFile.exists()) {
            MyMod.LOG.warn("blocks.json 不存在: {}", blocksJsonFile);
            return Map.of();
        }

        try {
            ArrayNode array = (ArrayNode) mapper.readTree(blocksJsonFile);
            Map<String, String> temp = new HashMap<>();
            for (JsonNode node : array) {
                String regName = node.path("registryName")
                    .asText(null);
                int meta = node.path("meta")
                    .asInt(0);
                String fileName = node.path("fileName")
                    .asText(null);
                if (regName != null && fileName != null) {
                    temp.put(regName + ":" + meta, fileName);
                }
            }

            MyMod.LOG.info("已加载 {} 条方块纹理映射", temp.size());
            return Collections.unmodifiableMap(temp);
        } catch (IOException e) {
            MyMod.LOG.error("加载 blocks.json 失败");
            return Map.of();
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
