/// <reference types="node" />
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

export interface MissingIconEntry {
    id: number;
    damage: number;
    tag?: string;
    registryName: string;
    localizedName: string;
    [key: string]: unknown;
}

const OUT_PATH = resolve(__dirname, "missing-icons.json");

const EXCLUDED_REGISTRY_NAMES = new Set([
    "ae2fc:fluid_drop",
]);

export function makeKey(e: MissingIconEntry): string {
    return `${e.id}:${e.damage}:${e.tag ?? ""}`;
}

export function isExcluded(e: MissingIconEntry): boolean {
    return EXCLUDED_REGISTRY_NAMES.has(e.registryName);
}

export function loadExisting(): MissingIconEntry[] {
    try {
        return JSON.parse(readFileSync(OUT_PATH, "utf-8")) as MissingIconEntry[];
    } catch {
        return [];
    }
}

export function dedup(...arrays: MissingIconEntry[][]): MissingIconEntry[] {
    const seen = new Set<string>();
    const result: MissingIconEntry[] = [];
    for (const entry of arrays.flat()) {
        if (isExcluded(entry)) continue;
        const key = makeKey(entry);
        if (!seen.has(key)) {
            seen.add(key);
            result.push(entry);
        }
    }
    return result;
}

export function saveMissingIcons(...arrays: MissingIconEntry[][]): number {
    const merged = dedup(...arrays);
    writeFileSync(OUT_PATH, JSON.stringify(merged, null, 2), "utf-8");
    return merged.length;
}
