/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";
import { WebApiClient } from "../src/client";
import * as v from "../src/validators";


const api = new WebApiClient({ baseUrl: "http://localhost:40002" });

describe(`LSC`, async () => {
    await it("direct", async () => {
        const r = await api.getGT5Machine({ x: -74, y: 116, z: 56 })
        assert.ok(v.GT5MachineInfoSchema.validate(r).error == undefined)
        const p = v.LSCInfoSchema.validate(r.state.rawInfo)
        assert.ok(p.error == undefined)
    })
})