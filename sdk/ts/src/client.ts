import type {
    RootInfo,
    TPSInfo,
    WorldInfoData,
    ProfilerData,
    LagAnalyzerData,
    Block,
    BlockDetail,
    SetBlockBody,
    SetBlockResult,
    FMPPart,
    Item,
    ItemDetail,
    AEItemDefinitions,
    Entity,
    EntitiesByDimension,
    ChunkWithDimension,
    ChunksByDimension,
    ChunkForceList,
    ChunkLoadResult,
    ChunkMapCell,
    AENode,
    AECPU,
    AEMEInterface,
    AEHitResult,
    AE2Pattern,
    AECraftingTaskBody,
    AECraftingTaskResult,
    AECPUCancelBody,
    AECPUCancelResult,
    AEItemStack,
    Coordinates,
    ApiResponse,
} from "./types";

import type {
    RootInfoSchema,
    TPSInfoSchema,
    WorldInfoDataSchema,
    ProfilerDataSchema,
    LagAnalyzerDataSchema,
    BlockSchema,
    BlockDetailSchema,
    SetBlockBodySchema,
    SetBlockResultSchema,
    FMPPartSchema,
    ItemSchema,
    ItemDetailSchema,
    AEItemDefinitionsSchema,
    EntitiesByDimensionSchema,
    EntitySchema,
    ChunksByDimensionSchema,
    ChunkWithDimensionSchema,
    ChunkForceListSchema,
    ChunkLoadResultSchema,
    ChunkMapCellSchema,
    AEHitResultSchema,
    AENodeSchema,
    AECPUSchema,
    AE2PatternSchema,
    AEMEInterfaceSchema,
    AEItemStackSchema,
    AECraftingTaskBodySchema,
    AECraftingTaskResultSchema,
    AECPUCancelBodySchema,
    AECPUCancelResultSchema,
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

    /** @returns 使用 {@link RootInfoSchema} 验证 */
    getRoot(): Promise<RootInfo> {
        return this.request<RootInfo>("/");
    }

    // ========== TPS / Performance ==========

    /** @returns 使用 `Joi.object().pattern(Joi.string(),`{@link TPSInfoSchema}`)` 验证 */
    getTPS(): Promise<Record<string, TPSInfo>> {
        return this.request<Record<string, TPSInfo>>("/tps");
    }

    /** @returns 使用 {@link ProfilerDataSchema} 验证 */
    getProfiler(): Promise<ProfilerData> {
        return this.request<ProfilerData>("/profiler");
    }

    /** @returns 使用 {@link LagAnalyzerDataSchema} 验证 */
    getLagAnalyzer(): Promise<LagAnalyzerData> {
        return this.request<LagAnalyzerData>("/lag-analyzer");
    }

    // ========== World ==========

    /** @returns 使用 `Joi.object().pattern(Joi.string(),`{@link WorldInfoDataSchema}`)` 验证 */
    getWorldInfo(): Promise<Record<string, WorldInfoData>> {
        return this.request<Record<string, WorldInfoData>>("/WorldInfo");
    }

    // ========== Blocks ==========

    /** @returns 使用 {@link BlockSchema}[] 验证 */
    getBlocks(): Promise<Block[]> {
        return this.request<Block[]>("/blocks");
    }

    /** @returns 使用 {@link BlockDetailSchema} 验证 */
    getBlock(params: { x: number, y: number, z: number, dim?: number }): Promise<BlockDetail> {
        return this.request<BlockDetail>(`/block${buildQuery(params)}`);
    }

    /**
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

    /** @returns 使用 {@link FMPPartSchema}[] 验证 */
    getBlockFMP(params: Coordinates): Promise<FMPPart[]> {
        return this.request<FMPPart[]>(`/block/fmp${buildQuery(params)}`);
    }

    /** @returns 二进制数据，无 JSON Schema */
    getBlockTile(params: { id?: number; regName?: string; meta?: number }): Promise<ArrayBuffer> {
        return this.request<ArrayBuffer>(`/block/tile${buildQuery(params)}`);
    }

    // ========== Items ==========

    /** @returns 使用 {@link ItemSchema}[] 验证 */
    getItems(): Promise<Item[]> {
        return this.request<Item[]>("/items");
    }

    /** @returns 使用 {@link ItemDetailSchema} 验证 */
    getItem(params: { id: number }): Promise<ItemDetail> {
        return this.request<ItemDetail>(`/item${buildQuery(params)}`);
    }

    /** @returns 使用 {@link AEItemDefinitionsSchema} 验证 */
    getAEItems(): Promise<AEItemDefinitions> {
        return this.request<AEItemDefinitions>("/items/ae");
    }

    // ========== Entities ==========

    /** @returns 使用 `Joi.object().pattern(Joi.string(),`{@link EntitiesByDimensionSchema}`)` 验证 */
    getEntities(): Promise<Record<string, EntitiesByDimension>> {
        return this.request<Record<string, EntitiesByDimension>>("/entities");
    }

    /** @returns 使用 {@link EntitySchema} 验证 */
    getEntity(params: { id: number }): Promise<Entity> {
        return this.request<Entity>(`/entity${buildQuery(params)}`);
    }

    // ========== Chunks ==========

    /** @returns 使用 `Joi.object().pattern(Joi.string(),`{@link ChunksByDimensionSchema}`)` 验证 */
    getChunks(): Promise<Record<string, ChunksByDimension>> {
        return this.request<Record<string, ChunksByDimension>>("/chunks");
    }

    /** @returns 使用 {@link ChunkWithDimensionSchema} 验证 */
    getChunk(
        params: { chunkX: number; chunkZ: number; dim?: number } | { x: number; z: number; dim?: number }
    ): Promise<ChunkWithDimension> {
        return this.request<ChunkWithDimension>(`/chunk${buildQuery(params)}`);
    }

    /** @returns JSON 模式使用 {@link ChunkMapCellSchema}[][] 验证；raw 模式为 ArrayBuffer */
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

    /** @returns 使用 {@link ChunkForceListSchema} 验证 */
    getChunkForceList(): Promise<ChunkForceList> {
        return this.request<ChunkForceList>("/chunk/force");
    }

    /** @returns 使用 {@link ChunkLoadResultSchema} 验证 */
    loadChunk(params: { x: number; z: number; dim?: number; duration?: number } | { chunkX: number; chunkZ: number; dim?: number; duration?: number }): Promise<ChunkLoadResult> {
        return this.request<ChunkLoadResult>(`/chunk/force${buildQuery({ action: "load", ...params })}`, {
            method: "POST",
        });
    }

    /** @returns 使用 {@link ChunkLoadResultSchema} 验证 */
    unloadChunk(params: { x: number; z: number; dim?: number } | { chunkX: number; chunkZ: number; dim?: number }): Promise<ChunkLoadResult> {
        return this.request<ChunkLoadResult>(`/chunk/force${buildQuery({ action: "unload", ...params })}`, {
            method: "POST",
        });
    }

    // ========== AE2 ==========

    /** @returns 使用 {@link AEHitResultSchema} 验证 */
    aeHit(params: Coordinates): Promise<AEHitResult> {
        return this.request<AEHitResult>(`/ae${buildQuery(params)}`);
    }

    /** @returns 使用 {@link AENodeSchema}[] 验证 */
    aeNodes(params: Coordinates): Promise<AENode[]> {
        return this.request<AENode[]>(`/ae/nodes${buildQuery(params)}`);
    }

    /** @returns 使用 {@link AECPUSchema}[] 验证 */
    aeCPUs(params: Coordinates): Promise<AECPU[]> {
        return this.request<AECPU[]>(`/ae/cpu${buildQuery(params)}`);
    }

    /** @returns 使用 `(`{@link AE2PatternSchema}` & { slot: Joi.NumberSchema, direction: Joi.StringSchema })[]` 验证 */
    aeME(params: Coordinates): Promise<Array<AE2Pattern & { slot: number; direction?: string }>> {
        return this.request<Array<AE2Pattern & { slot: number; direction?: string }>>(
            `/ae/me${buildQuery(params)}`
        );
    }

    /** @returns 使用 {@link AEMEInterfaceSchema}[] 验证 */
    aeMEs(params: Coordinates & { load?: boolean; world?: boolean }): Promise<AEMEInterface[]> {
        return this.request<AEMEInterface[]>(`/ae/mes${buildQuery(params)}`);
    }

    /** @returns 字符串数组，无专用 Schema */
    aeMESupport(): Promise<string[]> {
        return this.request<string[]>("/ae/me/support");
    }

    /** @returns 使用 {@link AEItemStackSchema}[] 验证 */
    aeItems(params: Coordinates): Promise<AEItemStack[]> {
        return this.request<AEItemStack[]>(`/ae/item${buildQuery(params)}`);
    }

    /**
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
