import type { Panel } from "@/data/Panel";

export interface PanelDefinition {
    id: string;
    title: string;
    panel: Panel<any>;
}

import { AEItemStorge, AEItemType, AECPUStatus, AEItem } from "./AE";

export const PANEL_REGISTRY: PanelDefinition[] = [
    { id: "ae-item-storage", title: AEItemStorge.title, panel: AEItemStorge },
    { id: "ae-item-type", title: AEItemType.title, panel: AEItemType },
    { id: "ae-item", title: AEItem.title, panel: AEItem },
    { id: "ae-cpu-status", title: AECPUStatus.title, panel: AECPUStatus },
];

export function getPanelDef(id: string): PanelDefinition | undefined {
    return PANEL_REGISTRY.find(p => p.id === id);
}
