/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import Joi from "joi";
import { WebApiClient } from "../src/client";
import * as v from "../src/validators";
import type { GT5BatchMachineCoord, GT5ScanMachine } from "../src/types";


const api = new WebApiClient({ baseUrl: "http://localhost:40002" });

describe(`batch check`, async () => {
    const r = await api.getGT5BatchJob({ id: "1" })
    console.log(v.GT5BatchJobResultSchema.validate(r))
    assert.ok(v.GT5BatchJobResultSchema.validate(r).error == undefined)
})