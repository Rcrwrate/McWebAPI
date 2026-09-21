package love.shirokasoke.webapi.utils;

import java.util.Collection;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * 用于 {@link FastUtilSafe} 安全访问 AE2 的 {@code records} / {@code setRecords} 。
 * 
 * <p>
 * <b>为什么不用 mixin</b>：
 * <p>
 * 整合包通过 RFB
 * <p>
 * {@code -Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader}
 * <p>
 * 把 {@code GTNH-server/falsepattern/it.unimi.dsi-fastutil-*.jar} 放在<b>系统类路径</b>上，
 * {@link ObjectOpenHashSet} 在任何 mixin 配置被准备之前就已加载完毕
 * <p>
 * Mixin 只会得到 {@link org.spongepowered.asm.mixin.transformer.throwables.MixinTargetAlreadyLoadedException}。
 * 
 * @apiNote 不要把本类实例交给 Java 原生序列化，默认序列化会丢掉 {@code transient} 的 {@code key} 表。
 */
public class SafeObjectOpenHashSet<T> extends ObjectOpenHashSet<T> {

    private static final long serialVersionUID = 1L;

    public SafeObjectOpenHashSet() {
        super();
    }

    public SafeObjectOpenHashSet(final Collection<? extends T> c) {
        super(c);
    }

    /** {@code ObjectOpenHashSet.key}，长度恒为 {@code n + 1}；绝不要缓存到静态字段或跨方法使用 */
    public T[] $key() {
        return this.key;
    }

    /** {@code ObjectOpenHashSet.containsNull}；null 元素不在 key 表里，迭代时靠该标记补一条 */
    public boolean $containsNull() {
        return this.containsNull;
    }

    /**
     * 转换成 {@link SafeObjectOpenHashSet} 实例
     */
    public static <T> SafeObjectOpenHashSet<T> convert(final ObjectOpenHashSet<T> set) {
        if (set instanceof SafeObjectOpenHashSet<T>o) {
            return o;
        }
        if (set == null) {
            return new SafeObjectOpenHashSet<>();
        }
        return new SafeObjectOpenHashSet<>(set);
    }
}
