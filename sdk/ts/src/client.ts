import type {
    AE2Pattern,
    AECPU,
    AECPUCancelBody,
    AECPUCancelResult,
    AECraftingTaskBody,
    AECraftingTaskResult,
    AEHitResult,
    AEItemDefinitions,
    AEItemStack,
    AEMEInterface,
    AENode,
    ApiResponse,
    Block,
    BlockDetail,
    ChunkForceList,
    ChunkLoadResult,
    ChunkMapCell,
    ChunksByDimension,
    ChunkWithDimension,
    Coordinates,
    EntitiesByDimension,
    Entity,
    FMPPart,
    Item,
    ItemDetail,
    LagAnalyzerData,
    ProfilerData,
    RootInfo,
    SetBlockBody,
    SetBlockResult,
    TPSInfo,
    WorldInfoData,
} from "./types";

import type {
    AE2PatternSchema,
    AECPUCancelBodySchema,
    AECPUCancelResultSchema,
    AECPUSchema,
    AECraftingTaskBodySchema,
    AECraftingTaskResultSchema,
    AEHitResultSchema,
    AEItemDefinitionsSchema,
    AEItemStackSchema,
    AEMEInterfaceSchema,
    AENodeSchema,
    BlockDetailSchema,
    BlockSchema,
    ChunkForceListSchema,
    ChunkLoadResultSchema,
    ChunkMapCellSchema,
    ChunksByDimensionSchema,
    ChunkWithDimensionSchema,
    EntitiesByDimensionSchema,
    EntitySchema,
    EntitySummarySchema,
    FMPPartSchema,
    ItemDetailSchema,
    ItemSchema,
    LagAnalyzerDataSchema,
    ProfilerDataSchema,
    RootInfoSchema,
    SetBlockBodySchema,
    SetBlockResultSchema
} from "./validators";

export type FetchLike = (input: string | URL | Request, init?: RequestInit) => Promise<Response>;


export interface WebApiClientOptions {
    baseUrl: string;
    authToken?: string;
    fetch?: FetchLike;
}

function buildQuery(params: object): string {
    const record = params as Record<string, unknown>;
    const qs = Object.entries(record)
        .filter(([, v]) => v !== undefined)
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
        .join("&");
    return qs ? `?${qs}` : "";
}

export class WebApiClient {
    private baseUrl: string;
    private authToken?: string;
    private fetchImpl: FetchLike;

    constructor(options: WebApiClientOptions) {
        this.baseUrl = options.baseUrl.replace(/\/$/, "");
        this.authToken = options.authToken;
        const globalFetch =
            typeof globalThis !== "undefined" ? (globalThis as Record<string, unknown>).fetch : undefined;
        this.fetchImpl = options.fetch || (globalFetch as FetchLike) || undefined;
        if (!this.fetchImpl) {
            throw new Error(
                "No fetch implementation provided. Pass one via options.fetch or run in an environment with global fetch."
            );
        }
    }

    private async request<T>(path: string, init?: RequestInit): Promise<T> {
        const req = new Request(`${this.baseUrl}${path}`, init)
        if (this.authToken) {
            req.headers.set("Authorization", this.authToken);
        }
        const res = await this.fetchImpl(req);
        const contentType = res.headers.get("content-type") || "";
        if (!res.ok) {
            if (contentType.includes("application/json")) {
                const body = await res.json().catch(() => ({ message: res.statusText }));
                throw new WebApiError(
                    (body as { message?: string }).message || res.statusText,
                    res.status,
                    body
                );
            }
            throw new WebApiError(res.statusText, res.status);
        }

        if (contentType.includes("application/json")) {
            const body = await res.json() as ApiResponse<T>;
            if (body.success) {
                return body.data;
            } else {
                throw new WebApiError(
                    body.message,
                    res.status,
                    body
                );
            }
        }

        return res.arrayBuffer() as unknown as T;
    }

    // ========== Root / Status ==========

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/RootHandler.java)
     * @returns 使用 {@link RootInfoSchema} 验证
     */
    getRoot(): Promise<RootInfo> {
        return this.request<RootInfo>("/");
    }

    // ========== TPS / Performance ==========

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/TPSHandler.java)
     * @returns 使用 `Joi.object().pattern(Joi.string(), {@link TPSInfoSchema})` 验证
     */
    getTPS(): Promise<Record<string, TPSInfo>> {
        return this.request<Record<string, TPSInfo>>("/tps");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/ProfilerHandler.java)
     * @returns 使用 {@link ProfilerDataSchema} 验证
     */
    getProfiler(): Promise<ProfilerData> {
        return this.request<ProfilerData>("/profiler");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/LagAnalyzerHandler.java)
     * @returns 使用 {@link LagAnalyzerDataSchema} 验证
     */
    getLagAnalyzer(): Promise<LagAnalyzerData> {
        return this.request<LagAnalyzerData>("/lag-analyzer");
    }

    // ========== World ==========

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/WorldInfoHandler.java)
     * @returns 使用 `Joi.object().pattern(Joi.string(), {@link WorldInfoDataSchema})` 验证
     */
    getWorldInfo(): Promise<Record<string, WorldInfoData>> {
        return this.request<Record<string, WorldInfoData>>("/WorldInfo");
    }

    // ========== Blocks ==========

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/block/BlocksHandler.java)
     * @returns 使用 {@link BlockSchema}[] 验证
     */
    getBlocks(): Promise<Block[]> {
        return this.request<Block[]>("/blocks");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/block/BlockHandler.java)
     * @returns 使用 {@link BlockDetailSchema} 验证
     */
    getBlock(params: { x: number, y: number, z: number, dim?: number }): Promise<BlockDetail> {
        return this.request<BlockDetail>(`/block${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/block/SetBlockHandler.java)
     * @param body 使用 {@link SetBlockBodySchema} 验证
     * @returns 使用 {@link SetBlockResultSchema} 验证
     */
    setBlock(params: { x: number, y: number, z: number, dim?: number }, body: SetBlockBody): Promise<SetBlockResult> {
        return this.request<SetBlockResult>(`/setblock${buildQuery(params)}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/block/FMPHandler.java)
     * @returns 使用 {@link FMPPartSchema}[] 验证
     */
    getBlockFMP(params: Coordinates): Promise<FMPPart[]> {
        return this.request<FMPPart[]>(`/block/fmp${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/block/BlockTileHandler.java)
     * @returns 二进制数据，无 JSON Schema
     */
    getBlockTile(params: { id?: number; regName?: string; meta?: number }): Promise<ArrayBuffer> {
        return this.request<ArrayBuffer>(`/block/tile${buildQuery(params)}`);
    }

    // ========== Items ==========

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/item/ItemsHandler.java)
     * @returns 使用 {@link ItemSchema}[] 验证
     */
    getItems(): Promise<Item[]> {
        return this.request<Item[]>("/items");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/item/ItemHandler.java)
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/item/ItemStaticHandler.java)
     * @returns 使用 {@link ItemDetailSchema} 验证
     */
    getItem(params: { id: number }): Promise<ItemDetail> {
        return this.request<ItemDetail>(`/item${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/item/ItemIconHandler.java)
     * @param params.id 物品 ID（对应 ItemStack 的 id）
     * @param params.damage 物品损伤值/元数据（可选，默认 0）
     * @param params.tag Base64 编码的 NBTTagCompound（可选）
     * @returns 二进制 PNG 图片数据 (ArrayBuffer)
     */
    getItemIcon(params: { id: number; damage?: number; tag?: string }): Promise<ArrayBuffer> {
        return this.request<ArrayBuffer>(`/item/icon${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/item/AEHandler.java)
     * @returns 使用 {@link AEItemDefinitionsSchema} 验证
     */
    getAEItems(): Promise<AEItemDefinitions> {
        return this.request<AEItemDefinitions>("/items/ae");
    }

    // ========== Entities ==========

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/entity/EntitiesHandler.java)
     * @returns 使用 `Joi.object().pattern(Joi.string(),`{@link EntitiesByDimensionSchema}`)` 验证
     * 
     * 其中实体为 {@link EntitySummarySchema} 精简结构
     */
    getEntities(): Promise<Record<string, EntitiesByDimension>> {
        return this.request<Record<string, EntitiesByDimension>>("/entities");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/entity/EntityHandler.java)
     * @returns 使用 {@link EntitySchema} 验证
     */
    getEntity(params: { id: number }): Promise<Entity> {
        return this.request<Entity>(`/entity${buildQuery(params)}`);
    }

    // ========== Chunks ==========

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/chunk/ChunksHandler.java)
     * @returns 使用 `Joi.object().pattern(Joi.string(),`{@link ChunksByDimensionSchema}`)` 验证
     */
    getChunks(): Promise<Record<string, ChunksByDimension>> {
        return this.request<Record<string, ChunksByDimension>>("/chunks");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/chunk/ChunkHandler.java)
     * @returns 使用 {@link ChunkWithDimensionSchema} 验证
     */
    getChunk(
        params: { chunkX: number; chunkZ: number; dim?: number } | { x: number; z: number; dim?: number }
    ): Promise<ChunkWithDimension> {
        return this.request<ChunkWithDimension>(`/chunk${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/chunk/ChunkMapHandler.java)
     * @returns JSON 模式使用 {@link ChunkMapCellSchema}[][] 验证；raw 模式为 ArrayBuffer
     */
    getChunkMap(
        params: { chunkX: number; chunkZ: number; dim?: number } | { x: number; z: number; dim?: number },
        raw?: boolean
    ): Promise<ArrayBuffer | ChunkMapCell[][]> {
        const q = buildQuery({
            ...(params as object),
            raw: raw !== undefined ? String(raw) : undefined,
        } as object);
        return this.request<ArrayBuffer | ChunkMapCell[][]>(`/chunk/map${q}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/chunk/ChunkForceHandler.java)
     * @returns 使用 {@link ChunkForceListSchema} 验证
     */
    getChunkForceList(): Promise<ChunkForceList> {
        return this.request<ChunkForceList>("/chunk/force");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/chunk/ChunkForceHandler.java)
     * @returns 使用 {@link ChunkLoadResultSchema} 验证
     */
    loadChunk(params: { x: number; z: number; dim?: number; duration?: number } | { chunkX: number; chunkZ: number; dim?: number; duration?: number }): Promise<ChunkLoadResult> {
        return this.request<ChunkLoadResult>(`/chunk/force${buildQuery({ action: "load", ...params })}`, {
            method: "POST",
        });
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/chunk/ChunkForceHandler.java)
     * @returns 使用 {@link ChunkLoadResultSchema} 验证
     */
    unloadChunk(params: { x: number; z: number; dim?: number } | { chunkX: number; chunkZ: number; dim?: number }): Promise<ChunkLoadResult> {
        return this.request<ChunkLoadResult>(`/chunk/force${buildQuery({ action: "unload", ...params })}`, {
            method: "POST",
        });
    }

    // ========== AE2 ==========

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/ae2/AEBaseHandler.java)
     * @returns 使用 {@link AEHitResultSchema} 验证
     */
    aeHit(params: Coordinates): Promise<AEHitResult> {
        return this.request<AEHitResult>(`/ae${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/ae2/AENodesHandler.java)
     * @returns 使用 {@link AENodeSchema}[] 验证
     */
    aeNodes(params: Coordinates): Promise<AENode[]> {
        return this.request<AENode[]>(`/ae/nodes${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/ae2/AECPUHandler.java)
     * @returns 使用 {@link AECPUSchema}[] 验证
     */
    aeCPUs(params: Coordinates): Promise<AECPU[]> {
        return this.request<AECPU[]>(`/ae/cpu${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/ae2/AEMEHandler.java)
     * @returns 使用 `(`{@link AE2PatternSchema}`& { slot: Joi.NumberSchema, direction: Joi.StringSchema })[]` 验证
     */
    aeME(params: Coordinates): Promise<Array<AE2Pattern & { slot: number; direction?: string }>> {
        return this.request<Array<AE2Pattern & { slot: number; direction?: string }>>(
            `/ae/me${buildQuery(params)}`
        );
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/ae2/AEMEsHandler.java)
     * @returns 使用 {@link AEMEInterfaceSchema}[] 验证
     */
    aeMEs(params: Coordinates & { load?: boolean; world?: boolean }): Promise<AEMEInterface[]> {
        return this.request<AEMEInterface[]>(`/ae/mes${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/ae2/AEMEsupportHandler.java)
     * @returns 字符串数组，无专用 Schema
     */
    aeMESupport(): Promise<string[]> {
        return this.request<string[]>("/ae/me/support");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/ae2/AEItemHandler.java)
     * @returns 使用 {@link AEItemStackSchema}[] 验证
     */
    aeItems(params: Coordinates): Promise<AEItemStack[]> {
        return this.request<AEItemStack[]>(`/ae/item${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/ae2/AECPUTaskHandler.java)
     * @param body 使用 {@link AECraftingTaskBodySchema} 验证
     * @returns 使用 {@link AECraftingTaskResultSchema} 验证
     */
    aeCraft(params: Coordinates, body: AECraftingTaskBody): Promise<AECraftingTaskResult> {
        return this.request<AECraftingTaskResult>(`/ae/cpu/task${buildQuery(params)}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/server/handlers/ae2/AECPUCancelHandler.java)
     * @param body 使用 {@link AECPUCancelBodySchema} 验证
     * @returns 使用 {@link AECPUCancelResultSchema} 验证
     */
    aeCancel(params: Coordinates, body: AECPUCancelBody): Promise<AECPUCancelResult> {
        return this.request<AECPUCancelResult>(`/ae/cpu/cancel${buildQuery(params)}`, {
            method: "DELETE",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
    }
}

export class WebApiError extends Error {
    status: number;
    body?: unknown;

    constructor(message: string, status: number, body?: unknown) {
        super(message);
        this.name = "WebApiError";
        this.status = status;
        this.body = body;
    }
}
