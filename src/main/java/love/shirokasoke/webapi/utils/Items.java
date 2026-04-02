package love.shirokasoke.webapi.utils;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ArrayListMultimap;

import love.shirokasoke.webapi.Constant;
import love.shirokasoke.webapi.MyMod;

public class Items {

    private static final ObjectMapper mapper = Constant.mapper;
    public static final ArrayListMultimap<Item, ItemStack> itemOverrides = ArrayListMultimap.create();

    public static ObjectNode dump(Item item, ObjectNode data) {
        ClassUtils.getClassInfo(item, data);
        data.put("id", Item.getIdFromItem(item));
        data.put("registryName", Item.itemRegistry.getNameForObject(item));
        data.put("UnlocalizedName", item.getUnlocalizedName());
        ItemStack stack = new ItemStack(item, 1, 0);
        try {
            data.put("localizedName", stack.getDisplayName());
        } catch (Throwable e) {
            log.e(e);
        }

        data.put("HasSubtypes", item.getHasSubtypes());
        return data;
    }

    public static ObjectNode dump(Item item) {
        return dump(item, mapper.createObjectNode());
    }

    public static ObjectNode dump(ItemStack stack, ObjectNode data) {
        dump(stack.getItem(), data);
        data.put("localizedName", stack.getDisplayName());

        data.put("MaxStackSize", stack.getMaxStackSize());
        data.put("damageable", stack.isItemStackDamageable());
        data.put("damage", stack.getItemDamage());
        data.put("UnlocalizedName", stack.getUnlocalizedName());

        data.set("AttributeModifiers", mapper.valueToTree(stack.getAttributeModifiers()));
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null) {
            data.put("nbbtstr", nbt.toString());
            data.set("nbt", mapper.valueToTree(nbt));
        }
        return data;
    }

    public static ObjectNode dump(ItemStack stack) {
        return dump(stack, mapper.createObjectNode());
    }

    public static List<ItemStack> getPermutations(Item item) {
        if (item == null || item.delegate.name() == null || !item.getHasSubtypes()) return null;
        final List<ItemStack> permutations = new LinkedList<>(itemOverrides.get(item));
        if (permutations.isEmpty()) {
            dumpAll(item, permutations);
            MyMod.LOG.info("{} 检测到{}个子物品", Item.itemRegistry.getNameForObject(item), permutations.size());
            itemOverrides.removeAll(item);
            itemOverrides.putAll(item, permutations);
        }
        return permutations;
    }

    private static void dumpAll(Item item, List<ItemStack> permutations) {
        ItemStack raw = new ItemStack(item, 1, 0);
        String rawName = null;
        try {
            rawName = raw.getDisplayName();
        } catch (Throwable e) {
            log.e(e);
            return;
        }

        String regName = Item.itemRegistry.getNameForObject(item);
        HashSet<String> nameSet = new HashSet<>();
        nameSet.add(rawName);
        permutations.add(raw);
        for (int meta = 1; meta < 32768; meta++) {
            ItemStack subStack = new ItemStack(item, 1, meta);
            String displayName = null;
            try {
                displayName = subStack.getDisplayName();
            } catch (Throwable e) {
                log.e(e);
                return;
            }
            // MyMod.LOG.info(displayName);
            String m = String.valueOf(meta);

            if (displayName != null && !displayName.equals("Unnamed")
                && !displayName.contains("item.null.name")
                && !displayName.contains("Disabled")
                && !displayName.endsWith(".name")
                && !displayName.endsWith("." + m)
                && !displayName.endsWith("#" + m)
                && !displayName.startsWith("Advanced Boiler Chassis")
                && !displayName.startsWith("Miniature color.")
                && nameSet.add(displayName)) {
                permutations.add(subStack);
            } else if (meta > 16 && regName.startsWith("minecraft")) {
                subStack = null;
                return;
            } else {

            }
            subStack = null;
        }
    }
}
