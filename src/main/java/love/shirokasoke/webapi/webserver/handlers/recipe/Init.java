package love.shirokasoke.webapi.webserver.handlers.recipe;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.webserver.RouteRegistry;
import love.shirokasoke.webapi.webserver.WebServer;

public class Init {

    public static void i() {
        RouteRegistry.register(new FurnaceRecipesHandler());
        RouteRegistry.register(CraftingRecipesHandler.INSTANCE);
        if (Config.indexCraftingRecipes) {
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
