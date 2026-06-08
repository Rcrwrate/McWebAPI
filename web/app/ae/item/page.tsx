"use client"

import CustomPagination from "@/app/blocks/CustomPagination"
import { H2 } from "@/components/H2"
import MCToolitip from "@/components/MCTooltip"
import Percent from "@/components/PerCent"
import { SelectableIconPaper } from "@/components/SelectableIconPaper"
import { useAPI } from "@/data/api"
import { formatBytes, formatCount } from "@/data/format"
import useCoords from "@/data/useCoords"
import CloseIcon from "@mui/icons-material/Close"
import SendIcon from '@mui/icons-material/Send'
import ViewListIcon from "@mui/icons-material/ViewList"
import ViewModuleIcon from "@mui/icons-material/ViewModule"
import {
    Alert,
    Badge,
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    CircularProgress,
    Container,
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
import type { GridFilterModel, GridRowSelectionModel, GridSortModel } from "@mui/x-data-grid"
import {
    DataGrid,
    GridColDef,
    gridExpandedSortedRowEntriesSelector
} from "@mui/x-data-grid"
import { GridApiCommunity } from "@mui/x-data-grid/internals"
import type { AEItemStack, AEItemsResult } from "@shirokasoke/webapi-sdk"
import { useSearchParams } from "next/navigation"
import { enqueueSnackbar } from "notistack"
import { useEffect, useRef, useState } from "react"
import { Footer } from "../Footer"
import ItemIcon from "../ItemIcon"
type AEItemRow = AEItemStack & { uid: string }
type AEItemStorageStats = Pick<AEItemsResult, "totalBytes" | "usedBytes" | "totalTypes" | "usedTypes">

const columns: GridColDef<AEItemRow>[] = [
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
        field: "stackSize",
        headerName: "数量",
        width: 120,
        type: "number",
        filterable: true,
        valueFormatter: (value: number) => formatCount(value),
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
    }
]

export default function AEItemPage() {
    const api = useAPI()
    const searchParams = useSearchParams()
    const [x, y, z, dimension] = useCoords(searchParams)
    const [items, setItems] = useState<AEItemRow[]>([])
    const [storageStats, setStorageStats] = useState<AEItemStorageStats | null>(null)
    const [error, setError] = useState<string | null>(null)

    const [viewMode, setViewMode] = useState<"list" | "icon">("icon")
    const [paginationModel, setPaginationModel] = useState({ page: 0, pageSize: 50 })

    const [displayRows, setDisplayRows] = useState<AEItemRow[]>([])

    const [sortM, setSortM] = useState<GridSortModel>()
    const [filterM, setFilterM] = useState<GridFilterModel>()
    const apiRef = useRef<GridApiCommunity>(null)

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
                    uid: `${it.id}-${it.damage}-${it.nbtstr ?? ""}`,
                }))
                setItems(rows)
                setDisplayRows(rows)
                setStorageStats({
                    totalBytes: data.totalBytes,
                    usedBytes: data.usedBytes,
                    totalTypes: data.totalTypes,
                    usedTypes: data.usedTypes,
                })
            })
            .catch((e) => setError(e instanceof Error ? e.message : "加载物品失败"))
    }, [api != undefined, x, y, z, dimension])

    useEffect(() => {
        if (!apiRef.current) return
        const timer = setTimeout(() => {
            const entries = gridExpandedSortedRowEntriesSelector(apiRef)
            setDisplayRows(entries.map((e) => e.model as AEItemRow))
        }, 200)
        return () => clearTimeout(timer)
    }, [filterM, sortM])

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
            <Container sx={{ pt: 10, textAlign: "center" }}>
                <CircularProgress size={80} />
                <Typography sx={{ mt: 2 }}>正在初始化 API...</Typography>
            </Container>
        )
    }

    const totalCount = items.reduce((s, it) => s + (it.stackSize || 0), 0)
    const storagePercent = storageStats && storageStats.totalBytes > 0
        ? Math.min((storageStats.usedBytes / storageStats.totalBytes) * 100, 100)
        : 0
    const typePercent = storageStats && storageStats.totalTypes > 0
        ? Math.min((storageStats.usedTypes / storageStats.totalTypes) * 100, 100)
        : 0

    return (
        <Container sx={{ p: 1 }}>
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
                        <Percent percent={storagePercent} title="存储占用" subtitle={storageStats ? `${formatBytes(storageStats.usedBytes)} / ${formatBytes(storageStats.totalBytes)}` : "-"} />
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                        <Percent percent={typePercent} title="类型占用" subtitle={storageStats ? `${formatCount(storageStats.usedTypes)} / ${formatCount(storageStats.totalTypes)}` : "-"} />
                    </Grid>
                </Grid>
            )}

            <Grid container spacing={2} sx={{ mb: 2, alignItems: "center", justifyContent: "center" }}>
                <Grid>
                    <TextField label="搜索物品" placeholder="输入名称、注册名或 ID" size="small" sx={{ minWidth: 280 }}
                        disabled={viewMode == "icon"}
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

            {viewMode === "list" ? (
                <Paper sx={{ height: "70vh", width: "100%", mb: 2 }}>
                    <DataGrid
                        apiRef={apiRef}
                        rows={items}
                        columns={columns}
                        loading={items.length === 0}
                        getRowId={(row) => row.uid}
                        pageSizeOptions={[25, 50, 100, 500]}
                        paginationModel={paginationModel}
                        onPaginationModelChange={setPaginationModel}
                        filterModel={filterM}
                        onFilterModelChange={(m) => setFilterM(m)}
                        sortModel={sortM}
                        onSortModelChange={(s) => setSortM(s)}
                        density="compact"
                        slots={{ pagination: CustomPagination }}
                        rowSelectionModel={rowSelectionModel}
                        onRowSelectionModelChange={(model) => {
                            setRowSelectionModel(model)
                        }}
                        onCellClick={(_, event) => {
                            setMousePos({ left: event.clientX, top: event.clientY })
                        }}
                        slotProps={{
                            loadingOverlay: {
                                variant: "skeleton",
                                noRowsVariant: "skeleton",
                            },
                        }}
                    />
                </Paper>
            ) : (
                <>
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
                </>
            )}
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
                                    Damage: item.damage,
                                    tag: item.nbtWrite,
                                }
                            );
                            enqueueSnackbar(`已提交合成任务，输出: ${result.output.localizedName} x${result.output.stackSize}，CPU: ${result.cpu}`, { variant: "success" });
                            setRowSelectionModel({ type: "include", ids: new Set() });
                            setMousePos(null);
                        } catch (e) {
                            enqueueSnackbar(e instanceof Error ? e.message : "提交合成任务失败", { variant: "error" });
                        }
                    }}>提交合成任务</Button>
                <Button size="small" color="inherit" startIcon={<CloseIcon />} onClick={() => {
                    setRowSelectionModel({ type: "include", ids: new Set() })
                    setMousePos(null)
                }}>取消选择</Button>
            </Popover>
            <Footer args={searchParams.toString()} />
        </Container>
    )
}
