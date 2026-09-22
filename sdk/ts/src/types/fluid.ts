import type { ClassInfo } from "./common";
import type { ItemStack } from "./item";

export interface Fluid {
    class?: ClassInfo;
    id: number;
    name: string;
    registryName: string;
    unlocalizedName: string;
    localizedName?: string;
    color: number;
    luminosity: number;
    density: number;
    temperature: number;
    viscosity: number;
    gaseous: boolean;
    block?: number;
}

export interface FluidTank extends Fluid {
    /** 
     * 储罐索引
     * @example 从 0-5 分别对应 下上北南西东
     */
    index: number;
    /** 当前存储量（mB） */
    amount: number;
    /** 储罐容量（mB） */
    capacity: number;
}

export interface FluidContainer {
    fluid?: Fluid;
    amount?: number;
    filledContainer?: ItemStack;
    emptyContainer?: ItemStack;
}
