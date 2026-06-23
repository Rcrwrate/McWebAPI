import Joi from "joi";
import { ClassInfoSchema, CoordinatesSchema } from "./common";
import { FluidSchema } from "./fluid";

// ========== GT5 Machine Base ==========

export const GT5MachineTypeSchema = Joi.string().valid("MULTIBLOCK", "SINGLE", "GENERATOR", "HATCH", "UNKNOWN");

export const GT5ShutDownReasonSchema = Joi.object({
    id: Joi.string().required(),
    displayString: Joi.string().allow("").required(),
    wasCritical: Joi.boolean().required(),
});

export const GT5MachineIOSchema = Joi.object({
    storedEU: Joi.number().required(),
    euCapacity: Joi.number().required(),
    Info: Joi.array()
        .items(Joi.string())
        .required(),
    rawInfo: Joi.object()
        .pattern(Joi.string(), Joi.string())
        .required(),
    inputVoltage: Joi.number().optional(),
    inputAmperage: Joi.number().optional(),
    outputVoltage: Joi.number().optional(),
    outputAmperage: Joi.number().optional(),
});

export const GT5MachineStateSchema = Joi.object({
    isActive: Joi.boolean().required(),
    isAllowedToWork: Joi.boolean().required(),
    wasShutdown: Joi.boolean().optional(),
    lastShutDownReason: GT5ShutDownReasonSchema.optional(),
    storedEU: Joi.number().optional(),
    euCapacity: Joi.number().optional(),
    Info: Joi.array()
        .items(Joi.string())
        .optional(),
    rawInfo: Joi.object()
        .pattern(Joi.string(), Joi.string())
        .optional(),
    inputVoltage: Joi.number().optional(),
    inputAmperage: Joi.number().optional(),
    outputVoltage: Joi.number().optional(),
    outputAmperage: Joi.number().optional(),
});

export const GT5HatchCoordSchema = Joi.object({
    x: Joi.number().required(),
    y: Joi.number().required(),
    z: Joi.number().required(),
});

export const GT5MaintenanceStateSchema = Joi.object({
    wrench: Joi.boolean().required(),
    screwdriver: Joi.boolean().required(),
    softMallet: Joi.boolean().required(),
    hardHammer: Joi.boolean().required(),
    solderingTool: Joi.boolean().required(),
    crowbar: Joi.boolean().required(),
});

export const GT5HatchesSchema = Joi.object({
    inputBus: Joi.array().items(GT5HatchCoordSchema).required(),
    outputBus: Joi.array().items(GT5HatchCoordSchema).required(),
    inputHatch: Joi.array().items(GT5HatchCoordSchema).required(),
    outputHatch: Joi.array().items(GT5HatchCoordSchema).required(),
    energyHatch: Joi.array().items(GT5HatchCoordSchema).required(),
    dynamoHatch: Joi.array().items(GT5HatchCoordSchema).required(),
    maintenanceHatch: Joi.array().items(GT5HatchCoordSchema).required(),
    mufflerHatch: Joi.array().items(GT5HatchCoordSchema).required(),
    dualInputHatch: Joi.array().items(GT5HatchCoordSchema).required(),
    smartInputHatch: Joi.array().items(GT5HatchCoordSchema).required(),
});

export const GT5MultiBlockInfoSchema = Joi.object({
    structureValid: Joi.boolean().required(),
    progressTime: Joi.number().required(),
    maxProgressTime: Joi.number().required(),
    euT: Joi.number().required(),
    efficiency: Joi.number().required(),
    pollution: Joi.number().required(),
    inputVoltageTier: Joi.number().required(),
    maxInputEu: Joi.number().required(),
    maxInputAmps: Joi.number().required(),
    storedEnergy: Joi.number().required(),
    maxEnergy: Joi.number().required(),
    maxParallelRecipes: Joi.number().required(),
    trueParallel: Joi.number().required(),
    maintenance: GT5MaintenanceStateSchema.required(),
    hatches: GT5HatchesSchema.required(),
});

export const GT5HatchInfoSchema = Joi.object({
    tier: Joi.number().required(),
});

export const GT5SingleBlockInfoSchema = Joi.object({
    tier: Joi.number().required(),
    progressTime: Joi.number().required(),
    maxProgressTime: Joi.number().required(),
    euT: Joi.number().required(),
    inputSlotCount: Joi.number().required(),
    amperage: Joi.number().required(),
    mainFacing: Joi.string().required(),
});

export const GT5GeneratorInfoSchema = Joi.object({
    tier: Joi.number().required(),
    storedEU: Joi.number().required(),
    maxEUStore: Joi.number().required(),
    pollution: Joi.number().required(),
    efficiency: Joi.number().required(),
    recipeMap: Joi.string().optional(),
    maxEUOutput: Joi.number().required(),
    capacity: Joi.number().required(),
    fluid: FluidSchema.optional(),
});

const GT5MachineFieldsSchema = Joi.object({
    machineType: GT5MachineTypeSchema.required(),
    multi: GT5MultiBlockInfoSchema.when("machineType", { is: "MULTIBLOCK", then: Joi.required() }),
    single: GT5SingleBlockInfoSchema.when("machineType", { is: "SINGLE", then: Joi.required() }),
    generator: GT5GeneratorInfoSchema.when("machineType", { is: "GENERATOR", then: Joi.required() }),
    hatch: GT5HatchInfoSchema.when("machineType", { is: "HATCH", then: Joi.required() }),
});

export const GT5MachineInfoSchema = Joi.object({
    coordinates: CoordinatesSchema.required(),
    localName: Joi.string().required(),
    internalName: Joi.string().required(),
    metaTileID: Joi.number().required(),
    owner: Joi.string().required(),
    state: GT5MachineStateSchema.required(),
    class: ClassInfoSchema.optional(),
}).concat(GT5MachineFieldsSchema);

// ========== GT5 Batch ==========

export const GT5BatchMachineCoordSchema = Joi.object({
    x: Joi.number().required(),
    y: Joi.number().required(),
    z: Joi.number().required(),
    dim: Joi.number().optional(),
});

export const GT5BatchSubmitResultSchema = Joi.object({
    id: Joi.string().required(),
    total: Joi.number().required(),
});

export const GT5BatchRerunResultSchema = Joi.object({
    id: Joi.string().required(),
    total: Joi.number().required(),
    runCount: Joi.number().required(),
});

export const GT5BatchMachineSchema = Joi.object({
    x: Joi.number().required(),
    y: Joi.number().required(),
    z: Joi.number().required(),
    dimension: Joi.number().required(),
    localName: Joi.string().required(),
    internalName: Joi.string().required(),
    metaTileID: Joi.number().required(),
    owner: Joi.string().required(),
    state: GT5MachineStateSchema.required(),
    class: ClassInfoSchema.optional(),
}).concat(GT5MachineFieldsSchema);

export const GT5BatchJobStatusSchema = Joi.string().valid("pending", "running", "completed");

export const GT5BatchJobResultSchema = Joi.object({
    id: Joi.string().required(),
    total: Joi.number().required(),
    completed: Joi.number().required(),
    success: Joi.number().required(),
    failed: Joi.number().required(),
    status: GT5BatchJobStatusSchema.required(),
    runCount: Joi.number().required(),
    createTime: Joi.number().required(),
    finishTime: Joi.number().optional(),
    durationMs: Joi.number().optional(),
    errors: Joi.array().items(Joi.string()).optional(),
    errorsTruncated: Joi.number().optional(),
    machines: Joi.array().items(GT5BatchMachineSchema).optional(),
});

// ========== GT5 Chunk Scan ==========

export const GT5ScanSubmitResultSchema = Joi.object({
    id: Joi.string().required(),
    total: Joi.number().required(),
});

export const GT5ScanMachineSchema = Joi.object({
    x: Joi.number().required(),
    y: Joi.number().required(),
    z: Joi.number().required(),
    machineType: GT5MachineTypeSchema.required(),
    localName: Joi.string().required(),
    internalName: Joi.string().required(),
    metaTileID: Joi.number().required(),
    owner: Joi.string().required(),
});

export const GT5ScanResultSchema = Joi.object({
    chunkX: Joi.number().required(),
    chunkZ: Joi.number().required(),
    dimension: Joi.number().required(),
    totalMachines: Joi.number().required(),
    machines: Joi.array().items(GT5ScanMachineSchema).required(),
});

export const GT5ScanJobStatusSchema = Joi.string().valid("pending", "running", "completed");

export const GT5ScanJobResultSchema = Joi.object({
    id: Joi.string().required(),
    total: Joi.number().required(),
    completed: Joi.number().required(),
    success: Joi.number().required(),
    failed: Joi.number().required(),
    status: GT5ScanJobStatusSchema.required(),
    createTime: Joi.number().required(),
    chunkX: Joi.number().required(),
    chunkZ: Joi.number().required(),
    dimension: Joi.number().required(),
    finishTime: Joi.number().optional(),
    durationMs: Joi.number().optional(),
    errors: Joi.array().items(Joi.string()).optional(),
    errorsTruncated: Joi.number().optional(),
    result: GT5ScanResultSchema.optional(),
});
