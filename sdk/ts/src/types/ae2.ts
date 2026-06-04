import type { ClassInfo, Coordinates } from "./common";
import type { ItemStack } from "./item";

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
    condensedInputs?: Array<ItemStack & { count: number }>;
    condensedOutputs?: Array<ItemStack & { count: number }>;
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
    finalOutput?: ItemStack & { stackSize: number };
    tasks?: Array<{
        remaining: number;
        inputs: Array<ItemStack & { stackSize: number }>;
        pattern: AE2Pattern;
        outputs: Array<ItemStack & {
            stackSize: number;
            providers: Array<{ x: number; y: number; z: number; dimension: number }>;
        }>;
    }>;
    tasking?: Array<ItemStack & {
        stackSize: number;
        providers: Array<{ x: number; y: number; z: number; dimension: number }>;
    }>;
    tasksError?: string;
}

export interface AEMEInterface {
    display: boolean;
    name: string;
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
    id: number;
    Count: number;
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

export type AEItemStack = ItemStack & { stackSize: number, Craftable: boolean };

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
}

export interface AEHitResult {
    message: string;
}
