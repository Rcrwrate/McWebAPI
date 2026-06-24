package love.shirokasoke.webapi.webserver.handlers.item;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Optional;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import appeng.api.util.AEColor;
import appeng.api.util.AEColoredItemDefinition;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.log;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class AEHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/items/ae";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.set(
            "items",
            collect(
                AEApi.instance()
                    .definitions()
                    .items()));
        root.set(
            "parts",
            collect(
                AEApi.instance()
                    .definitions()
                    .parts()));
        root.set(
            "materials",
            collect(
                AEApi.instance()
                    .definitions()
                    .materials()));
        root.set(
            "blocks",
            collect(
                AEApi.instance()
                    .definitions()
                    .blocks()));
        setCache(exchange, 86400);
        sendResponse(exchange, root);
    }

    private ArrayNode collect(Object definitions) {
        ArrayNode array = mapper.createArrayNode();
        if (definitions == null) {
            return array;
        }
        for (Method m : definitions.getClass()
            .getMethods()) {
            if (m.getParameterTypes().length != 0) {
                continue;
            }
            String name = m.getName();
            Object result;
            try {
                result = m.invoke(definitions);
            } catch (Throwable e) {
                continue;
            }
            if (result == null) {
                continue;
            }

            List<ItemStack> stacks = new ArrayList<>();

            if (result instanceof AEColoredItemDefinition) {
                AEColoredItemDefinition def = (AEColoredItemDefinition) result;
                for (AEColor color : AEColor.values()) {
                    ItemStack stack = def.stack(color, 1);
                    if (stack != null) {
                        stacks.add(stack);
                    }
                }
            } else if (result instanceof IItemDefinition) {
                IItemDefinition def = (IItemDefinition) result;
                if (def.isEnabled()) {
                    Optional<ItemStack> opt = def.maybeStack(1);
                    if (opt.isPresent()) {
                        stacks.add(opt.get());
                    }
                }
            }

            for (ItemStack stack : stacks) {
                try {
                    ObjectNode node = Items.dump(stack);
                    node.put("name", name);
                    array.add(node);
                } catch (java.lang.NullPointerException ex) {
                    // log.debugFields(stack);
                    // log.debugMethods(stack);
                    log.e(ex);
                }
            }
        }
        return array;
    }
}
