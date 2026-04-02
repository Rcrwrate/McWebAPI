package love.shirokasoke.webapi.server.handlers;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.RouteHandler;

public class TestHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/test";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        Item item = Item.getItemById(4144);
        ItemStack stack = new ItemStack(item, 1, 17);
        MyMod.LOG.info(stack.getDisplayName());
        MyMod.LOG.info(stack.getItemDamage());
        throw new Error(200, "");
    }

}
