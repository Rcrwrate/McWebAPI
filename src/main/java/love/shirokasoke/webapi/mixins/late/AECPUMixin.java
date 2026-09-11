package love.shirokasoke.webapi.mixins.late;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.NamedDimensionalCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cluster.implementations.CraftingCPUCluster.TaskProgress;
import appeng.util.ScheduledReason;
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

    /**
     * @author shirokasoke
     * @reason 替代反射
     */
    @Overwrite(remap = false)
    public static List<NamedDimensionalCoord> CraftingCPUCluster_getProviders(CraftingCPUCluster cluster,
        IAEStack<?> is) {
        return ((CraftingCPUClusterAccess) cluster).$getProviders()
            .getOrDefault(is, Collections.EMPTY_LIST);
    }

    /**
     * @author shirokasoke
     * @reason 替代反射
     */
    @Overwrite(remap = false)
    public static ScheduledReason CraftingCPUCluster_getReason(CraftingCPUCluster cluster,
        ICraftingPatternDetails details) {
        return ((CraftingCPUClusterAccess) cluster).$getReasonProvider()
            .getOrDefault(details, ScheduledReason.UNDEFINED);
    }

    /**
     * @author shirokasoke
     * @reason 替代反射
     */
    @Overwrite(remap = false)
    public static IItemList<IAEStack<?>> CraftingCPUCluster_waitingForMissing(CraftingCPUCluster cluster) {
        return ((CraftingCPUClusterAccess) cluster).$getWaitingForMissing();
    }

    /**
     * @author shirokasoke
     * @reason 替代反射
     */
    @Overwrite(remap = false)
    public static boolean CraftingCPUCluster_waiting(CraftingCPUCluster cluster) {
        return ((CraftingCPUClusterAccess) cluster).$getWaiting();
    }

    /**
     * @author shirokasoke
     * @reason 替代反射
     */
    @Overwrite(remap = false)
    public static boolean CraftingCPUCluster_suspended(CraftingCPUCluster cluster) {
        return ((CraftingCPUClusterAccess) cluster).$getSuspended();
    }

    /**
     * @author shirokasoke
     * @reason 替代反射
     */
    @Overwrite(remap = false)
    public static boolean CraftingCPUCluster_isMissingMode(CraftingCPUCluster cluster) {
        return ((CraftingCPUClusterAccess) cluster).$getIsMissingMode();
    }
}
