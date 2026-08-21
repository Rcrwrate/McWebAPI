package love.shirokasoke.webapi.webserver.handlers.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.utils.Fluids;
import love.shirokasoke.webapi.utils.McAccessor;
import love.shirokasoke.webapi.utils.Recipes;
import love.shirokasoke.webapi.webserver.RouteHandler;

/**
 * GT5 配方查询接口。
 * <p>
 * 覆盖 NEI 的 {@link gregtech.nei.GTNEIDefaultHandler} 范围：遍历所有已注册的
 * {@link RecipeMap}（机器配方表），按输出/输入物品/流体或配方来源查询 {@link GTRecipe}。
 * <p>
 * 与工作台/熔炉配方不同，GT 配方按机器（RecipeMap）组织，因此每条配方额外携带
 * 其所属配方表（机器）信息。产物匹配用严格比较（item+metadata+NBT），原料匹配用
 * 合成语义比较（含矿辞通配值）
 */
public class GTRecipesHandler implements RouteHandler {

    /** 配方表快照缓存：map unlocalizedName -> 配方列表（只读） */
    private static final ConcurrentMap<String, List<GTRecipe>> RECIPES_CACHE = new ConcurrentHashMap<>();

    /** 配方序列化结果缓存（按 {@link GTRecipe} 对象身份缓存，但是它未实现 equals/hashCode） */
    private static final Map<GTRecipe, ObjectNode> SERIALIZED_CACHE = new ConcurrentHashMap<>();

    @Override
    public String getPath() {
        return "/recipes/gt";
    }

    @Override
    public String getDescription() {
        return "查询 GT5 机器配方";
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

        // 可选：限定单个配方表（机器）
        RecipeMap<?> mapFilter = null;
        String mapName = params.get("map");
        if (mapName != null && !mapName.isEmpty()) {
            mapFilter = RecipeMap.ALL_RECIPE_MAPS.get(mapName);
            if (mapFilter == null) {
                throw new ApiException(404, "recipe map not found: " + mapName);
            }
        }

        // 物品查询（可省略）
        ItemStack itemQuery = null;
        try {
            itemQuery = Recipes.parseItemStack(params);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e.getMessage());
        }

        // 流体查询（可省略）：支持流体名称或数字 id
        Fluid fluidQuery = Recipes.parseFluid(params);

        // 未指定物品/流体查询时，仅允许在限定单一配方表（map）的情况下返回该机器全部配方，
        // 避免跨全量配方表返回海量数据。
        if (itemQuery == null && fluidQuery == null && mapFilter == null) {
            throw new ApiException(
                400,
                "missing query: specify 'id'/'damage' (item), 'fluid' (fluid), or 'map' (single recipe map)");
        }

        boolean isOutput = "output".equals(type);

        // 收集候选配方表（排序保证输出稳定）
        List<RecipeMap<?>> maps = new ArrayList<>();
        if (mapFilter != null) {
            maps.add(mapFilter);
        } else {
            maps.addAll(RecipeMap.ALL_RECIPE_MAPS.values());
            maps.sort(Comparator.comparing(m -> m.unlocalizedName));
        }

        ArrayNode data = mapper.createArrayNode();
        int matched = 0;
        for (RecipeMap<?> map : maps) {
            List<GTRecipe> recipes = getRecipesSnapshot(map);
            for (GTRecipe recipe : recipes) {
                if (!matches(recipe, isOutput, itemQuery, fluidQuery)) {
                    continue;
                }
                if (matched++ < offset) {
                    continue;
                }
                data.add(dumpRecipeCached(map, recipe));
                if (data.size() >= limit) {
                    break;
                }
            }
            if (data.size() >= limit) {
                break;
            }
        }

        ObjectNode result = mapper.createObjectNode();
        result.put("type", "gt");
        result.put("queryType", type);
        result.put("count", data.size());
        result.put("offset", offset);
        if (mapFilter != null) {
            result.put("map", mapFilter.unlocalizedName);
        }
        if (itemQuery != null && itemQuery.getItem() != null) {
            result.set("query", Recipes.safeDumpItemStack(itemQuery));
        }
        if (fluidQuery != null) {
            result.set("fluid", Fluids.dump(fluidQuery));
        }
        result.set("recipes", data);

        setCache(exchange, 3600);
        sendResponse(exchange, result);
    }

    /**
     * 获取指定配方表的只读配方快照（已过滤 null 元素），带缓存，
     * 可用 {@code server.recipe.cacheRecipes} 关闭。
     * <p>
     * {@link gregtech.api.recipe.RecipeMap#getAllRecipes()} 每次调用都会经 stream 重新分配集合，缓存以避免重复开销。
     * <p>
     * 快照基于首次访问时刻的配方表，之后运行时新增的配方不会出现。
     */
    private static List<GTRecipe> getRecipesSnapshot(RecipeMap<?> map) {
        if (!Config.cacheRecipes) {
            return snapshotOf(map);
        }
        return RECIPES_CACHE.computeIfAbsent(map.unlocalizedName, key -> snapshotOf(map));
    }

    private static List<GTRecipe> snapshotOf(RecipeMap<?> map) {
        Collection<GTRecipe> all = map.getAllRecipes();
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        List<GTRecipe> snapshot = new ArrayList<>(all.size());
        for (GTRecipe recipe : all) {
            if (recipe != null) {
                snapshot.add(recipe);
            }
        }
        return snapshot;
    }

    /** 判断配方是否匹配查询条件。itemQuery 与 fluidQuery 均为空时视为“返回全部”。 */
    private static boolean matches(GTRecipe recipe, boolean isOutput, ItemStack itemQuery, Fluid fluidQuery) {
        if (itemQuery == null && fluidQuery == null) {
            return true;
        }
        if (isOutput) {
            return itemMatches(recipe.mOutputs, itemQuery) || fluidMatches(recipe.mFluidOutputs, fluidQuery);
        } else {
            return itemMatches(recipe.mInputs, itemQuery) || fluidMatches(recipe.mFluidInputs, fluidQuery);
        }
    }

    private static boolean itemMatches(ItemStack[] stacks, ItemStack query) {
        if (query == null || stacks == null) {
            return false;
        }
        for (ItemStack s : stacks) {
            if (s != null && s.getItem() != null && Recipes.areStacksSameTypeCrafting(s, query)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fluidMatches(FluidStack[] stacks, Fluid query) {
        if (query == null || stacks == null) {
            return false;
        }
        for (FluidStack s : stacks) {
            if (s != null && s.getFluid() != null && s.getFluid() == query) {
                return true;
            }
        }
        return false;
    }

    /**
     * 序列化一条 GT 配方（带缓存，可用 {@code server.recipe.cacheRecipes} 关闭）。
     * <p>
     * {@link GTRecipe} 未实现 equals/hashCode，因此按对象身份缓存；
     * 缓存的 {@link ObjectNode} 为跨请求共享的只读对象，仅用于 JSON 输出，不得原地修改。
     */
    private ObjectNode dumpRecipeCached(RecipeMap<?> map, GTRecipe recipe) {
        if (!Config.cacheRecipes) {
            return dumpRecipe(map, recipe);
        }
        ObjectNode cached = SERIALIZED_CACHE.get(recipe);
        if (cached != null) {
            return cached;
        }
        ObjectNode node = dumpRecipe(map, recipe);
        SERIALIZED_CACHE.put(recipe, node);
        return node;
    }

    /** 序列化一条 GT 配方 */
    private ObjectNode dumpRecipe(RecipeMap<?> map, GTRecipe recipe) {
        ObjectNode node = mapper.createObjectNode();
        node.put("recipeMap", map.unlocalizedName);
        node.put("recipeMapName", StatCollector.translateToLocal(map.unlocalizedName));

        node.put("duration", recipe.mDuration);
        node.put("eut", recipe.mEUt);
        node.put("amperage", map.getAmperage());

        node.put("specialValue", recipe.mSpecialValue);
        node.put("enabled", recipe.mEnabled);
        node.put("hidden", recipe.mHidden);
        node.put("fake", recipe.mFakeRecipe);

        ObjectNode inputs = node.putObject("inputs");
        inputs.set("items", dumpItemArray(recipe.mInputs));
        inputs.set("fluids", dumpFluidArray(recipe.mFluidInputs));

        ObjectNode outputs = node.putObject("outputs");
        outputs.set("items", dumpItemArray(recipe.mOutputs));
        outputs.set("fluids", dumpFluidArray(recipe.mFluidOutputs));

        // 概率相关(生成产物和消耗输入的概率)
        node.set("inputChances", dumpIntArray(recipe.mInputChances));
        node.set("outputChances", dumpIntArray(recipe.mOutputChances));
        node.set("fluidInputChances", dumpIntArray(recipe.mFluidInputChances));
        node.set("fluidOutputChances", dumpIntArray(recipe.mFluidOutputChances));

        return node;
    }

    private ArrayNode dumpItemArray(ItemStack[] stacks) {
        ArrayNode arr = mapper.createArrayNode();
        if (stacks == null) {
            return arr;
        }
        for (ItemStack s : stacks) {
            if (s == null || s.getItem() == null) {
                arr.addNull();
                continue;
            }
            ObjectNode item = Recipes.safeDumpItemStack(s);
            item.put("stackSize", s.stackSize);
            arr.add(item);
        }
        return arr;
    }

    private ArrayNode dumpFluidArray(FluidStack[] stacks) {
        ArrayNode arr = mapper.createArrayNode();
        if (stacks == null) {
            return arr;
        }
        for (FluidStack s : stacks) {
            if (s == null || s.getFluid() == null) {
                arr.addNull();
                continue;
            }
            ObjectNode fluid = Fluids.dump(s.getFluid());
            fluid.put("amount", s.amount);
            arr.add(fluid);
        }
        return arr;
    }

    private ArrayNode dumpIntArray(int[] values) {
        ArrayNode arr = mapper.createArrayNode();
        if (values == null) {
            return arr;
        }
        for (int v : values) {
            arr.add(v);
        }
        return arr;
    }
}
