import Joi from "joi";
import type { EntitiesByDimension, Entity } from "../types/entity";
import { ClassInfoSchema } from "./common";
import { ItemStackSchema, NBTDataSchema } from "./item";

export const EntityBaseSchema = Joi.object({
    name: Joi.string().required(),
    entityId: Joi.number().required(),
    uniqueId: Joi.string().required(),
    dimension: Joi.number().required(),
    posX: Joi.number().required(),
    posY: Joi.number().required(),
    posZ: Joi.number().required(),
    prevPosX: Joi.number().optional(),
    prevPosY: Joi.number().optional(),
    prevPosZ: Joi.number().optional(),
    motionX: Joi.number().required(),
    motionY: Joi.number().required(),
    motionZ: Joi.number().required(),
    rotationYaw: Joi.number().required(),
    rotationPitch: Joi.number().required(),
    prevRotationYaw: Joi.number().optional(),
    prevRotationPitch: Joi.number().optional(),
    width: Joi.number().required(),
    height: Joi.number().required(),
    yOffset: Joi.number().required(),
    stepHeight: Joi.number().required(),
    boundingBoxMinX: Joi.number().optional(),
    boundingBoxMinY: Joi.number().optional(),
    boundingBoxMinZ: Joi.number().optional(),
    boundingBoxMaxX: Joi.number().optional(),
    boundingBoxMaxY: Joi.number().optional(),
    boundingBoxMaxZ: Joi.number().optional(),
    onGround: Joi.boolean().required(),
    isCollided: Joi.boolean().required(),
    isCollidedHorizontally: Joi.boolean().required(),
    isCollidedVertically: Joi.boolean().required(),
    isDead: Joi.boolean().required(),
    isAirBorne: Joi.boolean().required(),
    inWater: Joi.boolean().required(),
    fallDistance: Joi.number().required(),
    ticksExisted: Joi.number().required(),
    fireResistance: Joi.number().required(),
    hurtResistantTime: Joi.number().required(),
    chunkCoordX: Joi.number().required(),
    chunkCoordY: Joi.number().required(),
    chunkCoordZ: Joi.number().required(),
    RidingEntity: Joi.alternatives(Joi.string(), Joi.valid(null)).required(),
    RiddenByEntity: Joi.alternatives(Joi.string(), Joi.valid(null)).required(),
});

export const PotionEffectSchema = Joi.object({
    name: Joi.string().required(),
    duration: Joi.number().required(),
    amplifier: Joi.number().required(),
    isAmbient: Joi.boolean().required(),
});

export const EntityLivingBaseInfoSchema = Joi.object({
    health: Joi.number().required(),
    maxHealth: Joi.number().required(),
    absorptionAmount: Joi.number().required(),
    prevHealth: Joi.number().optional(),
    TotalArmor: Joi.number().required(),
    items: Joi.array().items(ItemStackSchema).optional(),
    hurtTime: Joi.number().required(),
    maxHurtTime: Joi.number().required(),
    attackTime: Joi.number().required(),
    deathTime: Joi.number().required(),
    attackedAtYaw: Joi.number().required(),
    arrowCountInEntity: Joi.number().required(),
    arrowHitTimer: Joi.number().required(),
    entityAge: Joi.number().required(),
    jumpMovementFactor: Joi.number().required(),
    isOnLadder: Joi.boolean().required(),
    canBreatheUnderwater: Joi.boolean().required(),
    maxHurtResistantTime: Joi.number().required(),
    cameraPitch: Joi.number().optional(),
    prevCameraPitch: Joi.number().optional(),
    attributes: Joi.object().pattern(
        Joi.string(),
        Joi.object({ baseValue: Joi.number().required(), currentValue: Joi.number().required() })
    ).optional(),
    creatureAttribute: Joi.string().required(),
    revengeTargetId: Joi.number().optional(),
    lastAttackerId: Joi.number().optional(),
    activePotionEffects: Joi.object().pattern(Joi.string(), PotionEffectSchema).optional(),
});

export const PlayerInfoSchema = Joi.object({
    experienceLevel: Joi.number().required(),
    experience: Joi.number().required(),
    experienceTotal: Joi.number().required(),
    isSleeping: Joi.boolean().required(),
    isBlocking: Joi.boolean().required(),
    score: Joi.number().required(),
    food: Joi.object({
        foodLevel: Joi.number().required(),
        saturationLevel: Joi.number().required(),
    }).required(),
    heldItem: ItemStackSchema.optional(),
    armor: Joi.array().items(Joi.alternatives(ItemStackSchema, Joi.valid(null))).optional(),
    items: Joi.array().items(ItemStackSchema.append({ slot: Joi.number().required() })).optional(),
});

export const EntitySchema = Joi.object<Entity>({
    Entity: EntityBaseSchema.concat(NBTDataSchema).required(),
    EntityLivingBase: EntityLivingBaseInfoSchema.optional(),
    Player: PlayerInfoSchema.optional(),
    class: ClassInfoSchema.optional(),
});

export const EntitySummarySchema = Joi.object({
    Entity: Joi.object({
        name: Joi.string().required(),
        entityId: Joi.number().required(),
        uniqueId: Joi.string().required(),
        dimension: Joi.number().required(),
        posX: Joi.number().required(),
        posY: Joi.number().required(),
        posZ: Joi.number().required(),
    }).required(),
});

export const EntitiesByDimensionSchema = Joi.object<EntitiesByDimension>({
    WorldName: Joi.string().required(),
    loadedEntityList: Joi.array().items(EntitySummarySchema).required(),
});
