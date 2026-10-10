"use client"

import {
    DataGridPro,
    type DataGridProProps,
    type GridApiPro,
    type GridColumnOrderChangeParams,
    type GridColumnVisibilityModel,
    type GridFilterModel,
    type GridSortModel,
    type GridValidRowModel,
} from "@mui/x-data-grid-pro"
import type { RefObject } from "react"
import { useEffect, useRef, useState } from "react"

/** 表格显示模型：筛选、排序、列显示、列顺序合并为单一状态统一管理 */
export interface GridDisplayModel {
    filter?: GridFilterModel
    sort?: GridSortModel
    /** 列显示：field -> 是否显示 */
    columnVisibility?: GridColumnVisibilityModel
    /** 列顺序：field 列表 */
    orderedFields?: string[]
}

const STORAGE_PREFIX = "grid:"

function loadDisplay(storageKey: string): GridDisplayModel | null {
    if (typeof window === "undefined") return null
    try {
        const raw = window.localStorage.getItem(STORAGE_PREFIX + storageKey)
        if (!raw) return null
        const parsed = JSON.parse(raw) as GridDisplayModel
        return {
            filter: parsed.filter ?? undefined,
            sort: parsed.sort ?? undefined,
            columnVisibility: parsed.columnVisibility ?? undefined,
            orderedFields: Array.isArray(parsed.orderedFields) ? parsed.orderedFields : undefined,
        }
    } catch {
        return null
    }
}

/**
 * DataGrid 的持久化抽象（内部为 DataGridPro）：
 * - 筛选/排序/列显示/列顺序合并为单一 display 模型，所有变化汇入唯一的 displayCallback（仅用于更新当前显示）
 * - 自动持久化到 localStorage，表格初始化时恢复
 * - 列顺序：拖拽列头调整（Pro 特性）；列显示：toolbar 的 Columns 面板
 *
 * @param storageKey localStorage 存储键，不同表格需唯一
 * @param apiRef 外部 apiRef，可选，供外部读取表格状态
 * @param onDisplayChange display 变化回调，仅用于更新当前显示（如筛选后的行数统计）
 */
export interface PersistentDataGridProps<R extends GridValidRowModel = any>
    extends Omit<DataGridProProps<R>,
        | "apiRef"
        | "filterModel" | "onFilterModelChange"
        | "sortModel" | "onSortModelChange"
        | "columnVisibilityModel" | "onColumnVisibilityModelChange"
        | "onColumnOrderChange"> {
    /** localStorage 存储键，不同表格需唯一 */
    storageKey: string
    /** 外部 apiRef，可选，供外部读取表格状态 */
    apiRef?: RefObject<GridApiPro | null>
    /** display 变化回调，仅用于更新当前显示 */
    onDisplayChange?: (display: GridDisplayModel) => void
}

export default function PersistentDataGrid<R extends GridValidRowModel = any>({
    storageKey,
    apiRef: externalApiRef,
    onDisplayChange,
    ...gridProps
}: PersistentDataGridProps<R>) {
    const fallbackApiRef = useRef<GridApiPro>(null)
    const apiRef = externalApiRef ?? fallbackApiRef

    const [display, setDisplay] = useState<GridDisplayModel>({})
    const displayRef = useRef<GridDisplayModel>(display)
    const changeListenerRef = useRef(onDisplayChange)
    changeListenerRef.current = onDisplayChange

    // 表格初始化：从 localStorage 恢复用户上次的显示设置
    useEffect(() => {
        const saved = loadDisplay(storageKey)
        if (!saved) return
        displayRef.current = saved
        setDisplay(saved)
        changeListenerRef.current?.(saved)
        // v9 列顺序为非受控状态，通过 api 应用
        const api = apiRef.current
        if (saved.orderedFields && api) {
            saved.orderedFields.forEach((field, index) => {
                if (api.getColumn(field)) {
                    api.setColumnIndex(field, index)
                }
            })
        }
    }, [storageKey, apiRef])

    /** displayCallback：显示变化的唯一入口，合并更新、持久化并通知当前显示 */
    const displayCallback = (patch: Partial<GridDisplayModel>) => {
        const next = { ...displayRef.current, ...patch }
        displayRef.current = next
        setDisplay(next)
        if (typeof window !== "undefined") {
            try {
                window.localStorage.setItem(STORAGE_PREFIX + storageKey, JSON.stringify(next))
            } catch {
                // 持久化失败不影响当前显示
            }
        }
        changeListenerRef.current?.(next)
    }

    /** 拖拽列头后读取最新列顺序并持久化 */
    const handleColumnOrderChange = (_params: GridColumnOrderChangeParams) => {
        const fields = apiRef.current?.getAllColumns().map((c) => c.field)
        if (fields && fields.length > 0) {
            displayCallback({ orderedFields: fields })
        }
    }

    return <DataGridPro
        {...gridProps}
        apiRef={apiRef}
        filterModel={display.filter}
        onFilterModelChange={(m) => displayCallback({ filter: m })}
        sortModel={display.sort}
        onSortModelChange={(s) => displayCallback({ sort: s })}
        columnVisibilityModel={display.columnVisibility}
        onColumnVisibilityModelChange={(m) => displayCallback({ columnVisibility: m })}
        onColumnOrderChange={handleColumnOrderChange}
    />
}
