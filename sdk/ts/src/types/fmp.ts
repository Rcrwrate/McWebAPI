import type { ClassInfo } from "./common";
import type { ItemStack } from "./item";

export interface Cuboid6Bounds {
    minX: number;
    minY: number;
    minZ: number;
    maxX: number;
    maxY: number;
    maxZ: number;
}

export interface FMPPart {
    class?: ClassInfo;
    type: string;
    lightValue: number;
    doesTick: boolean;
    bounds?: Cuboid6Bounds;
    collisionBoxes: Cuboid6Bounds[];
    drops?: ItemStack[];
    ae2?: {
        color: string;
        isEmpty: boolean;
        lightValue: number;
        parts: Array<{ direction: string; item?: ItemStack }>;
        facades: Array<{ direction: string; item?: ItemStack }>;
        error?: string;
    };
}
