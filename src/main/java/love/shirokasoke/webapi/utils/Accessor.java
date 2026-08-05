package love.shirokasoke.webapi.utils;

import java.io.DataInput;
import java.io.DataOutput;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cluster.implementations.CraftingCPUCluster.TaskProgress;
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
     * 访问 {@link MTEMultiBlockBase#mSmartInputHatches}
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
            return new ArrayList<>();
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

    /** 缓存 {@link CraftingCPUCluster} 的私有 tasks 字段 */
    private static Field craftingCPUClusterTasks = null;

    /**
     * 访问 {@link CraftingCPUCluster} 的私有 tasks 字段。
     *
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.AECPUMixin}
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

    /** 缓存 TaskProgress 的私有 value 字段 */
    private static Field taskProgressValue = null;

    /**
     * 访问 {@link TaskProgress} 的私有 value 字段。
     *
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.AECPUMixin}
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

    /** 缓存 {@link CraftingCPUCluster} 的私有 waitingFor 字段 */
    private static Field craftingCPUClusterWaitingFor = null;

    /**
     * 访问 {@link CraftingCPUCluster} 的私有 waitingFor 字段。
     *
     * @apiNote 相关Mixin {@link love.shirokasoke.webapi.mixins.late.AECPUMixin}
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
}
