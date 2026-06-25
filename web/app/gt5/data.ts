import type { GT5ScanMachine } from "@shirokasoke/webapi-sdk"
import { GT5ScanMachineSchema } from "@shirokasoke/webapi-sdk"
import Joi from "joi"

export interface SavedGT5Machine extends GT5ScanMachine {
    dimension: number
    /** 保存时间戳 (ms) */
    savedAt: number
}

const STORAGE_KEY = "gt5_machines"

export const SavedGT5MachineSchema = GT5ScanMachineSchema.append<SavedGT5Machine>({
    owner: Joi.string().allow("").required(),
    savedAt: Joi.number().integer().min(0).required(),
}).strict()

function validateMachine(raw: unknown): SavedGT5Machine | null {
    const { value, error } = SavedGT5MachineSchema.validate(raw, { stripUnknown: false })
    return error ? null : value
}

function validateMachines(raw: unknown): SavedGT5Machine[] {
    if (!Array.isArray(raw)) return []
    const result: SavedGT5Machine[] = []
    for (const item of raw) {
        const m = validateMachine(item)
        if (m) result.push(m)
    }
    return result
}

function load(): SavedGT5Machine[] {
    if (typeof window === "undefined") return []
    try {
        const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]")
        return validateMachines(raw)
    } catch {
        return []
    }
}

function save(machines: SavedGT5Machine[]) {
    if (typeof window === "undefined") return
    localStorage.setItem(STORAGE_KEY, JSON.stringify(machines))
}

/** 机器唯一标识：坐标 + 维度 */
function machineKey(m: { x: number; y: number; z: number; dimension: number }): string {
    return `${m.dimension}:${m.x},${m.y},${m.z}`
}

/**
 * 保存扫描到的机器数据到 localStorage，按 (x, y, z, dimension) 去重。
 * 输入数据会先经过 Joi 严格校验，不合法的数据会被跳过。
 * 已存在的坐标会被新数据覆盖，返回 { total, saved, skipped } 统计。
 */
export function saveMachines(
    machines: (GT5ScanMachine & { dimension: number })[]
): { total: number; saved: number; skipped: number } {
    const existing = load()
    const map = new Map<string, SavedGT5Machine>()
    const now = Date.now()

    for (const m of existing) {
        map.set(machineKey(m), m)
    }

    let saved = 0
    let skipped = 0
    for (const m of machines) {
        const validated = validateMachine({ ...m, savedAt: now })
        if (!validated) {
            skipped++
            continue
        }
        map.set(machineKey(validated), validated)
        saved++
    }

    const all = Array.from(map.values())
    save(all)
    return { total: all.length, saved, skipped }
}

/** 获取所有已保存的机器数据（经过 Joi 校验） */
export function getSavedMachines(): SavedGT5Machine[] {
    return load()
}

/** 清空所有已保存的机器数据 */
export function clearSavedMachines() {
    if (typeof window === "undefined") return
    localStorage.removeItem(STORAGE_KEY)
}

/** 删除指定坐标的机器，返回剩余数量 */
export function removeMachine(x: number, y: number, z: number, dimension: number): number {
    const all = load()
    const filtered = all.filter((m) => m.x !== x || m.y !== y || m.z !== z || m.dimension !== dimension)
    save(filtered)
    return filtered.length
}
