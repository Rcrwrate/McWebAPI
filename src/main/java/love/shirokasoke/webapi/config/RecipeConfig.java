package love.shirokasoke.webapi.config;

import com.gtnewhorizon.gtnhlib.config.Config;

import love.shirokasoke.webapi.MyMod;

@Config(modid = MyMod.MODID, category = "server.recipe", filename = "WebAPI", configSubDirectory = "shirokasoke")
public class RecipeConfig {

    @Config.Name("indexCraft")
    @Config.Comment("Enable index for crafting recipes")
    public static boolean indexCraftingRecipes = true;

    @Config.Comment("Enable cache for recipes, it may increase memory usage")
    public static boolean cacheRecipes = true;
}
