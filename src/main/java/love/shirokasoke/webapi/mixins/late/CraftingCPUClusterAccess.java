package love.shirokasoke.webapi.mixins.late;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.NamedDimensionalCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cluster.implementations.CraftingCPUCluster.TaskProgress;
import appeng.util.ScheduledReason;

@Mixin(CraftingCPUCluster.class)
public interface CraftingCPUClusterAccess {

    /** 暴露 {@link CraftingCPUCluster#tasks} 等待中的任务 */
    @Accessor(value = "tasks", remap = false)
    Map<ICraftingPatternDetails, TaskProgress> $getTasks();

    /** 暴露 {@link CraftingCPUCluster#waitingFor} 已派发，正在等待的产物 */
    @Accessor(value = "waitingFor", remap = false)
    IItemList<IAEStack<?>> $getWaitingFor();

    /** 暴露 {@link CraftingCPUCluster#providers} 派发任务的设备坐标 */
    @Accessor(value = "providers", remap = false)
    HashMap<IAEStack<?>, List<NamedDimensionalCoord>> $getProviders();

    /** 暴露 {@link CraftingCPUCluster#reasonProvider} （每个图案的调度原因）。 */
    @Accessor(value = "reasonProvider", remap = false)
    HashMap<ICraftingPatternDetails, ScheduledReason> $getReasonProvider();

    /** 暴露 {@link CraftingCPUCluster#waitingForMissing}（等待缺失的原料）。 */
    @Accessor(value = "waitingForMissing", remap = false)
    IItemList<IAEStack<?>> $getWaitingForMissing();

    /** 暴露 {@link CraftingCPUCluster#waiting} （本 tick 内 CPU 是否仍在等待推进）。 */
    @Accessor(value = "waiting", remap = false)
    boolean $getWaiting();

    /** 暴露 {@link CraftingCPUCluster#suspended} 的 suspended 字段（CPU 是否被暂停）。 */
    @Accessor(value = "suspended", remap = false)
    boolean $getSuspended();

    /** 暴露 {@link CraftingCPUCluster#isMissingMode}（材料缺失模式）。 */
    @Accessor(value = "isMissingMode", remap = false)
    boolean $getIsMissingMode();
}
