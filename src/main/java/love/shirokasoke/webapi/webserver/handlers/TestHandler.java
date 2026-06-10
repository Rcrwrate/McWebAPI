package love.shirokasoke.webapi.webserver.handlers;

import net.minecraft.nbt.NBTTagList;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.log;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class TestHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/test";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        NBTTagList nbt = new NBTTagList();
        log.debugFields(nbt);
        log.debugMethods(nbt);
        throw new Error(200, "");
    }
}
