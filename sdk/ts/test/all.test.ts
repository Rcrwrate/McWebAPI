/// <reference types="node" />
import { describe, it } from "node:test";
import assert from "node:assert";

import { WebApiClient, WebApiError } from "../src/client";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" })


describe("normal", () => {
    it("root", async () => {
        const r = await api.getRoot()
        assert.strictEqual(r.modid, "webapi");
    })
    it("tps", async () => {
        const r = await api.getTPS()
        Object.entries(r).forEach(([dim, info]) => {
            assert.ok(info.TPS > 10)
        });
    })

    it("blocks", async () => {
        const r = await api.getBlocks()
        assert.ok(r.length > 100)
    })

    it("items", async () => {
        const r = await api.getItems()
        assert.ok(r.length > 100)
    })

    it("subitems", async () => {
        const r = await api.getItem({ id: 4144 })
        assert.ok(r.subs)
        assert.ok(r.subs.length > 100)
    })

    it("AEitems", async () => {
        const r = await api.getAEItems()
        assert.ok(r.blocks.length > 60)
        assert.ok(r.items.length > 60)
        assert.ok(r.materials.length > 60)
        assert.ok(r.parts.length > 100)
    })
})

describe("chunks", () => {
    it("getChunks", async () => {
        const r = await api.getChunks()
        assert.ok(r["0"])
        assert.ok(r["0"].chunks.length > 100)

        const tmp = r["0"].chunks.filter(i => i.hasEntities).slice(0, 10)
        tmp.map(i => it(`getChunk-${i.chunkX}-${i.chunkZ}`, async () => {
            const r = await api.getChunk(i)
            assert.ok(r.hasEntities)
        }))

        it("loadExistChunk", async () => {
            const first = await api.loadChunk(tmp[0])

            assert.ok(first.chunkX = tmp[0].chunkX)
            assert.rejects(async () => {
                await api.loadChunk(tmp[0])
            }, { name: "WebApiError", message: `Chunk already being force loaded: 0:${first.chunkX}:${first.chunkZ}` })

            it("unload", async () => {
                await api.unloadChunk(tmp[0]);

                it("countLoads", async () => {
                    const r = await api.getChunkForceList();
                    assert.ok(r.chunks.length == 0)
                })
            })
        })
    })

    it("entities", async () => {
        const r = await api.getEntities()
        assert.ok(r["0"])
        assert.ok(r["0"].loadedEntityList.length > 100)
        const rans = new Array(10).fill(1).map(i => r["0"].loadedEntityList[Math.floor(Math.random() * r["0"].loadedEntityList.length)])

        rans.map(i => it(`entity-${i.Entity.entityId}`, async () => {
            const r = await api.getEntity({ id: i.Entity.entityId })
            assert.notDeepStrictEqual(i, r)
        }))
    })
})