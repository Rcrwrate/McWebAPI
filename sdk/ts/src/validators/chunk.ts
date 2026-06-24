import Joi from "joi";
import type { ChunkForceList, ChunkLoadResult, ChunkMapCell, ChunksByDimension, ChunkWithDimension } from "../types/chunk";
import { ClassInfoSchema } from "./common";
import { EntitySchema } from "./entity";

export const ChunkSchema = Joi.object({
    class: ClassInfoSchema.optional(),
    chunkX: Joi.number().required(),
    chunkZ: Joi.number().required(),
    xStart: Joi.number().required(),
    zStart: Joi.number().required(),
    xEnd: Joi.number().required(),
    zEnd: Joi.number().required(),
    lastSaveTime: Joi.number().required(),
    isTerrainPopulated: Joi.boolean().required(),
    isLightPopulated: Joi.boolean().required(),
    isModified: Joi.boolean().required(),
    hasEntities: Joi.boolean().required(),
    isChunkLoaded: Joi.boolean().required(),
    sendUpdates: Joi.boolean().required(),
    tileEntityCount: Joi.number().required(),
    entityCount: Joi.number().optional(),
    entityList: Joi.array().items(Joi.array().items(EntitySchema)).optional(),
    inhabitedTime: Joi.number().required(),
});

export const ChunkWithDimensionSchema = ChunkSchema.append<ChunkWithDimension>({
    dimension: Joi.number().required(),
});

export const ChunksByDimensionSchema = Joi.object<ChunksByDimension>({
    name: Joi.string().required(),
    class: ClassInfoSchema.optional(),
    chunks: Joi.array().items(ChunkSchema).required(),
    count: Joi.number().required(),
});

export const ChunkLoadInfoSchema = Joi.object({
    ticketKey: Joi.string().required(),
    chunkX: Joi.number().required(),
    chunkZ: Joi.number().required(),
    minX: Joi.number().required(),
    maxX: Joi.number().required(),
    minZ: Joi.number().required(),
    maxZ: Joi.number().required(),
    dimension: Joi.number().required(),
    startTime: Joi.number().required(),
    durationSec: Joi.number().required(),
    remainingSec: Joi.number().required(),
    isActive: Joi.boolean().required(),
});

export const ChunkForceListSchema = Joi.object<ChunkForceList>({
    totalLoaded: Joi.number().required(),
    chunks: Joi.array().items(ChunkLoadInfoSchema).required(),
});

export const ChunkLoadResultSchema = ChunkLoadInfoSchema.append<ChunkLoadResult>({
    action: Joi.valid("load", "unload").required(),
});

export const ChunkMapCellSchema = Joi.object<ChunkMapCell>({
    name: Joi.string().required(),
    meta: Joi.number().required(),
    y: Joi.number().required(),
});
