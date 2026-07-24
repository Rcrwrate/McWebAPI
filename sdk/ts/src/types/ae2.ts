import type { ClassInfo, Coordinates } from "./common";
import type { Fluid } from "./fluid";
import type { ItemStack } from "./item";

/** AE 堆（物品或流体），由服务端 Pattern.dumpAEStack 导出，count 表示该物品/流体的总数量 */
export type AEStack = (ItemStack | Fluid) & { count: number };

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

export interface AECPU {
    name: string;
    busy: boolean;
    availableStorage: number;
    usedStorage: number;
    coProcessors: number;
    remainingItemCount: number;
    startItemCount: number;
    elapsedTime: number;
    craftingAllowMode: string;
    finalOutput?: AEStack;
    tasks?: Array<{
        remaining: number;
        inputs: AEStack[];
        pattern: AE2Pattern;
        outputs: Array<AEStack & AEStackProviders>;
    }>;
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
