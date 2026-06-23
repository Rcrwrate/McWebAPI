package love.shirokasoke.webapi.mixins.late;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import gregtech.api.util.LongData;
import gregtech.api.util.LongRunningAverage;
import gregtech.common.misc.WirelessNetworkManager;
import kekztech.common.tileentities.MTELapotronicSuperCapacitor;

@Mixin(MTELapotronicSuperCapacitor.class)
public class MTELapotronicSuperCapacitorGetInfoMapMixin {

    @Shadow(remap = false)
    private BigInteger capacity;

    @Shadow(remap = false)
    private BigInteger stored;

    @Shadow(remap = false)
    private long passiveDischargeAmount;

    @Shadow(remap = false)
    private long inputLastTick;

    @Shadow(remap = false)
    private long outputLastTick;

    @Shadow(remap = false)
    @Final
    private LongData energyInputValues;

    @Shadow(remap = false)
    @Final
    private LongData energyOutputValues;

    @Shadow(remap = false)
    @Final
    private LongData energyInputValues5m;

    @Shadow(remap = false)
    @Final
    private LongData energyOutputValues5m;

    @Shadow(remap = false)
    @Final
    private LongRunningAverage energyInputValues1h;

    @Shadow(remap = false)
    @Final
    private LongRunningAverage energyOutputValues1h;

    @Shadow(remap = false)
    private boolean wireless_mode;

    @Shadow(remap = false)
    @Final
    private int[] capacitors;

    @Shadow(remap = false)
    private UUID global_energy_user_uuid;

    @Shadow(remap = false)
    private long mMaxEUIn;

    @Shadow(remap = false)
    private long mMaxEUOut;

    /**
     * 修复 {@link MTELapotronicSuperCapacitor#getInfoMap}，使其返回 LSC 自身的 BigInteger 存储/容量及实时 I/O 数据，
     * 而非父类从能源仓 buffer 读取的无关数据。
     */
    public Map<String, String> getInfoMap() {
        Map<String, String> infoMap = new HashMap<>();

        // 存储 & 容量（long 截断值 + BigInteger 精确值）
        infoMap.put("stored", Long.toString(stored.longValue()));
        infoMap.put("storedExact", stored.toString());
        infoMap.put("capacity", Long.toString(capacity.longValue()));
        infoMap.put("capacityExact", capacity.toString());

        // 实时 I/O（上一 tick）
        infoMap.put("inputLastTick", Long.toString(inputLastTick));
        infoMap.put("outputLastTick", Long.toString(outputLastTick));

        // 被动损耗
        infoMap.put("passiveDischargeAmount", Long.toString(passiveDischargeAmount));

        infoMap.put("avgInput100t", Long.toString(energyInputValues.avgLong()));
        infoMap.put("avgOutput100t", Long.toString(energyOutputValues.avgLong()));
        infoMap.put("avgInput5m", Long.toString(energyInputValues5m.avgLong()));
        infoMap.put("avgOutput5m", Long.toString(energyOutputValues5m.avgLong()));
        infoMap.put("avgInput1h", Long.toString(energyInputValues1h.avgLong()));
        infoMap.put("avgOutput1h", Long.toString(energyOutputValues1h.avgLong()));

        // 最大输入/输出功率
        infoMap.put("maxEUInput", Long.toString(mMaxEUIn));
        infoMap.put("maxEUOutput", Long.toString(mMaxEUOut));

        // 无线模式
        infoMap.put("wirelessMode", Boolean.toString(wireless_mode));
        if (wireless_mode && global_energy_user_uuid != null) {
            BigInteger wirelessEU = WirelessNetworkManager.getUserEU(global_energy_user_uuid);
            infoMap.put("wirelessEU", Long.toString(wirelessEU.longValue()));
            infoMap.put("wirelessEUExact", wirelessEU.toString());
        }

        // 电容数量（索引与 LSC 内部一致：4=UHV, 7=UEV, 8=UIV, 9=UMV）
        infoMap.put("capacitorUHV", Integer.toString(capacitors[4]));
        infoMap.put("capacitorUEV", Integer.toString(capacitors[7]));
        infoMap.put("capacitorUIV", Integer.toString(capacitors[8]));
        infoMap.put("capacitorUMV", Integer.toString(capacitors[9]));

        return infoMap;
    }
}
