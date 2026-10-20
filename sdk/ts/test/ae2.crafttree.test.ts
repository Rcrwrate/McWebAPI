/// <reference types="node" />
import assert from "node:assert";
import { describe, it } from "node:test";

import {
    AECraftingJobTreeSchema,
    AECraftingRequestNodeSchema,
    AECraftingTaskErrorBodySchema,
    AECraftingTaskResultSchema,
    WebApiClient,
    WebApiError,
    analyzeAECraftingJobTree,
} from "../src";
import type { AECraftingJobTree, AECraftingRequestNode } from "../src";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" });
const craftCoords = { x: 163, y: 138, z: -184, dimension: 0 };

/** 热钒镐合金锭（gregtech:gt.metaitem.01@12357） */
const vanadiumIngot = { id: 7437, Damage: 12357, Count: 1 };

/** 从 WebApiError 中取出失败响应体并校验其结构，返回其中的合成树 */
function extractErrorTree(err: unknown): AECraftingJobTree {
    assert.ok(err instanceof WebApiError, `expected WebApiError, got ${String(err)}`);
    assert.equal(err.status, 500);
    assert.match(err.message, /^Failed to simulate job/);
    const envelope = err.body as { body?: AECraftingJobTree | null };
    const check = AECraftingTaskErrorBodySchema.validate(envelope);
    assert.ok(check.error == undefined, check.error?.message ?? "unexpected");
    const tree = envelope.body;
    assert.ok(tree != undefined && tree != null, "error body should carry the crafting tree");
    const treeCheck = AECraftingJobTreeSchema.validate(tree);
    assert.ok(treeCheck.error == undefined, treeCheck.error?.message ?? "unexpected");
    return tree;
}

describe("AE crafting tree (real server)", () => {
    it("crafts successfully and returns an analyzable job tree (detail=all)", async () => {
        const result = await api.aeCraft({ ...craftCoords, detail: "all" }, vanadiumIngot);
        const check = AECraftingTaskResultSchema.validate(result);
        assert.ok(check.error == undefined, check.error?.message ?? "unexpected");
        assert.ok(result.job != undefined && result.job != null, "detail=all should include the job tree");

        const treeCheck = AECraftingJobTreeSchema.validate(result.job);
        assert.ok(treeCheck.error == undefined, treeCheck.error?.message ?? "unexpected");
        assert.ok(result.job.requests.length > 0, "tree should have at least one root request");
        const rootCheck = AECraftingRequestNodeSchema.validate(result.job.requests[0]);
        assert.ok(rootCheck.error == undefined, rootCheck.error?.message ?? "unexpected");
        // 请求树至少展开一层子请求（样板合成的材料）
        assert.ok((result.job.requests[0].children?.length ?? 0) > 0);

        const analysis = analyzeAECraftingJobTree(result.job);
        assert.equal(analysis.craftable, true);
        assert.deepEqual(analysis.reasons, []);
        assert.deepEqual(analysis.missing, []);
        assert.equal(analysis.simulatedTasks.length, 0);
    });

    it("omits the job tree unless detail=all", async () => {
        const result = await api.aeCraft(craftCoords, vanadiumIngot);
        const check = AECraftingTaskResultSchema.validate(result);
        assert.ok(check.error == undefined, check.error?.message ?? "unexpected");
        assert.equal(result.job, undefined);
    });

    it("reports missing materials through the error body tree", async () => {
        // 网络材料远不足以合成 100 万个钒镐锭，服务端返回 simulation 失败并附带合成树
        await assert.rejects(
            api.aeCraft(craftCoords, { id: 7437, Damage: 12357, Count: 1000000 }),
            (err: unknown) => {
                const tree = extractErrorTree(err);
                const analysis = analyzeAECraftingJobTree(tree);
                console.log(analysis)
                assert.equal(analysis.craftable, false);
                assert.ok(analysis.reasons.includes("SIMULATED"));
                assert.ok(analysis.simulatedTasks.length > 0);
                assert.ok(analysis.missing.length > 0);
                const missingIngot = analysis.missing.find(stack => stack.id === 7437);
                assert.ok(missingIngot != undefined, "missing stacks should contain the requested item");
                assert.ok(missingIngot.stackSize > 0);
                // 缺失数量来自 simulated 解析器的 resolved，不应超过全树 simulated 解析器总量
                const sumSimulated = (nodes: AECraftingRequestNode[]): number =>
                    nodes.reduce(
                        (sum, node) =>
                            sum +
                            node.resolvers
                                .filter(resolver => resolver.simulated)
                                .reduce((s, resolver) => s + resolver.resolved, 0) +
                            sumSimulated(node.children ?? []),
                        0,
                    );
                const simulatedSum = sumSimulated(tree.requests);
                assert.ok(simulatedSum > 0);
                assert.ok(missingIngot.stackSize <= simulatedSum);
                return true;
            },
        );
    });

    it("crafts fluid and returns a fluid job tree", async () => {
        // 连续提交合成时，前一任务的原料可能处于在途状态导致瞬态 simulation 失败，
        // 因此两种结果（成功 / 材料不足失败）均校验流体合成树的结构与分析结果
        let tree: AECraftingJobTree;
        let craftable: boolean;
        try {
            const result = await api.aeCraft(
                { ...craftCoords, detail: "all" },
                { id: 1272, Count: 1000, Type: "fluid" },
            );
            const check = AECraftingTaskResultSchema.validate(result);
            assert.ok(check.error == undefined, check.error?.message ?? "unexpected");
            assert.ok(result.job != undefined && result.job != null);
            tree = result.job;
            craftable = true;
        } catch (err) {
            tree = extractErrorTree(err);
            craftable = false;
        }

        const treeCheck = AECraftingJobTreeSchema.validate(tree);
        assert.ok(treeCheck.error == undefined, treeCheck.error?.message ?? "unexpected");
        // 流体请求树的根堆应为流体（含 gaseous 判别字段）
        assert.ok("gaseous" in tree.requests[0].stack);

        const analysis = analyzeAECraftingJobTree(tree);
        console.log(analysis)
        assert.equal(analysis.craftable, craftable);
        if (craftable) {
            assert.deepEqual(analysis.missing, []);
        } else {
            assert.ok(analysis.reasons.includes("SIMULATED"));
        }
    });
});