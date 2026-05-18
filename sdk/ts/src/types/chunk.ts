import type { ClassInfo } from "./common";
import type { Entity } from "./entity";

export interface Chunk {
    class?: ClassInfo;
    chunkX: number;
    chunkZ: number;
    xStart: number;
    zStart: number;
    xEnd: number;
    zEnd: number;
    lastSaveTime: number;
    isTerrainPopulated: boolean;
    isLightPopulated: boolean;
    isModified: boolean;
    hasEntities: boolean;
    isChunkLoaded: boolean;
    sendUpdates: boolean;
    tileEntityCount: number;
    entityCount?: number;
    entityList?: Entity[][];
    inhabitedTime: number;
}

export interface ChunkWithDimension extends Chunk {
    dimension: number;
}

export interface ChunksByDimension {
    name: string;
    class?: ClassInfo;
    chunks: Chunk[];
    count: number;
}

export interface ChunkLoadInfo {
    ticketKey: string;
    chunkX: number;
    chunkZ: number;
    minX: number;
    maxX: number;
    minZ: number;
    maxZ: number;
    dimension: number;
    startTime: number;
    durationSec: number;
    remainingSec: number;
    isActive: boolean;
}

export interface ChunkForceList {
    totalLoaded: number;
    chunks: ChunkLoadInfo[];
}

export interface ChunkLoadResult extends ChunkLoadInfo {
    action: "load" | "unload";
}

export interface ChunkMapCell {
    name: string;
    meta: number;
    y: number;
}
