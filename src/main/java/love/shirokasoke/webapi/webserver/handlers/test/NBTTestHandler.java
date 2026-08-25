package love.shirokasoke.webapi.webserver.handlers.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.utils.NBT;
import love.shirokasoke.webapi.webserver.RouteHandler;

public class NBTTestHandler implements RouteHandler {

    @Override
    public String getPath() {
        return "/test/nbt";
    }

    @Override
    public void run(HttpExchange exchange) throws Exception {
        ObjectNode data = mapper.createObjectNode();
        // 手工构造一个足够复杂的 NBTTagCompound 验证 sort：
        // nbt3 与 nbt4 内容完全一致，仅各层 key 的插入顺序相反
        NBTTagCompound nbt3 = buildComplexNBT(false);
        NBTTagCompound nbt4 = buildComplexNBT(true);
        NBTTagCompound sorted3 = NBT.sort(nbt3);
        NBTTagCompound sorted4 = NBT.sort(nbt4);
        NBT.dump(nbt3, data, "1");
        NBT.dump(sorted3, data, "2");
        NBT.dump(nbt4, data, "3");
        NBT.dump(sorted4, data, "4");
        // 插入顺序不影响排序结果
        data.put(
            "sortOrderIndependent",
            NBT.writeToBase64(sorted3)
                .equals(NBT.writeToBase64(sorted4)));
        // 重复排序结果不变（幂等）
        data.put(
            "sortIdempotent",
            NBT.writeToBase64(sorted3)
                .equals(NBT.writeToBase64(NBT.sort(sorted3))));
        // 排序不丢失内容（equals 为深比较，与 key 顺序无关）
        data.put("sortContentEqual", sorted3.equals(nbt3) && sorted4.equals(nbt4));
        sendResponse(exchange, data);
    }

    /**
     * 手工构造一个足够复杂的 {@link NBTTagCompound}：覆盖全部 11 种标签类型、
     * 多层嵌套 Compound、Compound 列表、嵌套 List，且各层 key 均按非字典序插入。
     *
     * @param reversed 是否以相反顺序插入各层 key（标签内容保持一致）
     */
    private static NBTTagCompound buildComplexNBT(boolean reversed) {
        LinkedHashMap<String, NBTBase> entries = new LinkedHashMap<>();
        entries.put("zebra", new NBTTagString("stripes"));
        entries.put("apple", new NBTTagInt(42));
        entries.put("mango", buildNestedCompound(reversed));
        entries.put("banana", buildCompoundList(reversed));
        entries.put("kiwi", new NBTTagByte((byte) 7));
        entries.put("grape", new NBTTagDouble(3.1415926));
        entries.put("lemon", buildNestedIntLists());
        entries.put("peach", new NBTTagIntArray(new int[] { 9, 7, 5, 3 }));
        entries.put("cherry", new NBTTagByteArray(new byte[] { 4, 2, 0 }));
        entries.put("fig", new NBTTagLong(9876543210L));
        entries.put("melon", new NBTTagFloat(2.71828f));
        entries.put("plum", new NBTTagShort((short) 1234));
        return compoundOf(entries, reversed);
    }

    /** 三层嵌套 Compound，每层 key 均乱序插入 */
    private static NBTTagCompound buildNestedCompound(boolean reversed) {
        LinkedHashMap<String, NBTBase> deep = new LinkedHashMap<>();
        deep.put("y", new NBTTagString("deep"));
        deep.put("a", new NBTTagInt(1));
        deep.put("n", new NBTTagDouble(0.125));

        LinkedHashMap<String, NBTBase> entries = new LinkedHashMap<>();
        entries.put("delta", new NBTTagInt(100));
        entries.put("alpha", new NBTTagString("nested"));
        entries.put("charlie", compoundOf(deep, reversed));
        entries.put("bravo", new NBTTagDouble(0.5));
        return compoundOf(entries, reversed);
    }

    /** Compound 列表：元素顺序固定，但每个 Compound 内部 key 乱序插入 */
    private static NBTTagList buildCompoundList(boolean reversed) {
        LinkedHashMap<String, NBTBase> first = new LinkedHashMap<>();
        first.put("zeta", new NBTTagInt(1));
        first.put("alpha", new NBTTagString("x"));
        first.put("mid", new NBTTagLong(1000000L));

        LinkedHashMap<String, NBTBase> second = new LinkedHashMap<>();
        second.put("beta", new NBTTagByte((byte) 2));
        second.put("abc", new NBTTagFloat(1.5f));

        NBTTagList list = new NBTTagList();
        list.appendTag(compoundOf(first, reversed));
        list.appendTag(compoundOf(second, reversed));
        return list;
    }

    /** 嵌套List：{@code List<List<Int>>}，验证 sortList 的递归处理 */
    private static NBTTagList buildNestedIntLists() {
        NBTTagList inner1 = new NBTTagList();
        inner1.appendTag(new NBTTagInt(3));
        inner1.appendTag(new NBTTagInt(1));
        NBTTagList inner2 = new NBTTagList();
        inner2.appendTag(new NBTTagInt(2));
        NBTTagList list = new NBTTagList();
        list.appendTag(inner1);
        list.appendTag(inner2);
        return list;
    }

    /** 按指定顺序将 entries 写入新建的 {@link NBTTagCompound} */
    private static NBTTagCompound compoundOf(LinkedHashMap<String, NBTBase> entries, boolean reversed) {
        NBTTagCompound nbt = new NBTTagCompound();
        List<String> keys = new ArrayList<>(entries.keySet());
        if (reversed) {
            Collections.reverse(keys);
        }
        for (String key : keys) {
            nbt.setTag(key, entries.get(key));
        }
        return nbt;
    }
}
