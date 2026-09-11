import Joi from "joi";
import type { CraftingRecipe, CraftingRecipesResult, FurnaceRecipe, FurnaceRecipesResult, GTRecipe, GTRecipeMap, GTRecipesResult, RecipeIngredient } from "../types/recipe";
import { FluidSchema } from "./fluid";
import { ItemStackSchema } from "./item";

/** 带 stackSize 的物品堆（配方中始终输出 stackSize） */
export const RecipeItemStackSchema = ItemStackSchema.append({ stackSize: Joi.number().required() });
/** 带 stackSize 的物品堆，但也允许缺失（用于 query 回显等场景） */
export const RecipeItemStackLooseSchema = ItemStackSchema.append({ stackSize: Joi.number().optional() });

export const RecipeIngredientSchema = Joi.object({
    ores: Joi.array().items(Joi.string()).optional(),
    items: Joi.array().items(RecipeItemStackSchema).required(),
});

export const CraftingRecipeSchema = Joi.object<CraftingRecipe>({
    recipeClass: Joi.string().required(),
    type: Joi.valid("shaped", "shapeless", "unknown").required(),
    shapeless: Joi.boolean().required(),
    width: Joi.number().optional(),
    height: Joi.number().optional(),
    grid: Joi.array().items(Joi.array().items(Joi.alternatives(RecipeIngredientSchema, Joi.valid(null)))).optional(),
    ingredients: Joi.array().items(RecipeIngredientSchema).optional(),
    output: Joi.alternatives(RecipeItemStackSchema, Joi.valid(null)).required(),
});

export const CraftingRecipesResultSchema = Joi.object<CraftingRecipesResult>({
    type: Joi.valid("crafting").required(),
    queryType: Joi.valid("output", "input").required(),
    count: Joi.number().required(),
    offset: Joi.number().required(),
    total: Joi.number().optional(),
    query: RecipeItemStackLooseSchema.optional(),
    recipes: Joi.array().items(CraftingRecipeSchema).required(),
});

export const FurnaceRecipeSchema = Joi.object<FurnaceRecipe>({
    input: RecipeItemStackSchema.required(),
    output: RecipeItemStackSchema.required(),
});

export const FurnaceRecipesResultSchema = Joi.object<FurnaceRecipesResult>({
    type: Joi.valid("furnace").required(),
    queryType: Joi.valid("output", "input").required(),
    count: Joi.number().required(),
    offset: Joi.number().required(),
    query: RecipeItemStackLooseSchema.optional(),
    recipes: Joi.array().items(FurnaceRecipeSchema).required(),
});

export const GTRecipeMapSchema = Joi.object<GTRecipeMap>({
    unlocalizedName: Joi.string().required(),
    name: Joi.string().required(),
});

/** 配方中的流体堆，附加 amount */
export const RecipeFluidStackSchema = FluidSchema.append({ amount: Joi.number().required() });

const GTRecipeIOItemsSchema = Joi.array().items(Joi.alternatives(RecipeItemStackSchema, Joi.valid(null))).required();
const GTRecipeIOFluidsSchema = Joi.array().items(Joi.alternatives(RecipeFluidStackSchema, Joi.valid(null))).required();

export const GTRecipeSchema = Joi.object<GTRecipe>({
    recipeMap: Joi.string().required(),
    recipeMapName: Joi.string().required(),
    duration: Joi.number().required(),
    eut: Joi.number().required(),
    amperage: Joi.number().required(),
    specialValue: Joi.number().required(),
    enabled: Joi.boolean().required(),
    hidden: Joi.boolean().required(),
    fake: Joi.boolean().required(),
    inputs: Joi.object({ items: GTRecipeIOItemsSchema, fluids: GTRecipeIOFluidsSchema }).required(),
    outputs: Joi.object({ items: GTRecipeIOItemsSchema, fluids: GTRecipeIOFluidsSchema }).required(),
    inputChances: Joi.array().items(Joi.number()).required(),
    outputChances: Joi.array().items(Joi.number()).required(),
    fluidInputChances: Joi.array().items(Joi.number()).required(),
    fluidOutputChances: Joi.array().items(Joi.number()).required(),
});

export const GTRecipesResultSchema = Joi.object<GTRecipesResult>({
    type: Joi.valid("gt").required(),
    queryType: Joi.valid("output", "input").required(),
    count: Joi.number().required(),
    offset: Joi.number().required(),
    map: Joi.string().optional(),
    query: RecipeItemStackLooseSchema.optional(),
    fluid: FluidSchema.optional(),
    recipes: Joi.array().items(GTRecipeSchema).required(),
});
