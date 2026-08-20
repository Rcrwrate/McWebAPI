package love.shirokasoke.webapi.webserver.handlers.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import love.shirokasoke.webapi.MyMod;
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
 * <p>
 * 直接遍历全配方表快照（{@link RecipeMap#getAllRecipes}）。
 */
public class GTRecipesHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/recipes/gt";
    }

    @Override
    public String getDescription() {
        return "查询 GT5 机器配方。参数：type=output|input（默认 output），id/damage/tag 指定查询物品，"
            + "fluid 指定查询流体（名称或 id），map 限定配方表（机器）；仅当指定 map 时允许省略物品/流体查询返回全部，"
            + "否则必须提供物品或流体查询。limit 限制数量，offset 分页偏移。";
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
            Collection<GTRecipe> recipes;
            try {
                recipes = map.getAllRecipes();
            } catch (Throwable t) {
                // 个别配方表在构造/遍历时可能抛异常，跳过不影响整体查询
                MyMod.LOG.warn("GT recipe map '{}' getAllRecipes failed: {}", map.unlocalizedName, t.toString());
                continue;
            }
            if (recipes == null) {
                continue;
            }
            for (GTRecipe recipe : recipes) {
                if (recipe == null) {
                    continue;
                }
                if (!matches(recipe, isOutput, itemQuery, fluidQuery)) {
                    continue;
                }
                if (matched++ < offset) {
                    continue;
                }
                data.add(dumpRecipe(map, recipe));
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
