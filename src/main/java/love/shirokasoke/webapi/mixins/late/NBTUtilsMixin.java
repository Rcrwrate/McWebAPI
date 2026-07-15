package love.shirokasoke.webapi.mixins.late;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Base64;

import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;

import love.shirokasoke.webapi.mixins.early.NBTAccess;
import love.shirokasoke.webapi.utils.log;

@Mixin(love.shirokasoke.webapi.utils.NBT.class)
public class NBTUtilsMixin {

    /**
     * @author shirokasoke
     * @reason 替代反射
     * @param base64
     * @return
     */
    @org.spongepowered.asm.mixin.Overwrite(remap = false)
    public static String writeToBase64(NBTTagCompound nbt) {
        if (nbt == null) {
            return null;
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            ((NBTAccess) nbt).$write(dos);
            dos.close();
            return Base64.getEncoder()
                .encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.e(e);
            return null;
        }
    }

    /**
     * @author shirokasoke
     * @reason 替代反射
     * @param base64
     * @return
     */
    @org.spongepowered.asm.mixin.Overwrite(remap = false)
    public static NBTTagCompound readFromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder()
                .decode(base64);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bais);
            NBTTagCompound nbt = new NBTTagCompound();
            ((NBTAccess) nbt).$read(dis, 0, NBTSizeTracker.field_152451_a);
            dis.close();
            return nbt;
        } catch (Exception e) {
            log.e(e);
            return null;
        }
    }
}
