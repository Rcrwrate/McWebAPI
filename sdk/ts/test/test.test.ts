/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import { WebApiClient } from "../src/client";
import * as v from "../src/validators";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" })

const x = Math.floor(Math.random() * 40000) + 10000;
const z = Math.floor(Math.random() * 40000) + 10000;
const y = Math.floor(Math.random() * 50) + 50;

describe("normal", () => {
    it("subitems", async () => {
        const r = await api.loadChunk({ x, z, dim: 0, duration: 120 })

        console.log(v.ChunkLoadResultSchema.validate(r))
        console.log(`http://localhost:40002/block?x=${x}&y=${y}&z=${z}&dim=0`)

    })
})