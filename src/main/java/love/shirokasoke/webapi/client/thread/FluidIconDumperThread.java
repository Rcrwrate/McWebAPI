package love.shirokasoke.webapi.client.thread;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.fasterxml.jackson.databind.node.ArrayNode;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.Constant;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.utils.Fluids;
import love.shirokasoke.webapi.utils.Logs;

/**
 * 客户端后台线程：导出所有已注册流体的 Icon 为 PNG 图片。
 *
 * <p>
 * 与 {@link ItemIconDumperThread} 类似，渲染任务通过
 * {@link Minecraft#func_152344_a(Runnable)} 投递到客户端主线程执行。
 */
public class FluidIconDumperThread extends Thread {

    /** 输出目录：.minecraft/dumps/fluid_icons/ */
    private final File outputDir;
    /** 输出图标尺寸（像素），由配置文件 {@link Config#fluidIconSize} 决定。 */
    private final int iconSize;
    private final Minecraft mc;
    /** 独立 Framebuffer，用于离屏渲染流体图标。延迟到第一次渲染时初始化。 */
    private Framebuffer framebuffer;

    public FluidIconDumperThread() {
        super("FluidIcon-Dumper");
        setDaemon(true);
        this.mc = Minecraft.getMinecraft();
        this.iconSize = Config.fluidIconSize;
        this.outputDir = new File(mc.mcDataDir, "dumps/fluid_icons");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }

    @Override
    public void run() {
        try {
            MyMod.LOG.info("开始收集流体列表...");
            List<Fluid> allFluids = new ArrayList<>(
                FluidRegistry.getRegisteredFluids()
                    .values());

            MyMod.LOG.info("开始导出流体数据到 fluids.json...");
            ArrayNode dumps = Constant.mapper.createArrayNode();
            for (Fluid fluid : allFluids) {
                try {
                    Fluids.dump(fluid, dumps.addObject());
                } catch (Throwable t) {
                    MyMod.LOG.error("导出流体数据失败: {}", fluid.getName(), t);
                }
            }
            File dumpsFile = new File(mc.mcDataDir, "dumps/fluids.json");
            try {
                Constant.mapper.writeValue(dumpsFile, dumps);
                MyMod.LOG.info("fluids.json 导出完成，共 {} 条记录", dumps.size());
            } catch (IOException e) {
                MyMod.LOG.error("写入 fluids.json 失败");
                Logs.e(e);
            }

            MyMod.LOG.info("共 {} 个流体需要导出", allFluids.size());

            int exported = 0;
            long startTime = System.currentTimeMillis();

            for (Fluid fluid : allFluids) {
                if (interrupted()) {
                    MyMod.LOG.warn("被中断，停止导出");
                    break;
                }

                if (fluid.getIcon() == null) {
                    MyMod.LOG.warn("流体 {} 没有图标，跳过", fluid.getName());
                    continue;
                }

                String fileName = Fluids.getFileName(fluid);
                File outFile = new File(outputDir, fileName + ".png");
                if (outFile.exists()) {
                    MyMod.LOG.warn(fileName + "\tskiped");
                    continue;
                }

                final Fluid fluidRef = fluid;
                final RenderResult result = new RenderResult();

                mc.func_152344_a(() -> {
                    try {
                        BufferedImage img = renderFluid(fluidRef);
                        result.image = img;
                    } catch (Exception e) {
                        MyMod.LOG.error("渲染流体失败: {}", fluidRef.getName());
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

                if (Config.fluidIconDelayMs > 0) {
                    try {
                        Thread.sleep(Config.fluidIconDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread()
                            .interrupt();
                        break;
                    }
                }

                if (exported % 100 == 0 && exported > 0) {
                    MyMod.LOG.info("已导出 {} / {} 个流体", exported, allFluids.size());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            MyMod.LOG.info("导出完成，共 {} 个流体，耗时 {}ms，输出目录: {}", exported, duration, outputDir.getAbsolutePath());
        } catch (Throwable e) {
            MyMod.LOG.error("导出流体图标时出错");
            Logs.e(e);
        }
    }

    /**
     * 在客户端主线程中渲染单个流体到独立 Framebuffer。
     *
     * <p>
     * <b>必须在拥有 OpenGL Context 的线程调用！</b>
     *
     * <p>
     * 渲染流程：
     * <ol>
     * <li>创建/绑定独立 Framebuffer（尺寸 = iconSize × iconSize）</li>
     * <li>清空缓冲区为透明黑色</li>
     * <li>绑定方块纹理图集（流体纹理在此图集上）</li>
     * <li>获取流体静止图标 {@link Fluid#getIcon()} 和流体颜色</li>
     * <li>设置正交投影矩阵</li>
     * <li>使用 {@link Tessellator} 绘制全屏 quad，并叠加流体颜色</li>
     * <li>读取像素数据，翻转 Y 轴</li>
     * <li>解绑 Framebuffer，恢复主屏幕</li>
     * </ol>
     *
     * @param fluid 要渲染的流体
     * @return 渲染后的图片，ARGB 格式；若该流体没有图标则返回 null
     */
    private BufferedImage renderFluid(Fluid fluid) {
        if (framebuffer == null) {
            framebuffer = new Framebuffer(iconSize, iconSize, true);
        }

        IIcon icon = fluid.getIcon();
        if (icon == null) {
            return null;
        }

        // 绑定方块纹理图集
        mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);

        // 绑定 Framebuffer 并清空为透明
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

        // 获取流体颜色（部分流体为灰度纹理，需叠加颜色）
        int color = fluid.getColor();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        GL11.glColor3ub((byte) r, (byte) g, (byte) b);

        // 绘制全屏 quad，UV 映射到该 icon 在 atlas 上的区域
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        // 左下
        tess.addVertexWithUV(0, iconSize, 0, icon.getMinU(), icon.getMaxV());
        // 右下
        tess.addVertexWithUV(iconSize, iconSize, 0, icon.getMaxU(), icon.getMaxV());
        // 右上
        tess.addVertexWithUV(iconSize, 0, 0, icon.getMaxU(), icon.getMinV());
        // 左上
        tess.addVertexWithUV(0, 0, 0, icon.getMinU(), icon.getMinV());
        tess.draw();

        // 重置颜色混合
        GL11.glColor4f(1f, 1f, 1f, 1f);

        BufferedImage image = readImage();
        framebuffer.unbindFramebuffer();
        return image;
    }

    /**
     * 从当前绑定的 Framebuffer 读取像素数据。
     *
     * <p>
     * OpenGL 的坐标系原点在左下角，而 BufferedImage 原点在左上角，需要对 Y 轴翻转。
     *
     * @return ARGB 格式的 BufferedImage
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

    private static class RenderResult {

        volatile boolean done = false;
        volatile BufferedImage image;
    }
}
