import type { Fluid } from "./fluid";
import type { ItemStack } from "./item";

/** 合成配方查询中单个格子/原料的可选物品集合 */
export interface RecipeIngredient {
    /** 矿物词典前缀列表（如 ["plankWood"]），仅有序/无序矿物词典配方会出现 */
    ores?: string[];
    /** 可替代的物品列表，至少一个 */
    items: Array<ItemStack & { stackSize: number }>;
}

/** 工作台合成配方（有序/无序） */
export interface CraftingRecipe {
    /** 配方实现类全名，如 net.minecraft.item.crafting.ShapedRecipes */
    recipeClass: string;
    /** shaped=有序，shapeless=无序，unknown=无法识别的实现 */
    type: "shaped" | "shapeless" | "unknown";
    shapeless: boolean;
    /** 有序配方网格宽度；无法还原网格或无序配方时为 -1 */
    width?: number;
    /** 有序配方网格高度；无法还原网格或无序配方时为 -1 */
    height?: number;
    /** 有序配方网格（可为 null 表示空槽）；无法还原网格时不输出 */
    grid?: Array<Array<RecipeIngredient | null>>;
    /** 无序配方原料列表；无法还原网格的有序配方也会退化为该字段 */
    ingredients?: RecipeIngredient[];
    output: (ItemStack & { stackSize: number }) | null;
}

/** 熔炉熔炼配方的单条记录 */
export interface FurnaceRecipe {
    input: ItemStack & { stackSize: number };
    output: ItemStack & { stackSize: number };
}

/** /recipes/crafting 响应 */
export interface CraftingRecipesResult {
    type: "crafting";
    queryType: "output" | "input";
    count: number;
    offset: number;
    /** 仅在启用索引加速（IndexedCraftingRecipesHandler）时输出 */
    total?: number;
    query?: ItemStack & { stackSize?: number };
    recipes: CraftingRecipe[];
}

/** /recipes/furnace 响应 */
export interface FurnaceRecipesResult {
    type: "furnace";
    queryType: "output" | "input";
    count: number;
    offset: number;
    query?: ItemStack & { stackSize?: number };
    recipes: FurnaceRecipe[];
}

/** GT5 配方表映射表中的一项 */
export interface GTRecipeMap {
    unlocalizedName: string;
    name: string;
}

/** GT5 配方（结构与 GTRecipe 字段对应） */
export interface GTRecipe {
    recipeMap: string;
    recipeMapName: string;
    duration: number;
    eut: number;
    amperage: number;
    specialValue: number;
    enabled: boolean;
    hidden: boolean;
    fake: boolean;
    inputs: {
        items: Array<(ItemStack & { stackSize: number }) | null>;
        fluids: Array<(Fluid & { amount: number }) | null>;
    };
    outputs: {
        items: Array<(ItemStack & { stackSize: number }) | null>;
        fluids: Array<(Fluid & { amount: number }) | null>;
    };
    inputChances: number[];
    outputChances: number[];
    fluidInputChances: number[];
    fluidOutputChances: number[];
}

/** /recipes/gt 响应 */
export interface GTRecipesResult {
    type: "gt";
    queryType: "output" | "input";
    count: number;
    offset: number;
    /** 限定查询的配方表（仅当传入 map 参数时输出） */
    map?: string;
    query?: ItemStack & { stackSize?: number };
    fluid?: Fluid;
    recipes: GTRecipe[];
}
