package love.shirokasoke.webapi.server.handlers.ae2;

import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.util.IInterfaceViewable;
import appeng.core.features.registries.InterfaceTerminalRegistry;
import love.shirokasoke.webapi.server.RouteHandler;

public class AEMEsupportHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/ae/me/support";
    }

    @Override
    public String getDescription() {
        return "ME supportedClasses";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        Set<Class<? extends IInterfaceViewable>> supportedClasses = InterfaceTerminalRegistry.instance()
            .getSupportedClasses();

        ArrayNode classesArray = mapper.createArrayNode();
        for (Class<? extends IInterfaceViewable> clazz : supportedClasses) {
            classesArray.add(clazz.getName());
        }

        setCache(exchange, 86400);
        sendResponse(exchange, classesArray);
    }
}
