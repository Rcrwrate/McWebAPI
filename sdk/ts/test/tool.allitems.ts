/// <reference types="node" />
import { WebApiClient, WebApiError } from "../src/client";
import { ApiErrorResponse } from "../src/types/common";
import { loadExisting, saveMissingIcons } from "./tool.missing-icon-util";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" });

(async () => {
    const allItems = await api.getItems();
    const missing: { id: number; damage: number; tag?: string; registryName: string; localizedName: string }[] = [];

    for (const item of allItems) {
        if (item.HasSubtypes) {
            try {
                const detail = await api.getItem({ id: item.id });
                if (detail.subs) {
                    for (const sub of detail.subs) {
                        missing.push({
                            id: sub.id,
                            damage: sub.damage,
                            tag: sub.nbtWrite,
                            registryName: sub.registryName,
                            localizedName: sub.localizedName,
                        })
                    }
                }
            } catch (e) {
                if (e instanceof WebApiError) {
                    console.error(`Failed to get item detail for id=${item.id}: ${e.message}`);
                    console.error((e.body as ApiErrorResponse).stack)
                } else {
                    throw e;
                }
            }
        } else {
            missing.push({
                id: item.id,
                damage: 0,
                registryName: item.registryName,
                localizedName: item.localizedName,
            })
        }
    }

    const existing = loadExisting();
    const total = saveMissingIcons(existing, missing);

    console.log(`found ${missing.length} all icons (total ${total} in file).`);
})();
