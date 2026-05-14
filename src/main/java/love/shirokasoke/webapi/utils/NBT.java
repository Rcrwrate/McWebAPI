package love.shirokasoke.webapi.utils;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTBase;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import love.shirokasoke.webapi.Constant;

public class NBT {

    private static final ObjectMapper mapper = Constant.mapper;

    public static void dump(NBTTagCompound nbt, ObjectNode dataNode) {
        if (nbt != null) {
            dataNode.put("nbtstr", nbt.toString());
            ObjectNode data = dataNode.putObject("nbt");
            single(nbt, data);
        }
    }

    public static void dump(NBTTagCompound nbt, ObjectNode dataNode, String key) {
        if (nbt != null) {
            ObjectNode data = dataNode.putObject(key);
            single(nbt, data);
        }
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
            case 0: // END
                break;

            case 1: // BYTE
                data.put(key, ((NBTTagByte) tag).func_150290_f());
                break;

            case 2: // SHORT
                data.put(key, ((NBTTagShort) tag).func_150289_e());
                break;

            case 3: // INT
                data.put(key, ((NBTTagInt) tag).func_150287_d());
                break;

            case 4: // LONG
                data.put(key, ((NBTTagLong) tag).func_150291_c());
                break;

            case 5: // FLOAT
                data.put(key, ((NBTTagFloat) tag).func_150288_h());
                break;

            case 6: // DOUBLE
                data.put(key, ((NBTTagDouble) tag).func_150286_g());
                break;

            case 7: // BYTE_ARRAY
                data.putPOJO(key, ((NBTTagByteArray) tag).func_150292_c());
                break;

            case 8: // STRING
                data.put(key, ((NBTTagString) tag).func_150285_a_());
                break;

            case 9: // LIST
                data.set(key, listToArray((NBTTagList) tag));
                break;

            case 10: // COMPOUND
                ObjectNode nested = data.putObject(key);
                single(tag, nested);
                break;

            case 11: // INT_ARRAY
                data.putPOJO(key, ((NBTTagIntArray) tag).func_150302_c());
                break;

            default:
                data.put(key, tag.toString());
                break;
        }
    }

    private static ArrayNode listToArray(NBTTagList tagList) {
        ArrayNode array = mapper.createArrayNode();
        try {
            Field f = tagList.getClass()
                .getDeclaredField("field_74747_a");
            // field_74747_a是编译后的tagList
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<NBTBase> tmp = (List<NBTBase>) f.get(tagList);
            for (NBTBase n : tmp) {
                addArrayElement(array, n);
            }
        } catch (Throwable e) {
            log.e(e);
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
            case 0: // END
                array.addNull();
                break;

            case 1: // BYTE
                array.add(((NBTTagByte) tag).func_150290_f());
                break;

            case 2: // SHORT
                array.add(((NBTTagShort) tag).func_150289_e());
                break;

            case 3: // INT
                array.add(((NBTTagInt) tag).func_150287_d());
                break;

            case 4: // LONG
                array.add(((NBTTagLong) tag).func_150291_c());
                break;

            case 5: // FLOAT
                array.add(((NBTTagFloat) tag).func_150288_h());
                break;

            case 6: // DOUBLE
                array.add(((NBTTagDouble) tag).func_150286_g());
                break;

            case 7: // BYTE_ARRAY
                array.addPOJO(((NBTTagByteArray) tag).func_150292_c());
                break;

            case 8: // STRING
                array.add(tag.toString());
                break;

            case 9: // LIST
                array.add(listToArray((NBTTagList) tag));
                break;

            case 10: // COMPOUND
                ObjectNode nested = mapper.createObjectNode();
                single(tag, nested);
                array.add(nested);
                break;

            case 11: // INT_ARRAY
                array.addPOJO(((NBTTagIntArray) tag).func_150302_c());
                break;

            default:
                array.add(tag.toString());
                break;
        }
    }

    // ==================== JSON -> NBT ====================

    /**
     * 将 Jackson JSON 对象转换为 NBTTagCompound。
     *
     * <p>
     * 类型映射规则：
     * <ul>
     * <li>Object -> NBTTagCompound</li>
     * <li>Array -> NBTTagList</li>
     * <li>String -> NBTTagString</li>
     * <li>Boolean -> NBTTagByte (1/0)</li>
     * <li>Floating point -> NBTTagDouble</li>
     * <li>Integer 根据范围自动选择 byte / short / int / long</li>
     * </ul>
     */
    public static NBTTagCompound fromJson(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        NBTTagCompound nbt = new NBTTagCompound();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            putJsonValue(nbt, entry.getKey(), entry.getValue());
        }
        return nbt;
    }

    private static void putJsonValue(NBTTagCompound nbt, String key, JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isObject()) {
            nbt.setTag(key, fromJson(value));
        } else if (value.isArray()) {
            nbt.setTag(key, listFromJson(value));
        } else if (value.isTextual()) {
            nbt.setString(key, value.asText());
        } else if (value.isBoolean()) {
            nbt.setBoolean(key, value.asBoolean());
        } else if (value.isFloatingPointNumber()) {
            nbt.setDouble(key, value.asDouble());
        } else if (value.isIntegralNumber()) {
            long l = value.asLong();
            if (l >= Byte.MIN_VALUE && l <= Byte.MAX_VALUE) {
                nbt.setByte(key, (byte) l);
            } else if (l >= Short.MIN_VALUE && l <= Short.MAX_VALUE) {
                nbt.setShort(key, (short) l);
            } else if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                nbt.setInteger(key, (int) l);
            } else {
                nbt.setLong(key, l);
            }
        }
    }

    private static NBTTagList listFromJson(JsonNode array) {
        NBTTagList list = new NBTTagList();
        for (JsonNode element : array) {
            if (element == null || element.isNull()) {
                continue;
            }
            if (element.isObject()) {
                list.appendTag(fromJson(element));
            } else if (element.isTextual()) {
                list.appendTag(new NBTTagString(element.asText()));
            } else if (element.isBoolean()) {
                list.appendTag(new NBTTagByte((byte) (element.asBoolean() ? 1 : 0)));
            } else if (element.isFloatingPointNumber()) {
                list.appendTag(new NBTTagDouble(element.asDouble()));
            } else if (element.isIntegralNumber()) {
                long l = element.asLong();
                if (l >= Byte.MIN_VALUE && l <= Byte.MAX_VALUE) {
                    list.appendTag(new NBTTagByte((byte) l));
                } else if (l >= Short.MIN_VALUE && l <= Short.MAX_VALUE) {
                    list.appendTag(new NBTTagShort((short) l));
                } else if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    list.appendTag(new NBTTagInt((int) l));
                } else {
                    list.appendTag(new NBTTagLong(l));
                }
            }
        }
        return list;
    }
}
