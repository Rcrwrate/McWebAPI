package love.shirokasoke.webapi.mixins.late;

import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mitchej123.hodgepodge.util.ServerThreadLongHashMap;

@Mixin(ServerThreadLongHashMap.class)
public class ServerThreadLongHashMapBypass {

    private static final String MOD_PACKAGE = "love.shirokasoke.webapi";

    @Final
    @Shadow(remap = false)
    private static Logger LOGGER;

    @Final
    @Shadow(remap = false)
    private static Set<String> loggedThreadNames;

    /**
     * {@link ServerThreadLongHashMap#logOffThread}
     * 
     * @author shirokasoke
     * @reason 本 mod 的非主线程读取不可避免，仅打印一行简短信息；其他线程保持原警告行为
     */
    @Inject(method = "logOffThread", at = @At("HEAD"), cancellable = true, remap = false)
    private void webapi$logOffThread(CallbackInfo ci) {
        final String name = Thread.currentThread()
            .getName();
        if (loggedThreadNames.contains(name)) {
            ci.cancel();
            return;
        }
        if (isModThread()) {
            loggedThreadNames.add(name);
            LOGGER.info("Off-thread chunk read from WebAPI thread '{}', serving from snapshot", name);
            ci.cancel();
        }
    }

    private static boolean isModThread() {
        for (StackTraceElement element : Thread.currentThread()
            .getStackTrace()) {
            if (element.getClassName()
                .startsWith(MOD_PACKAGE)) {
                return true;
            }
        }
        return false;
    }
}
