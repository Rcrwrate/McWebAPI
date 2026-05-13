package love.shirokasoke.webapi.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ArrayListMultimap;

import love.shirokasoke.webapi.Constant;
import love.shirokasoke.webapi.MyMod;

public class Items {

    private static final ObjectMapper mapper = Constant.mapper;
    public static final ArrayListMultimap<Item, ItemStack> itemOverrides = ArrayListMultimap.create();

    public static ObjectNode dump(Item item, ObjectNode data) {
        ClassUtils.getClassInfo(item, data);
        data.put("id", Item.getIdFromItem(item));
        data.put("registryName", Item.itemRegistry.getNameForObject(item));
        data.put("UnlocalizedName", item.getUnlocalizedName());
        ItemStack stack = new ItemStack(item, 1, 0);
        try {
            data.put("localizedName", stack.getDisplayName());
        } catch (Throwable e) {
            log.e(e);
        }

        data.put("HasSubtypes", item.getHasSubtypes());
        return data;
    }

    public static ObjectNode dump(Item item) {
        return dump(item, mapper.createObjectNode());
    }

    public static ObjectNode dump(ItemStack stack, ObjectNode data) {
        dump(stack.getItem(), data);
        data.put("localizedName", stack.getDisplayName());

        data.put("MaxStackSize", stack.getMaxStackSize());
        data.put("damageable", stack.isItemStackDamageable());
        data.put("damage", stack.getItemDamage());
        data.put("UnlocalizedName", stack.getUnlocalizedName());

        data.set("AttributeModifiers", mapper.valueToTree(stack.getAttributeModifiers()));
        NBT.dump(stack.getTagCompound(), data);
        return data;
    }

    public static ObjectNode dump(ItemStack stack) {
        return dump(stack, mapper.createObjectNode());
    }

    public static List<ItemStack> getPermutations(Item item) {
        if (item == null || item.delegate.name() == null || !item.getHasSubtypes()) return null;
        final List<ItemStack> permutations = new LinkedList<>(itemOverrides.get(item));
        if (permutations.isEmpty()) {
            dumpAll(item, permutations);
            MyMod.LOG.info("{} 检测到{}个子物品", Item.itemRegistry.getNameForObject(item), permutations.size());
            itemOverrides.removeAll(item);
            itemOverrides.putAll(item, permutations);
        }
        return permutations;
    }

    private static void dumpAll(Item item, List<ItemStack> permutations) {
        ItemStack raw = new ItemStack(item, 1, 0);
        String rawName = null;
        try {
            rawName = raw.getDisplayName();
        } catch (Throwable e) {
            log.e(e);
            return;
        }

        String regName = Item.itemRegistry.getNameForObject(item);
        HashSet<String> nameSet = new HashSet<>();
        nameSet.add(rawName);
        permutations.add(raw);
        for (int meta = 1; meta < 32768; meta++) {
            ItemStack subStack = new ItemStack(item, 1, meta);
            String displayName = null;
            try {
                displayName = subStack.getDisplayName();
            } catch (Throwable e) {
                log.e(e);
                return;
            }
            // MyMod.LOG.info(displayName);
            String m = String.valueOf(meta);

            if (displayName != null && !displayName.equals("Unnamed")
                && !displayName.contains("item.null.name")
                && !displayName.contains("Disabled")
                && !displayName.endsWith(".name")
                && !displayName.endsWith("." + m)
                && !displayName.endsWith("#" + m)
                && !displayName.startsWith("Advanced Boiler Chassis")
                && !displayName.startsWith("Miniature color.")
                && nameSet.add(displayName)) {
                permutations.add(subStack);
            } else if (meta > 16 && regName.startsWith("minecraft")) {
                subStack = null;
                return;
            } else {

            }
            subStack = null;
        }
    }

    /**
     * 文件系统非法字符表。
     *
     * <p>
     * 包含 Windows / Linux / macOS 文件系统中不允许出现在文件名中的控制字符和保留符号。
     *
     * @see #cleanFileName(String)
     */
    private static final int[] ILLEGAL_CHARS = {
        // 控制字符 0x00-0x1F
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29,
        30, 31,
        // 保留符号
        34, // " 双引号
        58, // : 冒号
        60, // < 小于号
        62, // > 大于号
        42, // * 星号
        63, // ? 问号
        92, // \ 反斜杠
        47, // / 正斜杠
        124 // | 竖线
    };

    static {
        Arrays.sort(ILLEGAL_CHARS);
    }

    /**
     * 生成物品的安全文件名（不含扩展名）。
     *
     * <p>
     * 格式：{@code modid_unlocalizedName_meta_displayName}
     * <br>
     * 示例：{@code minecraft_stone_0_Stone}
     *
     * <p>
     * 会对显示名称进行以下处理：
     * <ol>
     * <li>去除 Minecraft 格式化代码（§ 颜色代码）</li>
     * <li>替换文件系统非法字符为下划线</li>
     * </ol>
     *
     * @param stack 物品栈
     * @return 清理后的安全文件名
     */
    public static String getFileName(ItemStack stack) {
        String name = stack.getItem()
            .getUnlocalizedName();
        name = EnumChatFormatting.getTextWithoutFormattingCodes(name);
        name = cleanFileName(name);
        String id = Item.itemRegistry.getNameForObject(stack.getItem());
        return id.replace(":", "_") + "_" + stack.getItemDamage() + "_" + name;
    }

    /**
     * 清理文件名中的非法字符。
     *
     * <p>
     * 遍历字符串中的每个字符，如果字符在 {@link #ILLEGAL_CHARS} 中，则替换为下划线 {@code _}。
     * 这是跨平台兼容的必要处理，因为 Windows 不允许 {@code \ / : * ? " < > |} 等字符出现在文件名中。
     *
     * @param name 原始文件名
     * @return 清理后的安全文件名
     */
    public static String cleanFileName(String name) {
        StringBuilder cleanName = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            int c = name.charAt(i);
            if (Arrays.binarySearch(ILLEGAL_CHARS, c) < 0) {
                cleanName.append((char) c);
            } else {
                cleanName.append('_');
            }
        }
        return cleanName.toString();
    }
}
