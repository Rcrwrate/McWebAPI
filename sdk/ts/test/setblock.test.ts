/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import { WebApiClient } from "../src/client";
import type { Block, BlockDetail, BatchSetBlockTask } from "../src/types/block";
import * as v from "../src/validators";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" })

const x = Math.floor(Math.random() * 40000) + 10000;
const z = Math.floor(Math.random() * 40000) + 10000;
const y = Math.floor(Math.random() * 50) + 50;

const sleep = (t: number) => new Promise((r) => setTimeout(r, t))
describe(`setBlocks?x=${x}&y=${y}&z=${z}&dim=-1`, async () => {
    const dim = -1;
    await it("load", async () => {
        const loadResult = await api.loadChunk({ x, z, dim, duration: 120 })
        assert.ok(loadResult.durationSec == 120)
        assert.ok(v.ChunkLoadResultSchema.validate(loadResult).error == undefined)
        await sleep(5000)
    })

    let before: BlockDetail
    let targetBlock: Block | undefined
    await it("get", async () => {
        before = await api.getBlock({ x, y, z, dim });
        assert.ok(v.BlockDetailSchema.validate(before).error == undefined)
        const blocks = await api.getBlocks();
        targetBlock = blocks.find(b => b.id !== before.block.id);
        assert.ok(targetBlock)
    })

    await it("set", async () => {
        assert.ok(targetBlock)
        const setResult = await api.setBlock({ x, y, z, dim }, { id: targetBlock.id, metadataIn: 0 });
        assert.ok(v.SetBlockResultSchema.validate(setResult).error == undefined)
        assert.strictEqual(setResult.changed, true);
        assert.strictEqual(setResult.nbtchanged, false);
    })
    await it("set again", async () => {
        assert.ok(targetBlock)
        assert.rejects(api.setBlock({ x, y, z, dim }, { id: targetBlock.id, metadataIn: 0 }))
    })

    await it("after", async () => {
        const after = await api.getBlock({ x, y, z, dim });
        assert.ok(before);
        assert.notEqual(before, after);
    })

    await it("clean", async () => {
        const loadResult = await api.unloadChunk({ x, z, dim })
        assert.ok(loadResult.isActive)
    })
})

describe(`batchSetBlock?x=${x}&y=${y}&z=${z}&dim=-1`, async () => {
    const dim = -1;
    await it("load", async () => {
        const loadResult = await api.loadChunk({ x, z, dim, duration: 120 })
        assert.ok(loadResult.durationSec == 120)
        assert.ok(v.ChunkLoadResultSchema.validate(loadResult).error == undefined)
        await sleep(5000)
    })

    let before: BlockDetail
    let targetBlock: Block | undefined
    await it("get", async () => {
        before = await api.getBlock({ x, y, z, dim });
        assert.ok(v.BlockDetailSchema.validate(before).error == undefined)
        const blocks = await api.getBlocks();
        targetBlock = blocks.find(b => b.id !== before.block.id);
        assert.ok(targetBlock)
    })

    let jobId: string;
    await it("batch submit", async () => {
        assert.ok(targetBlock)
        const tasks: BatchSetBlockTask[] = [
            { x, y, z, dim, id: targetBlock.id, metadata: 0, flag: 2 },
        ];
        const submitResult = await api.batchSetBlock(tasks);
        assert.ok(v.BatchSetBlockSubmitResultSchema.validate(submitResult).error == undefined)
        assert.ok(submitResult.id)
        assert.strictEqual(submitResult.total, 1);
        jobId = submitResult.id;
    })

    await it("batch query", async () => {
        const jobResult = await api.waitForBatchSetBlockJob(jobId);
        assert.ok(v.BatchSetBlockJobResultSchema.validate(jobResult).error == undefined)
        assert.strictEqual(jobResult.id, jobId)
        assert.strictEqual(jobResult.total, 1)
        assert.strictEqual(jobResult.status, "completed")
        assert.strictEqual(jobResult.completed, 1)
        assert.strictEqual(jobResult.success + jobResult.failed, 1)
        assert.strictEqual(jobResult.success, 1)
        assert.strictEqual(jobResult.failed, 0)
        assert.strictEqual(jobResult.changed, 1)
        assert.strictEqual(jobResult.nbtchanged, 0)
    })

    await it("after", async () => {
        const after = await api.getBlock({ x, y, z, dim });
        assert.notEqual(before.block.id, after.block.id);
    })

    await it("clean", async () => {
        const loadResult = await api.unloadChunk({ x, z, dim })
        assert.ok(loadResult.isActive)
    })
})

describe(`batchSetBlock with nbt?x=${x}&y=${y}&z=${z}&dim=-1`, async () => {
    const dim = -1;
    /** 原版箱子，用于验证 TileEntity 的 NBT 写入 */
    const id = 3711;
    const metadata = 16;
    const nbt = "AwABeP///8MIAAJpZAAjdGlsZS5wcm9qZWN0cmVkLmlsbHVtaW5hdGlvbi5sYW1wfDADAAF6AAAAZgMAAXkAAAB0AQADaW52AQEAA3BvdwAEAAVzY2hlZP//////////AA==";

    await it("load", async () => {
        const loadResult = await api.loadChunk({ x, z, dim, duration: 120 })
        assert.ok(loadResult.durationSec == 120)
        assert.ok(v.ChunkLoadResultSchema.validate(loadResult).error == undefined)
        await sleep(5000)
    })

    const runBatch = async (task: BatchSetBlockTask) => {
        const submitResult = await api.batchSetBlock([task]);
        assert.ok(v.BatchSetBlockSubmitResultSchema.validate(submitResult).error == undefined)
        assert.strictEqual(submitResult.total, 1);
        return api.waitForBatchSetBlockJob(submitResult.id);
    }

    await it("set with nbt", async () => {
        const jobResult = await runBatch({ x, y, z, dim, id, metadata, flag: 2, nbt });
        assert.ok(v.BatchSetBlockJobResultSchema.validate(jobResult).error == undefined)
        // 方块被替换 + NBT 写入成功，两个位都命中
        assert.strictEqual(jobResult.changed, 1)
        assert.strictEqual(jobResult.nbtchanged, 1)
        const after = await api.getBlock({ x, y, z, dim });
        assert.ok(after.tileEntity)
        assert.notStrictEqual(after.metadata, 0)
    })

    await it("nbt only", async () => {
        // 方块最终状态未变化，但 NBT 仍写入 → 只命中 nbtchanged
        const jobResult = await runBatch({ x, y, z, dim, id, metadata, flag: 2, nbt });
        assert.strictEqual(jobResult.changed, 0)
        assert.strictEqual(jobResult.nbtchanged, 1)
        assert.strictEqual(jobResult.success, 1)
        assert.strictEqual(jobResult.failed, 0)
    })

    await it("clean", async () => {
        const loadResult = await api.unloadChunk({ x, z, dim })
        assert.ok(loadResult.isActive)
    })
})