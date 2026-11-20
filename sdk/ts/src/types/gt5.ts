import type { ClassInfo } from "./common";
import type { Fluid } from "./fluid";
import type { NBTCompound } from "./item";

// ========== GT5 Machine Base ==========

export type GT5MachineType = "MULTIBLOCK" | "SINGLE" | "GENERATOR" | "HATCH" | "UNKNOWN";
/**
 * 注意：所有 SimpleShutDownReason 的 id 都是 "simple_result"，只能靠 displayString / wasCritical 区分，不能按 id 判断
 */
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
    storedEU: number;
    euCapacity: number;
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
    nbt: NBTCompound;
} & GT5Machine;

export interface GT5StatusResult {
    /** 状态是否实际发生变化；机器已处于目标状态时为 false */
    changed: boolean;
}

// ========== GT5 Batch ==========

export interface GT5BatchMachineCoord {
    x: number;
    y: number;
    z: number;
    dim?: number;
    dimension?: number;
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

// ========== GT5 Vein ==========

/** VisualProspecting 缓存记录的矿脉来源 */
export type GT5VeinSource = "UNKNOWN" | "RESCAN" | "API" | "GENERATED" | "ADMIN";

/**
 * 矿脉各矿石角色的名称，键按 {@link GT5VeinOres}（本地化名）/ {@link GT5Vein.oreIds}（内部名）同构。
 * 某个角色缺失表示该矿脉没有对应矿石。
 */
export interface GT5VeinOres {
    primary?: string;
    secondary?: string;
    between?: string;
    sporadic?: string;
}

/** 已生成的矿脉（读取 VisualProspecting 服务端缓存） */
export interface GT5Vein {
    /** 矿脉注册名，如 `ore.mix.diamond` */
    vein: string;
    localizedName: string;
    /** 矿脉中心区块坐标 */
    chunkX: number;
    chunkZ: number;
    /** 矿脉中心方块坐标 */
    x: number;
    z: number;
    blockSize: number;
    depleted: boolean;
    source: GT5VeinSource;
    /** 本地化矿石名 */
    ores: GT5VeinOres;
    /** 矿石内部名 */
    oreIds: GT5VeinOres;
}

/** GET /gt5/veins 返回的维度矿脉列表，按区块坐标排序 */
export interface GT5VeinListResult {
    dimension: number;
    dimensionName: string;
    veins: GT5Vein[];
}

/** GET /gt5/vein 返回的矿脉，附带与查询坐标的平面距离 */
export interface GT5VeinWithDistance extends GT5Vein {
    /** 到查询坐标的平面距离（方块） */
    distance: number;
}

/** GET /gt5/vein 查询结果 */
export interface GT5VeinQueryResult {
    x: number;
    y: number;
    z: number;
    dimension: number;
    dimensionName: string;
    /** 按 `vein` 过滤的矿脉注册名，未指定时为 null */
    target: string | null;
    radius: number;
    radiusChunks: number;
    limit: number;
    excludeDepleted: boolean;
    /** 过滤后的匹配总数，可能大于 `veins` 长度 */
    matched: number;
    elapsedMs: number;
    /** 按距离升序，最多 `limit` 条 */
    veins: GT5VeinWithDistance[];
}

/** 矿脉定义（GT `WorldgenGTOreLayer`） */
export interface GT5VeinDefinition {
    /** 矿脉注册名，如 `ore.mix.diamond` */
    vein: string;
    localizedName: string;
    weight: number;
    /** 在本维度候选表中的抽取权重占比（0~1） */
    chance: number;
    size: number;
    density: number;
    minY: number;
    maxY: number;
    /** 本地化矿石名 */
    ores: GT5VeinOres;
    /** 矿石内部名 */
    oreIds: GT5VeinOres;
}

export interface GT5VeinDefDimension {
    dimension: number;
    dimensionName: string;
    veins: GT5VeinDefinition[];
}

/** GET /gt5/veindefs 返回结果，`dimensions` 键为维度 ID 字符串 */
export interface GT5VeinDefsResult {
    dimensions: Record<string, GT5VeinDefDimension>;
}
