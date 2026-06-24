import Joi from "joi";
import type { LSCRawInfoMap } from "../types/rawInfo";

/**
 * 校验并转换 LSC rawInfo 为强类型视图（{@link LSCRawInfoMap}）。
 */
export const LSCInfoSchema = Joi.object<LSCRawInfoMap>({
    stored: Joi.number().unsafe().required(),
    capacity: Joi.number().unsafe().required(),
    inputLastTick: Joi.number().required(),
    outputLastTick: Joi.number().required(),
    passiveDischargeAmount: Joi.number().required(),
    avgInput100t: Joi.number().required(),
    avgOutput100t: Joi.number().required(),
    avgInput5m: Joi.number().required(),
    avgOutput5m: Joi.number().required(),
    avgInput1h: Joi.number().required(),
    avgOutput1h: Joi.number().required(),
    maxEUInput: Joi.number().required(),
    maxEUOutput: Joi.number().required(),
    wirelessMode: Joi.boolean().required(),
    wirelessEU: Joi.number().unsafe().optional(),
    capacitorUHV: Joi.number().required(),
    capacitorUEV: Joi.number().required(),
    capacitorUIV: Joi.number().required(),
    capacitorUMV: Joi.number().required(),
});
