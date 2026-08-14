package love.shirokasoke.webapi.webserver.handlers.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.McAccessor;
import love.shirokasoke.webapi.utils.Recipes;
import love.shirokasoke.webapi.webserver.RouteHandler;

/**
 * 工作台合成表查询接口。
 * <p>
 * 覆盖 NEI 的 {@link codechicken.nei.recipe.ShapedRecipeHandler} / {@link codechicken.nei.recipe.ShapelessRecipeHandler}
 * 范围：
 * 有序合成（{@code ShapedRecipes} / {@code ShapedOreRecipe}）与
 * 无序合成（{@code ShapelessRecipes} / {@code ShapelessOreRecipe}）。
 */
public class CraftingRecipesHandler implements RouteHandler {

    public static CraftingRecipesHandler INSTANCE = new CraftingRecipesHandler();

    /** 已排序的配方快照（惰性初始化，初始化后只读） */
    private volatile List<IRecipe> cachedSorted;

    private CraftingRecipesHandler() {}

    @Override
    public String getPath() {
        return "/recipes/crafting";
    }

    @Override
    public String getDescription() {
        return "查询工作台合成配方（有序/无序）。参数：type=output|input（默认 output），id/damage/tag 指定查询物品（省略时返回全部），limit 限制数量，offset 分页偏移。";
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

        List<IRecipe> recipes = getSortedRecipes();

        boolean isOutput = "output".equals(type);
        ArrayNode data = mapper.createArrayNode();
        int matched = 0;
        for (IRecipe recipe : recipes) {
            if (query != null) {
                boolean matches = isOutput ? Recipes.areStacksSameTypeCrafting(recipe.getRecipeOutput(), query)
                    : Recipes.recipeUses(recipe, query);
                if (!matches) {
                    continue;
                }
            }

            if (matched++ < offset) {
                continue;
            }

            data.add(Recipes.dumpCraftingRecipe(recipe));

            if (data.size() >= limit) {
                break;
            }
        }

        ObjectNode result = mapper.createObjectNode();
        result.put("type", "crafting");
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
     */
    public List<IRecipe> getSortedRecipes() {
        List<IRecipe> local = cachedSorted;
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

    private static List<IRecipe> buildSorted() {
        List<IRecipe> all = CraftingManager.getInstance()
            .getRecipeList();
        List<IRecipe> list = new ArrayList<>(all.size());
        for (IRecipe recipe : all) {
            if (recipe != null && Recipes.isCraftingRecipe(recipe)) {
                list.add(recipe);
            }
        }
        list.sort(Comparator.comparing(CraftingRecipesHandler::outputKey));
        return list;
    }

    private static String outputKey(IRecipe recipe) {
        ItemStack output = recipe.getRecipeOutput();
        if (output == null || output.getItem() == null) {
            return "null";
        }
        return output.toString();
    }
}
