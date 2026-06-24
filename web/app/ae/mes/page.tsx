"use client"

import CustomPagination from "@/app/blocks/CustomPagination"
import { H2 } from "@/components/H2"
import { RContainer } from "@/components/RContainer"
import { useAPI } from "@/data/api"
import useCoords from "@/data/useCoords"
import CloseIcon from "@mui/icons-material/Close"
import ListAltIcon from "@mui/icons-material/ListAlt"
import MapIcon from "@mui/icons-material/Map"
import {
    Alert,
    Button,
    Card,
    CardContent,
    Chip,
    CircularProgress,
    Grid,
    Paper,
    Popover,
    TextField,
    Typography
} from "@mui/material"
import type { GridFilterModel, GridRowSelectionModel, GridSortModel } from "@mui/x-data-grid"
import {
    DataGrid,
    GridColDef,
    gridExpandedSortedRowEntriesSelector,
} from "@mui/x-data-grid"
import { GridApiCommunity } from "@mui/x-data-grid/internals"
import type { AEMEInterface } from "@shirokasoke/webapi-sdk"
import { useRouter, useSearchParams } from "next/navigation"
import { useEffect, useRef, useState } from "react"
import { Footer } from "../Footer"

type AEMESRow = AEMEInterface & { id: string }

const columns: GridColDef<AEMESRow>[] = [
    {
        field: "name",
        headerName: "名称",
        width: 200,
        filterable: true,
    },
    {
        field: "rawName",
        headerName: "标准名称",
        width: 200,
        filterable: true,
    },
    {
        field: "active",
        headerName: "活跃",
        width: 100,
        type: "boolean",
        filterable: true,
        renderCell: (params) => (
            <Chip
                label={params.row.active ? "是" : "否"}
                color={params.row.active ? "success" : "error"}
                size="small"
            />
        ),
    },
    {
        field: "display",
        headerName: "显示",
        width: 100,
        type: "boolean",
        filterable: true,
        renderCell: (params) => (
            <Chip
                label={params.row.display ? "是" : "否"}
                color={params.row.display ? "primary" : "default"}
                size="small"
            />
        ),
    },
    {
        field: "allowsPatternOptimization",
        headerName: "样板优化",
        width: 110,
        type: "boolean",
        filterable: true,
        renderCell: (params) => (
            <Chip
                label={params.row.allowsPatternOptimization ? "是" : "否"}
                color={params.row.allowsPatternOptimization ? "info" : "default"}
                size="small"
            />
        ),
    },
    {
        field: "playerID",
        headerName: "玩家ID",
        width: 100,
        type: "number",
        filterable: true,
    },
    {
        field: "location",
        headerName: "坐标",
        width: 200,
        filterable: true,
        valueGetter: (_value, row) => {
            const loc = row.location
            if (!loc) return "-"
            return `(${loc.x}, ${loc.y}, ${loc.z}) [${loc.dimension}]`
        },
    },
    {
        field: "patterns",
        headerName: "样板数",
        width: 100,
        type: "number",
        filterable: true,
        valueGetter: (_value, row) => row.patterns?.length ?? 0,
    },
]

export default function AEMESPage() {
    const api = useAPI()
    const searchParams = useSearchParams()
    const router = useRouter()
    const [x, y, z, dimension] = useCoords(searchParams)
    const [interfaces, setInterfaces] = useState<AEMESRow[]>([])
    const [error, setError] = useState<string | null>(null)
    const [displayRows, setDisplayRows] = useState<AEMESRow[]>([])

    const [sortM, setSortM] = useState<GridSortModel>()
    const [filterM, setFilterM] = useState<GridFilterModel>()
    const apiRef = useRef<GridApiCommunity>(null)

    const [rowSelectionModel, setRowSelectionModel] = useState<GridRowSelectionModel>({ type: "include", ids: new Set() })
    const [mousePos, setMousePos] = useState<{ left: number; top: number } | null>(null)

    useEffect(() => {
        if (!api || !x) return
        setError(null)
        api.aeMEs({ x, y, z, dimension })
            .then((data) => {
                const rows = data.map((n, i): AEMESRow => ({ ...n, id: String(i) }))
                setInterfaces(rows)
                setDisplayRows(rows)
            })
            .catch((e) => setError(e instanceof Error ? e.message : "加载接口列表失败"))
    }, [api != undefined, x, y, z, dimension])

    useEffect(() => {
        if (!apiRef.current) return
        const timer = setTimeout(() => {
            const entries = gridExpandedSortedRowEntriesSelector(apiRef)
            setDisplayRows(entries.map((e) => e.model as AEMESRow))
        }, 200)
        return () => clearTimeout(timer)
    }, [filterM, sortM])

    if (!api) {
        return (
            <RContainer sx={{ pt: 10, textAlign: "center" }}>
                <CircularProgress size={80} />
                <Typography sx={{ mt: 2 }}>正在初始化 API...</Typography>
            </RContainer>
        )
    }

    return (
        <RContainer>
            <H2>ME 接口列表</H2>
            {error && (
                <Alert severity="error" sx={{ mb: 2 }}>
                    {error}
                </Alert>
            )}

            {!error && (
                <Grid container spacing={2} sx={{ mb: 2 }}>
                    <Grid size={{ xs: 6, sm: 4, md: 3 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="primary">
                                    {interfaces.length}
                                </Typography>
                                <Typography variant="body2">
                                    总接口数
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 3 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="success">
                                    {interfaces.filter((i) => i.active).length}
                                </Typography>
                                <Typography variant="body2">
                                    活跃接口
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 3 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="info">
                                    {interfaces.filter((i) => i.display).length}
                                </Typography>
                                <Typography variant="body2">
                                    显示接口
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 3 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="secondary">
                                    {interfaces.reduce((sum, i) => sum + (i.patterns?.length ?? 0), 0)}
                                </Typography>
                                <Typography variant="body2">
                                    总样板数
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                </Grid>
            )}

            <Grid container spacing={2} sx={{ mb: 2, alignItems: "center", justifyContent: "center" }}>
                <Grid>
                    <TextField
                        label="搜索接口"
                        placeholder="输入任意内容快速筛选"
                        onChange={(e) => apiRef.current?.setQuickFilterValues([e.target.value])}
                        size="small"
                        sx={{ minWidth: 280 }}
                    />
                </Grid>
                <Grid>
                    <Typography variant="body2" color="primary">
                        共 {displayRows.length} / {interfaces.length} 个接口
                    </Typography>
                </Grid>
            </Grid>

            <Paper sx={{ height: "70vh", width: "100%", mb: 2 }}>
                <DataGrid
                    apiRef={apiRef}
                    rows={interfaces}
                    columns={columns.map(i => { i.align = "center"; i.headerAlign = "center"; return i })}
                    loading={interfaces.length == 0}
                    getRowId={(row) => row.id}
                    pageSizeOptions={[25, 50, 100]}
                    filterModel={filterM}
                    onFilterModelChange={(m) => setFilterM(m)}
                    sortModel={sortM}
                    onSortModelChange={(s) => setSortM(s)}
                    density="compact"
                    rowSelectionModel={rowSelectionModel}
                    onRowSelectionModelChange={(model) => {
                        setRowSelectionModel(model)
                    }}
                    onCellClick={(_, event) => {
                        setMousePos({ left: event.clientX, top: event.clientY })
                    }}
                    slots={{ pagination: CustomPagination }}
                    slotProps={{
                        loadingOverlay: {
                            variant: "skeleton",
                            noRowsVariant: "skeleton",
                        },
                    }}
                />

                <Popover
                    open={rowSelectionModel.ids.size > 0 && mousePos !== null}
                    anchorReference="anchorPosition"
                    anchorPosition={mousePos ?? { top: 0, left: 0 }}
                    onClose={() => setMousePos(null)}
                    anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
                    transformOrigin={{ vertical: "top", horizontal: "center" }}
                    slotProps={{ paper: { sx: { p: 1, display: "flex", flexDirection: "column", gap: 1, minWidth: 180 } } }}
                >
                    <Button size="small" variant="outlined" startIcon={<MapIcon />}
                        onClick={() => {
                            const loc = interfaces.find((i) => rowSelectionModel.ids.has(i.id))?.location
                            if (loc) router.push(`/map?x=${loc.x}&z=${loc.z}&dim=${loc.dimension}`)
                        }}
                    >查看区块地图</Button>
                    <Button size="small" variant="outlined" startIcon={<ListAltIcon />}
                        onClick={() => {
                            // const selectedIds = Array.from(rowSelectionModel.ids)
                            // const names = selectedIds
                            //     .map((id) => interfaces.find((i) => i.id === id)?.name)
                            //     .filter(Boolean)
                            // const nameParam = names.length > 0 ? `&name=${encodeURIComponent(names[0])}` : ""
                            // router.push(`/ae/patterns?${searchParams.toString()}${nameParam}`)
                        }}
                    >查看样板列表</Button>
                    <Button size="small" color="inherit" startIcon={<CloseIcon />} onClick={() => {
                        setRowSelectionModel({ type: "include", ids: new Set() })
                        setMousePos(null)
                    }}>取消选择</Button>
                </Popover>
            </Paper>
            <Footer args={searchParams.toString()} />
        </RContainer>
    )
}
