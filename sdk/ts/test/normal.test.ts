/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import Joi from "joi";
import { WebApiClient } from "../src/client";
import * as v from "../src/validators";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" })


describe("normal", () => {
    it("root", async () => {
        const r = await api.getRoot()
        assert.ok(v.RootInfoSchema.validate(r).error == undefined);
    })
    it("tps", async () => {
        const r = await api.getTPS()
        assert.ok(Joi.object().pattern(Joi.string(), v.TPSInfoSchema).validate(r).error == undefined)
    })
    it("blocks", async () => {
        const r = await api.getBlocks()
        assert.ok(r.length > 100)
        assert.ok(Joi.array().items(v.BlockSchema).validate(r).error == undefined)
    })
    it("items", async () => {
        const r = await api.getItems()
        assert.ok(r.length > 100)
        r.map(i => v.ItemSchema.validate(i).error == undefined)
    })
    it("subitems", async () => {
        const r = await api.getItem({ id: 4144 })
        if (r.HasSubtypes) {
            assert.ok(r.subs)
            assert.ok(r.subs.length > 100)
        }
        assert.ok(v.ItemDetailSchema.validate(r).error == undefined)
    })
    it("AEitems", async () => {
        const r = await api.getAEItems()
        assert.ok(v.AEItemDefinitionsSchema.validate(r).error == undefined)
        assert.ok(r.blocks.length > 60)
        assert.ok(r.items.length > 60)
        assert.ok(r.materials.length > 60)
        assert.ok(r.parts.length > 100)
    })
    it("fluids", async () => {
        const r = await api.getFluids()
        assert.ok(Joi.array().items(v.FluidSchema).validate(r).error == undefined)
    })
    it("fluidContainers", async () => {
        const r = await api.getFluidContainers()
        assert.ok(Joi.array().items(v.FluidContainerSchema).validate(r).error == undefined)
    })
})

describe("chunks", () => {
    it("getChunks", async () => {
        const r = await api.getChunks()
        const chunkV = Joi.object().pattern(Joi.string(), v.ChunksByDimensionSchema)
        assert.ok(chunkV.validate(r).error == undefined)
        assert.ok(r["0"].chunks.length > 100)

        const tmp = r["0"].chunks.filter(i => i.hasEntities).slice(0, 10)
        tmp.map(i => it(`getChunk-${i.chunkX}-${i.chunkZ}`, async () => {
            const r = await api.getChunk(i)
            assert.ok(v.ChunkWithDimensionSchema.validate(r).error == undefined)
        }))

        it("loadExistChunk", async () => {
            const first = await api.loadChunk(tmp[0])
            assert.ok(v.ChunkLoadResultSchema.validate(first).error == undefined)
            assert.ok(first.chunkX = tmp[0].chunkX)
            assert.rejects(async () => api.loadChunk(tmp[0]), { name: "WebApiError", message: `Chunk already being force loaded: ${first.dimension}:${first.chunkX}:${first.chunkZ}` })

            it("verifyLoaded", async () => {
                const r = await api.getChunks()
                const loaded = r["0"].chunks.find(c => c.chunkX === tmp[0].chunkX && c.chunkZ === tmp[0].chunkZ)
                assert.ok(loaded, "chunk should exist in chunks list")
                assert.ok(loaded.isChunkLoaded, "chunk should be loaded")
            })

            it("unload", async () => {
                const unload = await api.unloadChunk(tmp[0]);
                assert.ok(v.ChunkLoadResultSchema.validate(unload).error == undefined)

                it("countLoads", async () => {
                    const r = await api.getChunkForceList();
                    assert.ok(v.ChunkForceListSchema.validate(r).error == undefined)
                    assert.ok(r.chunks.length == 0)
                })
            })
        })
    })

    it("entities", async () => {
        const r = await api.getEntities()
        const entitiesV = Joi.object().pattern(Joi.string(), v.EntitiesByDimensionSchema)
        assert.ok(entitiesV.validate(r).error == undefined)
        assert.ok(r["0"].loadedEntityList.length > 10)
        const rans = new Array(10).fill(1).map(i => r["0"].loadedEntityList[Math.floor(Math.random() * r["0"].loadedEntityList.length)])

        rans.map(i => it(`entity-${i.Entity.entityId}`, async () => {
            const r = await api.getEntity({ id: i.Entity.entityId })
            assert.ok(v.EntitySchema.validate(r).error == undefined)
            assert.notDeepStrictEqual(i, r)
        }))
    })
})