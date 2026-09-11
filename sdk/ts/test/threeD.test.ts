/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";
import { deflateSync } from "node:zlib";

import { WebApiClient } from "../src/client";
import * as v from "../src/validators";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" })

/** 生成一张纯色 PNG（宽高均为 32px，可拆成 2x2 个打印块） */
function makePng(size = 32): Buffer {
    const raw = Buffer.alloc(size * (1 + size * 3));
    let o = 0;
    for (let y = 0; y < size; y++) {
        raw[o++] = 0; // filter: none
        for (let x = 0; x < size; x++) {
            raw[o++] = (x * 8) % 256;
            raw[o++] = (y * 8) % 256;
            raw[o++] = 128;
        }
    }
    const chunk = (type: string, data: Buffer) => {
        const len = Buffer.alloc(4);
        len.writeUInt32BE(data.length);
        const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
        const crc = Buffer.alloc(4);
        crc.writeUInt32BE(crc32(body) >>> 0);
        return Buffer.concat([len, body, crc]);
    };
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(size, 0);
    ihdr.writeUInt32BE(size, 4);
    ihdr[8] = 8; // bit depth
    ihdr[9] = 2; // color type: truecolor
    return Buffer.concat([
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
        chunk("IHDR", ihdr),
        chunk("IDAT", deflateSync(raw)),
        chunk("IEND", Buffer.alloc(0)),
    ]);
}

function crc32(buf: Buffer): number {
    let c = ~0;
    for (const b of buf) {
        c ^= b;
        for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
    }
    return ~c;
}

describe("3d print", () => {
    it("print size", async () => {
        const r = await api.getPrintSize(makePng())
        assert.ok(v.PrintSizeResultSchema.validate(r).error == undefined)
        assert.ok(r.size > 0)
    })

    it("world print", async () => {
        const img = makePng()
        const sub = await api.submitWorldPrint({ x: 0, y: 250, z: 0, dim: 0, facing: "south" }, img)
        assert.ok(v.WorldPrintSubmitResultSchema.validate(sub).error == undefined)
        const job = await api.waitForWorldPrintJob(sub.id)
        assert.ok(v.WorldPrintJobResultSchema.validate(job).error == undefined)
        assert.equal(job.status, "completed")
    })
})
