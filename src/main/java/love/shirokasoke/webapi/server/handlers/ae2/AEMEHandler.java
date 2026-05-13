package love.shirokasoke.webapi.server.handlers.ae2;

import java.io.IOException;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.parts.IPart;
import appeng.fmp.CableBusPart;
import appeng.helpers.IInterfaceHost;
import appeng.parts.CableBusContainer;
import appeng.tile.misc.TileInterface;
import codechicken.multipart.TMultiPart;
import codechicken.multipart.TileMultipart;
import love.shirokasoke.webapi.utils.Pattern;
import scala.collection.Iterator;

public class AEMEHandler extends AEBaseHandler {

    @Override
    public String getPath() {
        return "/ae/me";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        AEinit(exchange);
        TileEntity tile = world.getTileEntity(co.posX, co.posY, co.posZ);

        // 1) 方块形式的 ME 接口（TileInterface）
        if (tile instanceof TileInterface) {
            TileInterface iface = (TileInterface) tile;
            IInventory patternInv = iface.getInterfaceDuality()
                .getPatterns();
            if (patternInv != null) {
                sendPatterns(exchange, patternInv, null);
                return;
            }
            throw new Error(404, "ME interface has no IInventory");
        }

        // 2) FMP 形式的 ME 接口（PartInterface 以 CableBusPart 的面部件存在）
        TileMultipart mp = TileMultipart.getOrConvertTile(world, co.BlockCoord());
        if (mp != null) {
            Iterator<TMultiPart> it = ((scala.collection.Iterable<TMultiPart>) mp.partList()).iterator();
            while (it.hasNext()) {
                TMultiPart part = it.next();
                if (part instanceof CableBusPart) {
                    CableBusContainer cbc = ((CableBusPart) part).getCableBus();
                    for (ForgeDirection dir : ForgeDirection.values()) {
                        IPart sidePart = cbc.getPart(dir);
                        // 判断该面部件是否为 ME 接口（PartInterface 实现了 IInterfaceHost）
                        if (sidePart instanceof IInterfaceHost) {
                            IInventory patternInv = ((IInterfaceHost) sidePart).getPatterns();
                            if (patternInv != null) {
                                sendPatterns(exchange, patternInv, dir.name());
                                return;
                            }
                        }
                    }
                }
            }
        }

        throw new Error(404, "AE ME Interface not found");
    }

    /**
     * 将样板库存序列化为 JSON 并返回。
     *
     * @param exchange   HTTP 交换上下文
     * @param patternInv 样板物品栏
     * @param direction  部件所在方向（FMP 形式时提供，方块形式可为 null）
     */
    private void sendPatterns(HttpExchange exchange, IInventory patternInv, String direction) throws IOException {
        ArrayNode patterns = mapper.createArrayNode();
        for (int i = 0; i < patternInv.getSizeInventory(); i++) {
            ItemStack patternStack = patternInv.getStackInSlot(i);
            if (patternStack != null) {
                ObjectNode pattern = Pattern.dump(patternStack, true, world);
                pattern.put("slot", i);
                if (direction != null) {
                    pattern.put("direction", direction);
                }
                patterns.add(pattern);
            }
        }
        sendResponse(exchange, patterns);
    }
}
