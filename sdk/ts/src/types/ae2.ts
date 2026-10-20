import type { ApiErrorResponse, ClassInfo } from "./common";
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

/** `appeng.crafting.v2.resolvers.CraftingTask.State` 枚举名 */
export type AECraftingTaskState = "NEEDS_MORE_WORK" | "SUCCESS" | "FAILURE";

/** `appeng.crafting.v2.CraftingRequest.SubstitutionMode` 枚举名 */
export type AECraftingSubstitutionMode = "PRECISE_FRESH" | "PRECISE" | "ACCEPT_FUZZY";

/**
 * 合成树中的单个任务
 *
 * `type` 为任务类名，常见取值：
 * - `ExtractItemTask`：从 ME 网络直接提取（真实路径）
 * - `EmitableItemTask`：发射器供给
 * - `CraftFromPatternTask`：按样板合成（递归拆出子材料请求）
 * - `IgnoreMissingItemTask`：IGNORE_MISSING 模式下假定稍后补齐
 * - `ConjureItemTask`：材料不足时"无中生有"的模拟任务，**即缺失物品**
 * - `CheckOtherResolversTask`：计算器内部调度任务
 */
export interface AECraftingTaskNode {
    type: string;
    state: AECraftingTaskState;
    /** 是否仅为模拟（`isSimulated`）：true 表示该任务供应的物品网络中实际不存在 */
    simulated: boolean;
    priority: number;
    /** `task.toString()`，如 `ConjureItemTask{fulfilled=32, ...}`，fulfilled 即缺口数量 */
    detail: string;
}

/** 合成树中单个请求的解析器条目 */
export interface AECraftingResolverNode {
    type: string;
    state: AECraftingTaskState;
    /** 是否仅为模拟：true 表示该解析器通过"无中生有"满足请求，其 `resolved` 即缺失数量 */
    simulated: boolean;
    /** 任务 `toString()` */
    detail: string;
    /** 该解析器实际提供的数量（服务端 `entry.resolvedStack.getStackSize()`，物品为个数、流体为 mB） */
    resolved: number;
}

/** 合成树中的单个请求节点 */
export interface AECraftingRequestNode {
    stack: AEStack;
    substitutionMode: AECraftingSubstitutionMode;
    /** `remainingToProcess`：仍未被满足的数量，>0 表示该请求仍有缺口 */
    remaining: number;
    byteCost: number;
    /** `wasSimulated`：该请求（含子树）是否被模拟满足 */
    simulated: boolean;
    /** `incomplete`：是否因超出合成步骤上限被截断 */
    incomplete: boolean;
    /** `request.toString()` */
    detail: string;
    /** 满足该请求的任务链 */
    resolvers: AECraftingResolverNode[];
    children?: AECraftingRequestNode[];
    /** 子请求超过最大递归深度(256)被截断的数量 */
    childrenTruncated?: number;
}

/** 合成树：`requests` 为自根请求展开的请求树，`tasks` 为按执行顺序的任务列表 */
export interface AECraftingJobTree {
    requests: AECraftingRequestNode[];
    tasks: AECraftingTaskNode[];
}

export interface AECraftingTaskResult {
    bytes: number;
    cpu: string;
    output: ItemStack & { stackSize: number };
    /**
     * 合成树
     */
    job?: AECraftingJobTree | null;
}

/**
 * AE合成任务提交失败响应体
 * 
 * 材料不足或无法合成时，`body` 携带失败时的合成树，可配合 `analyzeAECraftingJobTree` 分析缺失物品
 */
export interface AECraftingTaskErrorBody extends ApiErrorResponse {
    body?: AECraftingJobTree | null;
}

export interface AECPUCancelBody {
    name?: string;
    id?: number;
}

export interface AECPUCancelResult {
    cpu: string;
    wasBusy: boolean;
}

/** AE CPU 暂停/恢复请求体，与取消任务一致：按名称或序号定位 CPU */
export type AECPUSuspendBody = AECPUCancelBody;

export interface AECPUSuspendResult {
    cpu: string;
    wasBusy: boolean;
    /** 本次操作后的挂起状态（POST 暂停为 true，DELETE 恢复为 false） */
    Suspended: boolean;
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
