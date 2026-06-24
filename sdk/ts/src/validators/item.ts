import Joi from "joi";
import type { AEItemDefinitions, Item, ItemDetail } from "../types/item";
import { ClassInfoSchema } from "./common";

export const NBTDataSchema = Joi.object({
    nbtstr: Joi.string().optional(),
    nbtWrite: Joi.string().optional(),
    nbt: Joi.object().unknown().optional(),
});

export const ItemSchema = Joi.object<Item>({
    class: ClassInfoSchema.optional(),
    id: Joi.number().required(),
    registryName: Joi.string().required(),
    UnlocalizedName: Joi.string().required(),
    localizedName: Joi.string().required(),
    HasSubtypes: Joi.boolean().required(),
});

export const ItemStackSchema = ItemSchema.append({
    MaxStackSize: Joi.number().required(),
    damageable: Joi.boolean().required(),
    damage: Joi.number().required(),
    AttributeModifiers: Joi.object().unknown().optional(),
    stackSize: Joi.number().optional(),
    count: Joi.number().optional(),
}).concat(NBTDataSchema);

export const ItemDetailSchema = ItemStackSchema.append<ItemDetail>({
    subs: Joi.array().items(ItemStackSchema).optional(),
});

export const AEItemDefinitionsSchema = Joi.object<AEItemDefinitions>({
    items: Joi.array().items(ItemStackSchema.append({ name: Joi.string().required() })).required(),
    parts: Joi.array().items(ItemStackSchema.append({ name: Joi.string().required() })).required(),
    materials: Joi.array().items(ItemStackSchema.append({ name: Joi.string().required() })).required(),
    blocks: Joi.array().items(ItemStackSchema.append({ name: Joi.string().required() })).required(),
});
