/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import Joi from "joi";
import { WebApiClient } from "../src/client";
import * as v from "../src/validators";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" })



describe("normal", () => {
    it("entities", async () => {
        const r = await api.getEntities()
        const entitiesV = Joi.object().pattern(Joi.string(), v.EntitiesByDimensionSchema)
        assert.ok(entitiesV.validate(r).error == undefined)
        const rans = new Array(10).fill(1).map(i => r["0"].loadedEntityList[Math.floor(Math.random() * r["0"].loadedEntityList.length)])

        rans.map(i => it(`entity-${i.Entity.entityId}`, async () => {
            const r = await api.getEntity({ id: i.Entity.entityId })
            assert.ok(v.EntitySchema.validate(r).error == undefined)
            assert.notDeepStrictEqual(i, r)
        }))
    })
})