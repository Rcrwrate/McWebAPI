import type { ClassInfo } from "./common";

export interface TPSInfo {
    WorldName?: string;
    TickTime: number;
    TPS: number;
}

export interface WorldInfoData {
    WorldServerClass?: ClassInfo;
    WorldInfoClass?: ClassInfo;
    WorldInfo: Record<string, unknown>;
}

export interface ProfilerData {
    server: {
        totalMemoryMB: number;
        freeMemoryMB: number;
        maxMemoryMB: number;
    };
    dimensions: Record<string, {
        name: string;
        loadedChunks: number;
        totalEntities: number;
        totalTileEntities: number;
        laggyChunks: Array<{
            chunkX: number;
            chunkZ: number;
            entityCount: number;
        }>;
    }>;
    profiler: {
        enabled: boolean;
    };
}

export interface LagAnalyzerData {
    entities: {
        byType: Array<{
            name: string;
            count: number;
            items: number;
            xpOrbs: number;
        }>;
        byDimension: Record<string, number>;
    };
    tileEntities: {
        byType: Array<{
            name: string;
            count: number;
            samplePositions: string[];
        }>;
        byDimension: Record<string, number>;
    };
    memory: {
        totalMB: number;
        freeMB: number;
        usedMB: number;
        maxMB: number;
        availableProcessors: number;
    };
}
