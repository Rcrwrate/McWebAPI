/// <reference types="node" />
import { WebApiClient, WebApiError } from "../src/client";
import { loadExisting, saveMissingIcons } from "./tool.missing-icon-util";

const api = new WebApiClient({ baseUrl: "http://localhost:40002" });

const AE_COORDS = { x: -25, y: 116, z: 63 };

(async () => {
    const result = await api.aeItems(AE_COORDS);
    const missing: { id: number; damage: number; tag?: string; registryName: string; localizedName: string; stackSize: number; craftable: boolean }[] = [];

    let checked = 0;

    for (const item of result.items) {
        if (item.type !== "item") continue;
        checked++;
        try {
            await api.getItemIcon({ id: item.id, damage: item.damage, tag: item.nbtWrite });
        } catch (e) {
            if (e instanceof WebApiError && e.status === 404) {
                missing.push({
                    id: item.id,
                    damage: item.damage,
                    tag: item.nbtWrite,
                    registryName: item.registryName,
                    localizedName: item.localizedName,
                    stackSize: item.stackSize,
                    craftable: item.Craftable,
                });
            } else {
                throw e;
            }
        }
    }

    const existing = loadExisting();
    const total = saveMissingIcons(existing, missing);

    console.log(`Checked ${checked} items from AE network, found ${missing.length} missing icons (total ${total} in file).`);
})();
