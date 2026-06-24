import Joi from "joi";
import type { RootInfo } from "../types/common";

export const ClassInfoSchema = Joi.object({
    package: Joi.string().optional(),
    location: Joi.string().optional(),
    extends: Joi.array().items(Joi.string()).optional(),
    implements: Joi.array().items(Joi.string()).optional(),
});

export const CoordinatesSchema = Joi.object({
    posX: Joi.number().required(),
    posY: Joi.number().required(),
    posZ: Joi.number().required(),
    dimension: Joi.number().required(),
});

export const ApiSuccessResponseSchema = <T>(dataSchema: Joi.Schema<T>) =>
    Joi.object({
        success: Joi.valid(true).required(),
        data: dataSchema.required(),
    });

export const ApiErrorResponseSchema = Joi.object({
    success: Joi.valid(false).required(),
    message: Joi.string().required(),
    stack: Joi.string().allow(null).optional(),
});

export const ApiResponseSchema = <T>(dataSchema: Joi.Schema<T>) =>
    Joi.alternatives(ApiSuccessResponseSchema(dataSchema), ApiErrorResponseSchema);

export const RootInfoSchema = Joi.object<RootInfo>({
    modid: Joi.string().required(),
    version: Joi.string().required(),
    buildTime: Joi.number().optional(),
});
