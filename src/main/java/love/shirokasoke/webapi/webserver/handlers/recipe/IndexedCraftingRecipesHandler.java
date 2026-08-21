package love.shirokasoke.webapi.webserver.handlers.recipe;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import love.shirokasoke.webapi.utils.McAccessor;
import love.shirokasoke.webapi.utils.Recipes;
import love.shirokasoke.webapi.webserver.RouteHandler;

/**
 * 工作台合成表查询接口（预构建缓存版）。
 * <p>
 * 覆盖范围与 {@link CraftingRecipesHandler} 一致（有序/无序合成），
 * 但首次访问时通过 {@link #buildIndex} 为合成表输入/输出的每一个物品
 * 预先创建索引，查询时按物品直接定位候选配方，避免全表逐条匹配。
 * <p>
 * 索引不保存实质合成表，只保存配方在有序快照中的位置下标（{@link IntArrayList}）。
 * 与排序快照一样，索引基于构建时刻的配方表与矿辞表快照，后续动态注册不会触发重建。
 */
public class IndexedCraftingRecipesHandler implements RouteHandler {

    private final List<IRecipe> recipes;
    /** 预构建缓存（惰性初始化，初始化后只读） */
    private final RecipeIndex index;

    public IndexedCraftingRecipesHandler(List<IRecipe> recipes) {
        this.recipes = recipes;
        this.index = buildIndex(recipes);
    }

    @Override
    public String getPath() {
        return "/recipes/crafting";
    }

    @Override
    public String getDescription() {
        return "查询工作台合成配方（有序/无序，预构建物品索引版）。参数：type=output|input（默认 output），id/damage/tag 指定查询物品（省略时返回全部），limit 限制数量，offset 分页偏移。";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        McAccessor.getServer();

        Map<String, String> params = parseQueryParams(exchange);

        String type = params.getOrDefault("type", "output");

        if (!"output".equals(type) && !"input".equals(type)) {
            throw new ApiException(400, "invalid 'type': " + type + " (expected 'output' or 'input')");
        }

        int limit = 200;
        String limitStr = params.get("limit");
        if (limitStr != null && !limitStr.isEmpty()) {
            try {
                limit = Integer.parseInt(limitStr);
            } catch (NumberFormatException e) {
                throw new ApiException(400, "invalid 'limit': " + limitStr);
            }
        }
        if (limit < 1 || limit > 5000) {
            limit = 200;
        }

        int offset = 0;
        String offsetStr = params.get("offset");
        if (offsetStr != null && !offsetStr.isEmpty()) {
            try {
                offset = Integer.parseInt(offsetStr);
            } catch (NumberFormatException e) {
                throw new ApiException(400, "invalid 'offset': " + offsetStr);
            }
        }
        if (offset < 0) {
            offset = 0;
        }

        ItemStack query;
        try {
            query = Recipes.parseItemStack(params);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e.getMessage());
        }

        ArrayNode data = mapper.createArrayNode();
        int total;
        if (query == null) {
            // 无查询物品：直接对快照分页
            total = recipes.size();
            int start = Math.min(offset, total);
            int end = Math.min(start + limit, total);
            for (int i = start; i < end; i++) {
                data.add(Recipes.dumpCraftingRecipeCached(recipes.get(i)));
            }
        } else if (query.getItem() == null) {
            total = 0;
        } else {
            // 索引下标升序即快照顺序，直接按下标分页
            IntArrayList matched = index.lookup("output".equals(type), query);
            total = matched.size();
            int start = Math.min(offset, total);
            int end = Math.min(start + limit, total);
            for (int i = start; i < end; i++) {
                data.add(Recipes.dumpCraftingRecipeCached(recipes.get(matched.getInt(i))));
            }
        }

        ObjectNode result = mapper.createObjectNode();
        result.put("type", "crafting");
        result.put("queryType", type);
        result.put("count", data.size());
        result.put("offset", offset);
        result.put("total", total);
        if (query != null && query.getItem() != null) {
            result.set("query", Recipes.safeDumpItemStack(query));
        }
        result.set("recipes", data);

        setCache(exchange, 3600);
        sendResponse(exchange, result);
    }

    /**
     * 为合成表输入/输出的每一个物品预先创建索引。
     * <p>
     * 索引不保存实质合成表，只保存配方在 {@code recipes} 中的位置下标（{@link IntArrayList}）。
     * 每个物品登记两级索引：
     * <ul>
     * <li>exact：key = (itemId, damage)，覆盖 damage 精确匹配与配方侧通配值匹配</li>
     * <li>perItem：key = itemId，聚合该物品全部 damage，覆盖可损耗物品与查询侧通配匹配</li>
     * </ul>
     * 矿辞输入通过 {@link Recipes#expandInput} 展开成具体物品后登记；
     * 同一配方内重复出现的同一物品只登记一次。
     */
    private static RecipeIndex buildIndex(List<IRecipe> recipes) {
        RecipeIndex index = new RecipeIndex();
        for (int i = 0; i < recipes.size(); i++) {
            IRecipe recipe = recipes.get(i);

            // 输出物品索引
            ItemStack output = recipe.getRecipeOutput();
            if (output != null && output.getItem() != null) {
                add(index.outputExact, pack(output), i);
                add(index.outputPerItem, itemId(output), i);
            }

            // 输入物品索引（同一配方内按物品去重）
            final int position = i;
            LongOpenHashSet seenKeys = new LongOpenHashSet();
            IntOpenHashSet seenItems = new IntOpenHashSet();
            forEachInput(recipe, stack -> {
                int id = Item.getIdFromItem(stack.getItem());
                long key = pack(id, stack.getItemDamage());
                if (seenKeys.add(key)) {
                    add(index.inputExact, key, position);
                }
                if (seenItems.add(id)) {
                    add(index.inputPerItem, id, position);
                }
            });
        }
        return index;
    }

    /** 遍历配方的全部输入物品（矿辞输入展开为具体物品） */
    private static void forEachInput(IRecipe recipe, Consumer<ItemStack> consumer) {
        if (recipe instanceof ShapedOreRecipe) {
            forEachInput(((ShapedOreRecipe) recipe).getInput(), consumer);
        } else if (recipe instanceof ShapelessOreRecipe) {
            forEachInput(((ShapelessOreRecipe) recipe).getInput(), consumer);
        } else if (recipe instanceof ShapedRecipes) {
            forEachInput(((ShapedRecipes) recipe).recipeItems, consumer);
        } else if (recipe instanceof ShapelessRecipes) {
            forEachInput(((ShapelessRecipes) recipe).recipeItems, consumer);
        }
    }

    private static void forEachInput(Object[] inputs, Consumer<ItemStack> consumer) {
        if (inputs == null) {
            return;
        }
        for (Object input : inputs) {
            expand(input, consumer);
        }
    }

    private static void forEachInput(List<?> inputs, Consumer<ItemStack> consumer) {
        if (inputs == null) {
            return;
        }
        for (Object input : inputs) {
            expand(input, consumer);
        }
    }

    private static void expand(Object input, Consumer<ItemStack> consumer) {
        if (input == null) {
            return;
        }
        Recipes.InputSpec spec = Recipes.expandInput(input);
        for (ItemStack stack : spec.items) {
            if (stack != null && stack.getItem() != null) {
                consumer.accept(stack);
            }
        }
    }

    /** 打包 (itemId, damage) 为索引 key */
    private static long pack(int itemId, int damage) {
        return ((long) itemId << 32) | (damage & 0xFFFFL);
    }

    private static long pack(ItemStack stack) {
        return pack(Item.getIdFromItem(stack.getItem()), stack.getItemDamage());
    }

    private static int itemId(ItemStack stack) {
        return Item.getIdFromItem(stack.getItem());
    }

    private static void add(Long2ObjectOpenHashMap<IntArrayList> map, long key, int position) {
        IntArrayList list = map.get(key);
        if (list == null) {
            list = new IntArrayList(4);
            map.put(key, list);
        }
        list.add(position);
    }

    private static void add(Int2ObjectOpenHashMap<IntArrayList> map, int key, int position) {
        IntArrayList list = map.get(key);
        if (list == null) {
            list = new IntArrayList(4);
            map.put(key, list);
        }
        list.add(position);
    }

    /**
     * 物品 -> 配方位置下标的索引
     */
    private static final class RecipeIndex {

        /** 输出物品精确索引：(itemId, damage) -> 配方下标 */
        final Long2ObjectOpenHashMap<IntArrayList> outputExact = new Long2ObjectOpenHashMap<>();
        /** 输出物品聚合索引（忽略 damage）：itemId -> 配方下标 */
        final Int2ObjectOpenHashMap<IntArrayList> outputPerItem = new Int2ObjectOpenHashMap<>();
        /** 输入物品精确索引：(itemId, damage) -> 配方下标 */
        final Long2ObjectOpenHashMap<IntArrayList> inputExact = new Long2ObjectOpenHashMap<>();
        /** 输入物品聚合索引（忽略 damage）：itemId -> 配方下标 */
        final Int2ObjectOpenHashMap<IntArrayList> inputPerItem = new Int2ObjectOpenHashMap<>();

        /**
         * 按 {@link Recipes#areStacksSameTypeCrafting} 语义收集候选配方下标（升序、去重）。
         *
         * @param output true 查输出物品索引，false 查输入物品索引
         */
        IntArrayList lookup(boolean output, ItemStack query) {
            Long2ObjectOpenHashMap<IntArrayList> exact = output ? outputExact : inputExact;
            Int2ObjectOpenHashMap<IntArrayList> perItem = output ? outputPerItem : inputPerItem;

            int itemId = Item.getIdFromItem(query.getItem());
            int damage = query.getItemDamage();

            IntOpenHashSet merged = new IntOpenHashSet();
            if (damage == OreDictionary.WILDCARD_VALUE) {
                // 查询侧 damage 为通配值：该物品任意 damage 均匹配
                addAll(merged, perItem.get(itemId));
            } else {
                // damage 精确匹配
                addAll(merged, exact.get(pack(itemId, damage)));
                // 配方侧 damage 为通配值
                addAll(merged, exact.get(pack(itemId, OreDictionary.WILDCARD_VALUE)));
                // 可损耗物品视为同类（无视 damage）
                if (query.getItem()
                    .isDamageable()) {
                    addAll(merged, perItem.get(itemId));
                }
            }

            IntArrayList result = new IntArrayList(merged);
            result.sort(IntComparators.NATURAL_COMPARATOR);
            return result;
        }

        private static void addAll(IntOpenHashSet set, IntArrayList list) {
            if (list != null) {
                set.addAll(list);
            }
        }
    }
}
