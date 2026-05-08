package love.shirokasoke.webapi.server.handlers.block;

import java.io.IOException;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpExchange;

import codechicken.multipart.TMultiPart;
import codechicken.multipart.TileMultipart;
import love.shirokasoke.webapi.utils.FMP;
import scala.collection.Iterator;

public class FMPHandler extends BlockHandler {

    @Override
    public String getPath() {
        return "/block/fmp";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI()
            .getQuery();
        coordinates co = checklist(query);

        TileMultipart mp = TileMultipart.getOrConvertTile(world, co.BlockCoord());
        if (mp == null) {
            throw new Error(404, "Not TileMultipart");
        } else {
            ArrayNode root = mapper.createArrayNode();
            Iterator<TMultiPart> it = ((scala.collection.Iterable<TMultiPart>) mp.partList()).iterator();
            while (it.hasNext()) {
                TMultiPart part = it.next();
                root.add(FMP.dump(part));
            }
            sendResponse(exchange, 200, root);
        }
    }
}
