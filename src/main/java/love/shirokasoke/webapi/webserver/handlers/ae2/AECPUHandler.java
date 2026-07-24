package love.shirokasoke.webapi.webserver.handlers.ae2;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
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
import love.shirokasoke.webapi.utils.ClassUtils;
import love.shirokasoke.webapi.utils.Pattern;
import love.shirokasoke.webapi.utils.log;

/**
 * 查看 AE 网络中所有合成 CPU 状态
 */
public class AECPUHandler extends AEBaseHandler {

    /** 缓存 {@link CraftingCPUCluster } 的私有 tasks 字段 */
    private static Field TASKS_FIELD = null;
    /** 缓存 TaskProgress 的私有 value 字段 */
    private static Field VALUE_FIELD = null;
    /** 缓存 {@link CraftingCPUCluster } 的私有 waitingFor 字段 */
    private static Field WAITING_FOR_FIELD = null;

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
        AEinit(exchange);

        // 从网格缓存中获取合成网格接口
        ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);
        if (craftingGrid == null) {
            throw new Error(500, "Crafting grid not available");
        }

        ArrayNode cpus = mapper.createArrayNode();
        for (ICraftingCPU cpu : craftingGrid.getCpus()) {
            ObjectNode cpuNode = mapper.createObjectNode();

            // 若 CPU 是 CraftingCPUCluster，尝试导出更详细的任务与机器坐标信息
            if (cpu instanceof CraftingCPUCluster cluster) {
                dumpCraftingTasks(cluster, cpuNode);
            }
            // 后置，因为调用isBusy会清空已完成的task
            dumpBasicInfo(cpu, cpuNode);
            cpus.add(cpuNode);
        }
        setCache(exchange, 5);
        sendResponse(exchange, cpus);
    }

    /**
     * 导出 CPU 的基础信息。
     */
    private ObjectNode dumpBasicInfo(ICraftingCPU cpu, ObjectNode cpuNode) {
        cpuNode.put("name", cpu.getName())
            .put("busy", cpu.isBusy())
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
     * </p>
     */
    private void dumpCraftingTasks(CraftingCPUCluster cluster, ObjectNode cpuNode) {
        try {
            if (TASKS_FIELD == null) {
                TASKS_FIELD = CraftingCPUCluster.class.getDeclaredField("tasks");
                TASKS_FIELD.setAccessible(true);
            }

            @SuppressWarnings("unchecked")
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) TASKS_FIELD
                .get(cluster);

            if (tasks != null && !tasks.isEmpty()) {
                ArrayNode tasksArray = cpuNode.putArray("tasks");
                for (Map.Entry<ICraftingPatternDetails, Object> entry : tasks.entrySet()) {
                    ICraftingPatternDetails details = entry.getKey();
                    Object taskProgress = entry.getValue();

                    ObjectNode taskNode = tasksArray.addObject();

                    long remaining = -1;
                    try {
                        if (VALUE_FIELD == null) {
                            VALUE_FIELD = taskProgress.getClass()
                                .getDeclaredField("value");
                            VALUE_FIELD.setAccessible(true);
                        }
                        remaining = (long) VALUE_FIELD.get(taskProgress);
                    } catch (Exception e) {
                        log.e(e);
                    }
                    taskNode.put("remaining", remaining);

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

                        // 通过 getProviders 获取执行该输出的机器坐标（支持并行合成）
                        ArrayNode providersArray = outputNode.putArray("providers");
                        try {
                            List<NamedDimensionalCoord> providers = cluster.getProviders(output);
                            for (NamedDimensionalCoord coord : providers) {
                                ObjectNode coordNode = mapper.createObjectNode();
                                coordNode.put("x", coord.x);
                                coordNode.put("y", coord.y);
                                coordNode.put("z", coord.z);
                                coordNode.put("dimension", coord.getDimension());
                                providersArray.add(coordNode);
                            }
                        } catch (Exception e) {
                            log.e(e);
                        }
                        outputsArray.add(outputNode);
                    }
                }
            }

            // 导出已派发、正在等待返回的产物（waitingFor）
            if (WAITING_FOR_FIELD == null) {
                WAITING_FOR_FIELD = CraftingCPUCluster.class.getDeclaredField("waitingFor");
                WAITING_FOR_FIELD.setAccessible(true);
            }
            @SuppressWarnings("unchecked")
            IItemList<IAEStack<?>> waitingFor = (IItemList<IAEStack<?>>) WAITING_FOR_FIELD.get(cluster);
            if (waitingFor != null && !waitingFor.isEmpty()) {
                ArrayNode taskingArray = cpuNode.putArray("tasking");
                for (IAEStack<?> stack : waitingFor) {
                    if (stack == null) continue;
                    ObjectNode itemNode = Pattern.dumpAEStack(stack);
                    if (itemNode == null) continue;

                    ArrayNode providersArray = itemNode.putArray("providers");
                    try {
                        List<NamedDimensionalCoord> providers = cluster.getProviders(stack);
                        for (NamedDimensionalCoord coord : providers) {
                            ObjectNode coordNode = mapper.createObjectNode();
                            coordNode.put("x", coord.x);
                            coordNode.put("y", coord.y);
                            coordNode.put("z", coord.z);
                            coordNode.put("dimension", coord.getDimension());
                            providersArray.add(coordNode);
                        }
                    } catch (Exception e) {
                        log.e(e);
                    }

                    taskingArray.add(itemNode);
                }
            }
        } catch (Exception e) {
            log.e(e);
            cpuNode.put("tasksError", e.getMessage());
        }
    }
}
