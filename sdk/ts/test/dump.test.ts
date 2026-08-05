/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import Joi from "joi";
import { WebApiClient } from "../src/client";
import * as v from "../src/validators";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" })

describe("dump", () => {
    it("chunks", async () => {
        const r = await api.getChunks()
        const test = r["0"].chunks.pop()
        assert.ok(test != undefined)
        const first = await api.getChunkMap(test)
        assert.ok(first instanceof ArrayBuffer)
        assert.ok(first.byteLength > 0)
        const second = await api.getChunkMap(test, true)
        const check = Joi.array().items(
            Joi.array().items(v.ChunkMapCellSchema)
        )
        assert.ok(check.validate(second).error == undefined)
    })

    it("MapError", async () => {
        const x = Math.floor(Math.random() * 40000) + 10000;
        const z = Math.floor(Math.random() * 40000) + 10000;
        const y = Math.floor(Math.random() * 50) + 50;
        assert.rejects(() => api.getChunkMap({ x, z }), { message: `Chunk not loaded at ${x >> 4},${z >> 4},${0}` })
    })

    it("itemIcon", async () => {
        const items = await api.getItems()
        const no = items.filter(i => i.HasSubtypes == false)
        const nor = no[Math.floor(Math.random() * no.length)]
        assert.ok(v.ItemSchema.validate(nor).error == undefined)

        const icon = await api.getItemIcon({ id: nor.id })
        assert.ok(icon instanceof ArrayBuffer)
        assert.ok(icon.byteLength > 0)

        const yes = items.filter(i => i.HasSubtypes == true)
        const yesr = yes[Math.floor(Math.random() * yes.length)]
        assert.ok(v.ItemSchema.validate(yesr).error == undefined)
        const yess = await api.getItem({ id: yesr.id })

        assert.ok(yess.HasSubtypes)
        assert.ok(yess.subs)

        const sub = yess.subs[Math.floor(Math.random() * yess.subs.length)]
        assert.ok(v.ItemStackSchema.validate(sub).error == undefined)
        const icon2 = await api.getItemIcon({ id: sub.id, damage: sub.damage, tag: sub.nbtWrite })
        assert.ok(icon2 instanceof ArrayBuffer)
        assert.ok(icon2.byteLength > 0)
    })

    it("fluidIcon", async () => {
        const one = await api.getFluidIcon({ id: 1 })
        assert.ok(one instanceof ArrayBuffer)
        const water = await api.getFluidIcon({ name: "water" })
        assert.ok(water instanceof ArrayBuffer)
    })
})