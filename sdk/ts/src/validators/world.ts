import Joi from "joi";
import { ClassInfoSchema } from "./common";

export const TPSInfoSchema = Joi.object({
    WorldName: Joi.string().optional(),
    TickTime: Joi.number().required(),
    TPS: Joi.number().required(),
});

export const WorldInfoDataSchema = Joi.object({
    WorldServerClass: ClassInfoSchema.optional(),
    WorldInfoClass: ClassInfoSchema.optional(),
    WorldInfo: Joi.object().unknown().required(),
});

export const ProfilerDataSchema = Joi.object({
    server: Joi.object({
        totalMemoryMB: Joi.number().required(),
        freeMemoryMB: Joi.number().required(),
        maxMemoryMB: Joi.number().required(),
    }).required(),
    dimensions: Joi.object().pattern(
        Joi.string(),
        Joi.object({
            name: Joi.string().required(),
            loadedChunks: Joi.number().required(),
            totalEntities: Joi.number().required(),
            totalTileEntities: Joi.number().required(),
            laggyChunks: Joi.array().items(
                Joi.object({
                    chunkX: Joi.number().required(),
                    chunkZ: Joi.number().required(),
                    entityCount: Joi.number().required(),
                })
            ).required(),
        })
    ).required(),
    profiler: Joi.object({
        enabled: Joi.boolean().required(),
    }).required(),
});

export const LagAnalyzerDataSchema = Joi.object({
    entities: Joi.object({
        byType: Joi.array().items(
            Joi.object({
                name: Joi.string().required(),
                count: Joi.number().required(),
                items: Joi.number().required(),
                xpOrbs: Joi.number().required(),
            })
        ).required(),
        byDimension: Joi.object().pattern(Joi.string(), Joi.number()).required(),
    }).required(),
    tileEntities: Joi.object({
        byType: Joi.array().items(
            Joi.object({
                name: Joi.string().required(),
                count: Joi.number().required(),
                samplePositions: Joi.array().items(Joi.string()).required(),
            })
        ).required(),
        byDimension: Joi.object().pattern(Joi.string(), Joi.number()).required(),
    }).required(),
    memory: Joi.object({
        totalMB: Joi.number().required(),
        freeMB: Joi.number().required(),
        usedMB: Joi.number().required(),
        maxMB: Joi.number().required(),
        availableProcessors: Joi.number().required(),
    }).required(),
});
