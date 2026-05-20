import Joi from "joi";
import { ClassInfoSchema } from "./common";
import { ItemStackSchema } from "./item";

export const Cuboid6BoundsSchema = Joi.object({
    minX: Joi.number().required(),
    minY: Joi.number().required(),
    minZ: Joi.number().required(),
    maxX: Joi.number().required(),
    maxY: Joi.number().required(),
    maxZ: Joi.number().required(),
});

export const FMPPartSchema = Joi.object({
    class: ClassInfoSchema.optional(),
    type: Joi.string().required(),
    lightValue: Joi.number().required(),
    doesTick: Joi.boolean().required(),
    bounds: Cuboid6BoundsSchema.optional(),
    collisionBoxes: Joi.array().items(Cuboid6BoundsSchema).required(),
    drops: Joi.array().items(ItemStackSchema).optional(),
    ae2: Joi.object({
        color: Joi.string().required(),
        isEmpty: Joi.boolean().required(),
        lightValue: Joi.number().required(),
        parts: Joi.array().items(
            Joi.object({
                direction: Joi.string().required(),
                item: ItemStackSchema.optional(),
            })
        ).required(),
        facades: Joi.array().items(
            Joi.object({
                direction: Joi.string().required(),
                item: ItemStackSchema.optional(),
            })
        ).required(),
        error: Joi.string().optional(),
    }).optional(),
});
