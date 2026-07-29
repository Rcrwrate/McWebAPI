import Joi from "joi";
import type { AECPU, AECPUCancelBody, AECPUCancelResult, AECraftingTaskBody, AECraftingTaskResult, AEHitResult, AEItemsResult, AEMEInterface, AENode } from "../types/ae2";
import { ClassInfoSchema } from "./common";
import { FluidSchema } from "./fluid";
import { ItemStackSchema } from "./item";

export const AEStackProvidersSchema = Joi.array().items(
    Joi.object({
        x: Joi.number().required(),
        y: Joi.number().required(),
        z: Joi.number().required(),
        dimension: Joi.number().required(),
    })
).required();

/** 对应服务端 Pattern.dumpAEStack 的输出：物品或流体堆，附加 stackSize 字段 */
export const AEStackSchema = Joi.alternatives(
    ItemStackSchema.append({ stackSize: Joi.number().required() }),
    FluidSchema.append({ stackSize: Joi.number().required() })
);

export const AEStackWithProvidersSchema = Joi.alternatives(
    ItemStackSchema.append({
        stackSize: Joi.number().required(),
        providers: AEStackProvidersSchema,
    }),
    FluidSchema.append({
        stackSize: Joi.number().required(),
        providers: AEStackProvidersSchema,
    })
);

export const AENodeSchema = Joi.object<AENode>({
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

export const AE2PatternSchema = ItemStackSchema.append({
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
    condensedInputs: Joi.array().items(AEStackSchema).optional(),
    condensedOutputs: Joi.array().items(AEStackSchema).optional(),
    patternParseError: Joi.string().optional(),
});

export const AECPUSchema = Joi.object<AECPU>({
    name: Joi.string().allow("").required(),
    busy: Joi.boolean().required(),
    availableStorage: Joi.number().unsafe().required(),
    usedStorage: Joi.number().required(),
    coProcessors: Joi.number().required(),
    remainingItemCount: Joi.number().required(),
    startItemCount: Joi.number().required(),
    elapsedTime: Joi.number().required(),
    craftingAllowMode: Joi.string().required(),
    finalOutput: AEStackSchema.optional(),
    tasks: Joi.array().items(
        Joi.object({
            remaining: Joi.number().required(),
            inputs: Joi.array().items(AEStackSchema).required(),
            pattern: AE2PatternSchema.required(),
            outputs: Joi.array().items(AEStackWithProvidersSchema).required(),
        })
    ).optional(),
    tasking: Joi.array().items(AEStackWithProvidersSchema).optional(),
    tasksError: Joi.string().optional(),
});

export const AEMEInterfaceSchema = Joi.object<AEMEInterface>({
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
    patterns: Joi.array().items(AE2PatternSchema.append({ slot: Joi.number().required() })).required(),
});

export const AECraftingTaskBodySchema = Joi.object<AECraftingTaskBody>({
    id: Joi.number().required(),
    Count: Joi.number().required(),
    Type: Joi.string().valid("item", "fluid").optional(),
    Damage: Joi.number().optional(),
    tag: Joi.string().optional(),
    cpu: Joi.string().optional(),
});

export const AECraftingTaskResultSchema = Joi.object<AECraftingTaskResult>({
    bytes: Joi.number().required(),
    cpu: Joi.string().required(),
    output: ItemStackSchema.append({ stackSize: Joi.number().required() }).required(),
});

export const AECPUCancelBodySchema = Joi.object<AECPUCancelBody>({
    name: Joi.string().optional(),
    id: Joi.number().optional(),
});

export const AECPUCancelResultSchema = Joi.object<AECPUCancelResult>({
    cpu: Joi.string().required(),
    wasBusy: Joi.boolean().required(),
});

export const AEItemStackSchema = Joi.alternatives(
    ItemStackSchema.append({
        type: Joi.string().valid("item").required(),
        stackSize: Joi.number().required(),
        Craftable: Joi.boolean().required(),
    }),
    FluidSchema.append({
        type: Joi.string().valid("fluid").required(),
        stackSize: Joi.number().required(),
        Craftable: Joi.boolean().required(),
    })
);

export const AEItemCellStatusSchema = Joi.object({
    all: Joi.number().required(),
    green: Joi.number().required(),
    blue: Joi.number().required(),
    orange: Joi.number().required(),
    red: Joi.number().required(),
});

export const AEItemsResultSchema = Joi.object<AEItemsResult>({
    items: Joi.array().items(AEItemStackSchema).required(),
    totalBytes: Joi.number().required(),
    usedBytes: Joi.number().required(),
    totalTypes: Joi.number().required(),
    usedTypes: Joi.number().required(),
    cellStatus: AEItemCellStatusSchema.required(),
    fluidTotalBytes: Joi.number().required(),
    fluidUsedBytes: Joi.number().required(),
    fluidTotalTypes: Joi.number().required(),
    fluidUsedTypes: Joi.number().required(),
    fluidCellStatus: AEItemCellStatusSchema.required(),
});

export const AEHitResultSchema = Joi.object<AEHitResult>({
    message: Joi.string().required(),
});
