import type { ClassInfo } from "./common";

export interface NBTData {
    nbtstr?: string;
    nbtWrite?: string;
    nbt?: Record<string, unknown>;
}

export interface Item {
    class?: ClassInfo;
    id: number;
    registryName: string;
    UnlocalizedName: string;
    localizedName: string;
    HasSubtypes: boolean;
}

export interface ItemStack extends Item, NBTData {
    MaxStackSize: number;
    damageable: boolean;
    damage: number;
    AttributeModifiers?: Record<string, unknown>;
    stackSize?: number;
}

export interface ItemDetail extends Item {
    subs?: ItemStack[];
}

export interface AEItemDefinitions {
    items: Array<Item & { name: string }>;
    parts: Array<Item & { name: string }>;
    materials: Array<Item & { name: string }>;
    blocks: Array<Item & { name: string }>;
}
