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
    it("block", async () => {
        const r = await api.getBlock({ x: -41, y: 100, z: 50 })
        assert.ok(v.BlockDetailSchema.validate(r).error == undefined)
    })
})