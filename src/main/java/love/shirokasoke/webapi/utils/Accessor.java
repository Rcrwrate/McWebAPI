package love.shirokasoke.webapi.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.ISmartInputHatch;

public final class Accessor {

    private Accessor() {}

    /** 缓存 {@link MTEMultiBlockBase#mSmartInputHatches } */
    private static Field mSmartInputHatches = null;

    @SuppressWarnings("unchecked")
    public static ArrayList<ISmartInputHatch> MTEMultiBlockBase_mSmartInputHatches(MTEMultiBlockBase multi) {
        try {
            if (mSmartInputHatches == null) {
                mSmartInputHatches = MTEMultiBlockBase.class.getDeclaredField("mSmartInputHatches");
                mSmartInputHatches.setAccessible(true);
            }
            return (ArrayList<ISmartInputHatch>) mSmartInputHatches.get(multi);
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
            log.e(e);
            return new ArrayList<ISmartInputHatch>();
        }
    }
}
