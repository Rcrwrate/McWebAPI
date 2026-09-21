/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import { WebApiClient } from "../src/client";
import * as v from "../src/validators";
import Joi from "joi";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" })

const x = -25
const y = 116
const z = 63
const dimension = 0

const main = async () => {
    const r = await api.aeCPUs({ x, y, z, dimension })
    assert.ok(Joi.array().items(v.AECPUSchema).validate(r).error == undefined)
    return main()
}

describe("normal", () => {
    it("cpus", main)
})