import Joi from "joi";
import type { Fluid, FluidContainer } from "../types/fluid";
import { ClassInfoSchema } from "./common";
import { ItemStackSchema } from "./item";

export const FluidSchema = Joi.object<Fluid>({
    class: ClassInfoSchema.optional(),
    id: Joi.number().required(),
    name: Joi.string().required(),
    registryName: Joi.string().required(),
    unlocalizedName: Joi.string().required(),
    localizedName: Joi.string().optional(),
    color: Joi.number().required(),
    luminosity: Joi.number().required(),
    density: Joi.number().required(),
    temperature: Joi.number().required(),
    viscosity: Joi.number().required(),
    gaseous: Joi.boolean().required(),
    block: Joi.number().optional(),
});

export const FluidContainerSchema = Joi.object<FluidContainer>({
    fluid: FluidSchema.optional(),
    amount: Joi.number().optional(),
    filledContainer: ItemStackSchema.optional(),
    emptyContainer: ItemStackSchema.optional(),
});
