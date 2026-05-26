import type { ClassInfo } from "./common";
import type { ItemStack } from "./item";

export interface Fluid {
    class?: ClassInfo;
    name: string;
    defaultName: string;
    unlocalizedName: string;
    localizedName?: string;
    fluidID: number;
    color: number;
    luminosity: number;
    density: number;
    temperature: number;
    viscosity: number;
    gaseous: boolean;
    block?: number;
}

export interface FluidContainer {
    fluid?: Fluid;
    amount?: number;
    filledContainer?: ItemStack;
    emptyContainer?: ItemStack;
}
