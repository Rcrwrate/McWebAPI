import { formatBytes, formatCount } from "@/data/format";
import { Panel } from "@/data/Panel";
import type { AECPU, AEItemsResult } from "@shirokasoke/webapi-sdk";
import Percent from "../PerCent";
import { AEItemContent, AEItemReq } from "./AEItem";

interface coord {
    x: number;
    y: number;
    z: number;
    dimension?: number;
}

const item = {
    'totalBytes': 0, "usedBytes": 0, "totalTypes": 0, "usedTypes": 0,
    fluidTotalBytes: 0, fluidUsedBytes: 0, fluidTotalTypes: 0, fluidUsedTypes: 0,
    items: [],
    cellStatus: { all: 0, green: 0, blue: 0, orange: 0, red: 0 },
    fluidCellStatus: { all: 0, green: 0, blue: 0, orange: 0, red: 0 },
}

export const AEItemStorge: Panel<AEItemsResult, coord> = {
    title: "AE 物品存储占用",
    method: "aeItems",
    dataKey: (requestData?: coord) => {
        return requestData ? `aeItems-${requestData.x}-${requestData.y}-${requestData.z}-${requestData.dimension}` : "aeItems"
    },
    dafaultData: item,
    size: { w: 6, h: 2 },
    Render: function (data: AEItemsResult): React.ReactElement {
        const storagePercent = data.totalBytes > 0 ? (data.usedBytes / data.totalBytes) * 100 : 0;
        return <Percent percent={storagePercent} title="物品存储占用" subtitle={data ? `${formatBytes(data.usedBytes)} / ${formatBytes(data.totalBytes)}` : "-"} />
    }
}

export const AEItemType: Panel<AEItemsResult, coord> = {
    title: "AE 物品类型占用",
    method: "aeItems",
    dataKey: (requestData?: coord) => {
        return requestData ? `aeItems-${requestData.x}-${requestData.y}-${requestData.z}-${requestData.dimension}` : "aeItems"
    },
    dafaultData: item,
    size: { w: 6, h: 2 },
    Render: function (data: AEItemsResult): React.ReactElement {
        const typePercent = data.totalTypes > 0 ? (data.usedTypes / data.totalTypes) * 100 : 0;
        return <Percent percent={typePercent} title="物品类型占用" subtitle={data ? `${formatCount(data.usedTypes)} / ${formatCount(data.totalTypes)}` : "-"} />
    }
}

export const AEFluidStorage: Panel<AEItemsResult, coord> = {
    title: "AE 流体存储占用",
    method: "aeItems",
    dataKey: (requestData?: coord) => {
        return requestData ? `aeItems-${requestData.x}-${requestData.y}-${requestData.z}-${requestData.dimension}` : "aeItems"
    },
    dafaultData: item,
    size: { w: 6, h: 2 },
    Render: function (data: AEItemsResult): React.ReactElement {
        const storagePercent = data.fluidTotalBytes > 0 ? (data.fluidUsedBytes / data.fluidTotalBytes) * 100 : 0;
        return <Percent percent={storagePercent} title="流体存储占用" subtitle={data ? `${formatBytes(data.fluidUsedBytes)} / ${formatBytes(data.fluidTotalBytes)}` : "-"} />
    }
}

export const AEFluidType: Panel<AEItemsResult, coord> = {
    title: "AE 流体类型占用",
    method: "aeItems",
    dataKey: (requestData?: coord) => {
        return requestData ? `aeItems-${requestData.x}-${requestData.y}-${requestData.z}-${requestData.dimension}` : "aeItems"
    },
    dafaultData: item,
    size: { w: 6, h: 2 },
    Render: function (data: AEItemsResult): React.ReactElement {
        const typePercent = data.fluidTotalTypes > 0 ? (data.fluidUsedTypes / data.fluidTotalTypes) * 100 : 0;
        return <Percent percent={typePercent} title="流体类型占用" subtitle={data ? `${formatCount(data.fluidUsedTypes)} / ${formatCount(data.fluidTotalTypes)}` : "-"} />
    }
}

export const AEItem: Panel<AEItemsResult, AEItemReq> = {
    title: "AE 物品监视",
    method: "aeItems",
    dataKey: (requestData?: AEItemReq) => {
        return requestData ? `aeItems-${requestData.x}-${requestData.y}-${requestData.z}-${requestData.dimension}` : "aeItems"
    },
    dafaultData: item,
    size: { w: 2, h: 2 },
    Render: function (data: AEItemsResult, req): React.ReactElement {
        return <AEItemContent data={data} req={req} />
    }
}

export const AECPUStatus: Panel<AECPU[], coord> = {
    title: "AE 合成 CPU",
    method: "aeCPUs",
    dataKey: (requestData?: coord) => {
        return requestData ? `aeCPUs-${requestData.x}-${requestData.y}-${requestData.z}-${requestData.dimension}` : "aeCPUs"
    },
    dafaultData: [],
    size: { w: 6, h: 3 },
    Render: function (data: AECPU[]): React.ReactElement {
        const busyCount = data.filter(c => c.busy).length;
        return <Percent percent={busyCount / data.length * 100} title="CPU" subtitle={`${busyCount}忙碌中/${data.length - busyCount}空闲`} />
    }
}
