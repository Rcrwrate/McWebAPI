package love.shirokasoke.webapi.mixins.early;

import java.io.DataInput;
import java.io.DataOutput;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NBTTagCompound.class)
public interface NBTAccess {

    /**
     * 暴露 {@link NBTTagCompound} 的私有 read 方法（混淆名 {@code func_152446_a}）。
     */
    @Invoker("func_152446_a")
    void $read(DataInput input, int depth, NBTSizeTracker tracker);

    /**
     * 暴露 {@link NBTBase} 的私有 write 方法（混淆名 {@code func_74734_a}）。
     */
    @Invoker("write")
    void $write(DataOutput output);
}
