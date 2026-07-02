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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.ServerThreadDispatcher;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.log;

public class AEItemHandler extends AEBaseHandler {

    /** 缓存刷新间隔（秒） */
    private static long REFRESH_INTERVAL_SECONDS = Config.AEITEM_INTERVAL;
    /** 缓存空闲超时（毫秒），超过此时间无访问则终止该 grid 的缓存 */
    private static long IDLE_TIMEOUT_MS = Config.AEITEM_IDLE_TIMEOUT * 60L * 1000L;

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
        String json = getOrCreateCache(gridId, grid);
        sendResponse(exchange, 200, json);
    }

    /**
     * 获取指定网格的缓存 JSON。若缓存不存在（首次请求），则立即同步构建一次，
     * 并激活每 {@link #REFRESH_INTERVAL_SECONDS} 秒刷新的定时任务。
     */
    private static String getOrCreateCache(UUID gridId, IGrid grid) {
        CacheEntry entry = CACHE.get(gridId);
        if (entry == null) {
            synchronized (CACHE) {
                entry = CACHE.get(gridId);
                if (entry == null) {
                    // 首次请求在 HTTP 线程内同步完成：主线程采集快照 + 当前线程构建 JSON
                    RawSnapshot snap = collectSnapshot(grid);
                    String json = buildJsonFromSnapshot(snap);
                    entry = new CacheEntry(json, grid);
                    // 激活定时缓存刷新任务
                    ScheduledFuture<?> future = SCHEDULER.scheduleAtFixedRate(
                        new RefreshTask(gridId),
                        REFRESH_INTERVAL_SECONDS,
                        REFRESH_INTERVAL_SECONDS,
                        TimeUnit.SECONDS);
                    entry.future = future;
                    CACHE.put(gridId, entry);
                }
            }
        }
        entry.lastAccessMs = System.currentTimeMillis();
        return entry.json;
    }

    /**
     * 在主线程采集 AE2 网络的原始数据快照
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

        boolean hasCache = storageGrid instanceof GridStorageCache;
        double totalBytes = 0, usedBytes = 0;
        long totalTypes = 0, usedTypes = 0;
        long cellAll = 0, cellG = 0, cellB = 0, cellO = 0, cellR = 0;
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
        }

        return new RawSnapshot(
            stacks,
            stackSizes,
            craftables,
            hasCache,
            totalBytes,
            usedBytes,
            totalTypes,
            usedTypes,
            cellAll,
            cellG,
            cellB,
            cellO,
            cellR);
    }

    /**
     * 根据原始数据快照构建完整的 JSON 响应字符串（已包含 success/data 包装）。
     * <p>
     * 该方法为纯计算，不依赖主线程，可在工作线程执行。这也是占用耗时的大头。
     */
    private static String buildJsonFromSnapshot(RawSnapshot snap) {
        ArrayNode items = mapper.createArrayNode();
        for (int i = 0; i < snap.stacks.length; i++) {
            if (snap.stacks[i] == null) continue;
            items.add(
                Items.dump(snap.stacks[i])
                    .put("stackSize", snap.stackSizes[i])
                    .put("Craftable", snap.craftables[i]));
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
        }

        ObjectNode wrapped = mapper.createObjectNode()
            .put("success", true)
            .set("data", response);
        try {
            return mapper.writeValueAsString(wrapped);
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

        RawSnapshot(ItemStack[] stacks, long[] stackSizes, boolean[] craftables, boolean hasCache, double totalBytes,
            double usedBytes, long totalTypes, long usedTypes, long cellAll, long cellG, long cellB, long cellO,
            long cellR) {
            this.stacks = stacks;
            this.stackSizes = stackSizes;
            this.craftables = craftables;
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
        }
    }

    private static final class CacheEntry {

        volatile String json;
        final IGrid grid;
        /** 后台刷新任务句柄，用于超时终止 */
        volatile ScheduledFuture<?> future;
        volatile long lastAccessMs;

        CacheEntry(String json, IGrid grid) {
            this.json = json;
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
                CACHE.remove(gridId);
                return;
            }

            final IGrid gridRef = entry.grid;
            ServerThreadDispatcher.scheduleOnServerThread(() -> {
                final long tickStart = System.currentTimeMillis();
                final RawSnapshot snap;
                try {
                    snap = collectSnapshot(gridRef);
                } catch (Throwable e) {
                    log.e(e);
                    return;
                }
                long collectMs = System.currentTimeMillis() - tickStart;

                JSON_WORKER.submit(() -> {
                    final long buildStart = System.currentTimeMillis();
                    try {
                        entry.json = buildJsonFromSnapshot(snap);
                    } catch (Throwable e) {
                        log.e(e);
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
