package love.shirokasoke.webapi.client.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import codechicken.nei.ThreadOperationTimer;
import codechicken.nei.api.ItemInfo;
import cpw.mods.fml.common.registry.GameData;
import love.shirokasoke.webapi.MyMod;

/**
 * 客户端物品列表工具类。
 * 参考 {@link codechicken.nei.ItemList#getPermutations } 的逻辑，获取客户端所有可见物品的完整列表。
 */
public class CItems {

    private static final HashSet<Item> erroredItems = new HashSet<>();
    private static final HashSet<String> stackTraces = new HashSet<>();

    public static List<ItemStack> getAllItems() {
        List<ItemStack> items = new ArrayList<>();
        HashSet<ItemStack> unique = new HashSet<>();

        for (Object obj : GameData.getItemRegistry()) {
            if (!(obj instanceof Item item)) continue;
            if (item == null || item.delegate.name() == null || erroredItems.contains(item)) continue;

            try {
                List<ItemStack> permutations = getPermutations(item);

                for (ItemStack stack : permutations) {
                    if (!unique.contains(stack)) {
                        unique.add(stack);
                        items.add(stack);
                    }
                }
            } catch (Throwable t) {
                MyMod.LOG.error("[ClientItemList] 获取物品 {} 的子类型时出错", item, t);
                erroredItems.add(item);
            }
        }

        return items;
    }

    public static List<ItemStack> getPermutations(Item item) {
        final List<ItemStack> permutations = new LinkedList<>(ItemInfo.itemOverrides.get(item));

        if (permutations.isEmpty()) {
            item.getSubItems(item, null, permutations);
        }

        if (permutations.isEmpty()) {
            damageSearch(item, permutations);
        }

        permutations.addAll(ItemInfo.itemVariants.get(item));

        return permutations.stream()
            .filter(
                stack -> stack.getItem() != null && stack.getItem().delegate.name() != null
                    && !ItemInfo.isHidden(stack))
            .toList();
    }

    private static void damageSearch(Item item, List<ItemStack> permutations) {
        HashSet<String> damageIconSet = new HashSet<>();
        for (int damage = 0; damage < 16; damage++) {
            try {
                ItemStack itemstack = new ItemStack(item, 1, damage);
                IIcon icon = item.getIconIndex(itemstack);
                String name = getTooltip(itemstack);
                String s = name + "@" + (icon == null ? 0 : icon.hashCode());
                if (damageIconSet.add(s)) {
                    permutations.add(itemstack);
                }
            } catch (ThreadOperationTimer.TimeoutException t) {
                throw t;
            } catch (Throwable t) {
                logErrorOnce(
                    t,
                    "Ommiting " + item
                        + ":"
                        + damage
                        + " "
                        + item.getClass()
                            .getSimpleName(),
                    item.toString());
            }
        }
    }

    private static String getTooltip(ItemStack stack) {
        try {
            return String.join("\n", stack.getTooltip(Minecraft.getMinecraft().thePlayer, false));
        } catch (Throwable ignored) {}

        return "";
    }

    private static void logErrorOnce(Throwable t, String message, String key) {
        if (stackTraces.add(key)) {
            MyMod.LOG.error(message, t);
        }
    }
}
