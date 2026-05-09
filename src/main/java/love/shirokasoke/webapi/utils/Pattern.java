package love.shirokasoke.webapi.utils;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.Platform;
import love.shirokasoke.webapi.Constant;

/**
 * AE2 样板（Encoded Pattern）序列化工具。
 * 用于将 AE2 编码样板（ItemEncodedPattern）的 NBT 数据解析为 JSON，
 * 包括输入/输出物品列表、替代设置、作者信息以及通过 PatternHelper 解析的浓缩输入/输出。
 */
public class Pattern {

    private static final ObjectMapper mapper = Constant.mapper;

    /**
     * 仅通过 NBT 解析样板信息，不依赖 World。
     * 适合在无法提供 World 上下文时使用（如纯物品查看）。
     *
     * @param stack AE2 编码样板物品堆栈
     * @return 包含样板信息的 JSON 对象
     */
    public static ObjectNode dump(ItemStack stack) {
        return dump(stack, null);
    }

    /**
     * 解析 AE2 编码样板的完整信息。
     * 如果提供了 World，还会通过 {@link ICraftingPatternItem#getPatternForItem} 获取
     * {@link ICraftingPatternDetails}，以补充浓缩后的输入/输出和优先级信息。
     *
     * @param stack AE2 编码样板物品堆栈
     * @param world 当前世界实例，用于 PatternHelper 解析；可为 null
     * @return 包含样板信息的 JSON 对象
     */
    public static ObjectNode dump(ItemStack stack, World world) {
        ObjectNode node = Items.dump(stack);

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            return node;
        }

        // === NBT 基础属性 ===
        // crafting: true 表示工作台合成样板，false 表示加工/处理样板
        node.put("crafting", nbt.getBoolean("crafting"));
        // substitute: 是否允许自动替代输入材料（如不同颜色的羊毛）
        node.put("substitute", nbt.getBoolean("substitute"));
        // beSubstitute: 是否允许该样板自身被其他样板替代
        node.put("beSubstitute", nbt.getBoolean("beSubstitute"));

        // author: 编码该模板的玩家名字
        String author = nbt.getString("author");
        if (!author.isEmpty()) {
            node.put("author", author);
        }

        // === 原始输入列表（含 null） ===
        // NBT 中 "in" 标签是一个 NBTTagList，每个元素是一个 NBTTagCompound（物品序列化后的 NBT）
        ArrayNode inputs = mapper.createArrayNode();
        NBTTagList inTag = nbt.getTagList("in", 10); // 10 = NBTTagCompound
        for (int i = 0; i < inTag.tagCount(); i++) {
            NBTTagCompound tag = inTag.getCompoundTagAt(i);
            // 空标签表示该格子没有物品
            if (tag.hasNoTags()) {
                inputs.addNull();
            } else {
                // 从 NBT 还原 ItemStack
                ItemStack s = Platform.loadItemStackFromNBT(tag);
                if (s != null) {
                    inputs.add(Items.dump(s));
                } else {
                    inputs.addNull();
                }
            }
        }
        node.set("inputs", inputs);

        // === 原始输出列表 ===
        // 加工配方可能有多个输出，合成配方通常只有一个
        ArrayNode outputs = mapper.createArrayNode();
        NBTTagList outTag = nbt.getTagList("out", 10);
        for (int i = 0; i < outTag.tagCount(); i++) {
            NBTTagCompound tag = outTag.getCompoundTagAt(i);
            if (tag.hasNoTags()) {
                outputs.addNull();
            } else {
                ItemStack s = Platform.loadItemStackFromNBT(tag);
                if (s != null) {
                    outputs.add(Items.dump(s));
                } else {
                    outputs.addNull();
                }
            }
        }
        node.set("outputs", outputs);

        // === 通过 PatternHelper 补充高级信息（需要 World） ===
        // ICraftingPatternItem 接口由 ItemEncodedPattern 实现，提供 getPatternForItem 方法
        if (world != null && stack.getItem() instanceof ICraftingPatternItem) {
            try {
                ICraftingPatternDetails details = ((ICraftingPatternItem) stack.getItem())
                    .getPatternForItem(stack, world);
                if (details != null) {
                    // isCraftable: 同 NBT 中的 crafting 字段，由 PatternHelper 再次确认
                    node.put("isCraftable", details.isCraftable());
                    // priority: 样板在自动合成中的优先级，数值越大越优先
                    node.put("priority", details.getPriority());
                    // canSubstitute / canBeSubstitute: 替代开关
                    node.put("canSubstitute", details.canSubstitute());
                    node.put("canBeSubstitute", details.canBeSubstitute());

                    // === 浓缩输入列表（合并了相同物品并统计数量） ===
                    // getCondensedInputs 会把 3×3 格子中相同的输入合并，适合查看实际需要多少材料
                    ArrayNode condensedInputs = mapper.createArrayNode();
                    for (IAEItemStack iae : details.getCondensedInputs()) {
                        if (iae != null) {
                            ObjectNode itemNode = Items.dump(iae.getItemStack());
                            // IAEItemStack.getStackSize() 返回该物品在该配方中的总需求量
                            itemNode.put("count", iae.getStackSize());
                            condensedInputs.add(itemNode);
                        }
                    }
                    node.set("condensedInputs", condensedInputs);

                    // === 浓缩输出列表 ===
                    ArrayNode condensedOutputs = mapper.createArrayNode();
                    for (IAEItemStack iae : details.getCondensedOutputs()) {
                        if (iae != null) {
                            ObjectNode itemNode = Items.dump(iae.getItemStack());
                            itemNode.put("count", iae.getStackSize());
                            condensedOutputs.add(itemNode);
                        }
                    }
                    node.set("condensedOutputs", condensedOutputs);
                }
            } catch (Throwable e) {
                // PatternHelper 构造失败（如 NBT 数据不完整或配方已失效）
                log.e(e);
                node.put("patternParseError", e.getMessage());
            }
        }

        return node;
    }
}
