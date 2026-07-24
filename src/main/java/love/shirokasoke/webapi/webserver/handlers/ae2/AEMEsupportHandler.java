package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.AEApi;
import appeng.api.util.IInterfaceViewable;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class AEMEsupportHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/ae/me/support";
    }

    @Override
    public String getDescription() {
        return "ME supportedClasses";
    }

    private Set<Class<? extends IInterfaceViewable>> supportedClasses = null;

    @Override
    public void run(HttpExchange exchange) throws IOException {
        if (supportedClasses == null) {
            supportedClasses = AEApi.instance()
                .registries()
                .interfaceTerminal()
                .getSupportedClasses();
        }

        ArrayNode classesArray = mapper.createArrayNode();
        for (Class<? extends IInterfaceViewable> clazz : supportedClasses) {
            classesArray.add(clazz.getName());
        }

        setCache(exchange, 86400);
        sendResponse(exchange, classesArray);
    }
}
