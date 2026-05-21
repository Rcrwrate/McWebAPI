/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import { WebApiClient } from "../src/client";
import * as v from "../src/validators";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" })

describe("normal", () => {
    it("subitems", async () => {
        const r = await api.getItem({ id: 4144 })
        if (r.HasSubtypes) {
            assert.ok(r.subs)
            assert.ok(r.subs.length > 100)
        }
        console.log(v.ItemDetailSchema.validate(r))
        assert.ok(v.ItemDetailSchema.validate(r).error == undefined)
    })
})