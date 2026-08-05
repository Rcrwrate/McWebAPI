package love.shirokasoke.webapi.client.thread;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cpw.mods.fml.common.registry.GameData;
import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.Constant;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.Logs;

/**
 * 客户端后台线程：导出所有方块的顶面纹理为 PNG 图片，并可选导出方块元数据到 JSON。
 *
 * <p>
 * 与 {@link ItemIconDumperThread} 类似，渲染任务通过
 * {@link Minecraft#func_152344_a(Runnable)} 投递到客户端主线程执行。
 */
public class MapTileDumperThread extends Thread {

    private final File outputDir;
    private final int iconSize;
    private final Minecraft mc;
    private Framebuffer framebuffer;

    public MapTileDumperThread() {
        super("MapTile-Dumper");
        setDaemon(true);
        this.mc = Minecraft.getMinecraft();
        this.iconSize = Config.blockTileSize;
        this.outputDir = new File(mc.mcDataDir, "dumps/block_tiles");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }

    @Override
    public void run() {
        try {
            MyMod.LOG.info("开始收集方块列表...");
            List<BlockMetaEntry> allBlocks = getAllBlockEntries();

            MyMod.LOG.info("开始导出方块数据到 blocks.json...");
            ArrayNode dumps = Constant.mapper.createArrayNode();
            for (BlockMetaEntry entry : allBlocks) {
                if (interrupted()) {
                    break;
                }
                try {
                    ObjectNode data = love.shirokasoke.webapi.utils.Blocks.dump(entry.block);
                    data.put("meta", entry.meta);
                    data.put("fileName", entry.fileName);
                    // 尝试获取方块基础颜色（不带生物群系着色）
                    try {
                        data.put("blockColor", entry.block.getBlockColor());
                    } catch (Throwable ignored) {}
                    dumps.add(data);
                } catch (Throwable t) {
                    MyMod.LOG.error("导出方块数据失败: {}", entry, t);
                }
            }
            File dumpsFile = new File(mc.mcDataDir, "dumps/blocks.json");
            try {
                Constant.mapper.writeValue(dumpsFile, dumps);
                MyMod.LOG.info("blocks.json 导出完成，共 {} 条记录", dumps.size());
            } catch (IOException e) {
                MyMod.LOG.error("写入 blocks.json 失败");
                Logs.e(e);
            }

            MyMod.LOG.info("共 {} 个方块变体需要导出", allBlocks.size());

            int exported = 0;
            long startTime = System.currentTimeMillis();

            for (BlockMetaEntry entry : allBlocks) {
                if (interrupted()) {
                    MyMod.LOG.warn("被中断，停止导出");
                    break;
                }

                File outFile = new File(outputDir, entry.fileName + ".png");
                if (outFile.exists()) {
                    continue;
                }

                final BlockMetaEntry entryRef = entry;
                final RenderResult result = new RenderResult();

                mc.func_152344_a(() -> {
                    try {
                        BufferedImage img = renderBlockTop(entryRef.block, entryRef.meta);
                        result.image = img;
                    } catch (Exception e) {
                        MyMod.LOG.error("渲染方块顶面失败: {}", entryRef);
                        Logs.e(e);
                        result.image = null;
                    } finally {
                        result.done = true;
                        synchronized (result) {
                            result.notifyAll();
                        }
                    }
                });

                synchronized (result) {
                    while (!result.done) {
                        result.wait(50);
                    }
                }

                if (result.image != null) {
                    try {
                        ImageIO.write(result.image, "png", outFile);
                        exported++;
                    } catch (IOException e) {
                        MyMod.LOG.error("保存图片失败: {}", outFile.getAbsolutePath());
                        Logs.e(e);
                    }
                }

                if (Config.blockTileDelayMs > 0) {
                    try {
                        Thread.sleep(Config.blockTileDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread()
                            .interrupt();
                        break;
                    }
                }

                if (exported % 100 == 0 && exported > 0) {
                    MyMod.LOG.info("已导出 {} / {} 个方块", exported, allBlocks.size());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            MyMod.LOG.info("导出完成，共 {} 个方块，耗时 {}ms，输出目录: {}", exported, duration, outputDir.getAbsolutePath());
        } catch (Throwable e) {
            MyMod.LOG.error("导出方块顶面时出错");
            Logs.e(e);
        }
    }

    /**
     * 收集所有需要导出的 Block + meta 组合。
     */
    private List<BlockMetaEntry> getAllBlockEntries() {
        List<BlockMetaEntry> list = new ArrayList<>();
        for (Object obj : GameData.getBlockRegistry()
            .typeSafeIterable()) {
            if (!(obj instanceof Block)) {
                continue;
            }
            Block block = (Block) obj;
            if (block == null || block == Blocks.air) {
                continue;
            }

            Item item = Item.getItemFromBlock(block);
            if (item == null) {
                list.add(new BlockMetaEntry(block, 0));
            } else {
                ArrayList<ItemStack> subBlocks = new ArrayList<>();
                try {
                    block.getSubBlocks(item, null, subBlocks);
                    if (subBlocks.isEmpty()) {
                        list.add(new BlockMetaEntry(block, 0));
                    } else {
                        for (ItemStack stack : subBlocks) {
                            list.add(new BlockMetaEntry(block, item.getDamage(stack)));
                        }
                    }
                } catch (Throwable t) {
                    MyMod.LOG.error("[MapTileDumper] 获取方块 {} 的子类型时出错", block, t);
                    list.add(new BlockMetaEntry(block, 0));
                }
            }
        }
        return list;
    }

    /**
     * 在客户端主线程中渲染指定方块的顶面纹理到独立 Framebuffer。
     *
     * <p>
     * <b>必须在拥有 OpenGL Context 的线程调用！</b>
     *
     * @param block 目标方块
     * @param meta  元数据
     * @return 渲染后的图片，ARGB 格式；若该方块没有顶面纹理则返回 null
     */
    private BufferedImage renderBlockTop(Block block, int meta) {
        IIcon icon;
        try {
            icon = block.getIcon(1, meta);
        } catch (Throwable t) {
            return null;
        }
        if (icon == null) {
            return null;
        }

        if (framebuffer == null) {
            framebuffer = new Framebuffer(iconSize, iconSize, true);
        }

        // 绑定方块纹理图集
        mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);

        // 绑定 Framebuffer 并清空
        framebuffer.bindFramebuffer(true);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClearDepth(1D);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        // 正交投影，使 quad 铺满整个 viewport
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, iconSize, iconSize, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 获取方块基础渲染颜色（如树叶、草等需要着色）
        int color = 16777215;
        try {
            color = block.getRenderColor(meta);
        } catch (Throwable ignored) {}

        // 绘制全屏 quad，UV 映射到该 icon 在 atlas 上的区域
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorOpaque_I(color);
        // 左下
        tess.addVertexWithUV(0, iconSize, 0, icon.getMinU(), icon.getMaxV());
        // 右下
        tess.addVertexWithUV(iconSize, iconSize, 0, icon.getMaxU(), icon.getMaxV());
        // 右上
        tess.addVertexWithUV(iconSize, 0, 0, icon.getMaxU(), icon.getMinV());
        // 左上
        tess.addVertexWithUV(0, 0, 0, icon.getMinU(), icon.getMinV());
        tess.draw();

        BufferedImage image = readImage();
        framebuffer.unbindFramebuffer();
        return image;
    }

    /**
     * 从当前绑定的 Framebuffer 读取像素数据。
     *
     * <p>
     * OpenGL 的坐标系原点在左下角，而 BufferedImage 原点在左上角，需要对 Y 轴翻转。
     */
    private BufferedImage readImage() {
        ByteBuffer imageByteBuffer = BufferUtils.createByteBuffer(4 * iconSize * iconSize);
        GL11.glReadPixels(0, 0, iconSize, iconSize, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, imageByteBuffer);

        int[] pixels = new int[iconSize * iconSize];
        imageByteBuffer.asIntBuffer()
            .get(pixels);

        // OpenGL Y 轴倒置翻转
        int[] flippedPixels = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int x = i % iconSize;
            int y = iconSize - (i / iconSize + 1);
            flippedPixels[i] = pixels[x + iconSize * y];
        }
        pixels = flippedPixels;

        BufferedImage image = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, iconSize, iconSize, pixels, 0, iconSize);
        return image;
    }

    /**
     * 生成方块变体的安全文件名。
     */
    private static String getFileName(Block block, int meta) {
        String regName = Block.blockRegistry.getNameForObject(block);
        if (regName == null) {
            regName = "unknown";
        }
        String unloc = block.getUnlocalizedName();
        return Items.cleanFileName(regName.replace(":", "_") + "_" + meta + "_" + unloc);
    }

    private static class BlockMetaEntry {

        final Block block;
        final int meta;
        final String fileName;

        BlockMetaEntry(Block block, int meta) {
            this.block = block;
            this.meta = meta;
            this.fileName = getFileName(block, meta);
        }

        @Override
        public String toString() {
            return fileName;
        }
    }

    private static class RenderResult {

        volatile boolean done = false;
        volatile BufferedImage image;
    }
}
