import Joi from "joi";
import { ClassInfoSchema, CoordinatesSchema } from "./common";
import { ItemStackSchema } from "./item";

export const MaterialSchema = Joi.object({
    isLiquid: Joi.boolean().required(),
    isSolid: Joi.boolean().required(),
    blocksMovement: Joi.boolean().required(),
    isOpaque: Joi.boolean().required(),
    isFlammable: Joi.boolean().required(),
    isReplaceable: Joi.boolean().required(),
    requiresNoTool: Joi.boolean().required(),
    mobilityFlag: Joi.number().required(),
    isAdventureModeExempt: Joi.boolean().required(),
});

export const BlockSchema = Joi.object({
    class: ClassInfoSchema.optional(),
    id: Joi.number().required(),
    registryName: Joi.string().required(),
    unlocalizedName: Joi.string().required(),
    localizedName: Joi.string().required(),
    resistance: Joi.alternatives(Joi.number().unsafe(), Joi.valid("Infinity")).required(),
    lightLevel: Joi.number().required(),
    isOpaqueCube: Joi.boolean().required(),
    isNormalCube: Joi.boolean().required(),
    slipperiness: Joi.number().required(),
    renderType: Joi.number().required(),
    material: MaterialSchema.required(),
    meta: Joi.number().optional(),
    fileName: Joi.string().optional(),
    blockColor: Joi.number().optional(),
});

export const BlockDetailSchema = Joi.object({
    block: BlockSchema.keys({
        hardness: Joi.number().required(),
        isReplaceable: Joi.boolean().required(),
        isPassable: Joi.boolean().required(),
    }).required(),
    coordinates: CoordinatesSchema.required(),
    metadata: Joi.number().required(),
    isAir: Joi.boolean().required(),
    tileEntity: Joi.object({
        class: ClassInfoSchema.optional(),
        inventorySize: Joi.number().optional(),
        items: Joi.array().items(ItemStackSchema.keys({ slot: Joi.number().required() })).optional(),
    }).unknown().optional(),
});

export const SetBlockBodySchema = Joi.object({
    id: Joi.number().required(),
    metadataIn: Joi.number().optional(),
    flag: Joi.number().optional(),
});

export const SetBlockResultSchema = Joi.valid(null);

export const BatchSetBlockTaskSchema = Joi.object({
    x: Joi.number().required(),
    y: Joi.number().required(),
    z: Joi.number().required(),
    dim: Joi.number().optional(),
    id: Joi.number().required(),
    metadata: Joi.number().optional(),
    flag: Joi.number().optional(),
});

export const BatchSetBlockSubmitResultSchema = Joi.object({
    id: Joi.string().required(),
    total: Joi.number().required(),
});

export const BatchSetBlockJobStatusSchema = Joi.valid("pending", "running", "completed");

export const BatchSetBlockFailureSchema = Joi.object({
    x: Joi.number().required(),
    y: Joi.number().required(),
    z: Joi.number().required(),
    reason: Joi.string().required(),
});

export const BatchSetBlockJobResultSchema = Joi.object({
    id: Joi.string().required(),
    total: Joi.number().required(),
    completed: Joi.number().required(),
    success: Joi.number().required(),
    failed: Joi.number().required(),
    status: BatchSetBlockJobStatusSchema.required(),
    createTime: Joi.number().required(),
    finishTime: Joi.number().optional(),
    durationMs: Joi.number().optional(),
    failures: Joi.array().items(BatchSetBlockFailureSchema).optional(),
    failuresTruncated: Joi.number().optional(),
});
