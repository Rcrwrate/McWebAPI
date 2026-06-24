/**
 * LSC rawInfo 解析后的强类型视图。
 * @java [java](../../../../src/main/java/love/shirokasoke/webapi/mixins/late/MTELapotronicSuperCapacitorGetInfoMapMixin.java)
 */
export interface LSCRawInfoMap {
    stored: string;
    capacity: string;
    inputLastTick: number;
    outputLastTick: number;
    passiveDischargeAmount: number;
    avgInput100t: number;
    avgOutput100t: number;
    avgInput5m: number;
    avgOutput5m: number;
    avgInput1h: number;
    avgOutput1h: number;
    maxEUInput: number;
    maxEUOutput: number;
    wirelessMode: boolean;
    wirelessEU?: string;
    capacitorUHV: number;
    capacitorUEV: number;
    capacitorUIV: number;
    capacitorUMV: number;
}
