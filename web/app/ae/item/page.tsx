"use client"

import { H2 } from "@/components/H2"
import MCToolitip from "@/components/MCTooltip"
import Percent from "@/components/PerCent"
import PersistentDataGrid from "@/components/PersistentDataGrid"
import { RContainer } from "@/components/RContainer"
import { SelectableIconPaper } from "@/components/SelectableIconPaper"
import { useAPI } from "@/data/api"
import CP from "@/data/Clipboard"
import { formatBytes, formatCount } from "@/data/format"
import useCoords from "@/data/useCoords"
import CloseIcon from "@mui/icons-material/Close"
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import SendIcon from '@mui/icons-material/Send'
import ViewListIcon from "@mui/icons-material/ViewList"
import ViewModuleIcon from "@mui/icons-material/ViewModule"
import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    CircularProgress,
    Grid,
    LinearProgress,
    Pagination,
    Paper,
    Popover,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Typography
} from "@mui/material"
import type { GridRowSelectionModel } from "@mui/x-data-grid"
import {
    GridColDef,
    gridExpandedSortedRowEntriesSelector
} from "@mui/x-data-grid"
import type { GridApiPro } from "@mui/x-data-grid-pro/models"
import type { AECraftingJobAnalysisReason, AECraftingTaskErrorBody, AEItemStack, AEItemsResult, AEStack } from "@shirokasoke/webapi-sdk"
import { WebApiError, analyzeAECraftingJobTree } from "@shirokasoke/webapi-sdk"
import { useSearchParams } from "next/navigation"
import { enqueueSnackbar } from "notistack"
import { useEffect, useRef, useState } from "react"
import { Footer } from "../Footer"
import ItemIcon from "../ItemIcon"
type AEItemRow = AEItemStack & { uid: string }
type AEItemStorageStats = Pick<AEItemsResult, "totalBytes" | "usedBytes" | "totalTypes" | "usedTypes">

const columns: GridColDef<AEItemRow>[] = [
    {
        field: "id",
        headerName: "ID",
        width: 80,
        type: "number",
        filterable: true,
    },
    {
        field: "damage",
        headerName: "元数据",
        width: 90,
        type: "number",
        filterable: true,
    },
    {
        field: "localizedName",
        headerName: "名称",
        width: 240,
        filterable: true,
    },
    {
        field: "registryName",
        headerName: "注册名",
        width: 320,
        filterable: true,
    },
    {
        field: "stackSize",
        headerName: "数量",
        width: 120,
        type: "number",
        filterable: true,
        valueFormatter: (value: number) => formatCount(value),
    },
    {
        field: "type",
        headerName: "类型",
        type: "boolean",
        filterable: true,
        valueGetter: (_value, row) => row.type == "item",
        renderCell: (params) => (
            <Chip
                label={params.value ? "物品" : "流体"}
                color={params.value ? "primary" : "secondary"}
                size="small"
            />
        ),
    },
    {
        field: "Craftable",
        headerName: "可合成",
        type: "boolean",
        filterable: true,
        renderCell: (params) => (
            <Chip
                label={params.row.Craftable ? "是" : "否"}
                color={params.row.Craftable ? "success" : "warning"}
                size="small"
            />
        ),
    },
    {
        field: "nbtstr",
        headerName: "NBT",
        width: 300,
        filterable: true,
        valueGetter: (_value, row) => row.type == "item" ? row.nbt?.nbtstr ?? "" : "",
    }
]

/** 合成失败原因的中文说明 */
const CRAFT_FAIL_REASON_TEXT: Record<AECraftingJobAnalysisReason, string> = {
    SIMULATED: "材料不足",
    FAILED_TASK: "合成路径失败",
    INCOMPLETE: "超出合成步骤上限",
    UNRESOLVED: "存在未满足的合成请求",
}

/** 失败原因的展示优先级：主因在前，未列出的原因（如 SDK 新增）追加在末尾 */
const CRAFT_FAIL_REASON_ORDER: AECraftingJobAnalysisReason[] = ["SIMULATED", "FAILED_TASK", "INCOMPLETE", "UNRESOLVED"]

/** 缺失材料清单文本：最多展示前 limit 种，超出以“等 N 种”收尾 */
function formatMissingStacks(missing: AEStack[], limit = 5): string {
    if (missing.length === 0) return ""
    const shown = missing
        .slice(0, limit)
        .map((s) => `${s.localizedName ?? "#" + s.id} x${formatCount(s.stackSize)}`)
    return missing.length > limit ? `${shown.join("、")} 等 ${missing.length} 种` : shown.join("、")
}

/**
 * 解析 AE 合成任务提交失败的原因。
 */
function describeCraftFailure(e: unknown): string {
    if (!(e instanceof WebApiError)) {
        return e instanceof Error ? e.message : "提交合成任务失败"
    }
    const tree = (e.body as AECraftingTaskErrorBody | undefined)?.body
    if (!tree) return e.message
    try {
        const analysis = analyzeAECraftingJobTree(tree)
        const reasons = [
            ...CRAFT_FAIL_REASON_ORDER.filter((r) => analysis.reasons.includes(r)),
            ...analysis.reasons.filter((r) => !CRAFT_FAIL_REASON_ORDER.includes(r)),
        ].map((r) => CRAFT_FAIL_REASON_TEXT[r] ?? r)
        const detail = [`合成失败：${reasons.length > 0 ? reasons.join("、") : e.message}`]
        const missing = formatMissingStacks(analysis.missing)
        if (missing) detail.push(`缺失：${missing}`)
        return detail.join("，")
    } catch {
        return e.message
    }
}

export default function AEItemPage() {
    const api = useAPI()
    const searchParams = useSearchParams()
    const [x, y, z, dimension] = useCoords(searchParams)
    const [items, setItems] = useState<AEItemRow[]>([])
    const [storageStats, setStorageStats] = useState<[AEItemStorageStats, AEItemStorageStats] | null>(null)
    const [error, setError] = useState<string | null>(null)

    const [viewMode, setViewMode] = useState<"list" | "icon">("icon")
    const [paginationModel, setPaginationModel] = useState({ page: 0, pageSize: 50 })

    const [displayRows, setDisplayRows] = useState<AEItemRow[]>([])

    const apiRef = useRef<GridApiPro>(null)

    const [rowSelectionModel, setRowSelectionModel] = useState<GridRowSelectionModel>({ type: "include", ids: new Set() })
    const [mousePos, setMousePos] = useState<{ left: number; top: number } | null>(null)
    const [craftCount, setCraftCount] = useState<number>(1)

    useEffect(() => {
        if (!api || !x) return
        setError(null)
        api.aeItems({ x, y, z, dimension })
            .then((data) => {
                const rows = data.items.map((it): AEItemRow => ({
                    ...it,
                    uid: it.type == "item" ? `${it.id}-${it.damage}-${it.nbt?.nbtstr ?? ""}` : `f-${it.id}`,
                }))
                setItems(rows)
                displayCallback()
                setStorageStats([{
                    totalBytes: data.totalBytes, usedBytes: data.usedBytes, totalTypes: data.totalTypes, usedTypes: data.usedTypes,
                }, {
                    totalBytes: data.fluidTotalBytes, usedBytes: data.fluidUsedBytes, totalTypes: data.fluidTotalTypes, usedTypes: data.fluidUsedTypes,
                }])
            })
            .catch((e) => setError(e instanceof Error ? e.message : "加载物品失败"))
    }, [api != undefined, x, y, z, dimension])

    const displayCallback = () => {
        if (!apiRef.current) return
        setTimeout(() => {
            const entries = gridExpandedSortedRowEntriesSelector(apiRef)
            setDisplayRows(entries.map((e) => e.model as AEItemRow))
        }, 200)
    }
    let pageCount = 0
    let pageItems: AEItemRow[] = []
    if (viewMode === "icon") {
        pageCount = Math.ceil(displayRows.length / paginationModel.pageSize)
        pageItems = displayRows.slice(
            paginationModel.page * paginationModel.pageSize,
            (paginationModel.page + 1) * paginationModel.pageSize
        )
    }

    if (!api) {
        return (
            <RContainer sx={{ pt: 10, textAlign: "center" }}>
                <CircularProgress size={80} />
                <Typography sx={{ mt: 2 }}>正在初始化 API...</Typography>
            </RContainer>
        )
    }

    const totalCount = items.reduce((s, it) => s + (it.stackSize || 0), 0)
    return (
        <RContainer>
            <H2>AE 存储物品</H2>
            {error && (
                <Alert severity="error" sx={{ mb: 2 }}>
                    {error}
                </Alert>
            )}

            {!error && (
                <Grid container spacing={2} sx={{ mb: 2 }}>
                    <Grid size={{ xs: 12, sm: 12, md: 4 }}>
                        <Card>
                            <LinearProgress variant="determinate" color="success" sx={{ height: 6 }} value={100} />
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="success">
                                    {formatCount(totalCount)}
                                </Typography>
                                <Typography variant="body2">
                                    物品总数
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                        <Percent percent={storageStats ? Math.min((storageStats[0].usedBytes / storageStats[0].totalBytes) * 100, 100) : 0} title="物品存储占用" subtitle={storageStats ? `${formatBytes(storageStats[0].usedBytes)} / ${formatBytes(storageStats[0].totalBytes)}` : "-"} />
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                        <Percent percent={storageStats ? Math.min((storageStats[0].usedTypes / storageStats[0].totalTypes) * 100, 100) : 0} title="物品类型占用" subtitle={storageStats ? `${formatCount(storageStats[0].usedTypes)} / ${formatCount(storageStats[0].totalTypes)}` : "-"} />
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                        <Percent percent={storageStats ? Math.min((storageStats[1].usedBytes / storageStats[1].totalBytes) * 100, 100) : 0} title="流体存储占用" subtitle={storageStats ? `${formatBytes(storageStats[1].usedBytes)} / ${formatBytes(storageStats[1].totalBytes)}` : "-"} />
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                        <Percent percent={storageStats ? Math.min((storageStats[1].usedTypes / storageStats[1].totalTypes) * 100, 100) : 0} title="流体类型占用" subtitle={storageStats ? `${formatCount(storageStats[1].usedTypes)} / ${formatCount(storageStats[1].totalTypes)}` : "-"} />
                    </Grid>
                </Grid>
            )}

            <Grid container spacing={2} sx={{ mb: 2, alignItems: "center", justifyContent: "center" }}>
                <Grid>
                    <TextField label="搜索物品" placeholder="输入名称、注册名或 ID" size="small" sx={{ minWidth: 280 }}
                        disabled={apiRef.current == undefined}
                        onChange={(e) => apiRef.current?.setQuickFilterValues([e.target.value])} />
                </Grid>
                <Grid>
                    <Typography variant="body2" color="primary">
                        共 {displayRows.length} / {items.length} 种物品
                    </Typography>
                </Grid>
                <Grid>
                    <ToggleButtonGroup
                        value={viewMode}
                        exclusive
                        size="small"
                        onChange={(_, v) => {
                            if (!v || v === viewMode) return
                            setViewMode(v)
                        }}
                    >
                        <ToggleButton value="list">
                            <ViewListIcon />
                        </ToggleButton>
                        <ToggleButton value="icon">
                            <ViewModuleIcon />
                        </ToggleButton>
                    </ToggleButtonGroup>
                </Grid>
            </Grid>
            <Paper sx={{ height: "70vh", width: "100%", mb: 2, display: viewMode === "list" ? undefined : "none" }}>
                <PersistentDataGrid
                    apiRef={apiRef}
                    storageKey="ae-item"
                    rows={items}
                    columns={columns}
                    loading={items.length === 0}
                    getRowId={(row) => row.uid}
                    pageSizeOptions={[25, 50, 100, 500]}
                    paginationModel={paginationModel}
                    onPaginationModelChange={setPaginationModel}
                    onFilterModelChange={displayCallback}
                    onSortModelChange={displayCallback}
                    rowSelectionModel={rowSelectionModel}
                    onRowSelectionModelChange={(model) => setRowSelectionModel(model)}
                    onCellClick={(_, event) => setMousePos({ left: event.clientX, top: event.clientY })}
                    slotProps={{
                        loadingOverlay: {
                            variant: "skeleton",
                            noRowsVariant: "skeleton",
                        },
                    }}
                />
            </Paper>
            {viewMode === "icon" && <>
                <Paper sx={{ p: 2, mb: 2, position: "relative", minHeight: 240 }}>
                    <Box
                        sx={{
                            display: "grid",
                            gridTemplateColumns: "repeat(auto-fill, minmax(64px, 1fr))",
                            gap: 1.5,
                            opacity: items.length == 0 ? 0.35 : 1,
                            transition: "opacity 0.2s ease",
                        }}
                    >
                        {pageItems.map((item) => {
                            const selected = rowSelectionModel.ids.has(item.uid)
                            return <MCToolitip k={item.uid} item={item}>
                                <SelectableIconPaper elevation={selected ? 16 : 0} selected={selected} onClick={(event) => {
                                    setRowSelectionModel({ type: "include", ids: selected ? new Set([]) : new Set([item.uid]) })
                                    setMousePos({ left: event.clientX, top: event.clientY })
                                }}>
                                    <ItemIcon api={api} item={item} badge />
                                </SelectableIconPaper>
                            </MCToolitip>
                        })}
                    </Box>
                    {items.length == 0 && (
                        <Box
                            sx={{
                                position: "absolute",
                                inset: 0,
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                bgcolor: "rgba(255, 255, 255, 0.35)",
                                backdropFilter: "blur(1px)",
                            }}
                        >
                            <CircularProgress />
                        </Box>
                    )}
                    {pageItems.length === 0 && items.length != 0 && (
                        <Typography align="center" sx={{ py: 4 }}>
                            无匹配结果
                        </Typography>
                    )}
                </Paper>
                {pageCount > 1 && (
                    <Box sx={{ display: "flex", justifyContent: "center", pb: 1 }}>
                        <Pagination
                            count={pageCount}
                            page={paginationModel.page + 1}
                            onChange={(_, p) => {
                                setPaginationModel(prev => ({ ...prev, page: p - 1 }))
                            }}
                            showFirstButton
                            showLastButton
                        />
                    </Box>
                )}
            </>}
            <Popover
                open={rowSelectionModel.ids.size > 0 && mousePos !== null}
                anchorReference="anchorPosition"
                anchorPosition={mousePos ?? { top: 0, left: 0 }}
                onClose={() => setMousePos(null)}
                anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
                transformOrigin={{ vertical: "top", horizontal: "center" }}
                slotProps={{ paper: { sx: { p: 1, display: "flex", flexDirection: "column", gap: 1, minWidth: 180 } } }}
            >
                <TextField size="small" type="number" label="合成数量" value={craftCount} onChange={(e) => {
                    setCraftCount(Math.max(1, parseInt(e.target.value) || 1))
                }} />
                <Button size="small" variant="outlined" startIcon={<SendIcon />} color="secondary"
                    onClick={async () => {
                        const uid = Array.from(rowSelectionModel.ids)[0] as string;
                        const item = items.find((it) => it.uid === uid);
                        if (!item || !x) return;
                        if (!item.Craftable) return enqueueSnackbar("此物品无合成配方", { variant: "warning" })
                        try {
                            const result = await api.aeCraft(
                                { x, y, z, dimension },
                                {
                                    id: item.id,
                                    Count: craftCount,
                                    Type: item.type,
                                    Damage: item.type == "item" ? item.damage : undefined,
                                    tag: item.type == "item" ? item.nbt?.nbtWrite : undefined,
                                }
                            );
                            enqueueSnackbar(`已提交合成任务，输出: ${result.output.localizedName} x${result.output.stackSize}，CPU: ${result.cpu}`, { variant: "success" });
                            setRowSelectionModel({ type: "include", ids: new Set() });
                            setMousePos(null);
                        } catch (e) {
                            enqueueSnackbar(describeCraftFailure(e), { variant: "error" });
                        }
                    }}>提交合成任务</Button>
                <Button size="small" variant="outlined" startIcon={<ContentCopyIcon />} color="secondary"
                    onClick={() => {
                        const uid = Array.from(rowSelectionModel.ids)[0] as string;
                        const item = items.find((it) => it.uid === uid);
                        if (!item || !x) return;
                        CP(JSON.stringify({ type: "ae-item", requestData: { x: x, y: y, z: z, dimension: dimension, ...item } }))
                    }}>复制监控面板代码</Button>
                <Button size="small" color="inherit" startIcon={<CloseIcon />} onClick={() => {
                    setRowSelectionModel({ type: "include", ids: new Set() })
                    setMousePos(null)
                }}>取消选择</Button>
            </Popover>
            <Footer args={searchParams.toString()} />
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, justifyContent: "center", pt: 2 }}>
                <Button onClick={() => CP(JSON.stringify({ type: "ae-item-storage", requestData: { x, y, z, dimension } }))}
                    size="small" variant="outlined" startIcon={<ContentCopyIcon />} >复制物品存储占用面板代码</Button>
                <Button onClick={() => CP(JSON.stringify({ type: "ae-item-type", requestData: { x, y, z, dimension } }))}
                    size="small" variant="outlined" startIcon={<ContentCopyIcon />} >复制物品类型占用面板代码</Button>
                <Button onClick={() => CP(JSON.stringify({ type: "ae-fluid-storage", requestData: { x, y, z, dimension } }))}
                    size="small" variant="outlined" startIcon={<ContentCopyIcon />} >复制流体存储占用面板代码</Button>
                <Button onClick={() => CP(JSON.stringify({ type: "ae-fluid-type", requestData: { x, y, z, dimension } }))}
                    size="small" variant="outlined" startIcon={<ContentCopyIcon />} >复制流体类型占用面板代码</Button>
            </Box>
        </RContainer>
    )
}
