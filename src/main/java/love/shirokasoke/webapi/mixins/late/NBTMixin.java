package love.shirokasoke.webapi.mixins.late;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.List;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.spongepowered.asm.mixin.Mixin;

import love.shirokasoke.webapi.mixins.early.NBTAccess;
import love.shirokasoke.webapi.mixins.early.NBTListAccess;

@Mixin(love.shirokasoke.webapi.utils.Accessor.class)
public class NBTMixin {

    /**
     * @author shirokasoke
     * @reason 替代反射
     * @return
     */
    @org.spongepowered.asm.mixin.Overwrite(remap = false)
    public static List<NBTBase> NBTTagList_tagList(NBTTagList tagList) {
        return ((NBTListAccess) tagList).$getTagList();
    }

    /**
     * @author shirokasoke
     * @reason 替代反射
     * @return
     */
    @org.spongepowered.asm.mixin.Overwrite(remap = false)
    public static void NBTTagCompound_write(NBTTagCompound nbt, DataOutput output) {
        ((NBTAccess) nbt).$write(output);
    }

    /**
     * @author shirokasoke
     * @reason 替代反射
     * @return
     */
    @org.spongepowered.asm.mixin.Overwrite(remap = false)
    public static void NBTTagCompound_read(NBTTagCompound nbt, DataInput input, int depth, NBTSizeTracker sizeTracker) {
        ((NBTAccess) nbt).$read(input, depth, sizeTracker);
    }
}
