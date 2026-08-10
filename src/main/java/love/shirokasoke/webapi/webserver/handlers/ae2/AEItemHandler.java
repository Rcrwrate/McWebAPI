package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.Fluids;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.Logs;

public class AEItemHandler extends AEBaseHandler {

    /** 缓存刷新间隔（秒） */
    private static final long REFRESH_INTERVAL_SECONDS = Config.AEITEM_INTERVAL;
    /** 缓存空闲超时（毫秒），超过此时间无访问则终止该 grid 的缓存 */
    private static final long IDLE_TIMEOUT_MS = Config.AEITEM_IDLE_TIMEOUT * 60L * 1000L;

    /** 单线程定时调度器，负责触发各 grid 的缓存刷新 */
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AEItem-Refresher");
        t.setDaemon(true);
        return t;
    });

    /** JSON 构建专用工作线程（执行 Items.dump 与序列化，纯计算，无需主线程） */
    private static final ExecutorService JSON_WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AEItem-Cache");
        t.setDaemon(true);
        return t;
    });

    /** 缓存条目表，key 为 {@link IGrid#getId()} */
    private static final ConcurrentHashMap<UUID, CacheEntry> CACHE = new ConcurrentHashMap<>();

    @Override
    public String getPath() {
        return "/ae/item";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        AEinit(exchange);
        UUID gridId = grid.getId();
        CacheEntry entry = getOrCreateCacheEntry(gridId, grid);
        exchange.getResponseHeaders()
            .set("Content-Type", "application/json");
        sendResponse(exchange, 200, entry.jsonBytes);
    }

    /**
     * 获取指定网格的缓存条目。若缓存不存在（首次请求），则立即同步构建一次，
     * 并激活每 {@link #REFRESH_INTERVAL_SECONDS} 秒刷新的定时任务。
     * 
     * @throws ApiException
     */
    private CacheEntry getOrCreateCacheEntry(UUID gridId, IGrid grid) throws ApiException {
        CacheEntry entry = CACHE.get(gridId);
        if (entry == null) {
            RawSnapshot snap;
            try {
                snap = ServerThreadDispatcher.callOnServerThread(() -> collectSnapshot(grid));
            } catch (Exception e) {
                Logs.e(e);
                throw new ApiException(503, e.getMessage());
            }

            byte[] bytes = buildJsonBytesFromSnapshot(snap);
            CacheEntry newEntry = new CacheEntry(bytes, grid);
            // 激活定时缓存刷新任务
            ScheduledFuture<?> future = SCHEDULER.scheduleAtFixedRate(
                new RefreshTask(gridId),
                REFRESH_INTERVAL_SECONDS,
                REFRESH_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
            newEntry.future = future;

            CacheEntry existing = CACHE.putIfAbsent(gridId, newEntry);
            if (existing == null) {
                entry = newEntry; // 竞争获胜，使用自己的条目
            } else {
                future.cancel(false); // 竞争失败：取消多余的刷新任务
                entry = existing;
            }
        }
        entry.lastAccessMs = System.currentTimeMillis();
        return entry;
    }

    /**
     * 在主线程采集 AE2 网络的原始数据快照
     * <p>
     * {@line IMEMonitor#getStorageList()} 的委托实现位于
     * {@link appeng.me.cache.NetworkMonitor#getStorageList()}
     * <p>
     * 访问{@code cachedList} -> {@link appeng.util.item.ItemList#setRecords}/{@link appeng.util.item.FluidList#records}
     * <p>
     * 类型 {@link it.unimi.dsi.fastutil.objects.ObjectOpenHashSet} (非线程安全，非fail-fast，需要注意，getStorageList会触发cachedList重建)
     * @apiNote 必须在主线程操作
     */
    private static RawSnapshot collectSnapshot(IGrid grid) {
        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        IMEMonitor<IAEItemStack> itemInventory = storageGrid.getItemInventory();
        IItemList<IAEItemStack> itemList = itemInventory.getStorageList();
        int count = itemList.size();
        ItemStack[] stacks = new ItemStack[count];
        long[] stackSizes = new long[count];
        boolean[] craftables = new boolean[count];
        int i = 0;
        for (IAEItemStack stack : itemList) {
            if (stack == null) continue;
            ItemStack minecraftStack = stack.getItemStack();
            if (minecraftStack == null) continue;
            stacks[i] = minecraftStack.copy();
            stackSizes[i] = stack.getStackSize();
            craftables[i] = stack.isCraftable();
            i++;
        }

        // 流体库存快照
        IMEMonitor<IAEFluidStack> fluidInventory = storageGrid.getFluidInventory();
        IItemList<IAEFluidStack> fluidList = fluidInventory.getStorageList();
        int fluidCount = fluidList.size();
        FluidStack[] fluidStacks = new FluidStack[fluidCount];
        long[] fluidStackSizes = new long[fluidCount];
        boolean[] fluidCraftables = new boolean[fluidCount];
        int j = 0;
        for (IAEFluidStack stack : fluidList) {
            if (stack == null) continue;
            FluidStack minecraftStack = stack.getFluidStack();
            if (minecraftStack == null) continue;
            fluidStacks[j] = minecraftStack.copy();
            fluidStackSizes[j] = stack.getStackSize();
            fluidCraftables[j] = stack.isCraftable();
            j++;
        }

        boolean hasCache = storageGrid instanceof GridStorageCache;
        double totalBytes = 0, usedBytes = 0;
        long totalTypes = 0, usedTypes = 0;
        long cellAll = 0, cellG = 0, cellB = 0, cellO = 0, cellR = 0;
        double fluidTotalBytes = 0, fluidUsedBytes = 0;
        long fluidTotalTypes = 0, fluidUsedTypes = 0;
        long fluidCellAll = 0, fluidCellG = 0, fluidCellB = 0, fluidCellO = 0, fluidCellR = 0;
        if (hasCache) {
            GridStorageCache cache = (GridStorageCache) storageGrid;
            totalBytes = cache.getItemBytesTotal();
            usedBytes = cache.getItemBytesUsed();
            totalTypes = cache.getItemTypesTotal();
            usedTypes = cache.getItemTypesUsed();
            cellAll = cache.getItemCellCount();
            cellG = cache.getItemCellG();
            cellB = cache.getItemCellB();
            cellO = cache.getItemCellO();
            cellR = cache.getItemCellR();
            fluidTotalBytes = cache.getFluidBytesTotal();
            fluidUsedBytes = cache.getFluidBytesUsed();
            fluidTotalTypes = cache.getFluidTypesTotal();
            fluidUsedTypes = cache.getFluidTypesUsed();
            fluidCellAll = cache.getFluidCellCount();
            fluidCellG = cache.getFluidCellG();
            fluidCellB = cache.getFluidCellB();
            fluidCellO = cache.getFluidCellO();
            fluidCellR = cache.getFluidCellR();
        }

        return new RawSnapshot(
            stacks,
            stackSizes,
            craftables,
            fluidStacks,
            fluidStackSizes,
            fluidCraftables,
            hasCache,
            totalBytes,
            usedBytes,
            totalTypes,
            usedTypes,
            cellAll,
            cellG,
            cellB,
            cellO,
            cellR,
            fluidTotalBytes,
            fluidUsedBytes,
            fluidTotalTypes,
            fluidUsedTypes,
            fluidCellAll,
            fluidCellG,
            fluidCellB,
            fluidCellO,
            fluidCellR);
    }

    /**
     * 根据原始数据快照构建完整的 JSON 响应字节（已包含 success/data 包装）。
     * <p>
     * 该方法为纯计算，不依赖主线程，可在工作线程执行。这也是占用耗时的大头。
     */
    private static byte[] buildJsonBytesFromSnapshot(RawSnapshot snap) {
        ArrayNode items = mapper.createArrayNode();
        for (int i = 0; i < snap.stacks.length; i++) {
            if (snap.stacks[i] == null) continue;
            items.add(
                Items.dump(snap.stacks[i])
                    .put("type", IAEStack.ST_ITEM)
                    .put("stackSize", snap.stackSizes[i])
                    .put("Craftable", snap.craftables[i]));
        }
        for (int i = 0; i < snap.fluidStacks.length; i++) {
            if (snap.fluidStacks[i] == null || snap.fluidStacks[i].getFluid() == null) continue;
            items.add(
                Fluids.dump(snap.fluidStacks[i].getFluid())
                    .put("type", IAEStack.ST_FLUID)
                    .put("stackSize", snap.fluidStackSizes[i])
                    .put("Craftable", snap.fluidCraftables[i]));
        }

        ObjectNode response = mapper.createObjectNode();
        response.set("items", items);
        if (snap.hasCache) {
            response.put("totalBytes", snap.totalBytes);
            response.put("usedBytes", snap.usedBytes);
            response.put("totalTypes", snap.totalTypes);
            response.put("usedTypes", snap.usedTypes);

            ObjectNode cellStatus = response.putObject("cellStatus");
            cellStatus.put("all", snap.cellAll);
            cellStatus.put("green", snap.cellG);
            cellStatus.put("blue", snap.cellB);
            cellStatus.put("orange", snap.cellO);
            cellStatus.put("red", snap.cellR);

            response.put("fluidTotalBytes", snap.fluidTotalBytes);
            response.put("fluidUsedBytes", snap.fluidUsedBytes);
            response.put("fluidTotalTypes", snap.fluidTotalTypes);
            response.put("fluidUsedTypes", snap.fluidUsedTypes);

            ObjectNode fluidCellStatus = response.putObject("fluidCellStatus");
            fluidCellStatus.put("all", snap.fluidCellAll);
            fluidCellStatus.put("green", snap.fluidCellG);
            fluidCellStatus.put("blue", snap.fluidCellB);
            fluidCellStatus.put("orange", snap.fluidCellO);
            fluidCellStatus.put("red", snap.fluidCellR);
        }

        ObjectNode wrapped = mapper.createObjectNode()
            .put("success", true)
            .set("data", response);
        try {
            return mapper.writeValueAsBytes(wrapped);
        } catch (Exception e) {
            // 序列化失败理论上不会发生，抛出以让上层捕获
            throw new RuntimeException(e);
        }
    }

    /**
     * 原始数据快照，由主线程采集，供子线程构建 JSON。 持有的 {@link ItemStack} 均为独立副本，可安全在工作线程访问。
     */
    private static final class RawSnapshot {

        final ItemStack[] stacks;
        final long[] stackSizes;
        final boolean[] craftables;
        final FluidStack[] fluidStacks;
        final long[] fluidStackSizes;
        final boolean[] fluidCraftables;
        final boolean hasCache;
        final double totalBytes;
        final double usedBytes;
        final long totalTypes;
        final long usedTypes;
        final long cellAll;
        final long cellG;
        final long cellB;
        final long cellO;
        final long cellR;
        final double fluidTotalBytes;
        final double fluidUsedBytes;
        final long fluidTotalTypes;
        final long fluidUsedTypes;
        final long fluidCellAll;
        final long fluidCellG;
        final long fluidCellB;
        final long fluidCellO;
        final long fluidCellR;

        RawSnapshot(ItemStack[] stacks, long[] stackSizes, boolean[] craftables, FluidStack[] fluidStacks,
            long[] fluidStackSizes, boolean[] fluidCraftables, boolean hasCache, double totalBytes, double usedBytes,
            long totalTypes, long usedTypes, long cellAll, long cellG, long cellB, long cellO, long cellR,
            double fluidTotalBytes, double fluidUsedBytes, long fluidTotalTypes, long fluidUsedTypes, long fluidCellAll,
            long fluidCellG, long fluidCellB, long fluidCellO, long fluidCellR) {
            this.stacks = stacks;
            this.stackSizes = stackSizes;
            this.craftables = craftables;
            this.fluidStacks = fluidStacks;
            this.fluidStackSizes = fluidStackSizes;
            this.fluidCraftables = fluidCraftables;
            this.hasCache = hasCache;
            this.totalBytes = totalBytes;
            this.usedBytes = usedBytes;
            this.totalTypes = totalTypes;
            this.usedTypes = usedTypes;
            this.cellAll = cellAll;
            this.cellG = cellG;
            this.cellB = cellB;
            this.cellO = cellO;
            this.cellR = cellR;
            this.fluidTotalBytes = fluidTotalBytes;
            this.fluidUsedBytes = fluidUsedBytes;
            this.fluidTotalTypes = fluidTotalTypes;
            this.fluidUsedTypes = fluidUsedTypes;
            this.fluidCellAll = fluidCellAll;
            this.fluidCellG = fluidCellG;
            this.fluidCellB = fluidCellB;
            this.fluidCellO = fluidCellO;
            this.fluidCellR = fluidCellR;
        }
    }

    private static final class CacheEntry {

        volatile byte[] jsonBytes;
        final IGrid grid;
        /** 后台刷新任务句柄，用于超时终止 */
        volatile ScheduledFuture<?> future;
        volatile long lastAccessMs;

        CacheEntry(byte[] jsonBytes, IGrid grid) {
            this.jsonBytes = jsonBytes;
            this.grid = grid;
            this.lastAccessMs = System.currentTimeMillis();
        }
    }

    /**
     * 后台缓存刷新任务。每 {@link #REFRESH_INTERVAL_SECONDS} 秒触发一次：
     * <ol>
     * <li>若距上次访问超过 {@link #IDLE_TIMEOUT_MS} 毫秒，则终止该 grid 的缓存；</li>
     * <li>把"主线程数据采集"提交到服务器主线程慢队列， 采集完成后再把"JSON 构建"派发到
     * {@link #JSON_WORKER} 子线程，从而把耗时的部分移出主线程。</li>
     * </ol>
     */
    private static final class RefreshTask implements Runnable {

        private final UUID gridId;

        RefreshTask(UUID gridId) {
            this.gridId = gridId;
        }

        @Override
        public void run() {
            CacheEntry entry = CACHE.get(gridId);
            if (entry == null) {
                return;
            }

            long now = System.currentTimeMillis();
            // 超过 IDLE_TIMEOUT_MS 分钟无人访问，终止缓存
            if (now - entry.lastAccessMs > IDLE_TIMEOUT_MS) {
                if (entry.future != null) {
                    entry.future.cancel(false);
                }
                CACHE.remove(gridId, entry);
                return;
            }

            final IGrid gridRef = entry.grid;
            ServerThreadDispatcher.scheduleOnServerThread(() -> {
                final long tickStart = System.currentTimeMillis();
                final RawSnapshot snap;
                try {
                    snap = collectSnapshot(gridRef);
                } catch (Throwable e) {
                    Logs.e(e);
                    return;
                }
                long collectMs = System.currentTimeMillis() - tickStart;

                JSON_WORKER.submit(() -> {
                    final long buildStart = System.currentTimeMillis();
                    try {
                        byte[] bytes = buildJsonBytesFromSnapshot(snap);
                        entry.jsonBytes = bytes;
                    } catch (Throwable e) {
                        Logs.e(e);
                        return;
                    }
                    MyMod.LOG.debug(
                        "grid={} collect={}ms build={}ms",
                        gridId,
                        collectMs,
                        System.currentTimeMillis() - buildStart);
                });
            });
        }
    }
}
