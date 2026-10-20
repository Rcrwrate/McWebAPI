package love.shirokasoke.webapi.mixins.late.AECrafting;

import java.util.Iterator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.utils.IAEStackSafe;

/**
 * 修正 {@link MECraftingInventory} 构造时拷贝网络库存的方式，使其可被非主线程安全构造
 */
@Mixin(MECraftingInventory.class)
public class MECraftingInventoryMixin {

    /**
     * 重定向目标构造器中 for-each 对 {@link IItemList#iterator()} 的调用
     */
    @Redirect(
        method = "<init>(Lappeng/api/storage/IStorageMonitorable;ZZZ)V",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/data/IItemList;iterator()Ljava/util/Iterator;"),
        remap = false,
        require = 1)
    private Iterator<IAEStack<?>> webapi$safeCopyStorageList(final IItemList<IAEStack<?>> storageList) {
        // 主线程：保留
        if ("Server thread".equals(
            Thread.currentThread()
                .getName())) {
            return storageList.iterator();
        }

        // 非主线程：快照引用后迭代
        MyMod.LOG.debug(
            "MECraftingInventory.<init> off server thread ({})",
            Thread.currentThread()
                .getName());
        return IAEStackSafe.collect(storageList)
            .iterator();
    }
}
