/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import { WebApiClient } from "../src/client";
import * as v from "../src/validators";
import Joi from "joi";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" });


describe("AE", async () => {
    // /?x=-25&y=116&z=63
    const x = -25
    const y = 116
    const z = 63
    const dimension = 0

    it("MEs", async () => {
        const r = await api.aeMEs({ x, y, z, dimension })
        assert.ok(Joi.array().items(v.AEMEInterfaceSchema).validate(r).error == undefined)
    })

    it("MEs load", async () => {
        const r = await api.aeMEs({ x, y, z, dimension, load: true })
        assert.ok(Joi.array().items(v.AEMEInterfaceSchema).validate(r).error == undefined)
    })

    it("MEs load world", async () => {
        const r = await api.aeMEs({ x, y, z, dimension, load: true, world: true })
        assert.ok(Joi.array().items(v.AEMEInterfaceSchema).validate(r).error == undefined)
    })

    it("nodes", async () => {
        const r = await api.aeNodes({ x, y, z, dimension })
        assert.ok(Joi.array().items(v.AENodeSchema).validate(r).error == undefined)
    })

    it("cpus", async () => {
        const r = await api.aeCPUs({ x, y, z, dimension })
        assert.ok(Joi.array().items(v.AECPUSchema).validate(r).error == undefined)
    })

    it("items", async () => {
        const r = await api.aeItems({ x, y, z, dimension })
        assert.ok(v.AEItemsResultSchema.validate(r).error == undefined)
    })


    it("me", async () => {
        const r = await api.aeME({ x: -72, y: 118, z: 69 })
        const check = Joi.array().items(
            v.AE2PatternSchema.append({
                slot: Joi.number().required(),
                direction: Joi.string().optional()
            })
        )
        assert.ok(check.validate(r).error == undefined)
    })

    it("send", async () => {
        const r = await api.aeCraft({
            "x": 163,
            "y": 138,
            "z": -184,
            "dimension": 0
        }, {
            id: 7437,
            Damage: 12357,
            Count: 1
        })
        console.log(v.AECraftingTaskResultSchema.validate(r))
        assert.ok(v.AECraftingTaskResultSchema.validate(r).error == undefined)
    })
})