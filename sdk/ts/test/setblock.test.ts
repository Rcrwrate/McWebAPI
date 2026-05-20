/// <reference types="node" />
import { describe, it } from "node:test";
import assert from "node:assert";

import { WebApiClient, WebApiError } from "../src/client";
import { BlockDetail } from "../src/types/block";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" })

const x = Math.floor(Math.random() * 40000) + 10000;
const z = Math.floor(Math.random() * 40000) + 10000;
const y = Math.floor(Math.random() * 50) + 50;

const sleep = (t: number) => new Promise((r) => setTimeout(r, t))
describe(`setBlocks${x}-${y}-${z}`, async () => {
    const dim = -1;
    await it("load", async () => {
        const loadResult = await api.loadChunk({ x, z, dim, duration: 120 })
        assert.ok(loadResult.durationSec == 120)
    })

    await sleep(2000)
    let before: BlockDetail
    await it("getBefore", async () => {
        before = await api.getBlock({ x, y, z, dim });
        const blocks = await api.getBlocks();
        const targetBlock = blocks.find(b => b.id !== before.block.id);
        assert.ok(targetBlock)
        const setResult = await api.setBlock({ x, y, z, dim }, { id: targetBlock.id, metadataIn: 0 });
        assert.strictEqual(setResult, null);
    })

    await it("setafter", async () => {
        const after = await api.getBlock({ x, y, z, dim });
        assert.ok(before);
        assert.notEqual(before, after);
    })


    await it("clean", async () => {
        const loadResult = await api.unloadChunk({ x, z, dim })
        assert.ok(loadResult.isActive)
    })
    // it("setAndRestoreBlock", async () => {

    //     const loadResult = await api.loadChunk({ x, z, dim, duration: 120 });
    //     assert.ok(loadResult.chunkX !== undefined);

    //     try {
    //         const before = await api.getBlock({ x, y, z, dim });
    //         const originalId = before.block.id;
    //         const originalMeta = before.metadata;

    //         const blocks = await api.getBlocks();
    //         const targetBlock = blocks.find(b => b.id !== originalId);
    //         assert.ok(targetBlock, "No alternative block found");

    //         const setResult = await api.setBlock({ x, y, z, dim }, { id: targetBlock.id, metadataIn: 0 });
    //         assert.strictEqual(setResult.success, true);

    //         const after = await api.getBlock({ x, y, z, dim });
    //         assert.notStrictEqual(after.block.id, originalId);

    //         const restoreResult = await api.setBlock({ x, y, z, dim }, { id: originalId, metadataIn: originalMeta });
    //         assert.strictEqual(restoreResult.success, true);

    //         const restored = await api.getBlock({ x, y, z, dim });
    //         assert.strictEqual(restored.block.id, originalId);
    //         assert.strictEqual(restored.metadata, originalMeta);
    //     } finally {
    //         await api.unloadChunk({ x, z, dim });
    //     }
    // });
})