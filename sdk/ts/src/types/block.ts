import type { ClassInfo } from "./common";
import type { ItemStack } from "./item";

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
    resistance: number;
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
        class?: ClassInfo;
        inventorySize?: number;
        items?: Array<ItemStack & { slot: number }>;
    } & Record<string, unknown>;
}

export interface SetBlockBody {
    id: number;
    metadataIn?: number;
    flag?: number;
}

export interface SetBlockResult {
    success: boolean;
    data: null;
}
