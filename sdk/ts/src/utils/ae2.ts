import type { AECraftingJobTree, AECraftingRequestNode, AECraftingTaskNode, AEStack } from "../types/ae2";
import type { ItemStack } from "../types/item";

/**
 * AE2 v2 计算器内部调度任务（`CraftingContext.CheckOtherResolversTask`）
 *
 * 该任务永远不会作为解析器输出（计划/CPU 侧均为 no-op），其 `simulated` 标志由所在请求的
 * 候选解析器列表 `allMatch(isSimulated)` 派生，**即使在完全成功的合成树中也可能为 true**，
 * 因此不能作为材料缺失的依据，分析时应排除。
 */
const INTERNAL_TASK_TYPES = ["CheckOtherResolversTask"];

/** 合成任务不可合成的原因 */
export type AECraftingJobAnalysisReason =
    /** 存在 simulated 请求/解析器：材料不足，网络中没有对应物品（等价于服务端 job.isSimulation()） */
    | "SIMULATED"
    /** 存在 FAILURE 任务：该合成路径彻底失败（如样板输出不匹配） */
    | "FAILED_TASK"
    /** 请求被截断：超出服务端 AEConfig.maxCraftingSteps 合成步骤上限 */
    | "INCOMPLETE"
    /** 存在未被满足的请求（remaining > 0） */
    | "UNRESOLVED";

/** {@link analyzeAECraftingJobTree} 的分析结果 */
export interface AECraftingJobAnalysis {
    /** 是否可正常合成：材料齐全、无失败任务、无截断、无未满足请求 */
    craftable: boolean;
    /** 不可合成的原因（craftable 为 false 时非空） */
    reasons: AECraftingJobAnalysisReason[];
    /** 缺失的物品/流体堆，已按物品（id + damage + NBT）合并，stackSize 为缺口总量 */
    missing: AEStack[];
    /**
     * 所有 simulated=true 且非内部调度的任务节点（已排除 {@link INTERNAL_TASK_TYPES}），
     * 缺失物品即由这些任务"无中生有"供应
     */
    simulatedTasks: AECraftingTaskNode[];
}

/** 流体与物品的判别字段：Fluid 具有 gaseous，ItemStack 具有 HasSubtypes */
function isFluidStack(stack: AEStack): stack is AEStack & { gaseous: boolean } {
    return "gaseous" in stack;
}

/** 堆的合并键：物品按 id + damage + NBT，流体按 id */
function stackKey(stack: AEStack): string {
    if (isFluidStack(stack)) {
        return `fluid:${stack.id}`;
    }
    const item = stack as ItemStack;
    return `item:${item.id}:${item.damage}:${item.nbt?.nbtstr ?? ""}`;
}

function mergeInto(target: Map<string, AEStack>, stack: AEStack): void {
    const key = stackKey(stack);
    const existing = target.get(key);
    if (existing) {
        existing.stackSize += stack.stackSize;
    } else {
        target.set(key, stack);
    }
}

/**
 * 分析合成树，判断任务能否正常合成，并汇总合成缺少的物品。
 *
 * 判定依据（对应服务端 AE2 v2 合成计算器）：
 * - 请求 `simulated`（wasSimulated）或其解析器 `simulated` ⇒ 材料不足，等价于服务端
 *   `job.isSimulation()`（`CraftingContext.wasSimulated`，仅由 SimulateMissingItemResolver 置位）；
 * - FAILURE 任务会中止整个计算；
 * - `incomplete` 请求 ⇒ 超出合成步骤上限，树被截断，不可提交；
 * - 缺失物品 = 请求树中所有 `simulated` 解析器（ConjureItemTask / IgnoreMissingItemTask /
 *   模拟 CraftFromPatternTask），其数量为解析器的 `resolved`，物品/流体类型取父请求的 `stack`。
 *
 * 注意：任务列表（`tasks`）中的 `simulated` 标志不可直接作为缺失依据——内部调度任务
 * {@link INTERNAL_TASK_TYPES} 的该标志为派生值，成功树中亦可能为 true，已予排除。
 *
 * @param tree {@link AECraftingJobTree}，来自 `AECraftingTaskResult.job` 或 `AECraftingTaskErrorBody.body`
 */
export function analyzeAECraftingJobTree(tree: AECraftingJobTree): AECraftingJobAnalysis {
    const simulatedTasks = tree.tasks.filter(
        task => task.simulated && !INTERNAL_TASK_TYPES.includes(task.type),
    );
    const failedTasks = tree.tasks.filter(task => task.state === "FAILURE");

    const reasons = new Set<AECraftingJobAnalysisReason>();
    const missing = new Map<string, AEStack>();

    const visitRequest = (node: AECraftingRequestNode): void => {
        if (node.incomplete) {
            reasons.add("INCOMPLETE");
        }
        if (node.remaining > 0) {
            reasons.add("UNRESOLVED");
        }
        if (node.simulated) {
            // wasSimulated：该请求（含子树）被"无中生有"满足，等价于服务端 job.isSimulation()
            reasons.add("SIMULATED");
        }
        for (const resolver of node.resolvers) {
            if (resolver.simulated && resolver.resolved > 0) {
                // 模拟解析器（ConjureItemTask / IgnoreMissingItemTask / 模拟 CraftFromPatternTask）：
                // resolved 即缺失数量，堆类型取父请求的 stack（服务端仅输出数量，未输出完整堆）
                reasons.add("SIMULATED");
                mergeInto(missing, { ...node.stack, stackSize: resolver.resolved });
            }
        }
        node.children?.forEach(visitRequest);
    };
    tree.requests.forEach(visitRequest);

    if (simulatedTasks.length > 0) {
        reasons.add("SIMULATED");
    }
    if (failedTasks.length > 0) {
        reasons.add("FAILED_TASK");
    }

    return {
        craftable: reasons.size === 0,
        reasons: [...reasons],
        missing: [...missing.values()],
        simulatedTasks,
    };
}
