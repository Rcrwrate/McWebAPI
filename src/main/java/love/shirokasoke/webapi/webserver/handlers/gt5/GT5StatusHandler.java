package love.shirokasoke.webapi.webserver.handlers.gt5;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.webserver.Context;

/**
 * 按坐标查询 / 启停单台 GT5 机器。
 * <p>
 * 启停等价于用软锤右键机器，最终调用 {@link IGregTechTileEntity#enableWorking()} /
 * {@link IGregTechTileEntity#disableWorking()}，机器会在下一 tick 自行中断正在跑的配方。
 */
public class GT5StatusHandler extends GT5BaseHandler {

    private static final String ACTION_START = "start";
    private static final String ACTION_STOP = "stop";

    @Override
    public String getPath() {
        return "/gt5/status";
    }

    @Override
    public String getDescription() {
        return "Query or control a single GT5 machine. Query params: x, y, z, dim (optional, default=0). "
            + "POST with action=start|stop to enable/disable the machine (same as a soft mallet); GET returns the current state only.";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod()
            .equals("POST")) {
            throw new ApiException(405, "method must be POST");
        }
        String action = parseQueryParams(exchange).get("action");
        if (!ACTION_START.equals(action) && !ACTION_STOP.equals(action)) {
            throw new ApiException(
                400,
                "Invalid action: " + action + ", expected '" + ACTION_START + "' or '" + ACTION_STOP + "'");
        }
        Context context = GT5init(exchange);

        boolean changed = execute(context, ACTION_START.equals(action));
        sendResponse(
            exchange,
            mapper.createObjectNode()
                .put("changed", changed));

    }

    /**
     * @param action {@code start} / {@code stop} 执行启停，传 {@code null} 表示仅查询
     * @apiNote 状态变更会触发纹理同步包与多方块回调，必须回到服务端主线程执行
     */
    private boolean execute(Context context, boolean start) throws IOException {
        try {
            return ServerThreadDispatcher.callOnServerThread(() -> {
                IGregTechTileEntity igte = context.igte;
                if (!igte.canAccessData() || !(igte.getMetaTileEntity() instanceof MetaTileEntity)) {
                    throw new ApiException(409, "GT5 machine is no longer valid");
                }
                boolean changed = start != igte.isAllowedToWork();
                if (changed) {
                    if (start) {
                        igte.enableWorking();
                    } else {
                        igte.disableWorking();
                    }
                }
                return changed;
            });
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
