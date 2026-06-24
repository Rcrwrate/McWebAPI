import type { ClassInfo } from "./common";
import type { Fluid } from "./fluid";

// ========== GT5 Machine Base ==========

export type GT5MachineType = "MULTIBLOCK" | "SINGLE" | "GENERATOR" | "HATCH" | "UNKNOWN";

export interface GT5ShutDownReason {
    id: string;
    displayString: string;
    wasCritical: boolean;
}

/**
 * 机器 IO 信息，由 GT5 IGregTechDeviceInformation 接口提供。
 * 字段 inputVoltage/inputAmperage 仅在机器接入电力输入网络时存在；
 * outputVoltage/outputAmperage 仅在机器接入电力输出网络时存在。
 */
export interface GT5MachineIO {
    storedEU: number;
    euCapacity: number;
    /** getInfoData() 返回的格式化字符串数组（IC2 信息显示屏同源数据） */
    Info: string[];
    /** getInfoMap() 返回的结构化键值对 */
    rawInfo: Record<string, string>;
    inputVoltage?: number;
    inputAmperage?: number;
    outputVoltage?: number;
    outputAmperage?: number;
}

export interface GT5MachineState {
    isActive: boolean;
    isAllowedToWork: boolean;
    wasShutdown?: boolean;
    lastShutDownReason?: GT5ShutDownReason;
    storedEU?: number;
    euCapacity?: number;
    Info?: string[];
    rawInfo?: Record<string, string>;
    inputVoltage?: number;
    inputAmperage?: number;
    outputVoltage?: number;
    outputAmperage?: number;
}

export interface GT5HatchCoord {
    x: number;
    y: number;
    z: number;
}

export interface GT5MaintenanceState {
    wrench: boolean;
    screwdriver: boolean;
    softMallet: boolean;
    hardHammer: boolean;
    solderingTool: boolean;
    crowbar: boolean;
}

export interface GT5MultiBlockInfo {
    structureValid: boolean;
    progressTime: number;
    maxProgressTime: number;
    euT: number;
    efficiency: number;
    pollution: number;
    inputVoltageTier: number;
    maxInputEu: number;
    maxInputAmps: number;
    /** 所有能源仓已存储 EU 之和（注意：LSC 等电池类机器 EU 不存于能源仓，此值为 0） */
    storedEnergy: number;
    /** 所有能源仓总容量之和（注意：LSC 等电池类机器 EU 不存于能源仓，此值仅为能源仓容量） */
    maxEnergy: number;
    maxParallelRecipes: number;
    trueParallel: number;
    maintenance: GT5MaintenanceState;
    hatches: {
        inputBus: GT5HatchCoord[];
        outputBus: GT5HatchCoord[];
        inputHatch: GT5HatchCoord[];
        outputHatch: GT5HatchCoord[];
        energyHatch: GT5HatchCoord[];
        dynamoHatch: GT5HatchCoord[];
        maintenanceHatch: GT5HatchCoord[];
        mufflerHatch: GT5HatchCoord[];
        dualInputHatch: GT5HatchCoord[];
        smartInputHatch: GT5HatchCoord[];
    };
}

export interface GT5HatchInfo {
    tier: number;
}

export interface GT5SingleBlockInfo {
    tier: number;
    progressTime: number;
    maxProgressTime: number;
    euT: number;
    inputSlotCount: number;
    amperage: number;
    mainFacing: string;
}

export interface GT5GeneratorInfo {
    tier: number;
    storedEU: number;
    maxEUStore: number;
    pollution: number;
    efficiency: number;
    recipeMap?: string;
    maxEUOutput: number;
    capacity: number;
    fluid?: Fluid;
}

export type GT5Machine =
    | { machineType: "MULTIBLOCK"; multi: GT5MultiBlockInfo }
    | { machineType: "SINGLE"; single: GT5SingleBlockInfo }
    | { machineType: "GENERATOR"; generator: GT5GeneratorInfo }
    | { machineType: "HATCH"; hatch: GT5HatchInfo }
    | { machineType: "UNKNOWN" };

/** GET /gt5 返回的完整机器信息 */
export type GT5MachineInfo = {
    x: number;
    y: number;
    z: number;
    dimension: number;
    localName: string;
    internalName: string;
    metaTileID: number;
    owner: string;
    state: GT5MachineState;
    class?: ClassInfo;
} & GT5Machine;

// ========== GT5 Batch ==========

export interface GT5BatchMachineCoord {
    x: number;
    y: number;
    z: number;
    dim?: number;
}

/** POST /gt5/batch 提交结果 */
export interface GT5BatchSubmitResult {
    id: string;
    total: number;
}

/** PATCH /gt5/batch 重执行结果 */
export interface GT5BatchRerunResult {
    id: string;
    total: number;
    runCount: number;
}

export type GT5BatchJobStatus = "pending" | "running" | "completed";

/** GET /gt5/batch 查询结果 */
export interface GT5BatchJobResult {
    id: string;
    total: number;
    completed: number;
    success: number;
    failed: number;
    status: GT5BatchJobStatus;
    runCount: number;
    createTime: number;
    finishTime?: number;
    durationMs?: number;
    errors?: string[];
    errorsTruncated?: number;
    machines?: GT5MachineInfo[];
}

// ========== GT5 Chunk Scan ==========

/** POST /gt5/scan 提交结果 */
export interface GT5ScanSubmitResult {
    id: string;
    total: number;
}

/** Scan 中单个机器信息（精简版，不含 state/multi/hatch/single） */
export interface GT5ScanMachine {
    x: number;
    y: number;
    z: number;
    machineType: GT5MachineType;
    localName: string;
    internalName: string;
    metaTileID: number;
    owner: string;
}

export interface GT5ScanResult {
    chunkX: number;
    chunkZ: number;
    dimension: number;
    totalMachines: number;
    machines: GT5ScanMachine[];
}

export type GT5ScanJobStatus = "pending" | "running" | "completed";

/** GET /gt5/scan 查询结果 */
export interface GT5ScanJobResult {
    id: string;
    total: number;
    completed: number;
    success: number;
    failed: number;
    status: GT5ScanJobStatus;
    createTime: number;
    chunkX: number;
    chunkZ: number;
    dimension: number;
    finishTime?: number;
    durationMs?: number;
    errors?: string[];
    errorsTruncated?: number;
    result?: GT5ScanResult;
}
