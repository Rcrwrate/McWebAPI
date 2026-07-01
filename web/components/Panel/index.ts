import type { Panel } from "@/data/Panel";

export interface PanelDefinition {
    id: string;
    title: string;
    panel: Panel<any>;
}

import { AEItemStorge, AEItemType, AECPUStatus } from "./AE";

export const PANEL_REGISTRY: PanelDefinition[] = [
    { id: "ae-item-storage", title: AEItemStorge.title, panel: AEItemStorge },
    { id: "ae-item-type", title: AEItemType.title, panel: AEItemType },
    { id: "ae-cpu-status", title: AECPUStatus.title, panel: AECPUStatus },
];

export function getPanelDef(id: string): PanelDefinition | undefined {
    return PANEL_REGISTRY.find(p => p.id === id);
}
