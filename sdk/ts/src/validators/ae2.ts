import Joi from "joi";
import { ClassInfoSchema } from "./common";
import { ItemStackSchema } from "./item";

export const AENodeSchema = Joi.object({
    active: Joi.boolean().required(),
    meetsChannel: Joi.boolean().required(),
    playerID: Joi.number().required(),
    machineClass: ClassInfoSchema.optional(),
    isPart: Joi.boolean().required(),
    isIActionHost: Joi.boolean().required(),
    location: Joi.object({
        x: Joi.number().required(),
        y: Joi.number().required(),
        z: Joi.number().required(),
        dimension: Joi.number().required(),
    }).optional(),
    idlePowerUsage: Joi.number().required(),
    flags: Joi.array().items(Joi.string()).required(),
});

export const AE2PatternSchema = ItemStackSchema.keys({
    crafting: Joi.boolean().required(),
    substitute: Joi.boolean().required(),
    beSubstitute: Joi.boolean().required(),
    author: Joi.string().optional(),
    inputs: Joi.array().items(Joi.alternatives(ItemStackSchema, Joi.valid(null))).optional(),
    outputs: Joi.array().items(Joi.alternatives(ItemStackSchema, Joi.valid(null))).optional(),
    isCraftable: Joi.boolean().optional(),
    priority: Joi.number().optional(),
    canSubstitute: Joi.boolean().optional(),
    canBeSubstitute: Joi.boolean().optional(),
    condensedInputs: Joi.array().items(ItemStackSchema.keys({ count: Joi.number().required() })).optional(),
    condensedOutputs: Joi.array().items(ItemStackSchema.keys({ count: Joi.number().required() })).optional(),
    patternParseError: Joi.string().optional(),
});

export const AECPUSchema = Joi.object({
    name: Joi.string().allow("").required(),
    busy: Joi.boolean().required(),
    availableStorage: Joi.number().unsafe().required(),
    usedStorage: Joi.number().required(),
    coProcessors: Joi.number().required(),
    remainingItemCount: Joi.number().required(),
    startItemCount: Joi.number().required(),
    elapsedTime: Joi.number().required(),
    craftingAllowMode: Joi.string().required(),
    finalOutput: ItemStackSchema.keys({ stackSize: Joi.number().required() }).optional(),
    tasks: Joi.array().items(
        Joi.object({
            remaining: Joi.number().required(),
            inputs: Joi.array().items(ItemStackSchema.keys({ stackSize: Joi.number().required() })).required(),
            pattern: AE2PatternSchema.required(),
            outputs: Joi.array().items(
                ItemStackSchema.keys({
                    stackSize: Joi.number().required(),
                    providers: Joi.array().items(
                        Joi.object({
                            x: Joi.number().required(),
                            y: Joi.number().required(),
                            z: Joi.number().required(),
                            dimension: Joi.number().required(),
                        })
                    ).required(),
                })
            ).required(),
        })
    ).optional(),
    tasking: Joi.array().items(
        ItemStackSchema.keys({
            stackSize: Joi.number().required(),
            providers: Joi.array().items(
                Joi.object({
                    x: Joi.number().required(),
                    y: Joi.number().required(),
                    z: Joi.number().required(),
                    dimension: Joi.number().required(),
                })
            ).required(),
        })
    ).optional(),
    tasksError: Joi.string().optional(),
});

export const AEMEInterfaceSchema = Joi.object({
    display: Joi.boolean().required(),
    name: Joi.string().required(),
    rawName: Joi.string().allow(null).required(),
    active: Joi.boolean().required(),
    allowsPatternOptimization: Joi.boolean().required(),
    playerID: Joi.number().required(),
    location: Joi.object({
        x: Joi.number().required(),
        y: Joi.number().required(),
        z: Joi.number().required(),
        dimension: Joi.number().required(),
    }).required(),
    patterns: Joi.array().items(AE2PatternSchema.keys({ slot: Joi.number().required() })).required(),
});

export const AECraftingTaskBodySchema = Joi.object({
    id: Joi.number().required(),
    Count: Joi.number().required(),
    Damage: Joi.number().optional(),
    tag: Joi.string().optional(),
    cpu: Joi.string().optional(),
});

export const AECraftingTaskResultSchema = Joi.object({
    bytes: Joi.number().required(),
    cpu: Joi.string().required(),
    output: ItemStackSchema.keys({ stackSize: Joi.number().required() }).required(),
});

export const AECPUCancelBodySchema = Joi.object({
    name: Joi.string().optional(),
    id: Joi.number().optional(),
});

export const AECPUCancelResultSchema = Joi.object({
    cpu: Joi.string().required(),
    wasBusy: Joi.boolean().required(),
});

export const AEItemStackSchema = ItemStackSchema.keys({
    stackSize: Joi.number().required(),
    Craftable: Joi.boolean().required(),
});

export const AEItemCellStatusSchema = Joi.object({
    all: Joi.number().required(),
    green: Joi.number().required(),
    blue: Joi.number().required(),
    orange: Joi.number().required(),
    red: Joi.number().required(),
});

export const AEItemsResultSchema = Joi.object({
    items: Joi.array().items(AEItemStackSchema).required(),
    totalBytes: Joi.number().required(),
    usedBytes: Joi.number().required(),
    totalTypes: Joi.number().required(),
    usedTypes: Joi.number().required(),
    cellStatus: AEItemCellStatusSchema.required(),
});

export const AEHitResultSchema = Joi.object({
    message: Joi.string().required(),
});
