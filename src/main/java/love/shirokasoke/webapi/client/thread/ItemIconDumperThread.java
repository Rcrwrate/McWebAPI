package love.shirokasoke.webapi.client.thread;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import codechicken.nei.ItemList;
import codechicken.nei.guihook.GuiContainerManager;
import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.Constant;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.client.utils.CItems;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.log;

/**
 * 客户端后台线程：导出所有物品 Icon 为 PNG 图片。
 *
 * <p>
 * 实现参考
 * <a href="https://github.com/ShadowTheAge/nesql-exporter">nesql-exporter</a> 的
 * {@code Renderer} 类，使用独立 Framebuffer 渲染物品图标，避免与主屏幕内容互相干扰。
 *
 * <p>
 * 由于 OpenGL 渲染必须在拥有 GL Context 的主线程执行，本线程通过
 * {@link Minecraft#func_152344_a(Runnable)} 将渲染任务投递到客户端主线程，后台线程阻塞等待结果。
 */
public class ItemIconDumperThread extends Thread {

    /** 输出目录：.minecraft/dumps/item_icons/ */
    private final File outputDir;
    /** 输出图标尺寸（像素），由配置文件 {@link Config#itemIconSize} 决定。 */
    private final int iconSize;
    private final Minecraft mc;
    /** 独立 Framebuffer，用于离屏渲染物品图标。延迟到第一次渲染时初始化。 */
    private Framebuffer framebuffer;

    private boolean useNEI = false;

    public ItemIconDumperThread(boolean useNEI) {
        super("ItemIcon-Dumper");
        setDaemon(true);
        this.mc = Minecraft.getMinecraft();
        this.iconSize = Config.itemIconSize;
        this.outputDir = new File(mc.mcDataDir, "dumps/item_icons");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        this.useNEI = useNEI;
    }

    @Override
    public void run() {
        try {
            MyMod.LOG.info("开始收集物品列表...");
            List<ItemStack> allStacks;
            if (useNEI) {
                allStacks = ItemList.items;
            } else {
                allStacks = CItems.getAllItems();
            }
            MyMod.LOG.info("开始导出物品数据到 items.json...");
            File dumpsFile = new File(mc.mcDataDir, "dumps/items.json");
            Map<String, ObjectNode> merged = new LinkedHashMap<>();

            if (dumpsFile.exists()) {
                try {
                    JsonNode existing = Constant.mapper.readTree(dumpsFile);
                    if (existing.isArray()) {
                        for (JsonNode node : existing) {
                            if (node.isObject()) {
                                ObjectNode obj = (ObjectNode) node;
                                merged.put(getUniqueKey(obj), obj);
                            }
                        }
                    }
                    MyMod.LOG.info("已加载现有 items.json，共 {} 条记录", merged.size());
                } catch (IOException e) {
                    MyMod.LOG.error("读取已存在的 items.json 失败");
                    log.e(e);
                }
            }

            for (ItemStack stack : allStacks) {
                try {
                    ObjectNode data = Items.dump(stack);
                    merged.put(getUniqueKey(data), data);
                } catch (Throwable t) {
                    MyMod.LOG.error("导出物品数据失败: {}", stack, t);
                }
            }

            ArrayNode dumps = Constant.mapper.createArrayNode();
            for (ObjectNode data : merged.values()) {
                dumps.add(data);
            }

            try {
                Constant.mapper.writeValue(dumpsFile, dumps);
                MyMod.LOG.info("items.json 导出完成，共 {} 条记录", dumps.size());
            } catch (IOException e) {
                MyMod.LOG.error("写入 items.json 失败");
                log.e(e);
            }
            MyMod.LOG.info("共 {} 个物品需要导出", allStacks.size());

            int exported = 0;
            long startTime = System.currentTimeMillis();

            for (ItemStack stack : allStacks) {
                if (interrupted()) {
                    MyMod.LOG.warn("被中断，停止导出");
                    break;
                }

                String fileName = Items.getFileName(stack);
                File outFile = new File(outputDir, fileName + ".png");
                if (outFile.exists()) {
                    MyMod.LOG.warn(fileName + "\tskiped");
                    continue;
                }

                // 复制 ItemStack 防止并发修改
                final ItemStack stackRef = stack.copy();
                final RenderResult result = new RenderResult();

                // 将渲染任务投递到客户端主线程（拥有 OpenGL Context）
                mc.func_152344_a(() -> {
                    try {
                        BufferedImage img = renderItem(stackRef);
                        result.image = img;
                    } catch (Exception e) {
                        MyMod.LOG.error("渲染物品失败: {}", stackRef);
                        log.e(e);
                        result.image = null;
                    } finally {
                        result.done = true;
                        synchronized (result) {
                            result.notifyAll();
                        }
                    }
                });
                // 后台线程阻塞等待主线程渲染完成
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
                        log.e(e);
                    }
                }
                // 可配置的延迟，降低 CPU/GPU 占用
                if (Config.itemIconDelayMs > 0) {
                    try {
                        Thread.sleep(Config.itemIconDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread()
                            .interrupt();
                        break;
                    }
                }
                if (exported % 100 == 0 && exported > 0) {
                    MyMod.LOG.info("已导出 {} / {} 个物品", exported, allStacks.size());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            MyMod.LOG.info("导出完成，共 {} 个物品，耗时 {}ms，输出目录: {}", exported, duration, outputDir.getAbsolutePath());
        } catch (Throwable e) {
            MyMod.LOG.error("导出物品图标时出错");
            log.e(e);
        }
    }

    /**
     * 在客户端主线程中渲染单个物品到独立 Framebuffer。
     *
     * <p>
     * <b>必须在拥有 OpenGL Context 的线程调用！</b>
     *
     * <p>
     * 渲染流程：
     * <ol>
     * <li>创建/绑定独立 Framebuffer（尺寸 = iconSize × iconSize）</li>
     * <li>清空缓冲区为透明黑色</li>
     * <li>设置正交投影矩阵（参考 nesql-exporter 的 setupRenderState）</li>
     * <li>启用 GUI 标准物品光照</li>
     * <li>使用 {@link GuiContainerManager#drawItem(int, int, ItemStack)} 绘制物品</li>
     * <li>读取像素数据，翻转 Y 轴</li>
     * <li>解绑 Framebuffer，恢复主屏幕</li>
     * </ol>
     *
     * @param stack 要渲染的物品
     * @return 渲染后的图片，ARGB 格式
     */
    private BufferedImage renderItem(ItemStack stack) {
        // 延迟初始化 Framebuffer，避免在构造时（无 GL Context）创建
        if (framebuffer == null) {
            framebuffer = new Framebuffer(iconSize, iconSize, true);
        }

        // 绑定 Framebuffer 并清空为透明
        framebuffer.bindFramebuffer(true);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClearDepth(1D);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        // 设置投影矩阵，使 16×16 的物品填满整个 Framebuffer
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, 1.0, 1.0, 0.0, -100.0, 100.0);
        double scaleFactor = 1 / 16.0;
        GL11.glScaled(scaleFactor, scaleFactor, scaleFactor);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        // 使用 NEI 的绘制方法渲染物品
        GuiContainerManager.drawItem(0, 0, stack);

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();

        // 读取像素并转换为 BufferedImage
        BufferedImage image = readImage();

        // 解绑 Framebuffer，恢复主屏幕渲染
        framebuffer.unbindFramebuffer();

        return image;
    }

    /**
     * 从当前绑定的 Framebuffer 读取像素数据。
     *
     * <p>
     * OpenGL 的坐标系原点在左下角，而 BufferedImage 原点在左上角，
     * 因此需要对 Y 轴进行翻转。
     *
     * @return ARGB 格式的 BufferedImage
     */
    private BufferedImage readImage() {
        ByteBuffer imageByteBuffer = BufferUtils.createByteBuffer(4 * iconSize * iconSize);
        GL11.glReadPixels(0, 0, iconSize, iconSize, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, imageByteBuffer);

        int[] pixels = new int[iconSize * iconSize];
        imageByteBuffer.asIntBuffer()
            .get(pixels);

        // OpenGL Y 轴是倒置的，需要翻转
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

    private String getUniqueKey(ObjectNode node) {
        return getText(node, "id") + "|" + getText(node, "damage") + "|" + getText(node, "nbtWrite");
    }

    private String getText(ObjectNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value != null && !value.isNull()) {
            return value.asText();
        }
        return "";
    }

    private static class RenderResult {

        volatile boolean done = false;
        volatile BufferedImage image;
    }
}
