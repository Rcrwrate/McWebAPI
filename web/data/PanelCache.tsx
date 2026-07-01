"use client"

import { useAPI } from "@/data/api"
import type { Panel } from "@/data/Panel"
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react"

interface CacheEntry {
    data: any
    loading: boolean
    error: string | null
    panel: Panel<any>
    requestData: any
    /** 当前活跃订阅数：移除的面板会递减，归零后不再参与 refreshAll */
    refs: number
}

interface PanelDataState {
    data: any
    loading: boolean
    error: string | null
}

interface PanelDataContextValue {
    /** 读取缓存数据（命中即返回，不触发刷新） */
    getData: <T>(panel: Panel<T>, requestData?: any) => PanelDataState
    /** 请求拉取：缓存命中则为 no-op */
    request: (panel: Panel<any>, requestData?: any) => void
    /** 强制刷新全部活跃面板 */
    refreshAll: () => void
    /** 面板卸载时递减引用计数 */
    release: (panel: Panel<any>, requestData?: any) => void
}

const PanelDataContext = createContext<PanelDataContextValue | null>(null)

/** 复合缓存键：相同 dataKey + 相同 requestData 共享一条缓存 */
function cacheKey(panel: Panel<any>, requestData?: any): string {
    return requestData != null
        ? `${panel.dataKey}::${JSON.stringify(requestData)}`
        : panel.dataKey
}

export function PanelDataProvider({ children }: { children: React.ReactNode }) {
    const api = useAPI()
    const [cache, setCache] = useState<Record<string, CacheEntry>>({})
    // 同步 ref：在 fetchPanel 内立即写入，避免同帧多个面板重复请求
    const cacheRef = useRef<Record<string, CacheEntry>>({})
    cacheRef.current = cache

    const fetchPanel = useCallback(
        (panel: Panel<any>, requestData: any, force = false, bumpRef = false) => {
            if (!api) return
            const key = cacheKey(panel, requestData)
            const existing = cacheRef.current[key]
            // 缓存命中（已加载或加载中且无错误）则不刷新
            if (
                !force &&
                existing &&
                (existing.loading || (existing.data !== undefined && !existing.error))
            ) {
                if (bumpRef && existing) {
                    cacheRef.current = {
                        ...cacheRef.current,
                        [key]: { ...existing, refs: existing.refs + 1 },
                    }
                    setCache(cacheRef.current)
                }
                return
            }

            const method = (api as any)[panel.method]
            const invoke = panel.func
                ? () => panel.func!(api, requestData)
                : typeof method === "function"
                    ? () => method.call(api, requestData)
                    : null

            if (!invoke) {
                const entry: CacheEntry = {
                    data: panel.dafaultData,
                    loading: false,
                    error: `未知方法: ${String(panel.method)}`,
                    panel,
                    requestData,
                    refs: (existing?.refs ?? 0) + (bumpRef ? 1 : 0),
                }
                cacheRef.current = { ...cacheRef.current, [key]: entry }
                setCache(cacheRef.current)
                return
            }

            // 立即标记 loading，防止同帧重复请求
            const loadingEntry: CacheEntry = {
                data: existing?.data ?? panel.dafaultData,
                loading: true,
                error: null,
                panel,
                requestData,
                refs: (existing?.refs ?? 0) + (bumpRef ? 1 : 0),
            }
            cacheRef.current = { ...cacheRef.current, [key]: loadingEntry }
            setCache(cacheRef.current)

            invoke()
                .then((data: any) => {
                    const cur = cacheRef.current[key]
                    cacheRef.current = {
                        ...cacheRef.current,
                        [key]: { ...cur, data, loading: false, error: null, panel, requestData },
                    }
                    setCache(cacheRef.current)
                })
                .catch((e: any) => {
                    const cur = cacheRef.current[key]
                    cacheRef.current = {
                        ...cacheRef.current,
                        [key]: {
                            ...cur,
                            data: panel.dafaultData,
                            loading: false,
                            error: e instanceof Error ? e.message : "加载失败",
                            panel,
                            requestData,
                        },
                    }
                    setCache(cacheRef.current)
                })
        },
        [api]
    )

    /** 面板卸载时递减引用计数，归零则从缓存移除（仅当不在 loading） */
    const release = useCallback((panel: Panel<any>, requestData?: any) => {
        const key = cacheKey(panel, requestData)
        const existing = cacheRef.current[key]
        if (!existing) return
        const nextRefs = existing.refs - 1
        if (nextRefs > 0 || existing.loading) {
            cacheRef.current = { ...cacheRef.current, [key]: { ...existing, refs: nextRefs } }
            setCache(cacheRef.current)
        } else {
            const rest = { ...cacheRef.current }
            delete rest[key]
            cacheRef.current = rest
            setCache(rest)
        }
    }, [])

    const request = useCallback(
        (panel: Panel<any>, requestData?: any) => {
            fetchPanel(panel, requestData, false, true)
        },
        [fetchPanel]
    )

    const getData = useCallback(
        <T,>(panel: Panel<T>, requestData?: any): PanelDataState => {
            const key = cacheKey(panel, requestData)
            const entry = cache[key]
            return {
                data: entry?.data ?? panel.dafaultData,
                loading: entry?.loading ?? false,
                error: entry?.error ?? null,
            }
        },
        [cache]
    )

    const refreshAll = useCallback(() => {
        Object.values(cacheRef.current).forEach(e => {
            if (e.refs > 0) fetchPanel(e.panel, e.requestData, true)
        })
    }, [fetchPanel])

    const value = useMemo(
        () => ({ getData, request, refreshAll, release }),
        [getData, request, refreshAll, release]
    )

    return (
        <PanelDataContext.Provider value={value}>
            {children}
        </PanelDataContext.Provider>
    )
}

/**
 * 面板数据 Hook：根据 dataKey + requestData 复合缓存，缓存命中不会重复刷新。
 * 多个共享同一 dataKey 且同一 requestData 的面板只会触发一次请求。
 */
export function usePanelData<T>(panel: Panel<T>, requestData?: any): PanelDataState & { data: T } {
    const ctx = useContext(PanelDataContext)
    const request = ctx?.request
    const release = ctx?.release
    const key = cacheKey(panel, requestData)

    useEffect(() => {
        request?.(panel, requestData)
        return () => release?.(panel, requestData)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [request, release, key])

    if (!ctx) {
        return { data: panel.dafaultData, loading: false, error: null }
    }
    return ctx.getData(panel, requestData) as PanelDataState & { data: T }
}

export function usePanelActions() {
    const ctx = useContext(PanelDataContext)
    return ctx
}
