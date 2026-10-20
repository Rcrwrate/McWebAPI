# @shirokasoke/webapi-sdk

[MC WebAPI](https://github.com/Rcrwrate/McWebAPI) 的 TypeScript SDK

[![最新构建(java)](https://img.shields.io/github/actions/workflow/status/Rcrwrate/McWebAPI/build-and-test.yml?logo=github&label=Build%20and%20test)](https://github.com/Rcrwrate/McWebAPI/actions/workflows/build-and-test.yml)
[![最新发布](https://img.shields.io/github/v/release/Rcrwrate/MCWebAPI)](https://github.com/Rcrwrate/McWebAPI/releases/latest)

## 安装

```bash
npm install @shirokasoke/webapi-sdk
```

## 快速开始

```ts
import { WebApiClient, WebApiError } from "@shirokasoke/webapi-sdk";

const client = new WebApiClient({
    baseUrl: "http://localhost:40002",
    authToken: "xxx", // 可选，原值作为 Authorization 头发送
});

const tps = await client.getTPS();                        // Record<string, TPSInfo>
const block = await client.getBlock({ x: 64, y: 64, z: 64, dim: 0 });
const icon = await client.getItemIcon({ id: 4 });         // ArrayBuffer
```

- 所有方法返回 `ApiResponse<T>` 解包后的 `data`；HTTP 错误或业务失败（`success=false`）抛出 `WebApiError`（含 `message` / `status` / `body`）
- 图片、贴图等二进制接口返回 `ArrayBuffer`
- 依赖全局 `fetch`（Node 18+），或通过 `options.fetch` 注入实现

## API 一览

| 分组 | 方法 |
| --- | --- |
| 状态 | `getRoot` `getTPS` `getProfiler` `getLagAnalyzer` `getWorldInfo` `checkSSE` |
| 物品 | `getItems` `getItem` `getItemIcon` `getAEItemsDef` |
| 方块 | `getBlocks` `getBlock` `setBlock` `batchSetBlock` `getBatchSetBlockJob` `waitForBatchSetBlockJob` `getBlockFMP` `getBlockTile` |
| 区块 | `getChunks` `getChunk` `getChunkMap` `getChunkForceList` `loadChunk` `unloadChunk` |
| 实体 | `getEntities` `getEntity` |
| 流体 | `getFluids` `getFluidContainers` `getFluidIcon` |
| GT5 | `getGT5Machine` `setGT5MachineStatus` `submitGT5Batch` `getGT5BatchJob` `rerunGT5Batch` `waitForGT5BatchJob` `submitGT5ChunkScan` `getGT5ScanJob` `waitForGT5ScanJob` |
| AE2 | `aeHit` `aeNodes` `aeCPUs` `aeME` `aeMEs` `aeMESupport` `aeItems` `aeCraft` `aeCancel` `aeSuspend` |
| 配方 | `getCraftingRecipes` `getFurnaceRecipes` `getGTRecipes` `getGTRecipeMaps` |
| 3D 打印 | `printToPlayer` `submitWorldPrint` `getWorldPrintJob` `waitForWorldPrintJob` `getPrintSize` |

每个方法都有对应的类型（`src/types`）与 Joi 校验 Schema（`src/validators`）一并导出，详见源码 JSDoc

## SSE 订阅

```ts
const ctrl = client.aeCpuSseCallback(
    { x: 64, y: 64, z: 64 },
    cpus => console.log(cpus),
    err => console.error(err),
);
ctrl.abort(); // 停止订阅
```

基于 `@microsoft/fetch-event-source`，可携带 `Authorization` 头。相关路由仅在服务端启用虚拟线程（`useVirtualThreads`）时注册，使用前可 `await client.checkSSE()` 探测。

## AE 合成与合成树分析

`aeCraft` 的 `detail` 参数控制合成树输出：`"all"` 始终返回、`"fail"`（默认）仅在失败时随错误 `body` 返回、`"none"` 不返回。

```ts
import { analyzeAECraftingJobTree, type AECraftingTaskErrorBody } from "@shirokasoke/webapi-sdk";

try {
    const result = await client.aeCraft({ x: 64, y: 64, z: 64 }, { id: 1234, Count: 1 });
    if (result.job) {
        const analysis = analyzeAECraftingJobTree(result.job);
        console.log(analysis);
    }
} catch (e) {
    if (e instanceof WebApiError && e.body) {
        // 模拟失败（材料不足）时，错误 body 中携带失败时的合成树
        const tree = (e.body as AECraftingTaskErrorBody).body;
        console.log(analyzeAECraftingJobTree(tree));
    }
}
```

## 开发

```bash
npm run build # tsc 构建到 dist/
tsx --test test/singlefile # 运行测试每次一个，勿并行
# npm run web   # 构建并同步到 web/node_modules/@shirokasoke/webapi-sdk/
```

## License

MIT
