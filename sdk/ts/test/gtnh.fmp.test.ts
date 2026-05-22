/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import { WebApiClient } from "../src/client";
import * as v from "../src/validators";
import Joi from "joi";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" });

(async () => {
    // /?x=-23&y=118&z=63
    const x = -23
    const y = 118
    const z = 63
    const dimension = 0
    //@ts-ignore
    const r = await api.aeNodes({ x, y, z, dimension })
    console.log(r.filter(i => i.location?.x == x && i.location?.y == y))
})()