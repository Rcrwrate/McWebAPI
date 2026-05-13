package love.shirokasoke.webapi.server.handlers.ae2;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.networking.IGridNode;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IInterfaceViewable;
import appeng.core.features.registries.InterfaceTerminalRegistry;
import love.shirokasoke.webapi.utils.Pattern;

public class AEMEsHandler extends AEBaseHandler {

    @Override
    public String getPath() {
        return "/ae/mes";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        AEinit(exchange);
        Map<String, String> params = parseQueryParams(exchange);

        // 获取可被ME接口终端查看的机器类型列表
        Set<Class<? extends IInterfaceViewable>> supportedClasses = InterfaceTerminalRegistry.instance()
            .getSupportedClasses();
        ArrayNode interfaces = mapper.createArrayNode();
        for (Class<? extends IInterfaceViewable> clazz : supportedClasses) {
            for (IGridNode node : grid.getMachines(clazz)) {
                IInterfaceViewable machine = (IInterfaceViewable) node.getMachine();
                ObjectNode iface = interfaces.addObject()
                    .put("display", machine.shouldDisplay())
                    .put("name", machine.getName())
                    .put("active", node.isActive())
                    .put("allowsPatternOptimization", machine.allowsPatternOptimization())
                    .put("playerID", node.getPlayerID());

                DimensionalCoord loc = machine.getLocation();
                iface.putObject("location")
                    .put("x", loc.x)
                    .put("y", loc.y)
                    .put("z", loc.z)
                    .put("dimension", loc.getDimension());

                ArrayNode patterns = iface.putArray("patterns");
                IInventory patternInv = machine.getPatterns();
                if (patternInv != null) {
                    for (int i = 0; i < patternInv.getSizeInventory(); i++) {
                        ItemStack patternStack = patternInv.getStackInSlot(i);
                        if (patternStack != null) {
                            ObjectNode pattern = Pattern.dump(
                                patternStack,
                                params.getOrDefault("load", "false")
                                    .equalsIgnoreCase("true"),
                                params.getOrDefault("world", "false")
                                    .equalsIgnoreCase("true") ? loc.getWorld() : null);
                            pattern.put("slot", i);
                            patterns.add(pattern);
                        }
                    }
                }
            }
        }

        sendResponse(exchange, interfaces);
    }
}
