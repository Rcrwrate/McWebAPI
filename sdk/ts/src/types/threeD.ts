/** 3D 打印任务状态 */
export type PrintJobStatus = "pending" | "running" | "completed";

/** /3d/player 响应：投递到玩家背包的结果 */
export interface PlayerPrintResult {
    /** 生成的打印件总数 */
    total: number;
    /** 成功放入背包的数量 */
    added: number;
    /** 背包放不下、掉落在玩家附近的数量 */
    dropped: number;
}

/** /3d/world PUT 提交响应 */
export interface WorldPrintSubmitResult {
    id: string;
    total: number;
    /** 全透明被跳过的小块数量 */
    skipped: number;
}

/** /3d/world 任务失败详情 */
export interface WorldPrintFailure {
    x: number;
    y: number;
    z: number;
    reason: string;
}

/** /3d/world GET 任务结果 */
export interface WorldPrintJobResult {
    id: string;
    total: number;
    completed: number;
    success: number;
    failed: number;
    status: PrintJobStatus;
    createTime: number;
    finishTime?: number;
    durationMs?: number;
    failures?: WorldPrintFailure[];
    failuresTruncated?: number;
}

/** /test/3d 响应：打印件 NBT 大小（字节） */
export interface PrintSizeResult {
    size: number;
}
