/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import { WebApiClient } from "../src/client";
import * as v from "../src/validators";
import Joi from "joi";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" });

(async () => {
    const all = await api.getItems()
    for (const i of all) {
        describe(`${i.id}`, () => {
            if (i.HasSubtypes) {
                it("has subtypes", async () => {
                    const r = await api.getItem({ id: i.id })
                    if (r.subs != undefined) {
                        for (const j of r.subs!) {
                            await it(`${i.id}-${j.id}`, async () => {
                                const icon = await api.getItemIcon({ id: j.id, damage: j.damage, tag: j.nbtWrite })
                                assert.ok(icon instanceof ArrayBuffer)
                                assert.ok(icon.byteLength > 0)
                            })
                        }
                    }
                })
            }
        })
    }
})();