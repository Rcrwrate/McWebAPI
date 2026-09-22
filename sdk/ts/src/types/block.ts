import type { ClassInfo } from "./common";
import type { FluidTank } from "./fluid";
import type { ItemStack, NBTCompound } from "./item";

export interface Material {
    isLiquid: boolean;
    isSolid: boolean;
    blocksMovement: boolean;
    isOpaque: boolean;
    isFlammable: boolean;
    isReplaceable: boolean;
    requiresNoTool: boolean;
    mobilityFlag: number;
    isAdventureModeExempt: boolean;
}

export interface Block {
    class?: ClassInfo;
    id: number;
    registryName: string;
    unlocalizedName: string;
    localizedName: string;
    resistance: number | "Infinity";
    lightLevel: number;
    isOpaqueCube: boolean;
    isNormalCube: boolean;
    slipperiness: number;
    renderType: number;
    material: Material;
    /** 仅在 blocks.json / 纹理导出中出现 */
    meta?: number;
    fileName?: string;
    blockColor?: number;
}

export interface BlockDetail {
    block: Block & {
        hardness: number;
        isReplaceable: boolean;
        isPassable: boolean;
    };
    coordinates: import("./common").Coordinates;
    metadata: number;
    isAir: boolean;
    tileEntity?: {
        nbt: NBTCompound;
        class?: ClassInfo;
        inventorySize?: number;
        items?: Array<ItemStack & { slot: number }>;
        fluids?: FluidTank[];
    };
}

export interface SetBlockBody {
    id: number;
    metadataIn?: number;
    flag?: number;
    nbt?: string;
}

export interface SetBlockResult {
    changed: boolean;
    nbtchanged: boolean;
}

export interface BatchSetBlockTask {
    x: number;
    y: number;
    z: number;
    dim?: number;
    id: number;
    metadata?: number;
    flag?: number;
    nbt?: string;
}

export interface BatchSetBlockSubmitResult {
    id: string;
    total: number;
}

export type BatchSetBlockJobStatus = "pending" | "running" | "completed";

export interface BatchSetBlockFailure {
    x: number;
    y: number;
    z: number;
    reason: string;
}

export interface BatchSetBlockJobResult {
    id: string;
    total: number;
    completed: number;
    success: number;
    failed: number;
    changed: number;
    nbtchanged: number;
    status: BatchSetBlockJobStatus;
    createTime: number;
    finishTime?: number;
    durationMs?: number;
    failures?: BatchSetBlockFailure[];
    failuresTruncated?: number;
}
