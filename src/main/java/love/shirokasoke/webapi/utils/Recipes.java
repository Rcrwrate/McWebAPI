package love.shirokasoke.webapi.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import love.shirokasoke.webapi.Constant;

/**
 * 配方查询工具类。
 * <p>
 * 参考 NEI 客户端查询机制（见 {@code docs/NEI/recipe.md}）：
 */
public final class Recipes {

    private static final ObjectMapper mapper = Constant.mapper;

    private Recipes() {}

    // region 物品匹配

    /**
     * 工作台合成语义的物品比较（与 {@link codechicken.nei.NEIServerUtils#areStacksSameTypeCrafting} 一致）。
     * 比较物品类型和 metadata，支持矿辞通配值（{@link OreDictionary#WILDCARD_VALUE}），
     * 并将可损耗物品视为同类；不要求 NBT 完全相等。
     */
    public static boolean areStacksSameTypeCrafting(ItemStack a, ItemStack b) {
        return a != null && b != null
            && a.getItem() != null
            && b.getItem() != null
            && a.getItem() == b.getItem()
            && (a.getItemDamage() == b.getItemDamage() || a.getItemDamage() == OreDictionary.WILDCARD_VALUE
                || b.getItemDamage() == OreDictionary.WILDCARD_VALUE
                || a.getItem()
                    .isDamageable());
    }

    /**
     * 严格物品比较（与 {@link codechicken.nei.NEIServerUtils#areStacksSameType} 一致），
     * 用于熔炉产物匹配：要求 item、metadata 与 NBT 均一致。
     */
    public static boolean areStacksSameType(ItemStack a, ItemStack b) {
        return a != null && b != null
            && a.getItem() != null
            && b.getItem() != null
            && a.getItem() == b.getItem()
            && (!b.getHasSubtypes() || b.getItemDamage() == a.getItemDamage())
            && ItemStack.areItemStackTagsEqual(b, a);
    }

    // region 重建输入

    /**
     * 从 URL 参数重建{@link ItemStack}
     * 
     * @throws IllegalArgumentException id / damage 不是合法数字时抛出
     */
    public static ItemStack parseItemStack(Map<String, String> params) {
        if (params == null || !params.containsKey("id")) {
            return null;
        }
        String idStr = params.get("id");
        if (idStr == null || idStr.isEmpty()) {
            return null;
        }

        NBTTagCompound nbt = new NBTTagCompound();
        try {
            nbt.setShort("id", Short.parseShort(idStr));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid 'id': " + idStr);
        }
        nbt.setByte("Count", (byte) 1);

        String damageStr = params.get("damage");
        if (damageStr != null && !damageStr.isEmpty()) {
            try {
                nbt.setShort("Damage", Short.parseShort(damageStr));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid 'damage': " + damageStr);
            }
        } else {
            nbt.setShort("Damage", (short) 0);
        }

        if (params.containsKey("tag")) {
            NBTTagCompound tag = NBT.readFromBase64(params.get("tag"));
            if (tag != null) {
                nbt.setTag("tag", tag);
            }
        }

        return ItemStack.loadItemStackFromNBT(nbt);
    }

    /**
     * 从 URL 参数重建{@link Fluid}
     * 
     * @param params 支持流体名称或数字
     * @return
     */
    public static Fluid parseFluid(Map<String, String> params) {
        String fluidStr = params.get("fluid");
        if (fluidStr == null || fluidStr.isEmpty()) {
            return null;
        }
        Fluid fluid = FluidRegistry.getFluid(fluidStr);
        if (fluid == null) {
            // 尝试按数字 id
            try {
                fluid = FluidRegistry.getFluid(Integer.parseInt(fluidStr));
            } catch (NumberFormatException ignored) {
                // 保持 null
            }
        }
        return fluid;
    }

    // region 配方输入展开

    /** 一个配方输入槽位的解析结果：候选物品列表 + 矿辞名称（如有） */
    public static class InputSpec {

        public final List<ItemStack> items = new ArrayList<>();
        public final List<String> ores = new ArrayList<>();

        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    /**
     * 展开配方输入对象为候选项列表。
     * 兼容 {@code null} / {@link ItemStack} / {@code ItemStack[]} /
     * {@code List<String>}（矿辞）/ {@code List<ItemStack>} 等常见输入形式。
     * 矿辞输入会通过 {@link OreDictionary#getOres} 展开成具体物品，服务端安全。
     */
    public static InputSpec expandInput(Object obj) {
        InputSpec spec = new InputSpec();
        collectInput(obj, spec);
        return spec;
    }

    private static void collectInput(Object obj, InputSpec spec) {
        if (obj == null) {
            return;
        }
        if (obj instanceof ItemStack) {
            spec.items.add(((ItemStack) obj).copy());
            return;
        }
        if (obj instanceof ItemStack[]) {
            for (ItemStack s : (ItemStack[]) obj) {
                if (s != null) {
                    spec.items.add(s.copy());
                }
            }
            return;
        }
        if (obj instanceof String) {
            addOre((String) obj, spec);
            return;
        }
        if (obj instanceof List) {
            for (Object o : (List<?>) obj) {
                collectInput(o, spec);
            }
        }
        // 其他自定义输入类型：忽略
    }

    private static void addOre(String oreName, InputSpec spec) {
        spec.ores.add(oreName);
        List<ItemStack> ores = OreDictionary.getOres(oreName);
        if (ores != null) {
            for (ItemStack s : ores) {
                if (s != null) {
                    spec.items.add(s.copy());
                }
            }
        }
    }

    /**
     * 判断 recipe 是否为 NEI 支持的工作台配方类型（有序/无序合成）
     * <p>
     * 与 NEI 的 ShapedRecipeHandler / ShapelessRecipeHandler 覆盖范围一致
     * 
     * @apiNote 矿辞配方优先判断，避免部分实现同时匹配基类时被误判为原版配方
     */
    public static boolean isCraftingRecipe(IRecipe recipe) {
        return recipe instanceof ShapedOreRecipe || recipe instanceof ShapelessOreRecipe
            || recipe instanceof ShapedRecipes
            || recipe instanceof ShapelessRecipes;
    }

    /**
     * 将工作台配方序列化为 JSON DTO（不含任何 GUI 坐标/渲染状态）。
     */
    public static ObjectNode dumpCraftingRecipe(IRecipe recipe) {
        ObjectNode node = mapper.createObjectNode();
        node.put(
            "recipeClass",
            recipe.getClass()
                .getName());

        if (recipe instanceof ShapedOreRecipe shaped) {
            dumpShaped(shapedOreWidth(shaped), shapedOreHeight(shaped), shaped.getInput(), node);
        } else if (recipe instanceof ShapelessOreRecipe) {
            dumpShapeless(((ShapelessOreRecipe) recipe).getInput(), node);
        } else if (recipe instanceof ShapedRecipes shaped) {
            dumpShaped(shaped.recipeWidth, shaped.recipeHeight, shaped.recipeItems, node);
        } else if (recipe instanceof ShapelessRecipes) {
            dumpShapeless(((ShapelessRecipes) recipe).recipeItems, node);
        } else {
            node.put("type", "unknown");
        }

        dumpOutput(recipe.getRecipeOutput(), node);
        return node;
    }

    private static void dumpShaped(int width, int height, Object[] items, ObjectNode node) {
        node.put("type", "shaped");
        node.put("shapeless", false);

        if (width > 0 && height > 0 && items != null) {
            node.put("width", width);
            node.put("height", height);
            ArrayNode grid = mapper.createArrayNode();
            for (int y = 0; y < height; y++) {
                ArrayNode row = mapper.createArrayNode();
                for (int x = 0; x < width; x++) {
                    int i = y * width + x;
                    Object input = i < items.length ? items[i] : null;
                    InputSpec spec = expandInput(input);
                    row.add(spec.isEmpty() ? null : dumpInputSpec(spec));
                }
                grid.add(row);
            }
            node.set("grid", grid);
        } else {
            // 无法还原网格（如 ShapedOreRecipe 反射取宽高失败），退化为平铺列表
            node.put("width", -1);
            node.put("height", -1);
            ArrayNode arr = mapper.createArrayNode();
            if (items != null) {
                for (Object o : items) {
                    InputSpec spec = expandInput(o);
                    if (!spec.isEmpty()) {
                        arr.add(dumpInputSpec(spec));
                    }
                }
            }
            node.set("ingredients", arr);
        }
    }

    private static void dumpShapeless(List<?> items, ObjectNode node) {
        node.put("type", "shapeless");
        node.put("shapeless", true);
        ArrayNode arr = mapper.createArrayNode();
        if (items != null) {
            for (Object o : items) {
                if (o == null) {
                    continue;
                }
                InputSpec spec = expandInput(o);
                if (!spec.isEmpty()) {
                    arr.add(dumpInputSpec(spec));
                }
            }
        }
        node.set("ingredients", arr);
    }

    /**
     * 安全的 ItemStack 序列化：单个物品序列化失败（如 getDisplayName 抛异常）
     * 时回退到基础信息，避免整个配方查询失败。
     */
    public static ObjectNode safeDumpItemStack(ItemStack stack) {
        try {
            return Items.dump(stack);
        } catch (Throwable e) {
            ObjectNode fallback = Items.dump(stack.getItem());
            fallback.put("damage", stack.getItemDamage());
            fallback.put("stackSize", stack.stackSize);
            fallback.put(
                "dumpError",
                e.getClass()
                    .getSimpleName() + ": "
                    + e.getMessage());
            return fallback;
        }
    }

    private static ObjectNode dumpInputSpec(InputSpec spec) {
        ObjectNode input = mapper.createObjectNode();
        if (!spec.ores.isEmpty()) {
            ArrayNode ores = mapper.createArrayNode();
            for (String o : new LinkedHashSet<>(spec.ores)) {
                ores.add(o);
            }
            input.set("ores", ores);
        }
        ArrayNode items = mapper.createArrayNode();
        for (ItemStack s : spec.items) {
            if (s == null || s.getItem() == null) {
                continue;
            }
            ObjectNode item = safeDumpItemStack(s);
            item.put("stackSize", s.stackSize);
            items.add(item);
        }
        input.set("items", items);
        return input;
    }

    private static void dumpOutput(ItemStack output, ObjectNode node) {
        if (output == null || output.getItem() == null) {
            node.putNull("output");
            return;
        }
        ObjectNode out = safeDumpItemStack(output);
        out.put("stackSize", output.stackSize);
        node.set("output", out);
    }

    /**
     * 判断工作台配方是否使用了指定物品作为原料。
     * 匹配输入槽位的全部候选项（矿辞输入会展开后匹配）。
     */
    public static boolean recipeUses(IRecipe recipe, ItemStack query) {
        if (recipe == null || query == null) {
            return false;
        }
        if (recipe instanceof ShapedOreRecipe) {
            return inputsContain(((ShapedOreRecipe) recipe).getInput(), query);
        } else if (recipe instanceof ShapelessOreRecipe) {
            return inputsContain(((ShapelessOreRecipe) recipe).getInput(), query);
        } else if (recipe instanceof ShapedRecipes) {
            return inputsContain(((ShapedRecipes) recipe).recipeItems, query);
        } else if (recipe instanceof ShapelessRecipes) {
            return inputsContain(((ShapelessRecipes) recipe).recipeItems, query);
        }
        return false;
    }

    private static boolean inputsContain(Object[] inputs, ItemStack query) {
        if (inputs == null) {
            return false;
        }
        for (Object o : inputs) {
            if (inputMatches(o, query)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inputsContain(List<?> inputs, ItemStack query) {
        if (inputs == null) {
            return false;
        }
        for (Object o : inputs) {
            if (inputMatches(o, query)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inputMatches(Object o, ItemStack query) {
        if (o instanceof ItemStack) {
            return areStacksSameTypeCrafting((ItemStack) o, query);
        }
        if (o instanceof ItemStack[]) {
            for (ItemStack s : (ItemStack[]) o) {
                if (s != null && areStacksSameTypeCrafting(s, query)) {
                    return true;
                }
            }
            return false;
        }
        if (o instanceof List) {
            for (Object e : (List<?>) o) {
                if (e instanceof String) {
                    // 矿辞输入：展开后匹配具体物品
                    List<ItemStack> ores = OreDictionary.getOres((String) e);
                    if (ores != null) {
                        for (ItemStack s : ores) {
                            if (s != null && areStacksSameTypeCrafting(s, query)) {
                                return true;
                            }
                        }
                    }
                } else if (inputMatches(e, query)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    // region ShapedOreRecipe 宽高反射

    private static int shapedOreWidth(ShapedOreRecipe recipe) {
        return getIntField(recipe, "width");
    }

    private static int shapedOreHeight(ShapedOreRecipe recipe) {
        return getIntField(recipe, "height");
    }

    /**
     * 反射读取 int 字段，支持按字段名精确查找，失败后按名称包含关系扫描。
     * 返回 -1 表示未找到。
     */
    private static int getIntField(Object obj, String... names) {
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            for (String name : names) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.getInt(obj);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    // 继续查找
                }
            }
            c = c.getSuperclass();
        }
        // 兜底：按名称包含关系扫描
        for (String name : names) {
            Class<?> cc = obj.getClass();
            while (cc != null && cc != Object.class) {
                for (Field f : cc.getDeclaredFields()) {
                    if (f.getType() == int.class && f.getName()
                        .toLowerCase()
                        .contains(name.toLowerCase())) {
                        try {
                            f.setAccessible(true);
                            return f.getInt(obj);
                        } catch (IllegalAccessException e) {
                            // 忽略
                        }
                    }
                }
                cc = cc.getSuperclass();
            }
        }
        return -1;
    }
}
