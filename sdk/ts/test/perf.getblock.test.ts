/// <reference types="node" />
import { describe, it } from "node:test";

import { WebApiClient } from "../src/client";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" });

/** 并发大小，可通过环境变量 CONCURRENCY 覆盖 */
const CONCURRENCY = parseInt(process.env.CONCURRENCY ?? "10", 10);
/** Y 轴范围 */
const Y_MIN = 0;
const Y_MAX = 50;

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

describe(`getBlock performance (concurrency=${CONCURRENCY}, y=${Y_MIN}-${Y_MAX})`, async () => {
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

    await it(`getBlock full chunk y=${Y_MIN}..${Y_MAX} (${total} blocks, concurrency=${CONCURRENCY})`, async () => {
        const tasks = coords.map(c => () => api.getBlock({ x: c.x, y: c.y, z: c.z, dim: 0 }));

        const start = performance.now();
        const results = await parallel(tasks, CONCURRENCY);
        const elapsed = performance.now() - start;

        const ok = results.filter(r => r !== null).length;
        const avg = elapsed / total;
        const qps = (total / elapsed) * 1000;

        console.log(`[perf] ${total} requests, ${ok} ok, ${elapsed.toFixed(1)}ms total, ${avg.toFixed(1)}ms avg, ${qps.toFixed(1)} req/s (concurrency=${CONCURRENCY})`);
    });
});
