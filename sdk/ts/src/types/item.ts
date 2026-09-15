import type { ClassInfo } from "./common";

export interface NBTCompound {
    /** NBT 的 SNBT 字符串形式 */
    nbtstr: string;
    /** Base64 编码的 NBT 二进制数据 */
    nbtWrite: string;
    /** NBT 复合标签内的各键值 */
    [key: string]: unknown;
}

/** 携带 NBT 的对象，NBT 为空时不输出 `nbt` */
export interface NBTData {
    nbt?: NBTCompound;
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
