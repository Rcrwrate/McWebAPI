package love.shirokasoke.webapi.webserver.handlers.recipe;

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
                IndexedCraftingRecipesHandler r = new IndexedCraftingRecipesHandler(
                    CraftingRecipesHandler.INSTANCE.getSortedRecipes());
                WebServer.removeRoute(r.getPath());
                WebServer.addRoute(r);
                return;
            }, "RecipeIndexer").start();
        }
    }
}
