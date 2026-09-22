import type { ClassInfo } from "./common";
import type { ItemStack, NBTData } from "./item";

export interface EntityBase {
    name: string;
    entityId: number;
    uniqueId: string;
    dimension: number;
    posX: number;
    posY: number;
    posZ: number;
    prevPosX?: number;
    prevPosY?: number;
    prevPosZ?: number;
    motionX: number;
    motionY: number;
    motionZ: number;
    rotationYaw: number;
    rotationPitch: number;
    prevRotationYaw?: number;
    prevRotationPitch?: number;
    width: number;
    height: number;
    yOffset: number;
    stepHeight: number;
    boundingBoxMinX?: number;
    boundingBoxMinY?: number;
    boundingBoxMinZ?: number;
    boundingBoxMaxX?: number;
    boundingBoxMaxY?: number;
    boundingBoxMaxZ?: number;
    onGround: boolean;
    isCollided: boolean;
    isCollidedHorizontally: boolean;
    isCollidedVertically: boolean;
    isDead: boolean;
    isAirBorne: boolean;
    inWater: boolean;
    fallDistance: number;
    ticksExisted: number;
    fireResistance: number;
    hurtResistantTime: number;
    chunkCoordX: number;
    chunkCoordY: number;
    chunkCoordZ: number;
    RidingEntity: string | null;
    RiddenByEntity: string | null;
}

export interface PotionEffect {
    name: string;
    duration: number;
    amplifier: number;
    isAmbient: boolean;
}

export interface EntityLivingBaseInfo {
    health: number;
    maxHealth: number;
    absorptionAmount: number;
    prevHealth?: number;
    TotalArmor: number;
    items?: ItemStack[];
    hurtTime: number;
    maxHurtTime: number;
    attackTime: number;
    deathTime: number;
    attackedAtYaw: number;
    arrowCountInEntity: number;
    arrowHitTimer: number;
    entityAge: number;
    jumpMovementFactor: number;
    isOnLadder: boolean;
    canBreatheUnderwater: boolean;
    maxHurtResistantTime: number;
    cameraPitch?: number;
    prevCameraPitch?: number;
    attributes?: Record<string, { baseValue: number; currentValue: number }>;
    creatureAttribute: string;
    revengeTargetId?: number;
    lastAttackerId?: number;
    activePotionEffects?: Record<string, PotionEffect>;
}

export interface PlayerAbilities {
    walkSpeed: number;
    flySpeed: number;
    /** 是否飞行中 */
    flying: number;
    /** 是否可瞬间破坏方块（创造模式） */
    instabuild: number;
    /** 是否允许飞行 */
    mayfly: number;
    /** 是否无敌 */
    invulnerable: number;
    /** 是否允许修改方块 */
    mayBuild: number;
}

export interface PlayerInfo {
    experienceLevel: number;
    experience: number;
    experienceTotal: number;
    abilities: PlayerAbilities;
    isSleeping: boolean;
    isBlocking: boolean;
    score: number;
    food: {
        foodLevel: number;
        saturationLevel: number;
    };
    /** 主手物品，未持有物品时不输出 */
    heldItem?: ItemStack;
    /** 4 格盔甲槽，空槽为 null */
    armor: (ItemStack | null)[];
    /** 主背包中的非空物品 */
    items: Array<ItemStack & { slot: number }>;
}

export interface Entity {
    Entity: EntityBase & Partial<NBTData>;
    EntityLivingBase?: EntityLivingBaseInfo;
    Player?: PlayerInfo;
    class?: ClassInfo;
}

/** getEntities 返回的精简实体（服务端强制 all=false） */
export interface EntitySummary {
    Entity: Pick<EntityBase, "name" | "entityId" | "uniqueId" | "dimension" | "posX" | "posY" | "posZ">;
}

export interface EntitiesByDimension {
    WorldName: string;
    loadedEntityList: EntitySummary[];
}
