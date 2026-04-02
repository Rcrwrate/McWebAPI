package love.shirokasoke.webapi.thread;

import net.minecraft.item.Item;

import love.shirokasoke.webapi.Config;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.utils.Items;
import love.shirokasoke.webapi.utils.log;

public class ItemsThread extends Thread {

    public ItemsThread() {
        super("ItemCache-Builder");
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            MyMod.LOG.info("[ItemsThread] 开始在后台线程构建物品数据缓存...");
            long startTime = System.currentTimeMillis();
            int processedCount = 0;

            for (Object obj : Item.itemRegistry) {
                if (obj instanceof Item) {
                    Item item = (Item) obj;
                    if (item.getHasSubtypes()) {
                        Items.getPermutations(item);
                        processedCount++;

                        if (Config.itemThreadDelayMs > 0) {
                            try {
                                Thread.sleep(Config.itemThreadDelayMs);
                            } catch (InterruptedException ie) {
                                Thread.currentThread()
                                    .interrupt();
                                MyMod.LOG.warn("[ItemsThread] 被中断，停止处理");
                                break;
                            }
                        }

                        if (processedCount % Config.itemThreadBatchSize == 0) {
                            System.gc();
                        }
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            MyMod.LOG
                .info("物品数据缓存构建完成，共 {} 个物品（{} 个有子类型），耗时 {}ms", Items.itemOverrides.size(), processedCount, duration);
        } catch (Throwable e) {
            MyMod.LOG.error("[ItemsThread] 构建物品数据缓存时出错");
            log.e(e);
        }
    }
}
