package love.shirokasoke.webapi.webserver.handlers.block;

import java.io.IOException;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpExchange;

import codechicken.multipart.TMultiPart;
import codechicken.multipart.TileMultipart;
import love.shirokasoke.webapi.utils.FMP;
import love.shirokasoke.webapi.webserver.Context;
import love.shirokasoke.webapi.webserver.RouteHandler;
import scala.collection.Iterator;

public class FMPHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/block/fmp";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        Context con = new Context(getCoordinates(exchange)).initServer()
            .initWorld()
            .checkblockExists();

        TileMultipart mp = TileMultipart.getOrConvertTile(con.world, con.co.BlockCoord());
        if (mp == null) {
            throw new ApiException(404, "Not TileMultipart");
        } else {
            ArrayNode root = mapper.createArrayNode();
            Iterator<TMultiPart> it = ((scala.collection.Iterable<TMultiPart>) mp.partList()).iterator();
            while (it.hasNext()) {
                TMultiPart part = it.next();
                root.add(FMP.dump(part));
            }
            sendResponse(exchange, root);
        }
    }
}
