package love.shirokasoke.webapi.client;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.client.thread.FluidIconDumperThread;
import love.shirokasoke.webapi.client.thread.ItemIconDumperThread;
import love.shirokasoke.webapi.client.thread.LangDumperThread;
import love.shirokasoke.webapi.client.thread.MapTileDumperThread;

public class ExportCommand extends CommandBase {

    private static volatile Thread itemThread = null;
    private static volatile Thread blockThread = null;
    private static volatile Thread fluidThread = null;
    private static volatile Thread langThread = null;

    @Override
    public String getCommandName() {
        return "export";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/export <items|nei|missing|blocks|fluids|lang>";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.addChatMessage(new ChatComponentText("[WebAPI] 用法: " + getCommandUsage(sender)));
            return;
        }
        String sub = args[0].toLowerCase();
        if ("items".equals(sub) || "nei".equals(sub) || "missing".equals(sub)) {
            if (itemThread != null && itemThread.isAlive()) {
                sender.addChatMessage(new ChatComponentText("[WebAPI] 物品图标导出线程已在运行中。"));
                return;
            }
            int mode = "nei".equals(sub) ? 1 : "missing".equals(sub) ? 2 : 0;
            String modeName = mode == 0 ? "默认" : mode == 1 ? "NEI" : "missing";
            MyMod.LOG.info("[ExportImagesCommand] 用户触发 {} 模式，启动 ItemIconDumperThread...", modeName);
            sender.addChatMessage(new ChatComponentText("[WebAPI] 正在启动物品图标导出线程（模式: " + modeName + "）..."));
            itemThread = new ItemIconDumperThread(mode);
            itemThread.start();
            sender.addChatMessage(new ChatComponentText("[WebAPI] 物品图标导出线程已启动，请查看后台日志。"));
        } else if ("blocks".equals(sub)) {
            if (blockThread != null && blockThread.isAlive()) {
                sender.addChatMessage(new ChatComponentText("[WebAPI] 方块纹理导出线程已在运行中。"));
                return;
            }
            MyMod.LOG.info("[ExportImagesCommand] 用户手动触发，启动 MapTileDumperThread...");
            sender.addChatMessage(new ChatComponentText("[WebAPI] 正在启动方块纹理导出线程..."));
            blockThread = new MapTileDumperThread();
            blockThread.start();
            sender.addChatMessage(new ChatComponentText("[WebAPI] 方块纹理导出线程已启动，请查看后台日志。"));
        } else if ("fluids".equals(sub)) {
            if (fluidThread != null && fluidThread.isAlive()) {
                sender.addChatMessage(new ChatComponentText("[WebAPI] 流体图标导出线程已在运行中。"));
                return;
            }
            MyMod.LOG.info("[ExportImagesCommand] 用户手动触发，启动 FluidIconDumperThread...");
            sender.addChatMessage(new ChatComponentText("[WebAPI] 正在启动流体图标导出线程..."));
            fluidThread = new FluidIconDumperThread();
            fluidThread.start();
            sender.addChatMessage(new ChatComponentText("[WebAPI] 流体图标导出线程已启动，请查看后台日志。"));
        } else if ("lang".equals(sub)) {
            if (langThread != null && langThread.isAlive()) {
                sender.addChatMessage(new ChatComponentText("[WebAPI] 语言文件导出线程已在运行中。"));
                return;
            }
            MyMod.LOG.info("[ExportImagesCommand] 用户触发，启动 LangDumperThread...");
            sender.addChatMessage(new ChatComponentText("[WebAPI] 正在启动语言文件导出线程..."));
            langThread = new LangDumperThread();
            langThread.start();
            sender.addChatMessage(new ChatComponentText("[WebAPI] 语言文件导出线程已启动，请查看后台日志。"));
        } else {
            sender.addChatMessage(new ChatComponentText("[WebAPI] 未知子命令 '" + sub + "'。用法: " + getCommandUsage(sender)));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }
}
