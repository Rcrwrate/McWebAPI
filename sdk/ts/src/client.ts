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
} from "./types";

type FetchResponseLike = {
    ok: boolean;
    status: number;
    statusText: string;
    headers: { get(name: string): string | null };
    json(): Promise<unknown>;
    arrayBuffer(): Promise<ArrayBuffer>;
};

export type FetchLike = (input: string, init?: Record<string, unknown>) => Promise<FetchResponseLike>;

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

    private async request<T>(path: string, init?: Record<string, unknown>): Promise<T> {
        const headers: Record<string, string> = {};
        if (init && typeof init.headers === "object" && init.headers !== null) {
            Object.assign(headers, init.headers as Record<string, string>);
        }
        if (this.authToken) {
            headers["Authorization"] = this.authToken;
        }

        const res = await this.fetchImpl(`${this.baseUrl}${path}`, {
            ...init,
            headers,
        });

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
            const body = await res.json();
            if (body && typeof (body as { success?: boolean }).success === "boolean") {
                if (!(body as { success: boolean }).success) {
                    throw new WebApiError(
                        (body as { message?: string }).message || "API Error",
                        res.status,
                        body
                    );
                }
                if ("data" in (body as Record<string, unknown>)) return (body as { data: T }).data;
            }
            return body as T;
        }

        return res.arrayBuffer() as unknown as T;
    }

    // ========== Root / Status ==========

    getRoot(): Promise<RootInfo> {
        return this.request<RootInfo>("/");
    }

    // ========== TPS / Performance ==========

    getTPS(): Promise<Record<string, TPSInfo>> {
        return this.request<Record<string, TPSInfo>>("/tps");
    }

    getProfiler(): Promise<ProfilerData> {
        return this.request<ProfilerData>("/profiler");
    }

    getLagAnalyzer(): Promise<LagAnalyzerData> {
        return this.request<LagAnalyzerData>("/lag-analyzer");
    }

    // ========== World ==========

    getWorldInfo(): Promise<Record<string, WorldInfoData>> {
        return this.request<Record<string, WorldInfoData>>("/WorldInfo");
    }

    // ========== Blocks ==========

    getBlocks(): Promise<Block[]> {
        return this.request<Block[]>("/blocks");
    }

    getBlock(params: Coordinates): Promise<BlockDetail> {
        return this.request<BlockDetail>(`/block${buildQuery(params)}`);
    }

    setBlock(params: Coordinates, body: SetBlockBody): Promise<SetBlockResult> {
        return this.request<SetBlockResult>(`/setblock${buildQuery(params)}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
    }

    getBlockFMP(params: Coordinates): Promise<FMPPart[]> {
        return this.request<FMPPart[]>(`/block/fmp${buildQuery(params)}`);
    }

    getBlockTile(params: { id?: number; regName?: string; meta?: number }): Promise<ArrayBuffer> {
        return this.request<ArrayBuffer>(`/block/tile${buildQuery(params)}`);
    }

    // ========== Items ==========

    getItems(): Promise<Item[]> {
        return this.request<Item[]>("/items");
    }

    getItem(params: { id: number }): Promise<ItemDetail> {
        return this.request<ItemDetail>(`/item${buildQuery(params)}`);
    }

    getAEItems(): Promise<AEItemDefinitions> {
        return this.request<AEItemDefinitions>("/items/ae");
    }

    // ========== Entities ==========

    getEntities(): Promise<Record<string, EntitiesByDimension>> {
        return this.request<Record<string, EntitiesByDimension>>("/entities");
    }

    getEntity(params: { id: number }): Promise<Entity> {
        return this.request<Entity>(`/entity${buildQuery(params)}`);
    }

    // ========== Chunks ==========

    getChunks(): Promise<Record<string, ChunksByDimension>> {
        return this.request<Record<string, ChunksByDimension>>("/chunks");
    }

    getChunk(
        params: { chunkX: number; chunkZ: number; dim?: number } | { x: number; z: number; dim?: number }
    ): Promise<ChunkWithDimension> {
        return this.request<ChunkWithDimension>(`/chunk${buildQuery(params)}`);
    }

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

    getChunkForceList(): Promise<ChunkForceList> {
        return this.request<ChunkForceList>("/chunk/force");
    }

    loadChunk(params: { x: number; z: number; dim?: number; duration?: number }): Promise<ChunkLoadResult> {
        return this.request<ChunkLoadResult>(`/chunk/force${buildQuery({ action: "load", ...params })}`, {
            method: "POST",
        });
    }

    unloadChunk(params: { x: number; z: number; dim?: number }): Promise<ChunkLoadResult> {
        return this.request<ChunkLoadResult>(`/chunk/force${buildQuery({ action: "unload", ...params })}`, {
            method: "POST",
        });
    }

    // ========== AE2 ==========

    aeHit(params: Coordinates): Promise<AEHitResult> {
        return this.request<AEHitResult>(`/ae${buildQuery(params)}`);
    }

    aeNodes(params: Coordinates): Promise<AENode[]> {
        return this.request<AENode[]>(`/ae/nodes${buildQuery(params)}`);
    }

    aeCPUs(params: Coordinates): Promise<AECPU[]> {
        return this.request<AECPU[]>(`/ae/cpu${buildQuery(params)}`);
    }

    aeME(params: Coordinates): Promise<Array<AE2Pattern & { slot: number; direction?: string }>> {
        return this.request<Array<AE2Pattern & { slot: number; direction?: string }>>(
            `/ae/me${buildQuery(params)}`
        );
    }

    aeMEs(params: Coordinates & { load?: boolean; world?: boolean }): Promise<AEMEInterface[]> {
        return this.request<AEMEInterface[]>(`/ae/mes${buildQuery(params)}`);
    }

    aeMESupport(): Promise<string[]> {
        return this.request<string[]>("/ae/me/support");
    }

    aeItems(params: Coordinates): Promise<AEItemStack[]> {
        return this.request<AEItemStack[]>(`/ae/item${buildQuery(params)}`);
    }

    aeCraft(params: Coordinates, body: AECraftingTaskBody): Promise<AECraftingTaskResult> {
        return this.request<AECraftingTaskResult>(`/ae/cpu/task${buildQuery(params)}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
    }

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
