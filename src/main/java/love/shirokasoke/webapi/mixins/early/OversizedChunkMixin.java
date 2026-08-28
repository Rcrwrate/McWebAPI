package love.shirokasoke.webapi.mixins.early;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.chunk.storage.RegionFile;

import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 处理 Hodgepodge {@link com.mitchej123.hodgepodge.mixins.early.minecraft.MixinRegionFile#write} 中超大区块写入时的
 * {@code Common.log.warn} 警告：
 * 每个 offset 只输出一次，之后直接拦截（不再打印）。
 * <p>
 * priority = 1001：保证本 Mixin 在 Hodgepodge 的 {@code MixinRegionFile}（默认 1000）之后应用，
 * 使其 {@code @Overwrite} 先合并进 {@code RegionFile#write}，本类的 {@code @Redirect} 才能匹配到
 * 合并后方法体中的 warn 调用。
 */
@Mixin(value = RegionFile.class, priority = 1001)
public abstract class OversizedChunkMixin {

    @Shadow
    protected abstract int getOffset(int x, int z);

    /** {@link com.mitchej123.hodgepodge.mixins.early.minecraft.MixinRegionFile#write} 是 synchronized，无需并发容器 */
    @Unique
    private final Set<Integer> webapi$warnedOffsets = new HashSet<>();

    /**
     * 同一 offset 首次放行该警告，之后吞掉。
     * <p>
     * require = 0：Hodgepodge 的
     * {@link com.mitchej123.hodgepodge.mixins.Mixins#SPIGOT_EXTENDED_CHUNKS}（{@link com.mitchej123.hodgepodge.config.FixesConfig#remove2MBChunkLimit}）被关闭
     * 或在服务端上目标调用不存在，此时静默跳过而不是导致启动崩溃。
     */
    @Redirect(
        method = "write(II[BI)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/apache/logging/log4j/Logger;warn(Ljava/lang/String;[Ljava/lang/Object;)V",
            remap = true),
        require = 0)
    private void webapi$warnOversizedChunkOnce(Logger logger, String message, Object[] params, int x, int z,
        byte[] data, int length) {
        if (this.webapi$warnedOffsets.add(this.getOffset(x, z))) {
            logger.warn(message, params);
        }
    }
}
