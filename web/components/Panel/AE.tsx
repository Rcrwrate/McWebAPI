import { formatBytes, formatCount } from "@/data/format";
import type { Panel } from "@/data/Panel";
import type { AECPU, AEItemsResult } from "@shirokasoke/webapi-sdk";
import Percent from "../PerCent";

const item = {
    'totalBytes': 0, "usedBytes": 0, "totalTypes": 0, "usedTypes": 0,
    items: [],
    cellStatus: { all: 0, green: 0, blue: 0, orange: 0, red: 0 }
}

export const AEItemStorge: Panel<AEItemsResult> = {
    title: "AE 存储占用",
    method: "aeItems",
    dataKey: "aeItems",
    dafaultData: item,
    size: { w: 6, h: 2 },
    Render: function (data: AEItemsResult): React.ReactElement {
        const storagePercent = data.totalBytes > 0 ? (data.usedBytes / data.totalBytes) * 100 : 0;
        return <Percent percent={storagePercent} title="存储占用" subtitle={data ? `${formatBytes(data.usedBytes)} / ${formatBytes(data.totalBytes)}` : "-"} />
    }
}

export const AEItemType: Panel<AEItemsResult> = {
    title: "AE 类型占用",
    method: "aeItems",
    dataKey: "aeItems",
    dafaultData: item,
    size: { w: 6, h: 2 },
    Render: function (data: AEItemsResult): React.ReactElement {
        const typePercent = data.totalTypes > 0 ? (data.usedTypes / data.totalTypes) * 100 : 0;
        return <Percent percent={typePercent} title="类型占用" subtitle={data ? `${formatCount(data.usedTypes)} / ${formatCount(data.totalTypes)}` : "-"} />
    }
}

export const AECPUStatus: Panel<AECPU[]> = {
    title: "AE 合成 CPU",
    method: "aeCPUs",
    dataKey: "aeCPUs",
    dafaultData: [],
    size: { w: 6, h: 3 },
    Render: function (data: AECPU[]): React.ReactElement {
        const busyCount = data.filter(c => c.busy).length;
        return <Percent percent={busyCount / data.length * 100} title="CPU" subtitle={`${busyCount}忙碌中/${data.length - busyCount}空闲`} />
    }
}
