package love.shirokasoke.webapi.webserver.handlers.chunk;

import static love.shirokasoke.webapi.webserver.handlers.block.BlockTileHandler.ensureBlockTileMapLoaded;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.world.chunk.Chunk;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.utils.McAccessor;

/**
 * 返回指定区块的最顶层图像 PNG。
 *
 * <p>
 * 从客户端预导出的方块顶面纹理图（block_tiles/）中查找对应图片，
 * 按区块实际方块逐列拼接成 16×16 的区块图像。
 *
 * <p>
 * Query 参数：
 * <ul>
 * <li>{@code chunkX}, {@code chunkZ} - 区块坐标（二选一）</li>
 * <li>{@code x}, {@code z} - 世界坐标（二选一）</li>
 * <li>{@code dim} / {@code dimension} - 维度 ID（可选，默认 0）</li>
 * </ul>
 */
public class ChunkMapHandler extends ChunkHandler {

    /** (registryName + ":" + meta) → 预导出 PNG 文件名（不含扩展名） */
    private final Map<String, String> blockTileMap;
    /** 文件名 → 缓存的 BufferedImage */
    private final Map<String, BufferedImage> tileCache = new ConcurrentHashMap<>();
    /** 缺失纹理的占位图（透明） */
    private volatile BufferedImage missingTile;
    /** block_tiles 目录 */
    private final File blockTileDir;

    private final int tileSize;

    /** 表示单个格子最顶层方块的数据 */
    private static class BlockInfo {

        final String registryName;
        final int meta;
        final int y;

        BlockInfo(String registryName, int meta, int y) {
            this.registryName = registryName;
            this.meta = meta;
            this.y = y;
        }

        public ObjectNode dump() {
            return mapper.createObjectNode()
                .put("name", registryName)
                .put("meta", meta)
                .put("y", y);
        }
    }

    public ChunkMapHandler(File blocksJsonFile, File blockTileDir, int tileSize) {
        this.blockTileDir = blockTileDir;
        this.tileSize = tileSize > 0 ? tileSize : 16;
        this.blockTileMap = ensureBlockTileMapLoaded(blocksJsonFile);
    }

    @Override
    public String getPath() {
        return "/chunk/map";
    }

    @Override
    public String getDescription() {
        return "Get chunk top-layer map image (PNG). Query: chunkX, chunkZ, dim (optional) or x, z, dim (optional)";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        ChunkCoord cc = getCo(params);
        Chunk chunk = McAccessor.loadChunk(cc.dimension, cc.chunkXPos, cc.chunkZPos);

        BlockInfo[][] data = extractChunkData(chunk);
        setCache(exchange, 60);
        if (params.containsKey("raw")) {
            ArrayNode rows = mapper.createArrayNode();
            for (int x = 0; x < 16; x++) {
                ArrayNode row = mapper.createArrayNode();
                for (int z = 0; z < 16; z++) {
                    BlockInfo info = data[x][z];
                    row.add(info.dump());
                }
                rows.add(row);
            }
            sendResponse(exchange, rows);
            return;
        }
        BufferedImage mapImage = renderChunkMap(data);

        // 编码为 PNG
        byte[] pngData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(mapImage, "png", baos);
            pngData = baos.toByteArray();
        }

        exchange.getResponseHeaders()
            .set("Content-Type", "image/png");
        sendResponse(exchange, pngData);
    }

    /**
     * 从 Chunk 中提取最顶层方块数据。
     *
     * @param chunk 目标区块
     * @return 16×16 的 BlockInfo 数组
     */
    private BlockInfo[][] extractChunkData(Chunk chunk) {
        BlockInfo[][] data = new BlockInfo[16][16];

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int y = chunk.getHeightValue(x, z) - 1;
                Block block = chunk.getBlock(x, y, z);

                // 草丛可能有问题(未被跳过)
                if (block == null || block.getRenderType() == -1) {
                    while (y > 0) {
                        y--;
                        block = chunk.getBlock(x, y, z);
                        if (block != null && block.getRenderType() != -1) {
                            break;
                        }
                    }
                }

                int meta = chunk.getBlockMetadata(x, y, z);
                String regName = null;
                if (block != null) {
                    regName = Block.blockRegistry.getNameForObject(block);
                }
                data[x][z] = new BlockInfo(regName, meta, y);
            }
        }

        return data;
    }

    /**
     * 根据提取的方块数据渲染区块图像。
     *
     * @param data 16×16 的 BlockInfo 数组
     * @return 渲染后的 BufferedImage
     */
    private BufferedImage renderChunkMap(BlockInfo[][] data) {
        int imgSize = 16 * tileSize;

        BufferedImage canvas = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = canvas.createGraphics();

        try {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockInfo info = data[x][z];
                    BufferedImage tile = getTileImage(info.registryName, info.meta);

                    if (tile != null) {
                        g2d.drawImage(tile, x * tileSize, z * tileSize, null);
                    }
                }
            }
        } finally {
            g2d.dispose();
        }

        return canvas;
    }

    /**
     * 根据方块注册名和 meta 获取对应的顶面纹理图片。
     */
    private BufferedImage getTileImage(String regName, int meta) {
        if (regName == null) {
            return getMissingTile();
        }

        String key = regName + ":" + meta;
        String fileName = blockTileMap.get(key);

        // 如果精确匹配找不到，尝试 meta=0
        if (fileName == null && meta != 0) {
            fileName = blockTileMap.get(regName + ":0");
        }

        if (fileName == null) {
            return getMissingTile();
        }

        // 缓存查找
        BufferedImage cached = tileCache.get(fileName);
        if (cached != null) {
            return cached;
        }

        // 从磁盘加载
        MyMod.LOG.info("Cache Miss {}", fileName);
        File tileFile = new File(blockTileDir, fileName + ".png");
        if (!tileFile.exists()) {
            return getMissingTile();
        }

        try {
            BufferedImage img = ImageIO.read(tileFile);
            if (img != null) {
                // 预缩放到统一尺寸，后续渲染可直接拼接而不带缩放参数
                if (img.getWidth() != tileSize || img.getHeight() != tileSize) {
                    BufferedImage scaled = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = scaled.createGraphics();
                    try {
                        g.drawImage(img, 0, 0, tileSize, tileSize, null);
                    } finally {
                        g.dispose();
                    }
                    img = scaled;
                }
                tileCache.put(fileName, img);
            }
            return img != null ? img : getMissingTile();
        } catch (IOException e) {
            MyMod.LOG.warn("读取方块纹理失败: {}", tileFile);
            return getMissingTile();
        }
    }

    /**
     * 获取缺失纹理的占位图。
     */
    private BufferedImage getMissingTile() {
        if (missingTile == null) {
            missingTile = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
        }
        return missingTile;
    }

}
