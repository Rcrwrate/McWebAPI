import Joi from "joi";
import type { Fluid, FluidContainer, FluidTank } from "../types/fluid";
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

/** 流体储罐 */
export const FluidTankSchema = FluidSchema.append<FluidTank>({
    index: Joi.number().required(),
    amount: Joi.number().required(),
    capacity: Joi.number().required(),
});

export const FluidContainerSchema = Joi.object<FluidContainer>({
    fluid: FluidSchema.optional(),
    amount: Joi.number().optional(),
    filledContainer: ItemStackSchema.optional(),
    emptyContainer: ItemStackSchema.optional(),
});
