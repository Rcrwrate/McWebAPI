import Joi from "joi";
import type { PlayerPrintResult, PrintSizeResult, WorldPrintJobResult, WorldPrintSubmitResult } from "../types/threeD";

export const PlayerPrintResultSchema = Joi.object<PlayerPrintResult>({
    total: Joi.number().required(),
    added: Joi.number().required(),
    dropped: Joi.number().required(),
});

export const WorldPrintSubmitResultSchema = Joi.object<WorldPrintSubmitResult>({
    id: Joi.string().required(),
    total: Joi.number().required(),
    skipped: Joi.number().required(),
});

export const PrintJobStatusSchema = Joi.valid("pending", "running", "completed");

export const WorldPrintFailureSchema = Joi.object({
    x: Joi.number().required(),
    y: Joi.number().required(),
    z: Joi.number().required(),
    reason: Joi.string().required(),
});

export const WorldPrintJobResultSchema = Joi.object<WorldPrintJobResult>({
    id: Joi.string().required(),
    total: Joi.number().required(),
    completed: Joi.number().required(),
    success: Joi.number().required(),
    failed: Joi.number().required(),
    status: PrintJobStatusSchema.required(),
    createTime: Joi.number().required(),
    finishTime: Joi.number().optional(),
    durationMs: Joi.number().optional(),
    failures: Joi.array().items(WorldPrintFailureSchema).optional(),
    failuresTruncated: Joi.number().optional(),
});

export const PrintSizeResultSchema = Joi.object<PrintSizeResult>({
    size: Joi.number().required(),
});
