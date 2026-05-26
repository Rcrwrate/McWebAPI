import Joi from "joi";
import { ClassInfoSchema } from "./common";
import { ItemStackSchema } from "./item";

export const FluidSchema = Joi.object({
    class: ClassInfoSchema.optional(),
    name: Joi.string().required(),
    defaultName: Joi.string().required(),
    unlocalizedName: Joi.string().required(),
    localizedName: Joi.string().optional(),
    fluidID: Joi.number().required(),
    color: Joi.number().required(),
    luminosity: Joi.number().required(),
    density: Joi.number().required(),
    temperature: Joi.number().required(),
    viscosity: Joi.number().required(),
    gaseous: Joi.boolean().required(),
    block: Joi.number().optional(),
});

export const FluidContainerSchema = Joi.object({
    fluid: FluidSchema.optional(),
    amount: Joi.number().optional(),
    filledContainer: ItemStackSchema.optional(),
    emptyContainer: ItemStackSchema.optional(),
});
