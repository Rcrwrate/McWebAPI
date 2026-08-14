package love.shirokasoke.webapi.webserver.handlers.recipe;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.McAccessor;
import love.shirokasoke.webapi.utils.Recipes;
import love.shirokasoke.webapi.webserver.RouteHandler;

/**
 * 熔炉配方查询接口。
 * <p>
 * 覆盖 NEI 的 {@link codechicken.nei.recipe.FurnaceRecipeHandler}
 * 产物匹配用严格比较（item+metadata+NBT），原料匹配用合成语义比较。
 */
public class FurnaceRecipesHandler implements RouteHandler {

    /** 已排序的配方快照（惰性初始化，初始化后只读） */
    private volatile List<Map.Entry<ItemStack, ItemStack>> cachedSorted;

    @Override
    public String getPath() {
        return "/recipes/furnace";
    }

    @Override
    public String getDescription() {
        return "查询熔炉熔炼配方。参数：type=output|input（默认 output），id/damage/tag 指定查询物品（省略时返回全部），limit 限制数量，offset 分页偏移。";
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

        List<Map.Entry<ItemStack, ItemStack>> smelting = getSortedRecipes();

        boolean isOutput = "output".equals(type);
        ArrayNode data = mapper.createArrayNode();
        int matched = 0;
        for (Map.Entry<ItemStack, ItemStack> entry : smelting) {
            ItemStack input = entry.getKey();
            ItemStack output = entry.getValue();

            if (query != null) {
                boolean matches = isOutput ? Recipes.areStacksSameType(output, query)
                    : Recipes.areStacksSameTypeCrafting(input, query);
                if (!matches) {
                    continue;
                }
            }

            if (matched++ < offset) {
                continue;
            }

            ObjectNode recipe = mapper.createObjectNode();
            recipe.set("input", dumpStack(input));
            recipe.set("output", dumpStack(output));
            data.add(recipe);

            if (data.size() >= limit) {
                break;
            }
        }

        ObjectNode result = mapper.createObjectNode();
        result.put("type", "furnace");
        result.put("queryType", type);
        result.put("count", data.size());
        result.put("offset", offset);
        if (query != null && query.getItem() != null) {
            result.set("query", Recipes.safeDumpItemStack(query));
        }
        result.set("recipes", data);

        setCache(exchange, 3600);
        sendResponse(exchange, result);
    }

    /**
     * 获取排序后的配方快照
     * <p>
     * 排序键：产物 -> 原料
     */
    private List<Map.Entry<ItemStack, ItemStack>> getSortedRecipes() {
        List<Map.Entry<ItemStack, ItemStack>> local = cachedSorted;
        if (local == null) {
            synchronized (this) {
                local = cachedSorted;
                if (local == null) {
                    local = buildSorted();
                    cachedSorted = local;
                }
            }
        }
        return local;
    }

    private static List<Map.Entry<ItemStack, ItemStack>> buildSorted() {
        Map<ItemStack, ItemStack> smelting = FurnaceRecipes.smelting()
            .getSmeltingList();
        List<Map.Entry<ItemStack, ItemStack>> list = new ArrayList<>(smelting.size());
        for (Map.Entry<ItemStack, ItemStack> entry : smelting.entrySet()) {
            ItemStack input = entry.getKey();
            ItemStack output = entry.getValue();
            if (input == null || input.getItem() == null || output == null || output.getItem() == null) {
                continue;
            }
            // 拷贝一份键值对，避免直接引用底层 map 的 entry，但不创建新的ItemStack副本
            list.add(new AbstractMap.SimpleImmutableEntry<>(input, output));
        }
        list.sort(
            Comparator.comparing(FurnaceRecipesHandler::getKeyName)
                .thenComparing(FurnaceRecipesHandler::getValueName));
        return list;
    }

    private static String getKeyName(Map.Entry<ItemStack, ItemStack> e) {
        return e.getKey()
            .toString();
    }

    private static String getValueName(Map.Entry<ItemStack, ItemStack> e) {
        return e.getValue()
            .toString();
    }

    private ObjectNode dumpStack(ItemStack stack) {
        ObjectNode node = Recipes.safeDumpItemStack(stack);
        node.put("stackSize", stack.stackSize);
        return node;
    }
}
