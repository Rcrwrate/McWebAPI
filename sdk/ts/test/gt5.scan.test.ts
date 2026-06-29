/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import Joi from "joi";
import { WebApiClient } from "../src/client";
import * as v from "../src/validators";
import type { GT5BatchMachineCoord, GT5ScanMachine } from "../src/types";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" });

/** 并发扫描的区块数，可通过环境变量 CONCURRENCY 覆盖 */
const CONCURRENCY = parseInt(process.env.CONCURRENCY ?? "8", 10);
/** 最多扫描的区块数量，0 表示不限制；可通过 MAX_CHUNKS 覆盖 */
const MAX_CHUNKS = parseInt(process.env.MAX_CHUNKS ?? "0", 10);
/** 仅扫描指定维度，-1 表示所有维度；可通过 DIM 覆盖 */
const DIM_FILTER = process.env.DIM !== undefined ? parseInt(process.env.DIM, 10) : -1;
/** 轮询任务状态的间隔 (ms) */
const POLL_INTERVAL = parseInt(process.env.POLL_INTERVAL ?? "200", 10);
/** 两次批量查询之间的刷新间隔 (ms) */
const REFRESH_DELAY_MS = parseInt(process.env.REFRESH_DELAY_MS ?? "1000", 10);

/** 限制并发执行的辅助函数 */
async function parallel<T>(tasks: (() => Promise<T>)[], concurrency: number): Promise<T[]> {
    const results: T[] = new Array(tasks.length);
    let next = 0;
    const workers = Array.from({ length: Math.min(concurrency, tasks.length) }, async () => {
        while (next < tasks.length) {
            const i = next++;
            results[i] = await tasks[i]();
        }
    });
    await Promise.all(workers);
    return results;
}

interface ChunkTarget {
    chunkX: number;
    chunkZ: number;
    dim: number;
}

describe(`GT5 chunk scan over all loaded chunks (CONCURRENCY=${CONCURRENCY})`, async () => {
    // ===== 阶段 1: 读取所有已加载区块列表 =====
    const chunksData = await api.getChunks();
    await it("getChunks", () => assert.ok(Joi.object().pattern(Joi.string(), v.ChunksByDimensionSchema).validate(chunksData).error == undefined))
    const targets: ChunkTarget[] = [];
    for (const [dimStr, dimData] of Object.entries(chunksData)) {
        const dim = parseInt(dimStr, 10);
        if (DIM_FILTER >= 0 && dim !== DIM_FILTER) continue;
        for (const c of dimData.chunks) {
            if (!c.isChunkLoaded) continue;
            targets.push({ chunkX: c.chunkX, chunkZ: c.chunkZ, dim });
        }
    }

    const limited = MAX_CHUNKS > 0 ? targets.slice(0, MAX_CHUNKS) : targets;
    console.log(`[scan] Loaded chunks to scan: ${limited.length}` +
        (DIM_FILTER >= 0 ? ` (dim filter=${DIM_FILTER})` : " (all dimensions)"));

    // ===== 阶段 2: 统一提交并等待所有区块扫描任务 (仅 Joi 校验) =====
    const scanJobs = await parallel(
        limited.map(t => async () => {
            const submit = await api.submitGT5ChunkScan({
                chunkX: t.chunkX, chunkZ: t.chunkZ, dim: t.dim,
            });
            assert.ok(v.GT5ScanSubmitResultSchema.validate(submit).error == undefined);
            return api.waitForGT5ScanJob(submit.id, POLL_INTERVAL);
        }),
        CONCURRENCY
    );

    await it("every scan job validates", () => {
        for (const job of scanJobs) {
            assert.ok(v.GT5ScanJobResultSchema.validate(job).error == undefined);
            assert.strictEqual(job.status, "completed");
            const result = job.result;
            if (result) {
                assert.ok(v.GT5ScanResultSchema.validate(result).error == undefined);
                for (const m of result.machines) {
                    assert.ok(v.GT5ScanMachineSchema.validate(m).error == undefined);
                }
            }
        }
    });

    // ===== 阶段 3: 汇总 MULTIBLOCK + SINGLE 坐标，统一提交 batch =====
    const coords: GT5BatchMachineCoord[] = [];
    for (const job of scanJobs) {
        for (const m of (job.result?.machines ?? []) as GT5ScanMachine[]) {
            if (["GENERATOR", "MULTIBLOCK", "SINGLE"].includes(m.machineType)) {
                coords.push({ x: m.x, y: m.y, z: m.z, dim: job.dimension });
            }
        }
    }
    console.log(`[runtime] MULTIBLOCK+SINGLE machines to batch query: ${coords.length}`);

    async function runBatchPass(label: string) {
        const submit = await api.submitGT5Batch(coords);
        assert.ok(v.GT5BatchSubmitResultSchema.validate(submit).error == undefined);
        assert.strictEqual(submit.total, coords.length, `${label}: submit total mismatch`);

        const job = await api.waitForGT5BatchJob(submit.id, POLL_INTERVAL);
        assert.ok(v.GT5BatchJobResultSchema.validate(job).error == undefined,
            `${label}: GT5BatchJobResult should match schema`);
        assert.strictEqual(job.status, "completed", `${label}: job should be completed`);

        for (const m of job.machines ?? []) {
            assert.ok(v.GT5MachineInfoSchema.validate(m).error == undefined,
                `${label}: GT5BatchMachine should match schema`);
        }
        console.log(`[runtime] ${label}: total=${job.total}, success=${job.success}, failed=${job.failed}`);
        return job;
    }

    // 第一次获取: 基线
    const firstJob = await runBatchPass("first pass");

    // 刷新间隔
    await new Promise(r => setTimeout(r, REFRESH_DELAY_MS));

    // 刷新一次: 再次统一提交同一批坐标
    const refreshJob = await runBatchPass(`refresh pass (after ${REFRESH_DELAY_MS}ms)`);

    it("both batch passes validate against schemas", () => {
        assert.strictEqual(firstJob.total, coords.length);
        assert.strictEqual(refreshJob.total, coords.length);
        assert.strictEqual(firstJob.status, "completed");
        assert.strictEqual(refreshJob.status, "completed");
    });
});
