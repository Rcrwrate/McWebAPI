package love.shirokasoke.webapi.mixins.late;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cluster.implementations.CraftingCPUCluster.TaskProgress;
import love.shirokasoke.webapi.utils.Accessor;

@Mixin(Accessor.class)
public class AECPUMixin {

    /**
     * @author shirokasoke
     * @reason 替代反射
     */
    @Overwrite(remap = false)
    public static Map<ICraftingPatternDetails, TaskProgress> CraftingCPUCluster_tasks(CraftingCPUCluster cluster) {
        return ((CraftingCPUClusterAccess) cluster).$getTasks();
    }

    /**
     * @author shirokasoke
     * @reason 替代反射
     */
    @Overwrite(remap = false)
    public static long TaskProgress_value(TaskProgress taskProgress) {
        return ((TaskProgressAccess) taskProgress).$getValue();
    }

    /**
     * @author shirokasoke
     * @reason 替代反射
     */
    @Overwrite(remap = false)
    public static IItemList<IAEStack<?>> CraftingCPUCluster_waitingFor(CraftingCPUCluster cluster) {
        return ((CraftingCPUClusterAccess) cluster).$getWaitingFor();
    }
}
