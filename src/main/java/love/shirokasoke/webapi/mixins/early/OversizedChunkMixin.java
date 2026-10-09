package love.shirokasoke.webapi.mixins.early;

import java.util.Set;

import net.minecraft.world.chunk.storage.RegionFile;

import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * 处理 Hodgepodge {@link com.mitchej123.hodgepodge.mixins.early.minecraft.MixinRegionFile#write} 中超大区块写入时的
 * {@code Common.log.warn} 重复警告
 */
@Mixin(value = RegionFile.class, priority = 1001)
public abstract class OversizedChunkMixin {

    /**
     * @apiNote 保持hodgepodge修改后的protected
     */
    @Shadow
    protected abstract int getOffset(int x, int z);

    /** {@link com.mitchej123.hodgepodge.mixins.early.minecraft.MixinRegionFile#write} 是 synchronized，无需并发容器 */
    @Unique
    private final Set<Integer> webapi$warnedOffsets = new IntOpenHashSet();

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
            remap = false),
        require = 0)
    private void webapi$warnOversizedChunkOnce(Logger logger, String message, Object[] params, int x, int z,
        byte[] data, int length) {
        if (this.webapi$warnedOffsets.add(this.getOffset(x, z))) {
            logger.warn(message, params);
        }
    }
}
