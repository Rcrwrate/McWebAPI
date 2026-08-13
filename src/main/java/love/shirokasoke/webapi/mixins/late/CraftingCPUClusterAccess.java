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

@Mixin(CraftingCPUCluster.class)
public interface CraftingCPUClusterAccess {

    /** 暴露 {@link CraftingCPUCluster} 的 tasks 字段。 */
    @Accessor(value = "tasks", remap = false)
    Map<ICraftingPatternDetails, TaskProgress> $getTasks();

    /** 暴露 {@link CraftingCPUCluster} 的 waitingFor 字段。 */
    @Accessor(value = "waitingFor", remap = false)
    IItemList<IAEStack<?>> $getWaitingFor();

    @Accessor(value = "providers", remap = false)
    HashMap<IAEStack<?>, List<NamedDimensionalCoord>> $getProviders();
}
