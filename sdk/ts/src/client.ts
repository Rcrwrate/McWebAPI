import { fetchEventSource } from "@microsoft/fetch-event-source";
import type {
    AE2Pattern,
    AECPU,
    AECPUCancelBody,
    AECPUCancelResult,
    AECraftingTaskBody,
    AECraftingTaskResult,
    AEHitResult,
    AEItemDefinitions,
    AEItemsResult,
    AEMEInterface,
    AENode,
    ApiResponse,
    BatchSetBlockJobResult,
    BatchSetBlockSubmitResult,
    BatchSetBlockTask,
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
    Fluid,
    FluidContainer,
    FMPPart,
    GT5BatchJobResult,
    GT5BatchMachineCoord,
    GT5BatchRerunResult,
    GT5BatchSubmitResult,
    GT5MachineInfo,
    GT5ScanJobResult,
    GT5ScanSubmitResult,
    Item,
    ItemDetail,
    LagAnalyzerData,
    ProfilerData,
    RootInfo,
    SetBlockBody,
    SetBlockResult,
    TPSInfo,
    WorldInfoData,
    CraftingRecipesResult,
    FurnaceRecipesResult,
    GTRecipesResult,
    GTRecipeMap,
    PlayerPrintResult,
    PrintSizeResult,
    WorldPrintJobResult,
    WorldPrintSubmitResult,
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
    AEItemsResultSchema,
    AEMEInterfaceSchema,
    AENodeSchema,
    BatchSetBlockJobResultSchema,
    BatchSetBlockSubmitResultSchema,
    BatchSetBlockTaskSchema,
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
    FluidContainerSchema,
    FluidSchema,
    FMPPartSchema,
    GT5BatchJobResultSchema,
    GT5BatchMachineCoordSchema,
    GT5BatchRerunResultSchema,
    GT5BatchSubmitResultSchema,
    GT5MachineInfoSchema,
    GT5ScanJobResultSchema,
    GT5ScanSubmitResultSchema,
    ItemDetailSchema,
    ItemSchema,
    LagAnalyzerDataSchema,
    ProfilerDataSchema,
    RootInfoSchema,
    SetBlockBodySchema,
    SetBlockResultSchema,
    CraftingRecipesResultSchema,
    FurnaceRecipesResultSchema,
    GTRecipesResultSchema,
    GTRecipeMapSchema,
    PlayerPrintResultSchema,
    PrintSizeResultSchema,
    WorldPrintJobResultSchema,
    WorldPrintSubmitResultSchema
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

/** 解包 `ApiResponse<T>`（如 SSE 事件帧），业务失败（success=false）时抛 {@link WebApiError} */
function unwrapApiResponse<T>(body: ApiResponse<T>): T {
    if (!body.success) {
        throw new WebApiError(body.message, 200, body);
    }
    return body.data;
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

    /**
     * SSE 订阅模板：统一处理鉴权头、响应校验、事件过滤与错误上报（基于 fetchEventSource，可携带
     * Authorization 头，原生 EventSource 不支持）。服务端推送的 `error` 事件（纯文本消息）会转为
     * {@link Error} 上报；连接建立后发生异常时同样上报并停止库内自动重连，由调用方决定是否重新订阅。
     * @param url 完整订阅地址（含 query）
     * @param eventName 业务事件名，其余事件将被忽略
     * @param callback 业务数据回调
     * @param errorCallback 出错回调，接收模板抛出或 `parse` 抛出的异常（如 {@link WebApiError}）
     * @param parse 解析事件的 data 文本为业务数据，抛出异常表示该帧无效
     * @returns AbortController，调用 `abort()` 可停止订阅
     */
    private subscribeSse<T>(
        url: string,
        eventName: string,
        callback: (data: T) => void,
        errorCallback: (error: Error) => void,
        parse: (data: string) => T
    ): AbortController {
        const controller = new AbortController();
        const headers: Record<string, string> = {};
        if (this.authToken) {
            headers["Authorization"] = this.authToken;
        }

        fetchEventSource(url, {
            signal: controller.signal,
            headers,
            openWhenHidden: true,
            fetch: this.fetchImpl,
            onopen: async (response) => {
                if (!response.ok) {
                    const body = (await response.json().catch(() => null)) as { message?: string } | null;
                    throw new WebApiError(body?.message || response.statusText, response.status, body);
                }
                const contentType = response.headers.get("content-type") || "";
                if (!contentType.includes("text/event-stream")) {
                    throw new WebApiError(`Expected content-type to be text/event-stream, actual: ${contentType}`, response.status);
                }
            },
            onmessage: (event) => {
                if (event.event === "error") {
                    errorCallback(new Error(event.data));
                    return;
                }
                if (event.event !== eventName) return;
                try {
                    callback(parse(event.data));
                } catch (err) {
                    errorCallback(err instanceof Error ? err : new Error(String(err)));
                }
            },
            onerror: (err) => {
                errorCallback(err instanceof Error ? err : new Error(String(err)));
                // 停止库内自动重连，由调用方决定是否重新订阅
                throw err;
            },
        }).catch(() => { /* 已通过 errorCallback 上报 */ });

        return controller;
    }

    // region Root / Status

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/RootHandler.java)
     * @returns 使用 {@link RootInfoSchema} 验证
     */
    getRoot(): Promise<RootInfo> {
        return this.request<RootInfo>("/version");
    }

    // region TPS / Performance

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/TPSHandler.java)
     * @returns 使用 `Joi.object().pattern(Joi.string(), {@link TPSInfoSchema})` 验证
     */
    getTPS(): Promise<Record<string, TPSInfo>> {
        return this.request<Record<string, TPSInfo>>("/tps");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ProfilerHandler.java)
     * @returns 使用 {@link ProfilerDataSchema} 验证
     */
    getProfiler(): Promise<ProfilerData> {
        return this.request<ProfilerData>("/profiler");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/LagAnalyzerHandler.java)
     * @returns 使用 {@link LagAnalyzerDataSchema} 验证
     */
    getLagAnalyzer(): Promise<LagAnalyzerData> {
        return this.request<LagAnalyzerData>("/lag-analyzer");
    }

    // region World

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/WorldInfoHandler.java)
     * @returns 使用 `Joi.object().pattern(Joi.string(), {@link WorldInfoDataSchema})` 验证
     */
    getWorldInfo(): Promise<Record<string, WorldInfoData>> {
        return this.request<Record<string, WorldInfoData>>("/WorldInfo");
    }

    // region Blocks

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/block/BlocksHandler.java)
     * @returns 使用 `Joi.array().items(`{@link BlockSchema}`)` 验证
     */
    getBlocks(): Promise<Block[]> {
        return this.request<Block[]>("/blocks");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/block/BlockHandler.java)
     * @returns 使用 {@link BlockDetailSchema} 验证
     */
    getBlock(params: { x: number, y: number, z: number, dim?: number }): Promise<BlockDetail> {
        return this.request<BlockDetail>(`/block${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/block/SetBlockHandler.java)
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
     * 提交批量 setblock 任务到慢队列，异步执行。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/block/BatchSetBlockHandler.java)
     * @param tasks 使用 `Joi.array().items(`{@link BatchSetBlockTaskSchema} ) 验证
     * @returns 使用 {@link BatchSetBlockSubmitResultSchema} 验证
     */
    batchSetBlock(tasks: BatchSetBlockTask[]): Promise<BatchSetBlockSubmitResult> {
        return this.request<BatchSetBlockSubmitResult>("/batchsetblock", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(tasks),
        });
    }

    /**
     * 查询批量 setblock 任务执行结果。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/block/BatchSetBlockHandler.java)
     * @returns 使用 {@link BatchSetBlockJobResultSchema} 验证
     */
    getBatchSetBlockJob(params: { id: string }): Promise<BatchSetBlockJobResult> {
        return this.request<BatchSetBlockJobResult>(`/batchsetblock${buildQuery(params)}`);
    }

    /**
     * 轮询等待批量 setblock 任务完成。
     * @param jobId 任务 ID（由 batchSetBlock 返回）
     * @param intervalMs 轮询间隔 (ms)，默认 100
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/block/BatchSetBlockHandler.java)
     * @returns 使用 {@link BatchSetBlockJobResultSchema} 验证
     */
    async waitForBatchSetBlockJob(jobId: string, intervalMs = 100): Promise<BatchSetBlockJobResult> {
        while (true) {
            const job = await this.getBatchSetBlockJob({ id: jobId });
            if (job.status === "completed") return job;
            await new Promise(r => setTimeout(r, intervalMs));
        }
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/block/FMPHandler.java)
     * @returns 使用 `Joi.array().items(`{@link FMPPartSchema}`)` 验证
     */
    getBlockFMP(params: Coordinates): Promise<FMPPart[]> {
        return this.request<FMPPart[]>(`/block/fmp${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/block/BlockTileHandler.java)
     * @returns 二进制数据，无 JSON Schema
     */
    getBlockTile(params: { id?: number; regName?: string; meta?: number }): Promise<ArrayBuffer> {
        return this.request<ArrayBuffer>(`/block/tile${buildQuery(params)}`);
    }

    // region Items

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/item/ItemsHandler.java)
     * @returns 使用 `Joi.array().items(`{@link ItemSchema}`)` 验证
     */
    getItems(): Promise<Item[]> {
        return this.request<Item[]>("/items");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/item/ItemHandler.java)
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/item/ItemStaticHandler.java)
     * @returns 使用 {@link ItemDetailSchema} 验证
     */
    getItem(params: { id: number }): Promise<ItemDetail> {
        return this.request<ItemDetail>(`/item${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/item/ItemIconHandler.java)
     * @param params.id 物品 ID（对应 ItemStack 的 id）
     * @param params.damage 物品损伤值/元数据（可选，默认 0）
     * @param params.tag Base64 编码的 NBTTagCompound（可选）
     * @returns 二进制 PNG 图片数据 (ArrayBuffer)
     */
    getItemIcon(params: { id: number; damage?: number; tag?: string }): Promise<ArrayBuffer> {
        return this.request<ArrayBuffer>(`/item/icon${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/item/AEHandler.java)
     * @returns 使用 {@link AEItemDefinitionsSchema} 验证
     */
    getAEItemsDef(): Promise<AEItemDefinitions> {
        return this.request<AEItemDefinitions>("/items/ae");
    }

    // region Fluids

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/fluid/FluidsHandler.java)
     * @returns 使用 `Joi.array().items(`{@link FluidSchema}`)` 验证
     */
    getFluids(): Promise<Fluid[]> {
        return this.request<Fluid[]>("/fluids");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/fluid/FluidContainersHandler.java)
     * @returns 使用 `Joi.array().items(`{@link FluidContainerSchema}`)` 验证
     */
    getFluidContainers(): Promise<FluidContainer[]> {
        return this.request<FluidContainer[]>("/fluidContainers");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/fluid/FluidIconHandler.java)
     * @param params.id 流体 ID（对应 Fluid 的 id）
     * @param params.name 流体名称（对应 Fluid 的 name）
     * @returns 二进制 PNG 图片数据 (ArrayBuffer)
     */
    getFluidIcon(params: { id?: number; name?: string }): Promise<ArrayBuffer> {
        return this.request<ArrayBuffer>(`/fluid/icon${buildQuery(params)}`);
    }

    // region Entities

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/entity/EntitiesHandler.java)
     * @returns 使用 `Joi.object().pattern(Joi.string(),`{@link EntitiesByDimensionSchema}`)` 验证
     * 
     * 其中实体为 {@link EntitySummarySchema} 精简结构
     */
    getEntities(): Promise<Record<string, EntitiesByDimension>> {
        return this.request<Record<string, EntitiesByDimension>>("/entities");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/entity/EntityHandler.java)
     * @returns 使用 {@link EntitySchema} 验证
     */
    getEntity(params: { id: number }): Promise<Entity> {
        return this.request<Entity>(`/entity${buildQuery(params)}`);
    }

    // region Chunks

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/chunk/ChunksHandler.java)
     * @returns 使用 `Joi.object().pattern(Joi.string(),`{@link ChunksByDimensionSchema}`)` 验证
     */
    getChunks(): Promise<Record<string, ChunksByDimension>> {
        return this.request<Record<string, ChunksByDimension>>("/chunks");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/chunk/ChunkHandler.java)
     * @returns 使用 {@link ChunkWithDimensionSchema} 验证
     */
    getChunk(
        params: { chunkX: number; chunkZ: number; dim?: number } | { x: number; z: number; dim?: number }
    ): Promise<ChunkWithDimension> {
        return this.request<ChunkWithDimension>(`/chunk${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/chunk/ChunkMapHandler.java)
     * @returns JSON 模式使用 `Joi.array().items(Joi.array().items(`{@link ChunkMapCellSchema}`)` 验证；raw 模式为 ArrayBuffer
     */
    getChunkMap(
        params: { chunkX: number; chunkZ: number; dim?: number } | { x: number; z: number; dim?: number },
        raw?: boolean
    ): Promise<ArrayBuffer | ChunkMapCell[][]> {
        const q = buildQuery({
            ...(params as object),
            raw: raw == true ? String(raw) : undefined,
        } as object);
        return this.request<ArrayBuffer | ChunkMapCell[][]>(`/chunk/map${q}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/chunk/ChunkForceHandler.java)
     * @returns 使用 {@link ChunkForceListSchema} 验证
     */
    getChunkForceList(): Promise<ChunkForceList> {
        return this.request<ChunkForceList>("/chunk/force");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/chunk/ChunkForceHandler.java)
     * @returns 使用 {@link ChunkLoadResultSchema} 验证
     */
    loadChunk(params: { x: number; z: number; dim?: number; duration?: number } | { chunkX: number; chunkZ: number; dim?: number; duration?: number }): Promise<ChunkLoadResult> {
        return this.request<ChunkLoadResult>(`/chunk/force${buildQuery({ action: "load", ...params })}`, {
            method: "POST",
        });
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/chunk/ChunkForceHandler.java)
     * @returns 使用 {@link ChunkLoadResultSchema} 验证
     */
    unloadChunk(params: { x: number; z: number; dim?: number } | { chunkX: number; chunkZ: number; dim?: number }): Promise<ChunkLoadResult> {
        return this.request<ChunkLoadResult>(`/chunk/force${buildQuery({ action: "unload", ...params })}`, {
            method: "POST",
        });
    }

    // region GT5

    /**
     * 查询单个 GT5 机器信息。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/gt5/GT5BaseHandler.java)
     * @returns 使用 {@link GT5MachineInfoSchema} 验证
     */
    getGT5Machine(params: { x: number; y: number; z: number; dim?: number }): Promise<GT5MachineInfo> {
        return this.request<GT5MachineInfo>(`/gt5${buildQuery(params)}`);
    }

    /**
     * 提交批量 GT5 机器查询任务。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/gt5/GT5BatchHandler.java)
     * @param machines 使用 `Joi.array().items(`{@link GT5BatchMachineCoordSchema}`)` 验证
     * @returns 使用 {@link GT5BatchSubmitResultSchema} 验证
     */
    submitGT5Batch(machines: GT5BatchMachineCoord[]): Promise<GT5BatchSubmitResult> {
        return this.request<GT5BatchSubmitResult>("/gt5/batch", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(machines),
        });
    }

    /**
     * 查询批量 GT5 机器任务状态。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/gt5/GT5BatchHandler.java)
     * @returns 使用 {@link GT5BatchJobResultSchema} 验证
     */
    getGT5BatchJob(params: { id: string }): Promise<GT5BatchJobResult> {
        return this.request<GT5BatchJobResult>(`/gt5/batch${buildQuery(params)}`);
    }

    /**
     * 重新执行已有的批量 GT5 机器查询任务。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/gt5/GT5BatchHandler.java)
     * @returns 使用 {@link GT5BatchRerunResultSchema} 验证
     */
    rerunGT5Batch(params: { id: string }): Promise<GT5BatchRerunResult> {
        return this.request<GT5BatchRerunResult>(`/gt5/batch${buildQuery(params)}`, {
            method: "PATCH",
        });
    }

    /**
     * 轮询等待批量 GT5 机器任务完成。
     * @param jobId 任务 ID（由 submitGT5Batch 返回）
     * @param intervalMs 轮询间隔 (ms)，默认 100
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/gt5/GT5BatchHandler.java)
     * @returns 使用 {@link GT5BatchJobResultSchema} 验证
     */
    async waitForGT5BatchJob(jobId: string, intervalMs = 100): Promise<GT5BatchJobResult> {
        while (true) {
            const job = await this.getGT5BatchJob({ id: jobId });
            if (job.status === "completed") return job;
            await new Promise(r => setTimeout(r, intervalMs));
        }
    }

    /**
     * 提交区块扫描任务，异步扫描指定区块内所有 GT5 机器。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/gt5/GT5ChunkScanHandler.java)
     * @returns 使用 {@link GT5ScanSubmitResultSchema} 验证
     */
    submitGT5ChunkScan(
        params: { chunkX: number; chunkZ: number; dim?: number } | { x: number; z: number; dim?: number }
    ): Promise<GT5ScanSubmitResult> {
        return this.request<GT5ScanSubmitResult>(`/gt5/scan${buildQuery(params)}`, {
            method: "POST",
        });
    }

    /**
     * 查询区块扫描任务状态。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/gt5/GT5ChunkScanHandler.java)
     * @returns 使用 {@link GT5ScanJobResultSchema} 验证
     */
    getGT5ScanJob(params: { id: string }): Promise<GT5ScanJobResult> {
        return this.request<GT5ScanJobResult>(`/gt5/scan${buildQuery(params)}`);
    }

    /**
     * 轮询等待区块扫描任务完成。
     * @param jobId 任务 ID（由 submitGT5ChunkScan 返回）
     * @param intervalMs 轮询间隔 (ms)，默认 100
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/gt5/GT5ChunkScanHandler.java)
     * @returns 使用 {@link GT5ScanJobResultSchema} 验证
     */
    async waitForGT5ScanJob(jobId: string, intervalMs = 100): Promise<GT5ScanJobResult> {
        while (true) {
            const job = await this.getGT5ScanJob({ id: jobId });
            if (job.status === "completed") return job;
            await new Promise(r => setTimeout(r, intervalMs));
        }
    }

    // region AE2

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AEBaseHandler.java)
     * @returns 使用 {@link AEHitResultSchema} 验证
     */
    aeHit(params: { x: number, y: number, z: number, dimension?: number }): Promise<AEHitResult> {
        return this.request<AEHitResult>(`/ae${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AENodesHandler.java)
     * @returns 使用 `Joi.array().items(`{@link AENodeSchema}`)` 验证
     */
    aeNodes(params: { x: number, y: number, z: number, dimension?: number }): Promise<AENode[]> {
        return this.request<AENode[]>(`/ae/nodes${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AECPUHandler.java)
     * @returns 使用 `Joi.array().items(`{@link AECPUSchema}`)` 验证
     */
    aeCPUs(params: { x: number, y: number, z: number, dimension?: number }): Promise<AECPU[]> {
        return this.request<AECPU[]>(`/ae/cpu${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AEMEHandler.java)
     * @returns 使用 `Joi.array().items(`{@link AE2PatternSchema}`.append({ slot: Joi.number().required(), direction: Joi.string().optional() }))` 验证
     */
    aeME(params: { x: number, y: number, z: number, dimension?: number }): Promise<Array<AE2Pattern & { slot: number; direction?: string }>> {
        return this.request<Array<AE2Pattern & { slot: number; direction?: string }>>(
            `/ae/me${buildQuery(params)}`
        );
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AEMEsHandler.java)
     * @returns 使用 `Joi.array().items(`{@link AEMEInterfaceSchema}`)` 验证
     */
    aeMEs(params: { x: number, y: number, z: number, dimension?: number, pattern?: boolean, load?: boolean; world?: boolean }): Promise<AEMEInterface[]> {
        if (params.load || params.world) {
            params.pattern = true;
        }
        return this.request<AEMEInterface[]>(`/ae/mes${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AEMEsupportHandler.java)
     * @returns 字符串数组，无专用 Schema
     */
    aeMESupport(): Promise<string[]> {
        return this.request<string[]>("/ae/me/support");
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AEItemHandler.java)
     * @returns 使用 {@link AEItemsResultSchema} 验证
     */
    aeItems(params: { x: number, y: number, z: number, dimension?: number }): Promise<AEItemsResult> {
        return this.request<AEItemsResult>(`/ae/item${buildQuery(params)}`);
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AECPUTaskHandler.java)
     * @param body 使用 {@link AECraftingTaskBodySchema} 验证
     * @returns 使用 {@link AECraftingTaskResultSchema} 验证
     */
    aeCraft(params: { x: number, y: number, z: number, dimension?: number }, body: AECraftingTaskBody): Promise<AECraftingTaskResult> {
        return this.request<AECraftingTaskResult>(`/ae/cpu/task${buildQuery(params)}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
    }

    /**
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AECPUCancelHandler.java)
     * @param body 使用 {@link AECPUCancelBodySchema} 验证
     * @returns 使用 {@link AECPUCancelResultSchema} 验证
     */
    aeCancel(params: { x: number, y: number, z: number, dimension?: number }, body: AECPUCancelBody): Promise<AECPUCancelResult> {
        return this.request<AECPUCancelResult>(`/ae/cpu/cancel${buildQuery(params)}`, {
            method: "DELETE",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
    }

    /**
     * 构建 AE 库存 SSE（Server-Sent Events）订阅地址。
     * 注意：仅当服务端启用虚拟线程（useVirtualThreads）时该路由才会注册。
     * 返回 EventSource 可直接使用的完整 URL。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AEItemSSEHandler.java)
     */
    aeItemsSseUrl(params: { x: number; y: number; z: number; dimension?: number }): string {
        return `${this.baseUrl}/ae/item/sse${buildQuery(params)}`;
    }

    /**
     * 以回调方式订阅 AE 库存 SSE 推送（基于 fetchEventSource，可携带 Authorization 头，原生 EventSource 不支持）。
     * 服务端周期性推送 `aeitem` 事件（`ApiResponse<{@link AEItemsResult}>` JSON，间隔由服务端 AE2Config.item.interval 决定），
     * 回调收到解包后的 {@link AEItemsResult}，业务失败（success=false）时以 {@link WebApiError} 上报；
     * 连接建立后发生异常时推送 `error` 事件（纯文本消息）并关闭连接。
     * @param params 目标方块坐标
     * @param callback 每次收到库存快照时调用
     * @param errorCallback 出错（网络错误、鉴权失败、服务端 error 事件、业务失败、数据解析失败）时调用，库内自动重连会随之停止
     * @returns AbortController，调用 `abort()` 可停止订阅
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AEItemSSEHandler.java)
     */
    aeItemSseCallback(
        params: { x: number; y: number; z: number; dimension?: number },
        callback: (data: AEItemsResult) => void,
        errorCallback: (error: Error) => void
    ): AbortController {
        return this.subscribeSse<AEItemsResult>(
            `${this.baseUrl}/ae/item/sse${buildQuery(params)}`,
            "aeitem",
            callback,
            errorCallback,
            raw => unwrapApiResponse(JSON.parse(raw) as ApiResponse<AEItemsResult>)
        );
    }

    /**
     * 构建 AE 合成 CPU 状态 SSE（Server-Sent Events）订阅地址。
     * 注意：仅当服务端启用虚拟线程（useVirtualThreads）时该路由才会注册。
     * 返回 EventSource 可直接使用的完整 URL。
     * @param params 目标方块坐标；interval 为推送间隔（秒），省略时由服务端取默认值 5
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AECPUSSEHandler.java)
     */
    aeCpuSseUrl(params: { x: number; y: number; z: number; dimension?: number; interval?: number }): string {
        return `${this.baseUrl}/ae/cpu/sse${buildQuery(params)}`;
    }

    /**
     * 以回调方式订阅 AE 合成 CPU 状态 SSE 推送（基于 fetchEventSource，可携带 Authorization 头，原生 EventSource 不支持）。
     * 服务端周期性推送 `aecpu` 事件（{@link AECPU} 数组 JSON，间隔由 interval 参数（秒）决定，服务端默认 5），
     * 回调收到 CPU 状态列表；连接建立后发生异常时推送 `error` 事件（纯文本消息）并关闭连接。
     * @param params 目标方块坐标；interval 为推送间隔（秒），省略时由服务端取默认值
     * @param callback 每次收到 CPU 状态快照时调用
     * @param errorCallback 出错（网络错误、鉴权失败、服务端 error 事件、业务失败、数据解析失败）时调用，库内自动重连会随之停止
     * @returns AbortController，调用 `abort()` 可停止订阅
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ae2/AECPUSSEHandler.java)
     */
    aeCpuSseCallback(
        params: { x: number; y: number; z: number; dimension?: number; interval?: number },
        callback: (data: AECPU[]) => void,
        errorCallback: (error: Error) => void
    ): AbortController {
        return this.subscribeSse<AECPU[]>(
            `${this.baseUrl}/ae/cpu/sse${buildQuery(params)}`,
            "aecpu",
            callback,
            errorCallback,
            raw => {
                const body = JSON.parse(raw) as AECPU[] | ApiResponse<AECPU[]>;
                // 服务端当前推送裸数组，同时兼容 ApiResponse 包装形式
                return Array.isArray(body) ? body : unwrapApiResponse(body);
            }
        );
    }

    // region Recipes

    /**
     * 查询工作台合成配方（有序/无序）。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/recipe/CraftingRecipesHandler.java)
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/recipe/IndexedCraftingRecipesHandler.java)
     * @returns 使用 {@link CraftingRecipesResultSchema} 验证
     */
    getCraftingRecipes(params?: { type?: "output" | "input"; id?: number; damage?: number; tag?: string; limit?: number; offset?: number }): Promise<CraftingRecipesResult> {
        return this.request<CraftingRecipesResult>(`/recipes/crafting${buildQuery(params ?? {})}`);
    }

    /**
     * 查询熔炉熔炼配方。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/recipe/FurnaceRecipesHandler.java)
     * @returns 使用 {@link FurnaceRecipesResultSchema} 验证
     */
    getFurnaceRecipes(params?: { type?: "output" | "input"; id?: number; damage?: number; tag?: string; limit?: number; offset?: number }): Promise<FurnaceRecipesResult> {
        return this.request<FurnaceRecipesResult>(`/recipes/furnace${buildQuery(params ?? {})}`);
    }

    /**
     * 查询 GT5 配方表的映射表（unlocalizedName → 显示名）。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/recipe/GTmaps.java)
     * @returns 使用 `Joi.array().items(`{@link GTRecipeMapSchema}`)` 验证
     */
    getGTRecipeMaps(): Promise<GTRecipeMap[]> {
        return this.request<GTRecipeMap[]>("/recipes/gt/maps");
    }

    /**
     * 查询 GT5 机器配方。
     * 注意：必须至少提供 id/damage（物品）、fluid（流体）或 map（单个配方表）之一。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/recipe/GTRecipesHandler.java)
     * @returns 使用 {@link GTRecipesResultSchema} 验证
     */
    getGTRecipes(params: { type?: "output" | "input"; id?: number; damage?: number; tag?: string; fluid?: string | number; map?: string; limit?: number; offset?: number }): Promise<GTRecipesResult> {
        return this.request<GTRecipesResult>(`/recipes/gt${buildQuery(params)}`);
    }

    // region 3D Print

    /**
     * 上传图片 → OC 3D 打印件，投递到玩家背包（多余掉落在玩家附近）。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ThreeD/PlayerPrintHandler.java)
     * @param params 二选一：id（实体 ID）或 name（玩家名）
     * @param image 图片二进制数据（png/jpg 等）
     * @returns 使用 {@link PlayerPrintResultSchema} 验证
     */
    printToPlayer(
        params: { id: number; label?: string; tooltip?: string } | { name: string; label?: string; tooltip?: string },
        image: RequestInit["body"]
    ): Promise<PlayerPrintResult> {
        return this.request<PlayerPrintResult>(`/3d/player${buildQuery(params)}`, {
            method: "PUT",
            body: image,
        });
    }

    /**
     * 上传图片 → 在世界中铺设 OC 3D 打印像素画（慢队列逐块执行）。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ThreeD/WorldPrintHandler.java)
     * @returns 使用 {@link WorldPrintSubmitResultSchema} 验证
     */
    submitWorldPrint(
        params: { x: number; y: number; z: number; dim?: number; facing?: "north" | "south" | "east" | "west"; label?: string; tooltip?: string },
        image: RequestInit["body"]
    ): Promise<WorldPrintSubmitResult> {
        return this.request<WorldPrintSubmitResult>(`/3d/world${buildQuery(params)}`, {
            method: "PUT",
            body: image,
        });
    }

    /**
     * 查询世界 3D 打印任务进度。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ThreeD/WorldPrintHandler.java)
     * @returns 使用 {@link WorldPrintJobResultSchema} 验证
     */
    getWorldPrintJob(params: { id: string }): Promise<WorldPrintJobResult> {
        return this.request<WorldPrintJobResult>(`/3d/world${buildQuery(params)}`);
    }

    /**
     * 轮询等待世界 3D 打印任务完成。
     * @param jobId 任务 ID（由 submitWorldPrint 返回）
     * @param intervalMs 轮询间隔 (ms)，默认 100
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ThreeD/WorldPrintHandler.java)
     * @returns 使用 {@link WorldPrintJobResultSchema} 验证
     */
    async waitForWorldPrintJob(jobId: string, intervalMs = 100): Promise<WorldPrintJobResult> {
        while (true) {
            const job = await this.getWorldPrintJob({ id: jobId });
            if (job.status === "completed") return job;
            await new Promise(r => setTimeout(r, intervalMs));
        }
    }

    /**
     * 上传图片，估算生成的 3D 打印件 NBT 大小（不实际打印）。
     * @java [java](../../../src/main/java/love/shirokasoke/webapi/webserver/handlers/ThreeD/PrintSizeHandler.java)
     * @returns 使用 {@link PrintSizeResultSchema} 验证
     */
    getPrintSize(image: RequestInit["body"], params?: { label?: string; tooltip?: string }): Promise<PrintSizeResult> {
        return this.request<PrintSizeResult>(`/3d/size${buildQuery(params ?? {})}`, {
            method: "PUT",
            body: image,
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
