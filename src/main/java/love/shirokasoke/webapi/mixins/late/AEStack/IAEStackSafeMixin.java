package love.shirokasoke.webapi.mixins.late.AEStack;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import appeng.api.storage.data.IAEStack;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import love.shirokasoke.webapi.utils.IAEStackSafe;
import love.shirokasoke.webapi.utils.IAEStackSafeAccess;

/**
 * 通用安全只读视图
 */
@Mixin(
    value = { appeng.util.item.FluidList.class, appeng.util.item.HashBasedItemList.class,
        thaumicenergistics.common.storage.EssentiaList.class },
    remap = false)
@Implements(@Interface(iface = IAEStackSafeAccess.class, prefix = "webapi$"))
public class IAEStackSafeMixin {

    @SuppressWarnings("rawtypes")
    @Shadow(remap = false)
    private ObjectOpenHashSet<IAEStack> records;

    public int webapi$$forEachSafe(final Consumer<Object> consumer) {
        if (this.records == null) {
            return 0;
        }
        return IAEStackSafe.forEachSet(this.records, stack -> {
            if (stack != null && stack.isMeaningful()) {
                consumer.accept(stack);
            }
        });
    }
}
