package love.shirokasoke.webapi;

import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import love.shirokasoke.webapi.client.thread.ItemIconDumperThread;

public class ClientProxy extends CommonProxy {

    @Override
    public void loadComplete(FMLLoadCompleteEvent event) {
        super.loadComplete(event);
        if (Config.itemIconDumperEnable) {
            MyMod.LOG.info("[ClientProxy] 客户端加载完成，启动 ItemIconDumperThread...");
            new ItemIconDumperThread().start();
        }
    }

    @Override
    public void serverStarting(FMLServerStartingEvent event) {

    }

    @Override
    public void serverStarted(FMLServerStartedEvent event) {

    }

    @Override
    public void serverStopping(FMLServerStoppingEvent event) {

    }
}
