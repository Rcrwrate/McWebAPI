import { WebApiClient } from "./client";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" });

(async () => {
    const r = await api.loadChunk({ x: 10000, z: 66666, dim: -1 })
    console.log(r)
})();
