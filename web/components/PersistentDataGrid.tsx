"use client"

import Box from "@mui/material/Box"
import CircularProgress from "@mui/material/CircularProgress"
import {
    DataGridPro,
    type DataGridProProps,
    type GridApiPro,
    type GridInitialState,
    type GridValidRowModel,
} from "@mui/x-data-grid-pro"
import type { RefObject } from "react"
import { useCallback, useLayoutEffect, useRef, useState } from "react"

const STORAGE_PREFIX = "grid-state:"

/**
 * DataGrid 的持久化抽象（内部为 DataGridPro）：
 * - 通过 exportState()/initialState 完整保存与恢复表格状态（筛选、排序、列显示、列顺序、分页等）
 * - 挂载时从 localStorage 恢复为 initialState；刷新/关闭页面（beforeunload）及组件卸载时保存快照
 *
 * @param storageKey localStorage 存储键，不同表格需唯一
 * @param apiRef 外部 apiRef，可选，供外部读取表格状态
 */
export interface PersistentDataGridProps<R extends GridValidRowModel = any>
    extends Omit<DataGridProProps<R>, | "apiRef"> {
    /** localStorage 存储键，不同表格需唯一 */
    storageKey: string
    /** 外部 apiRef，可选，供外部读取表格状态 */
    apiRef?: RefObject<GridApiPro | null>
}

export default function PersistentDataGrid<R extends GridValidRowModel = any>({
    storageKey,
    apiRef: externalApiRef,
    ...gridProps
}: PersistentDataGridProps<R>) {
    const fallbackApiRef = useRef<GridApiPro>(null)
    const apiRef = externalApiRef ?? fallbackApiRef
    // api 实例在表格生命周期内不变；卸载清理时 React 已把 apiRef.current 置空，故另持一份引用
    const snapshotApiRef = useRef<GridApiPro | null>(null)

    const [initialState, setInitialState] = useState<GridInitialState>()

    /** 导出当前状态快照并持久化 */
    const saveSnapshot = useCallback(() => {
        const api = snapshotApiRef.current
        if (typeof window === "undefined" || !api?.exportState) return
        try {
            window.localStorage.setItem(STORAGE_PREFIX + storageKey, JSON.stringify(api.exportState()))
        } catch {
            // 持久化失败不影响当前显示
        }
    }, [storageKey])

    // apiRef 挂载后捕获 api 实例（不回退为 null）
    useLayoutEffect(() => {
        snapshotApiRef.current = apiRef.current ?? snapshotApiRef.current
    })

    // 表格初始化：从 localStorage 恢复上次的显示设置
    useLayoutEffect(() => {
        let restored: GridInitialState = {}
        try {
            const raw = window.localStorage.getItem(STORAGE_PREFIX + storageKey)
            if (raw) restored = JSON.parse(raw)
        } catch {
            // 解析失败则使用默认状态
        }
        setInitialState(restored)

        // 处理刷新与关闭页面
        window.addEventListener("beforeunload", saveSnapshot)
        return () => {
            // SPA 卸载时移除监听并保存
            window.removeEventListener("beforeunload", saveSnapshot)
            saveSnapshot()
        }
    }, [storageKey, saveSnapshot])

    return initialState ?
        <DataGridPro
            {...gridProps}
            apiRef={apiRef}
            initialState={{
                ...gridProps.initialState,
                ...initialState,
            }}
        /> : <Box sx={{ height: "100%", display: "grid", placeItems: "center" }}>
            <CircularProgress />
        </Box>
}

