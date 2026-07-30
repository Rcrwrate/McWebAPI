package love.shirokasoke.webapi.mixins.late;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.me.cluster.implementations.CraftingCPUCluster.TaskProgress;

@Mixin(TaskProgress.class)
public interface TaskProgressAccess {

    /** 暴露 {@link TaskProgress} 的 value 字段。 */
    @Accessor(value = "value", remap = false)
    long $getValue();
}
