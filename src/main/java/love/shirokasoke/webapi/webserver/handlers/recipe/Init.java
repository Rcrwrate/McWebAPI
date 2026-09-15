package love.shirokasoke.webapi.webserver.handlers.recipe;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.minecraft.item.crafting.IRecipe;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.config.RecipeConfig;
import love.shirokasoke.webapi.webserver.RouteRegistry;
import love.shirokasoke.webapi.webserver.WebServer;

public class Init {

    public static void i() {
        RouteRegistry.register(new FurnaceRecipesHandler());
        RouteRegistry.register(CraftingRecipesHandler.INSTANCE);
        RouteRegistry.register(new GTmaps());
        RouteRegistry.register(new GTRecipesHandler());
    }

    public static void after() {
        if (RecipeConfig.indexCraftingRecipes) {
            new Thread(() -> {
                try {
                    // 延迟加载
                    TimeUnit.SECONDS.sleep(60);
                } catch (InterruptedException e) {}
                List<IRecipe> recipes = null;
                int count = 0;
                while (recipes == null && count < 3) {
                    count++;
                    try {
                        recipes = CraftingRecipesHandler.INSTANCE.getSortedRecipes();
                    } catch (ConcurrentModificationException e) {
                        MyMod.LOG.warn("CME when index the recipes, try 60s later {} times", count);
                        try {
                            TimeUnit.SECONDS.sleep(60);
                        } catch (InterruptedException _) {}
                    }
                }
                IndexedCraftingRecipesHandler r = new IndexedCraftingRecipesHandler(recipes);
                WebServer.removeRoute(r.getPath());
                WebServer.addRoute(r);
            }, "RecipeIndexer").start();
        }
    }
}
