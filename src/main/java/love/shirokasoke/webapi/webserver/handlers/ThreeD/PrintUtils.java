package love.shirokasoke.webapi.webserver.handlers.ThreeD;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import li.cil.oc.api.Items;
import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.webserver.RouteHandler.ApiException;

/**
 * OpenComputers 3D 打印件（{@code print} 方块物品）。
 *
 * <p>
 * 不经过游戏内打印机（printer3d 组件），直接按 {@link li.cil.oc.common.item.data.PrintData}
 * 的 NBT 格式构造物品，可直接放置到世界中渲染显示。
 */
public final class PrintUtils {

    /** 白色贴图，靠 tint 染色显示任意颜色 */
    public static final String SHAPE_TEXTURE = "opencomputers:White";

    /** OC 默认 printer.maxShapes（application.conf）。直接构造 NBT 不受此限制，仅供参考 */
    public static final int MAX_SHAPES_PER_PRINT = 24;

    /** 打印件发光等级 */
    public static final int PRINT_LIGHT_LEVEL = 15;

    /** OC {@link li.cil.oc.Constants.BlockName.Printer} 的值 */
    public static final String PRINT_ITEM_NAME = "print";

    static final int ALPHA_THRESHOLD = Config.ALPHA_THRESHOLD;
    static final int TOLERANCE_R = Config.TOLERANCE_R;
    static final int TOLERANCE_G = Config.TOLERANCE_G;
    static final int TOLERANCE_B = Config.TOLERANCE_B;

    private PrintUtils() {}

    /**
     * 一个形状 = 方块内一个 AABB（坐标为 ×16 后的 0~16 字节值）。
     *
     * @param minX/minY/minZ/maxX/maxY/maxZ 形状包围盒（×16 体素坐标）
     * @param texture                       贴图资源名
     * @param tint                          24 位颜色（0xRRGGBB），null 表示不染色
     */
    record Shape(byte minX, byte minY, byte minZ, byte maxX, byte maxY, byte maxZ, String texture, Integer tint) {}

    // region 图片加载与预处理

    /** 图片单边最大像素（超过直接拒绝，防止高分辨率炸弹） */
    public static final int MAX_IMAGE_DIMENSION = 4096;

    /** 解码前允许的最大像素总数，防解压炸弹 OOM */
    public static final long MAX_IMAGE_PIXELS = ((long) MAX_IMAGE_DIMENSION) * ((long) MAX_IMAGE_DIMENSION);

    /**
     * 完整解码前读取图片头部，校验格式与尺寸（不解码像素数据）。
     * <p>
     * 防御解压炸弹：几 KB 的 PNG 可声明数万×数万像素，直接 {@link ImageIO#read} 会按 {@code w*h*4} 字节分配位图导致 OOM。
     *
     * @return int[]{width, height}
     * @throws IOException
     */
    public static int[] checkImage(byte[] data) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (iis == null) {
                throw new ApiException(400, "no image input stream available");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new ApiException(400, "not a recognizable image format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0) {
                    throw new ApiException(400, "invalid image dimensions: " + w + "x" + h);
                }
                if (w > MAX_IMAGE_DIMENSION || h > MAX_IMAGE_DIMENSION) {
                    throw new ApiException(
                        400,
                        "image too large: " + w + "x" + h + ", max edge is " + MAX_IMAGE_DIMENSION);
                }
                if ((long) w * h > MAX_IMAGE_PIXELS) {
                    throw new ApiException(
                        400,
                        "image too large: " + w + "x" + h + " (" + ((long) w * h) + " px), max is " + MAX_IMAGE_PIXELS);
                }
                return new int[] { w, h };
            } finally {
                reader.dispose();
            }
        }
    }

    /** 从文件读取图片（png/jpg/bmp 等均支持），无法解码时抛出异常 */
    public static BufferedImage loadImage(File file) throws IOException {
        BufferedImage img = ImageIO.read(file);
        if (img == null) {
            throw new ApiException(404, "not a decodable image: " + file);
        }
        return img;
    }

    /**
     * 从内存字节读取图片
     * 
     * @apiNote 解码前先做尺寸校验，防解压炸弹
     */
    public static BufferedImage loadImage(byte[] data) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) {
            throw new ApiException(404, "not a decodable image");
        }
        return img;
    }

    /**
     * 居中扩展到 16 的整数倍（不足处填透明像素）。
     * 每个游戏方块承载一个 16×16 像素块，尺寸对齐后才能逐块打印。
     */
    public static BufferedImage padToBlocks(BufferedImage raw) {
        int w = (raw.getWidth() + 15) & ~15;
        int h = (raw.getHeight() + 15) & ~15;
        if (w == raw.getWidth() && h == raw.getHeight()) {
            return raw;
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.getGraphics();
        try {
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, w, h);
            g.drawImage(
                raw,
                (w - raw.getWidth()) / 2,
                (h - raw.getHeight()) / 2,
                raw.getWidth(),
                raw.getHeight(),
                null);
        } finally {
            g.dispose();
        }
        return img;
    }

    // region 打印件生成

    /**
     * 图片 → 打印件列表（自动对齐到 16 的倍数，每个 16×16 块一个 ItemStack）。
     * 全透明的块会被跳过。
     * <p>
     * 形状生成规则（与游戏内 {@code addShape(x, y, 15, x+1, y+l, 16, ...)} 等价的"贴片画"）：
     * <ul>
     * <li>每个 16×16 像素块 = 一个打印件（一个游戏方块）；
     * <li>每个不透明像素 = 方块内一个 1/16³ 体素，贴北面（z ∈ [0, 1/16]）；
     * <li>贴图固定为 {@code opencomputers:White}，颜色由 24 位 tint 染色还原；
     * <li>同一列中颜色相近（容差见 {@link Config#TOLERANCE_R}/{@link Config#TOLERANCE_G}/{@link Config#TOLERANCE_B}）
     * 的连续像素合并为一个竖条形状，alpha &le; {@link Config#ALPHA_THRESHOLD} 视为透明不生成形状。
     * </ul>
     *
     * @param label   打印件自定义名称（OC setLabel，最长 24），可为 null
     * @param tooltip 打印件鼠标提示（OC setTooltip，最长 128），可为 null
     */
    public static List<ItemStack> createPrints(BufferedImage image, String label, String tooltip) {
        BufferedImage img = padToBlocks(image);
        List<ItemStack> prints = new ArrayList<>(img.getWidth() / 16 * img.getHeight() / 16);
        for (int j = 0; j < img.getHeight(); j += 16) {
            for (int i = 0; i < img.getWidth(); i += 16) {
                ItemStack stack = createPrint(img, i, j, String.format(label, i, j), String.format(tooltip, i, j));
                if (stack != null) {
                    prints.add(stack);
                }
            }
        }
        return prints;
    }

    /** 图片字节 → 打印件列表 */
    public static List<ItemStack> createPrints(byte[] imageData, String label, String tooltip) throws IOException {
        return createPrints(loadImage(imageData), label, tooltip);
    }

    /**
     * 单个 16×16 块 → 打印件 ItemStack（含完整 PrintData NBT，可直接放入世界）。
     *
     * @param img    已对齐到 16 倍数的图片
     * @param pixelX 块左上角像素 x
     * @param pixelY 块左上角像素 y
     * @return 打印件；块内无任何不透明像素时返回 null
     */
    public static ItemStack createPrint(BufferedImage img, int pixelX, int pixelY, String label, String tooltip) {
        List<Shape> shapes = collectShapes(img, pixelX, pixelY);
        if (shapes.isEmpty()) {
            return null;
        }
        if (shapes.size() > MAX_SHAPES_PER_PRINT) {
            MyMod.LOG.debug(
                "[ImageUtils] block({},{}) has {} shapes, exceeds printer.maxShapes={};"
                    + " NBT is not capped in-game, but this print is unobtainable via printer3d%n",
                pixelX / 16,
                pixelY / 16,
                shapes.size(),
                MAX_SHAPES_PER_PRINT);
        }
        ItemStack stack = newPrintStack();
        stack.setTagCompound(createPrintNBT(shapes, label, tooltip));
        return stack;
    }

    /**
     * 逐列扫描收集形状：竖直方向合并相近颜色，透明像素跳过。
     * 图片 y 轴向下、方块 y 轴向上，行 j 起长 len 的竖条对应方块 y ∈ [16-j-len, 16-j]。
     */
    static List<Shape> collectShapes(BufferedImage img, int sx, int sy) {
        List<Shape> shapes = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16;) {
                int color = img.getRGB(sx + i, sy + j);
                if (isOpaque(color)) {
                    int len = 1;
                    while (j + len < 16 && sameColor(color, img.getRGB(sx + i, sy + j + len))) {
                        len++;
                    }
                    shapes.add(
                        new Shape(
                            (byte) i,
                            (byte) (16 - j - len),
                            (byte) 0,
                            (byte) (i + 1),
                            (byte) (16 - j),
                            (byte) 1,
                            SHAPE_TEXTURE,
                            color & 0xFFFFFF));
                    j += len;
                } else {
                    j++;
                }
            }
        }
        return shapes;
    }

    // region NBT 构造
    // 与 PrintData.save / shapeToNBT 对齐

    /**
     * 构造打印件物品的完整 NBT（即 PrintData 序列化格式）。
     * 无激活态（stateOn 为空列表），光照 {@link #PRINT_LIGHT_LEVEL}，其余开关全关。
     */
    static NBTTagCompound createPrintNBT(List<Shape> shapes, String label, String tooltip) {
        // 复刻 PrintData.setNewShapeSet 的排序（compareShape，降序），
        // 保证 NBT 与游戏内打印机产物一致（可堆叠/比较）
        List<Shape> sorted = new ArrayList<>(shapes);
        sorted.sort((a, b) -> {
            int c = Integer.compare(b.minX(), a.minX());
            if (c != 0) return c;
            c = Integer.compare(b.minY(), a.minY());
            if (c != 0) return c;
            c = Integer.compare(b.minZ(), a.minZ());
            if (c != 0) return c;
            c = Integer.compare(b.maxX(), a.maxX());
            if (c != 0) return c;
            c = Integer.compare(b.maxY(), a.maxY());
            if (c != 0) return c;
            c = Integer.compare(b.maxZ(), a.maxZ());
            if (c != 0) return c;
            // tint 降序，null 视为最小（对应 Option.empty）
            int ta = a.tint() == null ? -1 : a.tint();
            int tb = b.tint() == null ? -1 : b.tint();
            c = Integer.compare(tb, ta);
            if (c != 0) return c;
            return b.texture()
                .compareTo(a.texture());
        });

        NBTTagList stateOff = new NBTTagList();
        for (Shape shape : sorted) {
            stateOff.appendTag(shapeToNBT(shape));
        }

        NBTTagCompound nbt = new NBTTagCompound();
        if (label != null && !label.isEmpty()) {
            nbt.setString("label", label.substring(0, Math.min(label.length(), 24)));
        }
        if (tooltip != null && !tooltip.isEmpty()) {
            nbt.setString("tooltip", tooltip.substring(0, Math.min(tooltip.length(), 128)));
        }
        nbt.setBoolean("isButtonMode", false);
        nbt.setInteger("redstoneLevel", 0);
        nbt.setBoolean("pressurePlate", false);
        nbt.setTag("stateOff", stateOff);
        nbt.setTag("stateOn", new NBTTagList());
        nbt.setBoolean("isBeaconBase", false);
        nbt.setByte("lightLevel", (byte) PRINT_LIGHT_LEVEL);
        nbt.setBoolean("noclipOff", false);
        nbt.setBoolean("noclipOn", false);
        return nbt;
    }

    /** 单个形状 NBT：bounds 字节数组（×16 体素坐标）+ 贴图名 + 可选 tint */
    static NBTTagCompound shapeToNBT(Shape shape) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setByteArray(
            "bounds",
            new byte[] { shape.minX(), shape.minY(), shape.minZ(), shape.maxX(), shape.maxY(), shape.maxZ() });
        nbt.setString("texture", shape.texture());
        if (shape.tint() != null) {
            nbt.setInteger("tint", shape.tint());
        }
        return nbt;
    }

    /** 取 OC 打印件物品（OpenComputers:oc.print 的 ItemBlock），OC 未安装时抛出异常 */
    static ItemStack newPrintStack() {
        try {
            ItemStack stack = Items.get(PRINT_ITEM_NAME)
                .createItemStack(1);
            if (stack == null || stack.getItem() == null) {
                throw new IllegalStateException("OpenComputers item '" + PRINT_ITEM_NAME + "' is not registered");
            }
            return stack;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable t) {
            // NoClassDefFoundError 等：运行环境未安装 OpenComputers
            throw new IllegalStateException("OpenComputers is not installed", t);
        }
    }

    // region 颜色工具

    static boolean isOpaque(int rgb) {
        return a(rgb) > ALPHA_THRESHOLD;
    }

    /** c1 为当前段起始色，c2 为候选延续色；同色判定含透明度门槛与 RGB 容差 */
    static boolean sameColor(int c1, int c2) {
        return a(c1) > ALPHA_THRESHOLD && abs(r(c1) - r(c2)) < TOLERANCE_R
            && abs(g(c1) - g(c2)) < TOLERANCE_G
            && abs(b(c1) - b(c2)) < TOLERANCE_B;
    }

    static int a(int rgb) {
        return (rgb >> 24) & 0xFF;
    }

    static int r(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    static int g(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    static int b(int rgb) {
        return rgb & 0xFF;
    }

    static int abs(int i) {
        return i < 0 ? -i : i;
    }
}
