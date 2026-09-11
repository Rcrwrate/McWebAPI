package love.shirokasoke.webapi.webserver.handlers.ThreeD;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import com.sun.net.httpserver.HttpExchange;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class PrintSizeHandler implements RouteHandler {

    /** 请求体大小上限 32MB */
    private static final int MAX_BODY_BYTES = 32 * 1024 * 1024;

    @Override
    public String getPath() {
        return "/3d/size";
    }

    @Override
    public String getDescription() {
        return "PUT 上传图片，大致计算NBT大小";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        if (!"PUT".equals(exchange.getRequestMethod())) {
            throw new ApiException(405, "Method must be PUT");
        }
        byte[] imageData = {};
        try (InputStream is = exchange.getRequestBody()) {
            imageData = is.readAllBytes();
        }
        if (imageData.length == 0) {
            throw new ApiException(400, "Empty request body");
        }
        if (imageData.length > MAX_BODY_BYTES) {
            throw new ApiException(400, "Image too large: " + imageData.length + " bytes");
        }

        PrintUtils.checkImage(imageData);
        Map<String, String> params = parseQueryParams(exchange);
        String label = params.getOrDefault("label", "3d-print %d,%d");
        String tooltip = params.getOrDefault("tooltip", "created by love.shirokasoke.webapi");
        final List<ItemStack> prints = PrintUtils.createPrints(imageData, label, tooltip);

        ByteBuf tmpBuf = Unpooled.buffer();
        PacketBuffer pb = new PacketBuffer(tmpBuf);
        for (ItemStack is : prints) {
            pb.writeItemStackToBuffer(is);
        }
        sendResponse(
            exchange,
            mapper.createObjectNode()
                .put("size", pb.writerIndex()));
    }

}
