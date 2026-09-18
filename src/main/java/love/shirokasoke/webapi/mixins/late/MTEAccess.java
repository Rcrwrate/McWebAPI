package love.shirokasoke.webapi.mixins.late;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.ISmartInputHatch;

@Mixin(MTEMultiBlockBase.class)
public interface MTEAccess {

    @Accessor(value = "mSmartInputHatches", remap = false)
    public ArrayList<ISmartInputHatch> $getmSmartInputHatches();
}
