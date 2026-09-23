package love.shirokasoke.webapi.utils;

import java.util.function.Consumer;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * {@link ObjectOpenHashSet} 的多线程用安全访问工具。
 *
 * <p>
 * 不做任何长度假设：直接扫 key 表（不会越界），收集阶段按需扩容，不预分配定长数组
 * </p>
 *
 * @apiNote <b>不保证强一致</b>，返回的是扫描期间某个时刻的元素集合，可能落后于最新状态；
 */
public final class FastUtilSafe {

    private FastUtilSafe() {}

    /**
     * 遍历 {@link ObjectOpenHashSet} 并逐元素回调
     *
     * @param set      目标集合，可为 null（此时直接返回，不回调）
     * @param consumer 元素回调，可为 null（此时直接返回）
     * @return 实际读到的元素个数
     */
    public static <T> int forEach(final ObjectOpenHashSet<T> set, final Consumer<? super T> consumer) {
        if (set == null || consumer == null) {
            return 0;
        }

        // 首选子类直读（AE2 的 records 在构造时已被换成 SafeObjectOpenHashSet）；其它集合退回反射
        final Object[] key;
        if (set instanceof SafeObjectOpenHashSet<?>safe) {
            key = safe.$key();
        } else {
            key = SafeObjectOpenHashSet.convert(set)
                .$key();
        }

        // key 长度恒为 n + 1（n ≥ 1），故 length - 1 即可安全定界，永不越界。
        // 绝不要读 mask：key 与 mask 可能来自不同时刻，会错位越界。
        if (key == null || key.length < 2) {
            return 0;
        }

        final int limit = key.length - 1;

        int count = 0;
        // 批次预算 = 当前可见元素数 + null 元素（若存在）。
        // remaining 减到 0 即“本批扫完”，不能多加余量，否则每批都会多欠一个，
        // 导致同一批元素被反复回调（元素数被放大 size 倍）。
        int remaining = countVisible(key, limit) + (containsNull(set) ? 1 : 0);

        // task 上限只是保险丝：正常情况下第一个 task 就能扫完
        for (int task = 0; task < 64; task++) {
            for (int pos = 0; pos < limit; pos++) {
                final T element = (T) readAt(key, pos);
                if (element == null) {
                    continue;
                }
                remaining--;
                count++;
                consumer.accept(element);
            }

            // null 元素不存在 key 数组里，fastutil 用 containsNull 标记、由迭代器在扫完表后补一条 null。
            // mustReturnNull 语义：只有 hasNext() 为真（即确实还有未返回的元素）时才补。
            if (remaining > 0 && containsNull(set)) {
                remaining--;
                count++;
                consumer.accept(null);
            }

            // 本批扫完就收工。remaining > 0 说明扫描期间表被并发插入/扩容，重新估一批再继续
            if (remaining <= 0) {
                break;
            }

            remaining = countVisible(key, limit) + (containsNull(set) ? 1 : 0);
        }

        return count;
    }

    /**
     * 数一遍表中当前可见的非 null 元素。
     *
     * <p>
     * 只用于“批”控制流，<b>不能</b>用于预分配数组长度：并发插入时两趟结果会不一致，
     * 预分配定长数组就是把 NPE 换成 AIOOBE
     * </p>
     */
    private static int countVisible(final Object[] key, final int limit) {
        int size = 0;
        for (int pos = 0; pos < limit; pos++) {
            if (readAt(key, pos) != null) {
                size++;
            }
        }
        return size;
    }

    /**
     * 读取槽位。所有读都放在独立栈帧里，配合调用方的 null 检查，
     * 可避开 {@code key} 被并发替换时 JIT 对元素访问的重排序/常见子表达式消除。
     */
    private static <T> T readAt(final T[] key, final int pos) {
        return key[pos];
    }

    private static <T> boolean containsNull(final ObjectOpenHashSet<T> set) {
        if (set instanceof SafeObjectOpenHashSet<?>safe) {
            return safe.$containsNull();
        } else {
            return SafeObjectOpenHashSet.convert(set)
                .$containsNull();
        }
    }
}
