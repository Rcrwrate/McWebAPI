/// <reference types="node" />
import { WebApiClient, WebApiError } from "../src/client";
import { loadExisting, saveMissingIcons } from "./tool.missing-icon-util";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" });

(async () => {
    const allItems = await api.getItems();
    const missing: { id: number; damage: number; tag?: string; registryName: string; localizedName: string }[] = [];

    let checked = 0;

    for (const item of allItems) {
        if (item.HasSubtypes) {
            try {
                const detail = await api.getItem({ id: item.id });
                if (detail.subs) {
                    for (const sub of detail.subs) {
                        checked++;
                        try {
                            await api.getItemIcon({ id: sub.id, damage: sub.damage, tag: sub.nbtWrite });
                        } catch (e) {
                            if (e instanceof WebApiError && e.status === 404) {
                                missing.push({
                                    id: sub.id,
                                    damage: sub.damage,
                                    tag: sub.nbtWrite,
                                    registryName: sub.registryName,
                                    localizedName: sub.localizedName,
                                });
                            } else {
                                throw e;
                            }
                        }
                    }
                }
            } catch (e) {
                if (e instanceof WebApiError) {
                    console.error(`Failed to get item detail for id=${item.id}: ${e.message}`);
                } else {
                    throw e;
                }
            }
        } else {
            checked++;
            try {
                await api.getItemIcon({ id: item.id });
            } catch (e) {
                if (e instanceof WebApiError && e.status === 404) {
                    missing.push({
                        id: item.id,
                        damage: 0,
                        registryName: item.registryName,
                        localizedName: item.localizedName,
                    });
                } else {
                    throw e;
                }
            }
        }
    }

    const existing = loadExisting();
    const total = saveMissingIcons(existing, missing);

    console.log(`Checked ${checked} items, found ${missing.length} missing icons (total ${total} in file).`);
})();
