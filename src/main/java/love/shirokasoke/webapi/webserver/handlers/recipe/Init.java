package love.shirokasoke.webapi.webserver.handlers.recipe;

import java.util.concurrent.TimeUnit;

import cpw.mods.fml.common.Loader;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.config.RecipeConfig;
import love.shirokasoke.webapi.webserver.RouteRegistry;
import love.shirokasoke.webapi.webserver.WebServer;

public class Init {

    private static final String MODID = "programmablehatches";
    private static final long DELAY = 60L;

    public static void i() {
        RouteRegistry.register(new FurnaceRecipesHandler());
        RouteRegistry.register(CraftingRecipesHandler.INSTANCE);
        RouteRegistry.register(new GTmaps());
        RouteRegistry.register(new GTRecipesHandler());
    }

    public static void after() {
        final boolean NeedDelay = Loader.isModLoaded(MODID);
        MyMod.LOG.info("Detected {} , delay recipe indexing for {} seconds", MODID, DELAY);
        if (RecipeConfig.indexCraftingRecipes) {
            new Thread(() -> {
                if (NeedDelay) {
                    try {
                        TimeUnit.SECONDS.sleep(DELAY);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                IndexedCraftingRecipesHandler r = new IndexedCraftingRecipesHandler(
                    CraftingRecipesHandler.INSTANCE.getSortedRecipes());
                WebServer.removeRoute(r.getPath());
                WebServer.addRoute(r);
            }, "RecipeIndexer").start();
        }
    }
}
