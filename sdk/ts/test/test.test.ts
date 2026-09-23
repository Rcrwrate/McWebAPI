/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import { WebApiClient } from "../src/client";
import * as v from "../src/validators";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" })

describe("temp", () => {
    it(`entity`, async () => {

        const r = await api.setBlock({ x: -58, y: 115, z: 104 }, {
            id: 3711,
            metadataIn: 16,
            nbt: "AwABeP///8MIAAJpZAAjdGlsZS5wcm9qZWN0cmVkLmlsbHVtaW5hdGlvbi5sYW1wfDADAAF6AAAAZgMAAXkAAAB0AQADaW52AQEAA3BvdwAEAAVzY2hlZP//////////AA=="
        })
        console.log(r)
    })
})