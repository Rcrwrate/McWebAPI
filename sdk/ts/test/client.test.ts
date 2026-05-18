import { describe, it } from "node:test";
import assert from "node:assert";
import { WebApiClient, WebApiError } from "../src/client";
import type { FetchLike } from "../src/client";

function mockFetch(response: {
    ok: boolean;
    status: number;
    statusText: string;
    json: unknown;
    contentType?: string;
}): FetchLike {
    return async () =>
        ({
            ok: response.ok,
            status: response.status,
            statusText: response.statusText,
            headers: {
                get(name: string) {
                    if (name.toLowerCase() === "content-type") {
                        return response.contentType || "application/json";
                    }
                    return null;
                },
            },
            json: async () => response.json,
            arrayBuffer: async () => new ArrayBuffer(0),
        }) as Awaited<ReturnType<FetchLike>>;
}

describe("WebApiClient", () => {
    it("getRoot returns root info", async () => {
        const client = new WebApiClient({
            baseUrl: "http://localhost:40002",
            fetch: mockFetch({
                ok: true,
                status: 200,
                statusText: "OK",
                json: { modid: "webapi", version: "1.0.0" },
            }),
        });
        const root = await client.getRoot();
        assert.strictEqual(root.modid, "webapi");
        assert.strictEqual(root.version, "1.0.0");
    });

    it("unwraps {success,data} wrapper", async () => {
        const client = new WebApiClient({
            baseUrl: "http://localhost:40002",
            fetch: mockFetch({
                ok: true,
                status: 200,
                statusText: "OK",
                json: { success: true, data: { id: 1, localizedName: "Stone" } },
            }),
        });
        const block = await client.getBlock({ posX: 0, posY: 64, posZ: 0, dimension: 0 });
        assert.strictEqual(block.localizedName, "Stone");
    });

    it("throws WebApiError on API failure", async () => {
        const client = new WebApiClient({
            baseUrl: "http://localhost:40002",
            fetch: mockFetch({
                ok: false,
                status: 404,
                statusText: "Not Found",
                json: { success: false, message: "Chunk not found" },
            }),
        });
        await assert.rejects(
            async () => client.getChunk({ chunkX: 0, chunkZ: 0 }),
            (err: unknown) => err instanceof WebApiError && err.status === 404
        );
    });

    it("builds query params correctly", async () => {
        let capturedUrl = "";
        const client = new WebApiClient({
            baseUrl: "http://localhost:40002",
            fetch: async (url) => {
                capturedUrl = url;
                return {
                    ok: true,
                    status: 200,
                    statusText: "OK",
                    headers: { get: () => "application/json" },
                    json: async () => ({ success: true, data: {} }),
                    arrayBuffer: async () => new ArrayBuffer(0),
                } as Awaited<ReturnType<FetchLike>>;
            },
        });
        await client.getBlock({ posX: 10, posY: 64, posZ: -5, dimension: 0 });
        assert.ok(capturedUrl.includes("posX=10"));
        assert.ok(capturedUrl.includes("posY=64"));
        assert.ok(capturedUrl.includes("posZ=-5"));
        assert.ok(capturedUrl.includes("dimension=0"));
    });
});
