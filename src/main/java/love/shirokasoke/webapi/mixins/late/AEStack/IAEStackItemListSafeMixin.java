package love.shirokasoke.webapi.mixins.late.AEStack;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.ItemList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import love.shirokasoke.webapi.utils.IAEStackSafe;
import love.shirokasoke.webapi.utils.IAEStackSafeAccess;

/**
 * {@link ItemList} 的安全只读视图
 */
@Mixin(value = ItemList.class, remap = false)
@Implements(@Interface(iface = IAEStackSafeAccess.class, prefix = "webapi$"))
public class IAEStackItemListSafeMixin {

    @Shadow(remap = false)
    private ObjectOpenHashSet<IAEItemStack> setRecords;

    public int webapi$$forEachSafe(final Consumer<Object> consumer) {
        if (this.setRecords == null) {
            return 0;
        }
        return IAEStackSafe.forEachSet(this.setRecords, stack -> {
            if (stack != null && stack.isMeaningful()) {
                consumer.accept(stack);
            }
        });
    }
}
