package love.shirokasoke.webapi.crafting;

import java.util.concurrent.Future;

import net.minecraft.world.World;

import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.v2.CraftingJobV2;

/**
 * 实验性：可脱离主线程的 {@link CraftingJobV2}
 *
 * <p>
 * 一次性构建所需数据
 * {@link appeng.crafting.v2.CraftingContext#itemModel} /
 * {@link appeng.crafting.v2.CraftingContext#availableCache} 为私有拷贝，样板表为
 * {@link appeng.me.cache.CraftingGridCache#getCraftingMultiPatterns()} 返回的不可变拷贝
 *
 * <p>
 * 计算期间可能会只读访问活动网格的
 * {@link appeng.me.cache.CraftingGridCache#canEmitFor(appeng.api.storage.data.IAEStack)} /
 * {@link appeng.me.cache.CraftingGridCache#getMediums(appeng.api.networking.crafting.ICraftingPatternDetails)}，
 * 若此时发生样板变更（{@link appeng.api.networking.events.MENetworkCraftingPatternChange} 会触发CME；
 * <p>
 * 合成样板判定还会在调用线程触发 Forge 合成事件{@link appeng.crafting.v2.CraftingContext#simulateComplexCrafting}与假玩家匹配
 */
public class OffTickCraftingJobV2<StackType extends IAEStack<StackType>> extends CraftingJobV2<StackType> {

    /** 单次计算切片时长（毫秒） */
    private static final int SLICE_MS = 50;

    /**
     * @param world        必须在服务器主线程调用；父类构造器在此读取网络库存与样板表完成快照
     * @param meGrid       ME 网格
     * @param actionSource 权限来源
     * @param what         请求合成的物品/流体
     * @param craftingMode 合成模式
     * @param callback     计算完成回调（可为 null）
     */
    public OffTickCraftingJobV2(World world, IGrid meGrid, BaseActionSource actionSource, StackType what,
        CraftingMode craftingMode, ICraftingCallback callback) {
        super(world, meGrid, actionSource, what, craftingMode, callback);
    }

    /**
     * 不注册 {@link appeng.hooks.TickHandler}
     */
    @Override
    public Future<ICraftingJob<StackType>> schedule() {
        return this;
    }

    /**
     * 在当前线程分片驱动合成树计算，到达超时后自行取消。
     *
     * <p>
     * job 实例与 {@link appeng.crafting.v2.CraftingContext} 均为本请求私有，构造完成后不再触碰网络存储，
     * 因此可在任意线程驱动；虚拟线程下阻塞等待不占用平台线程。
     * {@link #state} 只由本线程写入，调用方随后读取不存在跨线程状态竞争。
     * </p>
     *
     * @param timeoutMs 计算超时（毫秒）
     */
    public void compute(long timeoutMs) {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (simulateFor(SLICE_MS) && System.currentTimeMillis() < deadline) {
            // 每次让出 SLICE_MS，直到计算完成或超时
        }
        if (state == State.RUNNING) {
            cancel(true);
        }
    }
}
