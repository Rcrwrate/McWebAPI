package love.shirokasoke.webapi.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import love.shirokasoke.webapi.MyMod;

/**
 * AE2 {@link IAEStack} 列表的安全遍历
 *
 * <p>
 * AE2 {@link IItemList} 会在遍历时自清理，而底层是 {@code ObjectOpenHashSet}（非 fail-fast、非线程安全）
 * </p>
 */
public final class IAEStackSafe {

    private IAEStackSafe() {}

    /**
     * 遍历任意 {@link IItemList}，对每个 meaningful 元素回调。
     *
     * @return 实际读到的元素个数；返回 -1 表示无法安全读取（调用方需自行回退）
     */
    public static <T extends IAEStack<?>> int forEach(final IItemList<T> list, final Consumer<? super T> consumer) {
        if (list == null || consumer == null) {
            return 0;
        }

        if (list instanceof IAEStackSafeAccess safe) {
            return safe.$forEachSafe(consumer);
        }
        MyMod.LOG.warn("direct access may cause NPE on server thread: {}", list);
        // 只由 AE2 主线程访问的列表（例如局部 list），走原生迭代器即可
        return forEachNative(list, consumer);
    }

    /**
     * 是否存在任意 meaningful 元素。<b>只读</b>，不会像 {@link IItemList#isEmpty()} 那样删元素。
     */
    public static <T extends IAEStack<?>> boolean hasAny(final IItemList<T> list) {
        if (list == null) {
            return false;
        }
        final boolean[] found = { false };
        forEach(list, stack -> found[0] = true);
        return found[0];
    }

    /**
     * 收集为 {@link List}（元素为表内引用，不 copy，避免额外开销）。
     */
    public static <T extends IAEStack<?>> List<T> collect(final IItemList<T> list) {
        final List<T> out = new ArrayList<>();
        if (list == null) {
            return out;
        }
        forEach(list, out::add);
        return out;
    }

    /**
     * 安全读取一个 fastutil {@link ObjectOpenHashSet}。
     *
     * @return 实际读到的元素个数；-1 表示 mixin 与反射都不可用（调用方需自行回退）
     */
    public static <T> int forEachSet(final ObjectOpenHashSet<T> set, final Consumer<? super T> consumer) {
        return FastUtilSafe.forEach(set, consumer);
    }

    /**
     * 最终回退
     * 
     * @apiNote 仅在调用方确认是主线程访问时才安全
     */
    private static <T extends IAEStack<?>> int forEachNative(final IItemList<T> list,
        final Consumer<? super T> consumer) {
        int count = 0;
        try {
            final Iterator<T> it = list.iterator();
            while (it.hasNext()) {
                final T stack = it.next();
                if (stack != null) {
                    count++;
                    consumer.accept(stack);
                }
            }
        } catch (Exception e) {
            Logs.e(e);
            return -1;
        }
        return count;
    }
}
