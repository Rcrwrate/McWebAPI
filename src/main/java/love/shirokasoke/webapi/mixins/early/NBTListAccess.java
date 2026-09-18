package love.shirokasoke.webapi.mixins.early;

import java.util.List;

import net.minecraft.nbt.NBTTagList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NBTTagList.class)
public interface NBTListAccess {

    @Accessor(value = "tagList", remap = true)
    public List $getTagList();
}
