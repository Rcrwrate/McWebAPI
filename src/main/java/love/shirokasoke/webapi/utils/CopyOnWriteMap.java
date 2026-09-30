package love.shirokasoke.webapi.utils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 写时复制（Copy-On-Write）并发 {@link Map}
 *
 * 每次写入都是 O(n) 全量复制，条目多且写入频繁时请改用 {@link java.util.concurrent.ConcurrentHashMap}。
 *
 * <p>
 * <b>一致性</b>：
 * {@link #keySet()}、{@link #values()}、{@link #entrySet()} 及其迭代器都是<b>获取时刻的快照</b>，
 * 不抛 {@link java.util.ConcurrentModificationException}，且不支持任何修改操作
 * （抛 {@link UnsupportedOperationException}）
 * 读到的数据要么是完整的旧状态，要么是完整的新状态
 *
 * <p>
 * <b>限制</b>：
 * <ul>
 * <li>与 {@link java.util.concurrent.ConcurrentHashMap} 一致，<b>不允许 {@code null} 键和 {@code null} 值</b>。</li>
 * <li>{@code compute*} / {@code merge} / {@code replaceAll} 的用户函数在<b>全局锁内</b>执行（保证原子性），
 * {@code synchronized} 可重入所以函数体内调用本对象方法不会死锁，
 * 但<b>不要</b>在函数体内修改本对象，否则该修改会被外层操作生成的副本覆盖而丢失。</li>
 * </ul>
 */
public class CopyOnWriteMap<K, V> implements ConcurrentMap<K, V>, Serializable, Cloneable {

    private static final long serialVersionUID = 1L;

    /** 当前不可变快照；所有读操作无锁直接访问该引用 */
    private volatile Map<K, V> snapshot;

    /** 写操作互斥锁；{@code transient}，由 {@link #readObject} 重建 */
    private transient Object lock = new Object();

    /**
     * 构造一个空的 {@link CopyOnWriteMap}
     */
    public CopyOnWriteMap() {
        this.snapshot = Collections.emptyMap();
    }

    /**
     * 构造一个空的 {@link CopyOnWriteMap}（预分配容量，减少首次写入的扩容开销）
     */
    public CopyOnWriteMap(final int initialCapacity) {
        this.snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(initialCapacity));
    }

    /**
     * 拷贝 {@code m} 的全部条目构造 {@link CopyOnWriteMap}（{@code m} 后续变化不影响本对象）
     */
    public CopyOnWriteMap(final Map<? extends K, ? extends V> m) {
        this.snapshot = copyOfMap(m);
    }

    // region 读：无锁直接访问快照

    @Override
    public int size() {
        return snapshot.size();
    }

    @Override
    public boolean isEmpty() {
        return snapshot.isEmpty();
    }

    @Override
    public boolean containsKey(final Object key) {
        return snapshot.containsKey(key);
    }

    @Override
    public boolean containsValue(final Object value) {
        return snapshot.containsValue(value);
    }

    @Override
    public V get(final Object key) {
        return snapshot.get(key);
    }

    /** 因为不允许 {@code null} 值，命中与否只看 {@code null} 即可 */
    @Override
    public V getOrDefault(final Object key, final V defaultValue) {
        final V v = snapshot.get(key);
        return v != null ? v : defaultValue;
    }

    @Override
    public void forEach(final BiConsumer<? super K, ? super V> action) {
        snapshot.forEach(action);
    }

    /** 键的快照视图：不可修改，迭代期间不受后续写操作影响 */
    @Override
    public Set<K> keySet() {
        return snapshot.keySet();
    }

    /** 值的快照视图：不可修改，迭代期间不受后续写操作影响 */
    @Override
    public Collection<V> values() {
        return snapshot.values();
    }

    /** 条目的快照视图：不可修改，其 {@code setValue} 会抛 {@link UnsupportedOperationException} */
    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return snapshot.entrySet();
    }

    // endregion

    // region 写：全局锁内整体复制后原子替换快照

    @Override
    public V put(final K key, final V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        synchronized (lock) {
            final Map<K, V> copy = new LinkedHashMap<>(snapshot);
            final V prev = copy.put(key, value);
            snapshot = Collections.unmodifiableMap(copy);
            return prev;
        }
    }

    @Override
    public V remove(final Object key) {
        synchronized (lock) {
            final Map<K, V> copy = new LinkedHashMap<>(snapshot);
            final V prev = copy.remove(key);
            if (prev != null) {
                snapshot = Collections.unmodifiableMap(copy);
            }
            return prev;
        }
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> m) {
        Objects.requireNonNull(m, "map");
        m.forEach((k, v) -> {
            Objects.requireNonNull(k, "key");
            Objects.requireNonNull(v, "value");
        });
        if (m.isEmpty()) {
            return;
        }
        synchronized (lock) {
            final Map<K, V> copy = new LinkedHashMap<>(snapshot);
            copy.putAll(m);
            snapshot = Collections.unmodifiableMap(copy);
        }
    }

    @Override
    public void clear() {
        synchronized (lock) {
            if (!snapshot.isEmpty()) {
                snapshot = Collections.emptyMap();
            }
        }
    }

    /**
     * 原子地用 {@code m} 的内容<b>整体替换</b>当前内容。
     *
     * <p>
     * 不必逐条 {@link #put}，一次 volatile 写完成切换，是写时复制结构特有的整体更新操作。
     */
    public void setAll(final Map<? extends K, ? extends V> m) {
        synchronized (lock) {
            snapshot = copyOfMap(m);
        }
    }

    // endregion

    // region ConcurrentMap 复合操作：锁内一次复制完成，保证原子性

    @Override
    public V putIfAbsent(final K key, final V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        synchronized (lock) {
            final V prev = snapshot.get(key);
            if (prev != null) {
                return prev;
            }
            final Map<K, V> copy = new LinkedHashMap<>(snapshot);
            copy.put(key, value);
            snapshot = Collections.unmodifiableMap(copy);
            return null;
        }
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        synchronized (lock) {
            final V cur = snapshot.get(key);
            if (cur == null || !cur.equals(value)) {
                return false;
            }
            final Map<K, V> copy = new LinkedHashMap<>(snapshot);
            copy.remove(key);
            snapshot = Collections.unmodifiableMap(copy);
            return true;
        }
    }

    @Override
    public V replace(final K key, final V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        synchronized (lock) {
            if (!snapshot.containsKey(key)) {
                return null;
            }
            final Map<K, V> copy = new LinkedHashMap<>(snapshot);
            final V prev = copy.replace(key, value);
            snapshot = Collections.unmodifiableMap(copy);
            return prev;
        }
    }

    @Override
    public boolean replace(final K key, final V oldValue, final V newValue) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(oldValue, "oldValue");
        Objects.requireNonNull(newValue, "newValue");
        synchronized (lock) {
            final V cur = snapshot.get(key);
            if (cur == null || !cur.equals(oldValue)) {
                return false;
            }
            final Map<K, V> copy = new LinkedHashMap<>(snapshot);
            copy.replace(key, newValue);
            snapshot = Collections.unmodifiableMap(copy);
            return true;
        }
    }

    // endregion

    // region compute 系列：用户函数在锁内对副本执行，全程原子

    @Override
    public V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mappingFunction, "mappingFunction");
        synchronized (lock) {
            final V old = snapshot.get(key);
            if (old != null) {
                return old;
            }
            final Map<K, V> copy = new LinkedHashMap<>(snapshot);
            final V neu = copy.computeIfAbsent(key, mappingFunction);
            snapshot = Collections.unmodifiableMap(copy);
            return neu;
        }
    }

    @Override
    public V computeIfPresent(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(remappingFunction, "remappingFunction");
        synchronized (lock) {
            if (!snapshot.containsKey(key)) {
                return null;
            }
            final Map<K, V> copy = new LinkedHashMap<>(snapshot);
            final V neu = copy.computeIfPresent(key, remappingFunction);
            snapshot = Collections.unmodifiableMap(copy);
            return neu;
        }
    }

    @Override
    public V compute(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(remappingFunction, "remappingFunction");
        synchronized (lock) {
            final Map<K, V> copy = new LinkedHashMap<>(snapshot);
            final V neu = copy.compute(key, remappingFunction);
            snapshot = Collections.unmodifiableMap(copy);
            return neu;
        }
    }

    @Override
    public V merge(final K key, final V value, final BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(remappingFunction, "remappingFunction");
        synchronized (lock) {
            final Map<K, V> copy = new LinkedHashMap<>(snapshot);
            final V neu = copy.merge(key, value, remappingFunction);
            snapshot = Collections.unmodifiableMap(copy);
            return neu;
        }
    }

    @Override
    public void replaceAll(final BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function, "function");
        synchronized (lock) {
            if (snapshot.isEmpty()) {
                return;
            }
            final Map<K, V> next = new LinkedHashMap<>(snapshot.size());
            snapshot.forEach((k, v) -> {
                final V neu = function.apply(k, v);
                Objects.requireNonNull(neu, "function 返回了 null 值");
                next.put(k, neu);
            });
            snapshot = Collections.unmodifiableMap(next);
        }
    }

    // endregion

    // region Object / 克隆

    @Override
    public boolean equals(final Object o) {
        return snapshot.equals(o);
    }

    @Override
    public int hashCode() {
        return snapshot.hashCode();
    }

    @Override
    public String toString() {
        return snapshot.toString();
    }

    /**
     * 返回内容相同的<b>浅拷贝</b>（新实例，键值本身不复制）
     */
    @Override
    public CopyOnWriteMap<K, V> clone() {
        return new CopyOnWriteMap<>(this.snapshot);
    }

    // endregion

    // region 内部辅助 / 序列化

    /** 校验 {@code m} 无 {@code null} 键值并返回其不可变副本 */
    private static <K, V> Map<K, V> copyOfMap(final Map<? extends K, ? extends V> m) {
        Objects.requireNonNull(m, "map");
        m.forEach((k, v) -> {
            Objects.requireNonNull(k, "key");
            Objects.requireNonNull(v, "value");
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(m));
    }

    /** {@code lock} 不是序列化字段，反序列化后重建，避免写操作 NPE */
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.lock = new Object();
    }
    // endregion
}
