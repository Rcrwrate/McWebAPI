package love.shirokasoke.webapi.webserver.handlers.ae2;

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
 * 脱离主线程的 {@link CraftingJobV2}
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
 * 若此时发生样板变更（{@link appeng.api.networking.events.MENetworkCraftingPatternChange} 会触发CME
 * <p>
 * 合成样板判定还会在调用线程触发 Forge 合成事件{@link appeng.crafting.v2.CraftingContext#simulateComplexCrafting}与假玩家匹配
 * 
 * @apiNote {@link love.shirokasoke.webapi.mixins.late.AECrafting.MECraftingInventoryMixin}
 */
public class OffTickCraftingJobV2<StackType extends IAEStack<StackType>> extends CraftingJobV2<StackType> {

    public OffTickCraftingJobV2(World world, IGrid meGrid, BaseActionSource actionSource, StackType what,
        CraftingMode craftingMode, ICraftingCallback callback) {
        super(world, meGrid, actionSource, what, craftingMode, callback);
    }

    /**
     * 不注册 {@link appeng.hooks.TickHandler}
     * 
     * @apiNote 需要手动控制计算
     */
    @Override
    public Future<ICraftingJob<StackType>> schedule() {
        return this;
    }
}
