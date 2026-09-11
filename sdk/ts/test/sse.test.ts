/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import Joi from "joi";
import { WebApiClient } from "../src/client";
import type { AECPU, AEItemsResult } from "../src/types";
import * as v from "../src/validators";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" });

/*
 * aeItemSseCallback 基于 @microsoft/fetch-event-source 实现，该库依赖浏览器全局对象
 * （document / window），这里在 Node 测试环境中最小化打桩。
 */
(globalThis as any).window ??= globalThis;
(globalThis as any).document ??= {
    hidden: false,
    addEventListener: () => { },
    removeEventListener: () => { },
};


describe("AE", async () => {
    // /?x=-25&y=116&z=63
    const x = -25
    const y = 116
    const z = 63
    const dimension = 0

    it("items sse", async () => {
        // /ae/item/sse 仅当服务端启用虚拟线程（useVirtualThreads）时注册
        let timer: NodeJS.Timeout | undefined;
        try {
            const r = await new Promise<AEItemsResult>((resolve, reject) => {
                const controller = api.aeItemSseCallback(
                    { x, y, z, dimension },
                    data => {
                        clearTimeout(timer);
                        controller.abort();
                        resolve(data);
                    },
                    err => {
                        clearTimeout(timer);
                        controller.abort();
                        reject(err);
                    }
                );
                timer = setTimeout(() => {
                    controller.abort();
                    reject(new Error("ae item sse timeout"));
                }, 1000);
            });
            assert.ok(v.AEItemsResultSchema.validate(r).error == undefined);
        } finally {
            clearTimeout(timer);
        }
    })

    it("cpus sse", async () => {
        // /ae/item/sse 仅当服务端启用虚拟线程（useVirtualThreads）时注册
        let timer: NodeJS.Timeout | undefined;
        try {
            const r = await new Promise<AECPU[]>((resolve, reject) => {
                const controller = api.aeCpuSseCallback(
                    { x, y, z, dimension },
                    data => {
                        clearTimeout(timer);
                        controller.abort();
                        resolve(data);
                    },
                    err => {
                        clearTimeout(timer);
                        controller.abort();
                        reject(err);
                    }
                );
                timer = setTimeout(() => {
                    controller.abort();
                    reject(new Error("ae item sse timeout"));
                }, 1000);
            });
            assert.ok(Joi.array().items(v.AECPUSchema).validate(r).error == undefined)
        } finally {
            clearTimeout(timer);
        }
    })

})