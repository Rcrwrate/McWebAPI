import type { ClassInfo, Coordinates } from "./common";
import type { Fluid } from "./fluid";
import type { ItemStack } from "./item";

export type AEStack = (ItemStack | Fluid) & { stackSize: number };

export interface AEStackProviders {
    providers: Array<{ x: number; y: number; z: number; dimension: number }>;
}

export interface AENode {
    active: boolean;
    meetsChannel: boolean;
    playerID: number;
    machineClass?: ClassInfo;
    isPart: boolean;
    isIActionHost: boolean;
    location?: {
        x: number;
        y: number;
        z: number;
        dimension: number;
    };
    idlePowerUsage: number;
    flags: string[];
}

export interface AE2Pattern extends ItemStack {
    crafting: boolean;
    substitute: boolean;
    beSubstitute: boolean;
    author?: string;
    inputs?: (ItemStack | null)[];
    outputs?: (ItemStack | null)[];
    isCraftable?: boolean;
    priority?: number;
    canSubstitute?: boolean;
    canBeSubstitute?: boolean;
    condensedInputs?: AEStack[];
    condensedOutputs?: AEStack[];
    patternParseError?: string;
}

/** `appeng.api.networking.crafting.CraftingAllowMode` 枚举名 */
export type AECraftingAllowMode = "YES" | "NO" | "ONLY_PLAYERS" | string;

/**
 * `appeng.util.ScheduledReason` 枚举名，表示任务的调度原因。
 * 类型侧保留开放联合
 */
export type AECPUScheduledReason =
    | "UNDEFINED"
    | "SOMETHING_STUCK"
    | "BLOCKING_MODE"
    | "LOCK_MODE"
    | "NO_TARGET"
    | "NOT_ENOUGH_INGREDIENTS"
    | "SAME_NETWORK"
    | "UNSUPPORTED_STACK"
    | (string & {});

/** 合成 CPU 中正在进行的单个并行合成任务 */
export interface AECPUTask {
    /** 剩余执行次数 */
    remaining: number;
    /** 任务调度原因（ScheduledReason 枚举名） */
    scheduledReason: AECPUScheduledReason;
    inputs: AEStack[];
    pattern: AE2Pattern;
    outputs: Array<AEStack & AEStackProviders>;
}

export interface AECPU {
    name: string;
    busy: boolean;
    availableStorage: number;
    usedStorage: number;
    coProcessors: number;
    remainingItemCount: number;
    startItemCount: number;
    elapsedTime: number;
    /** `CraftingAllowMode` 枚举名 */
    craftingAllowMode: AECraftingAllowMode;
    /** 是否有任务处于等待中 */
    waiting: boolean;
    /** 是否已挂起 */
    suspended: boolean;
    /** 是否处于缺少原料模式 */
    missingMode: boolean;
    finalOutput?: AEStack;
    /** 等待缺失的原料（服务端仅在非空时输出） */
    waitingForMissing?: AEStack[];
    tasks?: AECPUTask[];
    tasking?: Array<AEStack & AEStackProviders>;
    tasksError?: string;
}

export interface AEMEInterface {
    display: boolean;
    name: string;
    rawName: string | null;
    active: boolean;
    allowsPatternOptimization: boolean;
    playerID: number;
    location: {
        x: number;
        y: number;
        z: number;
        dimension: number;
    };
    patterns: Array<AE2Pattern & { slot: number }>;
}

export interface AECraftingTaskBody {
    /** 物品 ID；当 Type 为 "fluid" 时表示流体 ID */
    id: number;
    /** 合成数量（流体时为 mB） */
    Count: number;
    /** 堆类型，缺省或非 "fluid" 时按物品处理 */
    Type?: "item" | "fluid";
    Damage?: number;
    tag?: string;
    cpu?: string;
}

export interface AECraftingTaskResult {
    bytes: number;
    cpu: string;
    output: ItemStack & { stackSize: number };
}

export interface AECPUCancelBody {
    name?: string;
    id?: number;
}

export interface AECPUCancelResult {
    cpu: string;
    wasBusy: boolean;
}

/** AE 网络库存堆：物品（type 为 "item"）或流体（type 为 "fluid"），可通过 type 判别收窄 */
export type AEItemStack =
    | (ItemStack & { type: "item"; stackSize: number; Craftable: boolean })
    | (Fluid & { type: "fluid"; stackSize: number; Craftable: boolean });

export interface AEItemCellStatus {
    all: number;
    green: number;
    blue: number;
    orange: number;
    red: number;
}

export interface AEItemsResult {
    items: AEItemStack[];
    totalBytes: number;
    usedBytes: number;
    totalTypes: number;
    usedTypes: number;
    cellStatus: AEItemCellStatus;
    fluidTotalBytes: number;
    fluidUsedBytes: number;
    fluidTotalTypes: number;
    fluidUsedTypes: number;
    fluidCellStatus: AEItemCellStatus;
}

export interface AEHitResult {
    message: string;
}
