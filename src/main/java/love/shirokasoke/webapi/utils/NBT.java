package love.shirokasoke.webapi.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import love.shirokasoke.webapi.Constant;

/**
 * @apiNote 涉及Mixin {@link love.shirokasoke.webapi.mixins.late.NBTUtilsMixin}
 */
public final class NBT {

    private static final ObjectMapper mapper = Constant.mapper;

    private NBT() {}

    public static void dump(NBTTagCompound nbt, ObjectNode dataNode) {
        if (nbt != null) {
            dataNode.put("nbtstr", nbt.toString());
            dataNode.put("nbtWrite", writeToBase64(nbt));
            ObjectNode data = dataNode.putObject("nbt");
            single(nbt, data);
        }
    }

    public static String writeToBase64(NBTTagCompound nbt) {
        if (nbt == null) {
            return null;
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            Accessor.NBTTagCompound_write(nbt, dos);
            dos.close();
            return Base64.getEncoder()
                .encodeToString(baos.toByteArray());
        } catch (Exception e) {
            Logs.e(e);
            return null;
        }
    }

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
            Accessor.NBTTagCompound_read(nbt, dis, 0, NBTSizeTracker.field_152451_a);
            dis.close();
            return nbt;
        } catch (Exception e) {
            Logs.e(e);
            return null;
        }
    }

    public static void dump(NBTTagCompound nbt, ObjectNode dataNode, String key) {
        if (nbt != null) {
            ObjectNode data = dataNode.putObject(key);
            single(nbt, data);
        }
    }

    /**
     * 统一构造{@link ItemStack}
     */
    public static ItemStack toItemStack(int id, int damage, String tagBase64) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setShort("id", (short) id);
        nbt.setByte("Count", (byte) 1);
        nbt.setShort("Damage", (short) damage);
        if (tagBase64 != null && !tagBase64.isEmpty()) {
            NBTTagCompound tagNbt = readFromBase64(tagBase64);
            if (tagNbt != null) {
                nbt.setTag("tag", tagNbt);
            }
        }
        return ItemStack.loadItemStackFromNBT(nbt);
    }

    private static void single(NBTBase nbtbase, ObjectNode data) {
        if (nbtbase instanceof NBTTagCompound) {
            NBTTagCompound nbt = (NBTTagCompound) nbtbase;
            Iterator<String> iterator = nbt.func_150296_c()
                .iterator();

            while (iterator.hasNext()) {
                String key = iterator.next();
                putValue(key, nbt.getTag(key), data);
            }
        }
    }

    private static void putValue(String key, NBTBase tag, ObjectNode data) {
        if (tag == null) {
            return;
        }

        switch (tag.getId()) {
            case 0 -> {} // END

            case 1 -> // BYTE
                data.put(key, ((NBTTagByte) tag).func_150290_f());

            case 2 -> // SHORT
                data.put(key, ((NBTTagShort) tag).func_150289_e());

            case 3 -> // INT
                data.put(key, ((NBTTagInt) tag).func_150287_d());

            case 4 -> // LONG
                data.put(key, ((NBTTagLong) tag).func_150291_c());

            case 5 -> // FLOAT
                data.put(key, ((NBTTagFloat) tag).func_150288_h());

            case 6 -> // DOUBLE
                data.put(key, ((NBTTagDouble) tag).func_150286_g());

            case 7 -> // BYTE_ARRAY
                data.putPOJO(key, ((NBTTagByteArray) tag).func_150292_c());

            case 8 -> // STRING
                data.put(key, ((NBTTagString) tag).func_150285_a_());

            case 9 -> // LIST
                data.set(key, listToArray((NBTTagList) tag));

            case 10 -> { // COMPOUND
                ObjectNode nested = data.putObject(key);
                single(tag, nested);
            }

            case 11 -> // INT_ARRAY
                data.putPOJO(key, ((NBTTagIntArray) tag).func_150302_c());

            default -> data.put(key, tag.toString());
        }
    }

    private static ArrayNode listToArray(NBTTagList tagList) {
        ArrayNode array = mapper.createArrayNode();
        try {
            List<NBTBase> tmp = Accessor.NBTTagList_tagList(tagList);
            for (NBTBase n : tmp) {
                addArrayElement(array, n);
            }
        } catch (Throwable e) {
            // Logs.e(e);
            NBTTagList tmp = (NBTTagList) tagList.copy();
            while (tmp.tagCount() > 0) {
                NBTBase tag = tmp.removeTag(0);
                addArrayElement(array, tag);
            }
            // 不能使用getCompoundTagAt
            // for (int i = 0; i < tagList.tagCount(); i++) {
            // NBTBase tag = tagList.getCompoundTagAt(i);
            // addArrayElement(array, tag);
            // }
        }
        return array;
    }

    private static void addArrayElement(ArrayNode array, NBTBase tag) {
        if (tag == null) {
            array.addNull();
            return;
        }

        switch (tag.getId()) {
            case 0 -> array.addNull(); // END

            case 1 -> // BYTE
                array.add(((NBTTagByte) tag).func_150290_f());

            case 2 -> // SHORT
                array.add(((NBTTagShort) tag).func_150289_e());

            case 3 -> // INT
                array.add(((NBTTagInt) tag).func_150287_d());

            case 4 -> // LONG
                array.add(((NBTTagLong) tag).func_150291_c());

            case 5 -> // FLOAT
                array.add(((NBTTagFloat) tag).func_150288_h());

            case 6 -> // DOUBLE
                array.add(((NBTTagDouble) tag).func_150286_g());

            case 7 -> // BYTE_ARRAY
                array.addPOJO(((NBTTagByteArray) tag).func_150292_c());

            case 8 -> // STRING
                array.add(tag.toString());

            case 9 -> // LIST
                array.add(listToArray((NBTTagList) tag));

            case 10 -> { // COMPOUND
                ObjectNode nested = mapper.createObjectNode();
                single(tag, nested);
                array.add(nested);
            }

            case 11 -> // INT_ARRAY
                array.addPOJO(((NBTTagIntArray) tag).func_150302_c());

            default -> array.add(tag.toString());
        }
    }
}
