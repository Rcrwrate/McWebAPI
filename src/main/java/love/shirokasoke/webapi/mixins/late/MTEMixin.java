package love.shirokasoke.webapi.mixins.late;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.ISmartInputHatch;

@Mixin(love.shirokasoke.webapi.utils.Accessor.class)
public class MTEMixin {

    /**
     * @author shirokasoke
     * @reason 替代反射
     * @return
     */
    @org.spongepowered.asm.mixin.Overwrite(remap = false)
    public static ArrayList<ISmartInputHatch> MTEMultiBlockBase_mSmartInputHatches(MTEMultiBlockBase multi) {
        return ((MTEAccess) multi).$getmSmartInputHatches();
    }
}
