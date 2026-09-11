package love.shirokasoke.webapi.utils;

import java.io.DataInput;
import java.io.DataOutput;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.NamedDimensionalCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cluster.implementations.CraftingCPUCluster.TaskProgress;
import appeng.util.ScheduledReason;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.ISmartInputHatch;

/**
 * 统一反射管理单元
 * Accessor
 */
public final class Accessor {

    private Accessor() {}

    /** 缓存 {@link MTEMultiBlockBase#mSmartInputHatches } */
    private static Field mSmartInputHatches = null;

    /**
     * 访问 {@link MTEMultiBlockBase#mSmartInputHatches} 类型 {@link ArrayList} (fail-fast，无需关心)
     */
    public static ArrayList<ISmartInputHatch> MTEMultiBlockBase_mSmartInputHatches(MTEMultiBlockBase multi) {
        try {
            if (mSmartInputHatches == null) {
                mSmartInputHatches = MTEMultiBlockBase.class.getDeclaredField("mSmartInputHatches");
                mSmartInputHatches.setAccessible(true);
            }
            return (ArrayList<ISmartInputHatch>) mSmartInputHatches.get(multi);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            Logs.e(e);
            return new ArrayList<>();
        }
    }

    /** 缓存 {@link NBTTagList#tagList } 混淆之后是field_74747_a */
    private static Field field_74747_a = null;

    /** 访问 {@link NBTTagList#tagList } 混淆之后是field_74747_a */
    public static List<NBTBase> NBTTagList_tagList(NBTTagList tagList) {
        try {
            if (field_74747_a == null) {
                field_74747_a = ReflectionHelper.findField(NBTTagList.class, "field_74747_a", "tagList");
            }
            return (List<NBTBase>) field_74747_a.get(tagList);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            Logs.e(e);
            return List.of();
        }
    }

    /** 缓存 {@link NBTTagCompound#write } 的私有 write 方法 */
    private static Method nbtWrite = null;

    /**
     * 访问 {@link NBTTagCompound#write } 的私有 read 方法
     * 
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.NBTMixin#NBTTagCompound_write}
     */
    public static void NBTTagCompound_write(NBTTagCompound nbt, DataOutput output) {
        try {
            if (nbtWrite == null) {
                nbtWrite = ReflectionHelper.findMethod(
                    NBTTagCompound.class,
                    nbt,
                    new String[] { "func_74734_a", "write" },
                    java.io.DataOutput.class);
            }
            nbtWrite.invoke(nbt, output);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Logs.e(e);
        }
    }

    /** 缓存 {@link NBTTagCompound#func_152446_a } 的私有 read 方法 */
    private static Method nbtRead = null;

    /**
     * 访问 {@link NBTTagCompound#func_152446_a } 的私有 read 方法
     * 
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.NBTMixin#NBTTagCompound_read}
     */
    public static void NBTTagCompound_read(NBTTagCompound nbt, DataInput input, int depth, NBTSizeTracker sizeTracker) {
        try {
            if (nbtRead == null) {
                nbtRead = NBTTagCompound.class
                    .getDeclaredMethod("func_152446_a", java.io.DataInput.class, int.class, NBTSizeTracker.class);
                nbtRead.setAccessible(true);
            }
            nbtRead.invoke(nbt, input, depth, sizeTracker);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Logs.e(e);
        }
    }

    /** 缓存私有字段 {@link CraftingCPUCluster#tasks} */
    private static Field craftingCPUClusterTasks = null;

    /**
     * 访问私有字段 {@link CraftingCPUCluster#tasks} 类型 {@link java.util.TreeMap} (fail-fast，无需关心)
     *
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.AECPUMixin#CraftingCPUCluster_tasks}
     */
    public static Map<ICraftingPatternDetails, TaskProgress> CraftingCPUCluster_tasks(CraftingCPUCluster cluster) {
        try {
            if (craftingCPUClusterTasks == null) {
                craftingCPUClusterTasks = CraftingCPUCluster.class.getDeclaredField("tasks");
                craftingCPUClusterTasks.setAccessible(true);
            }
            return (Map<ICraftingPatternDetails, TaskProgress>) craftingCPUClusterTasks.get(cluster);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            Logs.e(e);
            return Map.of();
        }
    }

    /** 缓存私有字段 {@link TaskProgress#value} */
    private static Field taskProgressValue = null;

    /**
     * 访问私有字段 {@link TaskProgress#value} 访问安全
     *
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.AECPUMixin#TaskProgress_value}
     */
    public static long TaskProgress_value(TaskProgress taskProgress) {
        try {
            if (taskProgressValue == null) {
                taskProgressValue = TaskProgress.class.getDeclaredField("value");
                taskProgressValue.setAccessible(true);
            }
            return taskProgressValue.getLong(taskProgress);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            Logs.e(e);
            return -1;
        }
    }

    /** 缓存私有字段 {@link CraftingCPUCluster#waitingFor} */
    private static Field craftingCPUClusterWaitingFor = null;

    /**
     * 访问私有字段 {@link CraftingCPUCluster#waitingFor}
     * <p>
     * 相关类型 {@link appeng.util.item.IAEStackList} -> {@link java.util.IdentityHashMap} (fast fail，无需关心)
     *
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.AECPUMixin#CraftingCPUCluster_waitingFor}
     */
    public static IItemList<IAEStack<?>> CraftingCPUCluster_waitingFor(CraftingCPUCluster cluster) {
        try {
            if (craftingCPUClusterWaitingFor == null) {
                craftingCPUClusterWaitingFor = CraftingCPUCluster.class.getDeclaredField("waitingFor");
                craftingCPUClusterWaitingFor.setAccessible(true);
            }
            return (IItemList<IAEStack<?>>) craftingCPUClusterWaitingFor.get(cluster);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            Logs.e(e);
            return null;
        }
    }

    private static Field craftingCPUClusterProviders = null;

    /**
     * 访问私有字段 {@link CraftingCPUCluster#providers}
     * <p>
     * 直接调用{@link CraftingCPUCluster#getProviders(IAEStack)}存在跨线程写入的风险，采用反射绕过
     *
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.AECPUMixin#CraftingCPUCluster_getProviders}
     */
    public static List<NamedDimensionalCoord> CraftingCPUCluster_getProviders(CraftingCPUCluster cluster,
        IAEStack<?> is) {
        Map<IAEStack<?>, List<NamedDimensionalCoord>> providers = Collections.emptyMap();
        try {
            if (craftingCPUClusterProviders == null) {
                craftingCPUClusterProviders = CraftingCPUCluster.class.getDeclaredField("providers");
                craftingCPUClusterProviders.setAccessible(true);
            }
            providers = (HashMap<IAEStack<?>, List<NamedDimensionalCoord>>) craftingCPUClusterProviders.get(cluster);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            Logs.e(e);
            return Collections.EMPTY_LIST;
        }
        return providers.getOrDefault(is, Collections.EMPTY_LIST);
    }

    /** 缓存私有字段 {@link CraftingCPUCluster#reasonProvider} */
    private static Field craftingCPUClusterReasonProvider = null;

    /**
     * 访问私有字段 {@link CraftingCPUCluster#reasonProvider}，获取某图案的调度原因
     *
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.AECPUMixin#CraftingCPUCluster_getReason}
     */
    public static ScheduledReason CraftingCPUCluster_getReason(CraftingCPUCluster cluster,
        ICraftingPatternDetails details) {
        try {
            if (craftingCPUClusterReasonProvider == null) {
                craftingCPUClusterReasonProvider = CraftingCPUCluster.class.getDeclaredField("reasonProvider");
                craftingCPUClusterReasonProvider.setAccessible(true);
            }
            Map<ICraftingPatternDetails, ScheduledReason> reasonProvider = (Map<ICraftingPatternDetails, ScheduledReason>) craftingCPUClusterReasonProvider
                .get(cluster);
            return reasonProvider.getOrDefault(details, ScheduledReason.UNDEFINED);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            Logs.e(e);
            return ScheduledReason.UNDEFINED;
        }
    }

    /** 缓存私有字段 {@link CraftingCPUCluster#waitingForMissing} */
    private static Field craftingCPUClusterWaitingForMissing = null;

    /**
     * 访问私有字段 {@link CraftingCPUCluster#waitingForMissing}（等待缺失的原料）
     * <p>
     * 相关类型 {@link appeng.util.item.IAEStackList} -> {@link java.util.IdentityHashMap} (fast fail，无需关心)
     *
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.AECPUMixin#CraftingCPUCluster_waitingForMissing}
     */
    public static IItemList<IAEStack<?>> CraftingCPUCluster_waitingForMissing(CraftingCPUCluster cluster) {
        try {
            if (craftingCPUClusterWaitingForMissing == null) {
                craftingCPUClusterWaitingForMissing = CraftingCPUCluster.class.getDeclaredField("waitingForMissing");
                craftingCPUClusterWaitingForMissing.setAccessible(true);
            }
            return (IItemList<IAEStack<?>>) craftingCPUClusterWaitingForMissing.get(cluster);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            Logs.e(e);
            return null;
        }
    }

    /** 缓存私有字段 {@link CraftingCPUCluster#waiting} */
    private static Field craftingCPUClusterWaiting = null;

    /**
     * 访问私有字段 {@link CraftingCPUCluster#waiting}（本 tick 内 CPU 是否仍在等待推进）
     *
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.AECPUMixin#CraftingCPUCluster_waiting}
     */
    public static boolean CraftingCPUCluster_waiting(CraftingCPUCluster cluster) {
        try {
            if (craftingCPUClusterWaiting == null) {
                craftingCPUClusterWaiting = CraftingCPUCluster.class.getDeclaredField("waiting");
                craftingCPUClusterWaiting.setAccessible(true);
            }
            return craftingCPUClusterWaiting.getBoolean(cluster);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            Logs.e(e);
            return false;
        }
    }

    /** 缓存私有字段 {@link CraftingCPUCluster#suspended} */
    private static Field craftingCPUClusterSuspended = null;

    /**
     * 访问私有字段 {@link CraftingCPUCluster#suspended}（CPU 是否被暂停）
     * 
     * @deprecated {@link CraftingCPUCluster#isSuspended()}
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.AECPUMixin#CraftingCPUCluster_suspended}
     */
    @Deprecated
    public static boolean CraftingCPUCluster_suspended(CraftingCPUCluster cluster) {
        try {
            if (craftingCPUClusterSuspended == null) {
                craftingCPUClusterSuspended = CraftingCPUCluster.class.getDeclaredField("suspended");
                craftingCPUClusterSuspended.setAccessible(true);
            }
            return craftingCPUClusterSuspended.getBoolean(cluster);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            Logs.e(e);
            return false;
        }
    }

    /** 缓存私有字段 {@link CraftingCPUCluster#isMissingMode} */
    private static Field craftingCPUClusterIsMissingMode = null;

    /**
     * 访问私有字段 {@link CraftingCPUCluster#isMissingMode}（材料缺失模式）
     * 
     * @deprecated {@link CraftingCPUCluster#isMissingMode()}
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.AECPUMixin#CraftingCPUCluster_isMissingMode}
     */
    @Deprecated
    public static boolean CraftingCPUCluster_isMissingMode(CraftingCPUCluster cluster) {
        try {
            if (craftingCPUClusterIsMissingMode == null) {
                craftingCPUClusterIsMissingMode = CraftingCPUCluster.class.getDeclaredField("isMissingMode");
                craftingCPUClusterIsMissingMode.setAccessible(true);
            }
            return craftingCPUClusterIsMissingMode.getBoolean(cluster);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            Logs.e(e);
            return false;
        }
    }
}
