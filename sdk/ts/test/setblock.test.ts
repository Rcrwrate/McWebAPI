/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import { WebApiClient } from "../src/client";
import type { Block, BlockDetail, BatchSetBlockTask, BatchSetBlockSubmitResult, BatchSetBlockJobResult } from "../src/types/block";
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
        assert.strictEqual(setResult, null);
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
        await sleep(2000)
        const jobResult = await api.getBatchSetBlockJob({ id: jobId });
        assert.ok(v.BatchSetBlockJobResultSchema.validate(jobResult).error == undefined)
        assert.strictEqual(jobResult.id, jobId)
        assert.strictEqual(jobResult.total, 1)
        assert.strictEqual(jobResult.status, "completed")
        assert.strictEqual(jobResult.completed, 1)
        assert.strictEqual(jobResult.success + jobResult.failed, 1)
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