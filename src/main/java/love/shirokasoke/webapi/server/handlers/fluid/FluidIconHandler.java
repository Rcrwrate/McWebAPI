package love.shirokasoke.webapi.server.handlers.fluid;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.RouteHandler;
import love.shirokasoke.webapi.utils.Fluids;

public class FluidIconHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/fluid/icon";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Map<String, String> params = parseQueryParams(exchange);
        if (params == null || (!params.containsKey("id") && !params.containsKey("name"))) {
            throw new Error(400, "missing query param 'id' or 'name'");
        }

        if (Config.FluidIconFolder == null || Config.FluidIconFolder.isEmpty()) {
            throw new Error(500, "FluidIconFolder not configured");
        }

        Fluid fluid = null;
        if (params.containsKey("id")) {
            fluid = FluidRegistry.getFluid(Integer.parseInt(params.get("id")));
        } else if (params.containsKey("name")) {
            fluid = FluidRegistry.getFluid(params.get("name"));
        }

        if (fluid == null) {
            throw new Error(404, "fluid not found");
        }

        String fileName = Fluids.getFileName(fluid) + ".png";
        File iconFile = new File(Config.FluidIconFolder, fileName);

        if (!iconFile.exists() || !iconFile.isFile()) {
            MyMod.LOG.warn("[FluidIconHandler] Icon not found: {}", iconFile.getAbsolutePath());
            throw new Error(404, "icon not found");
        }

        byte[] imageData = Files.readAllBytes(iconFile.toPath());
        exchange.getResponseHeaders()
            .set("Content-Type", "image/png");
        setCache(exchange, 86400);
        sendResponse(exchange, imageData);
    }
}
