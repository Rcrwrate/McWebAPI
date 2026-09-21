package love.shirokasoke.webapi.utils;

import java.util.function.Consumer;

/**
 * 由 {@code love.shirokasoke.webapi.mixins.late.AEStack} 系列 mixin 实现的伪安全只读视图接口。
 */
public interface IAEStackSafeAccess {

    /**
     * 不设长度假设的只读遍历，避免调用 AE2 的自清理 remove()。
     *
     * @return 读到的元素个数
     */
    <T> int $forEachSafe(Consumer<T> consumer);
}
