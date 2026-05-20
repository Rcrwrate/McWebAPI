/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import { WebApiClient } from "../src/client";
import { BlockDetail } from "../src/types/block";
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
    await it("get", async () => {
        before = await api.getBlock({ x, y, z, dim });
        assert.ok(v.BlockDetailSchema.validate(before).error == undefined)
    })
    await it("set", async () => {
        const blocks = await api.getBlocks();
        const targetBlock = blocks.find(b => b.id !== before.block.id);
        assert.ok(targetBlock)
        const setResult = await api.setBlock({ x, y, z, dim }, { id: targetBlock.id, metadataIn: 0 });
        assert.strictEqual(setResult, null);
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