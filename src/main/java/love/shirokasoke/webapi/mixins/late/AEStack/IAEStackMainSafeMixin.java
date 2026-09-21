package love.shirokasoke.webapi.mixins.late.AEStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.item.IAEStackList;
import love.shirokasoke.webapi.utils.IAEStackSafe;
import love.shirokasoke.webapi.utils.IAEStackSafeAccess;

/**
 * IAEStack主入口(AE物品堆栈设计核心,hashmap嵌套)
 * <p>
 * {@link IAEStackList} 的安全只读视图
 */
@Mixin(value = IAEStackList.class, remap = false)
@Implements(@Interface(iface = IAEStackSafeAccess.class, prefix = "webapi$"))
public class IAEStackMainSafeMixin {

    @SuppressWarnings("rawtypes")
    @Shadow(remap = false)
    private Map<IAEStackType<?>, IItemList> lists;

    /**
     * 导出所有类别的物品/流体/源质
     * 
     * @apiNote 理论上来说，一个IAEStack只会存一类物品，可能需要后续处理
     * 
     * @param consumer
     * @return
     */
    public int webapi$$forEachSafe(final Consumer<Object> consumer) {
        final Map<IAEStackType<?>, IItemList> current = this.lists;
        if (current == null || current.isEmpty()) {
            return 0;
        }

        // 一次性取出子表引用：不迭代 IdentityHashMap，避免 fail-fast
        final List<IItemList<?>> children = new ArrayList<>(current.size());
        for (final IItemList<?> child : current.values()) {
            if (child != null) {
                children.add(child);
            }
        }

        int count = 0;
        for (final IItemList<?> child : children) {
            count += IAEStackSafe.forEach(child, consumer);
        }
        return count;
    }
}
