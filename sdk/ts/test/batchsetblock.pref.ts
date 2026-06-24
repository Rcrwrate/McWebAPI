/// <reference types="node" />
import { describe, it } from "node:test";

import { WebApiClient } from "../src/client";
import { Block, BatchSetBlockTask } from "../src/types/block";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" });

/** 并发大小，可通过环境变量 CONCURRENCY 覆盖 */
const CONCURRENCY = parseInt(process.env.CONCURRENCY ?? "10", 10);

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

/** Y 轴范围 */
const Y_MIN = 0;
const Y_MAX = 100;

describe(`batchSetBlock performance (y=${Y_MIN}-${Y_MAX})`, async () => {
    // 获取方块列表，随机选一个
    const blocks = await api.getBlocks();
    const target: Block = blocks[Math.floor(Math.random() * blocks.length)];
    console.log(`[perf] Target block: id=${target.id} registryName=${target.registryName} localizedName=${target.localizedName}`);

    // 获取已加载区块，随机选一个
    const chunksData = await api.getChunks();
    const dim = "0";
    const loadedChunks = chunksData[dim].chunks.filter(c => c.isChunkLoaded);
    if (loadedChunks.length === 0) {
        throw new Error("No loaded chunks found in dimension 0");
    }
    const picked = loadedChunks[Math.floor(Math.random() * loadedChunks.length)];
    const baseX = picked.xStart;
    const baseZ = picked.zStart;

    // 构建区块内所有坐标: 16x16 x (Y_MAX-Y_MIN+1) 个方块
    const coords: { x: number; y: number; z: number }[] = [];
    for (let ox = 0; ox < 16; ox++) {
        for (let oz = 0; oz < 16; oz++) {
            for (let y = Y_MIN; y <= Y_MAX; y++) {
                coords.push({ x: baseX + ox, y, z: baseZ + oz });
            }
        }
    }
    const total = coords.length; // 16 * 16 * 51 = 13056

    console.log(`[perf] Picked chunk (${picked.chunkX}, ${picked.chunkZ}), baseX=${baseX}, baseZ=${baseZ}, total=${total} blocks`);

    // 记录原始方块以便恢复
    const originals = new Map<string, { id: number; metadata: number }>();

    await it(`getBlock before batch (snapshot)`, async () => {
        const tasks = coords.map(c => () => api.getBlock({ x: c.x, y: c.y, z: c.z, dim: 0 }));
        const results = await parallel(tasks, CONCURRENCY);
        for (let i = 0; i < coords.length; i++) {
            const key = `${coords[i].x},${coords[i].y},${coords[i].z}`;
            originals.set(key, { id: results[i].block.id, metadata: results[i].metadata });
        }
        console.log(`[perf] Snapshot done: ${originals.size} blocks recorded`);
    });

    await it(`batchSetBlock full chunk to id=${target.id} (${total} blocks)`, async () => {
        const tasks: BatchSetBlockTask[] = coords.map(c => ({
            x: c.x, y: c.y, z: c.z, dim: 0,
            id: target.id, metadata: 0, flag: 2,
        }));

        const start = performance.now();
        const submit = await api.batchSetBlock(tasks);
        const submitTime = performance.now() - start;

        const job = await api.waitForBatchSetBlockJob(submit.id);
        const totalTime = performance.now() - start;

        console.log(
            `[perf] ${total} blocks: submit=${submitTime.toFixed(1)}ms, ` +
            `total=${totalTime.toFixed(1)}ms, ` +
            `success=${job.success}, failed=${job.failed}, ` +
            `duration=${job.durationMs}ms, ` +
            `throughput=${(total / (totalTime / 1000)).toFixed(1)} blocks/s`
        );

        if (job.failed > 0 && job.failures) {
            console.log(`[perf] First 5 failures:`, job.failures.slice(0, 5));
        }
    });

    // 恢复原始方块
    await it(`restore original blocks via batch (${total} blocks)`, async () => {
        const tasks: BatchSetBlockTask[] = coords.map(c => {
            const orig = originals.get(`${c.x},${c.y},${c.z}`)!;
            return { x: c.x, y: c.y, z: c.z, dim: 0, id: orig.id, metadata: orig.metadata, flag: 2 };
        });

        const start = performance.now();
        const submit = await api.batchSetBlock(tasks);
        const job = await api.waitForBatchSetBlockJob(submit.id);
        const elapsed = performance.now() - start;

        console.log(`[perf] Restore done in ${elapsed.toFixed(1)}ms, success=${job.success}, failed=${job.failed}`);
    });
});
