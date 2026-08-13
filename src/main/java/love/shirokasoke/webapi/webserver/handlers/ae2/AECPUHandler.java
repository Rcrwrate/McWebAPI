package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.NamedDimensionalCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cluster.implementations.CraftingCPUCluster.TaskProgress;
import love.shirokasoke.webapi.utils.Accessor;
import love.shirokasoke.webapi.utils.ClassUtils;
import love.shirokasoke.webapi.utils.Logs;
import love.shirokasoke.webapi.utils.Pattern;
import love.shirokasoke.webapi.webserver.Context;

/**
 * 查看 AE 网络中所有合成 CPU 状态
 */
public class AECPUHandler extends AEBaseHandler {

    @Override
    public String getPath() {
        return "/ae/cpu";
    }

    @Override
    public String getDescription() {
        return "AE Crafting CPUs";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        Context context = AEinit(exchange);

        // 从网格缓存中获取合成网格接口
        ICraftingGrid craftingGrid = context.grid.getCache(ICraftingGrid.class);
        if (craftingGrid == null) {
            throw new ApiException(500, "Crafting grid not available");
        }

        ArrayNode cpus = mapper.createArrayNode();
        for (ICraftingCPU cpu : craftingGrid.getCpus()) {
            ObjectNode cpuNode = mapper.createObjectNode();

            // 理论上 CPU 只可能是 CraftingCPUCluster
            if (cpu instanceof CraftingCPUCluster cluster) {
                dumpCraftingTasks(cluster, cpuNode);
                dumpBasicInfo(cluster, cpuNode);
            } else {
                Logs.debugFields(cpus);
            }
            cpus.add(cpuNode);
        }
        setCache(exchange, 5);
        sendResponse(exchange, cpus);
    }

    /**
     * 导出 CPU 的基础信息，类型访问安全
     */
    private ObjectNode dumpBasicInfo(CraftingCPUCluster cpu, ObjectNode cpuNode) {
        cpuNode.put("name", cpu.getName())
            // .put("busy", cpu.isBusy()) isBusy会对task进行写入，规避，已移至dumpCraftingTasks
            .put("availableStorage", cpu.getAvailableStorage())
            .put("usedStorage", cpu.getUsedStorage())
            .put("coProcessors", cpu.getCoProcessors())
            .put("remainingItemCount", cpu.getRemainingItemCount())
            .put("startItemCount", cpu.getStartItemCount())
            .put("elapsedTime", cpu.getElapsedTime())
            .put(
                "craftingAllowMode",
                cpu.getCraftingAllowMode()
                    .name());
        ClassUtils.getClassInfo(cpu, cpuNode);

        // 若 CPU 正在合成，导出最终产物信息
        IAEStack<?> finalOutput = cpu.getFinalMultiOutput();
        if (finalOutput != null) {
            ObjectNode outputNode = Pattern.dumpAEStack(finalOutput);
            if (outputNode != null) {
                cpuNode.set("finalOutput", outputNode);
            }
        }

        return cpuNode;
    }

    /**
     * 导出 CraftingCPUCluster 内部正在进行的并行合成任务及机器坐标。
     *
     * <p>
     * 每个任务包含输入物品、输出物品、剩余次数以及执行该样板的机器位置列表。
     * <p>
     * 
     * {@link ICraftingPatternDetails} 实现于 {@link appeng.helpers.PatternHelper} 一次性构建完成后不会修改
     */
    private void dumpCraftingTasks(CraftingCPUCluster cluster, ObjectNode cpuNode) {
        try {
            Map<ICraftingPatternDetails, TaskProgress> tasks = Accessor.CraftingCPUCluster_tasks(cluster);
            boolean isBusy = false;

            if (tasks != null && !tasks.isEmpty()) {
                isBusy = true;
                ArrayNode tasksArray = cpuNode.putArray("tasks");
                for (Map.Entry<ICraftingPatternDetails, TaskProgress> entry : tasks.entrySet()) {
                    ICraftingPatternDetails details = entry.getKey();
                    TaskProgress taskProgress = entry.getValue();

                    ObjectNode taskNode = tasksArray.addObject();

                    taskNode.put("remaining", Accessor.TaskProgress_value(taskProgress));

                    ArrayNode inputsArray = taskNode.putArray("inputs");
                    for (IAEStack<?> input : details.getCondensedAEInputs()) {
                        if (input == null) continue;
                        ObjectNode inputNode = Pattern.dumpAEStack(input);
                        if (inputNode != null) {
                            inputsArray.add(inputNode);
                        }
                    }

                    taskNode.set("pattern", Pattern.dump(details.getPattern(), true, null));

                    // 收集该任务的所有输出物品
                    ArrayNode outputsArray = taskNode.putArray("outputs");
                    for (IAEStack<?> output : details.getCondensedAEOutputs()) {
                        if (output == null) continue;

                        ObjectNode outputNode = Pattern.dumpAEStack(output);
                        if (outputNode == null) continue;

                        ArrayNode providersArray = outputNode.putArray("providers");
                        try {
                            for (NamedDimensionalCoord coord : Accessor
                                .CraftingCPUCluster_getProviders(cluster, output)) {
                                ObjectNode coordNode = mapper.createObjectNode();
                                coordNode.put("x", coord.x);
                                coordNode.put("y", coord.y);
                                coordNode.put("z", coord.z);
                                coordNode.put("dimension", coord.getDimension());
                                providersArray.add(coordNode);
                            }
                        } catch (Exception e) {
                            Logs.e(e);
                        }
                        outputsArray.add(outputNode);
                    }
                }
            }

            IItemList<IAEStack<?>> waitingFor = Accessor.CraftingCPUCluster_waitingFor(cluster);
            if (waitingFor != null && !waitingFor.isEmpty()) {
                isBusy = true;
                ArrayNode taskingArray = cpuNode.putArray("tasking");
                for (IAEStack<?> stack : waitingFor) {
                    if (stack == null) continue;
                    ObjectNode itemNode = Pattern.dumpAEStack(stack);
                    if (itemNode == null) continue;

                    ArrayNode providersArray = itemNode.putArray("providers");
                    try {
                        for (NamedDimensionalCoord coord : Accessor.CraftingCPUCluster_getProviders(cluster, stack)) {
                            ObjectNode coordNode = mapper.createObjectNode();
                            coordNode.put("x", coord.x);
                            coordNode.put("y", coord.y);
                            coordNode.put("z", coord.z);
                            coordNode.put("dimension", coord.getDimension());
                            providersArray.add(coordNode);
                        }
                    } catch (Exception e) {
                        Logs.e(e);
                    }
                    taskingArray.add(itemNode);
                }
            }
            cpuNode.put("busy", isBusy);
        } catch (Exception e) {
            Logs.e(e);
            cpuNode.put("tasksError", e.getMessage());
        }
    }
}
