"use client"

import CustomPagination from "@/app/blocks/CustomPagination"
import { H2 } from "@/components/H2"
import { useAPI } from "@/data/api"
import AddToQueueIcon from "@mui/icons-material/AddToQueue"
import CloseIcon from "@mui/icons-material/Close"
import MapIcon from "@mui/icons-material/Map"
import {
    Alert,
    Button,
    Card,
    CardContent,
    Chip,
    CircularProgress,
    Container,
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
import type { AENode } from "@shirokasoke/webapi-sdk"
import { useSearchParams } from "next/navigation"
import { enqueueSnackbar } from "notistack"
import { useEffect, useRef, useState } from "react"
import { Footer } from "../Footer"
import { getSavedCoords, saveCoords } from "../coords"


type AENodeRow = AENode & { id: string }


const columns: GridColDef<AENodeRow>[] = [
    {
        field: "active",
        headerName: "活跃",
        width: 100,
        type: "boolean",
        filterable: true,
        renderCell: (params) => (
            <Chip
                label={params.row.active ? "是" : "否"}
                color={params.row.active ? "success" : "default"}
                size="small"
            />
        ),
    },
    {
        field: "meetsChannel",
        headerName: "满足频道",
        width: 110,
        type: "boolean",
        filterable: true,
        renderCell: (params) => (
            <Chip
                label={params.row.meetsChannel ? "是" : "否"}
                color={params.row.meetsChannel ? "success" : "error"}
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
        field: "machineClass",
        headerName: "机器类",
        width: 280,
        filterable: true,
        valueGetter: (_value, row) => {
            const info = row.machineClass
            if (!info) return "-"
            return info.location?.split("/").pop() || info.package || "未知"
        },
    },
    {
        field: "isPart",
        headerName: "部件",
        width: 90,
        type: "boolean",
        filterable: true,
        renderCell: (params) => (
            <Chip
                label={params.row.isPart ? "是" : "否"}
                color={params.row.isPart ? "primary" : "default"}
                size="small"
            />
        ),
    },
    {
        field: "isIActionHost",
        headerName: "操作主机",
        width: 100,
        type: "boolean",
        filterable: true,
        renderCell: (params) => (
            <Chip
                label={params.row.isIActionHost ? "是" : "否"}
                color={params.row.isIActionHost ? "info" : "default"}
                size="small"
            />
        ),
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
        field: "idlePowerUsage",
        headerName: "待机功耗",
        width: 110,
        type: "number",
        filterable: true,
        valueFormatter: (value: number) => `${value.toFixed(2)} AE/t`,
    },
    {
        field: "flags",
        headerName: "标志",
        width: 300,
        filterable: true,
        valueGetter: (_value, row) => row.flags.join(", ") || "-",
    },
]

export default function AENodePage() {
    const api = useAPI()
    const searchParams = useSearchParams()
    const [nodes, setNodes] = useState<AENodeRow[]>([])
    const [error, setError] = useState<string | null>(null)
    const [displayRows, setDisplayRows] = useState<AENodeRow[]>([])

    const [sortM, setSortM] = useState<GridSortModel>()
    const [filterM, setFilterM] = useState<GridFilterModel>()
    const apiRef = useRef<GridApiCommunity>(null)

    const [rowSelectionModel, setRowSelectionModel] = useState<GridRowSelectionModel>({ type: "include", ids: new Set() })
    const [mousePos, setMousePos] = useState<{ left: number; top: number } | null>(null)

    const x = searchParams.get("x")
    const y = searchParams.get("y")
    const z = searchParams.get("z")
    const dimension = searchParams.get("dimension")

    useEffect(() => {
        if (!api || !x || !y || !z || !dimension) return
        const px = parseInt(x)
        const py = parseInt(y)
        const pz = parseInt(z)
        const dim = parseInt(dimension)
        if (isNaN(px) || isNaN(py) || isNaN(pz) || isNaN(dim)) {
            setError("坐标参数无效")
            return
        }
        setError(null)
        api.aeNodes({ x: px, y: py, z: pz, dimension: dim })
            .then((data) => {
                const rows = data.map((n, i): AENodeRow => ({ ...n, id: String(i) }))
                setNodes(rows)
                setDisplayRows(rows)
            })
            .catch((e) => setError(e instanceof Error ? e.message : "加载节点失败"))

    }, [api, x, y, z, dimension])

    useEffect(() => {
        if (!apiRef.current) return
        const timer = setTimeout(() => {
            const entries = gridExpandedSortedRowEntriesSelector(apiRef)
            setDisplayRows(entries.map((e) => e.model as AENodeRow))
        }, 200)
        return () => clearTimeout(timer)
    }, [filterM, sortM])


    const handleAddToAEList = async () => {
        if (!api) return
        const saved = getSavedCoords()
        const updated = [...saved]
        let added = 0
        let skipped = 0

        const selected = nodes.filter((n) => rowSelectionModel.ids.has(n.id))
        for (const row of selected) {
            const loc = row.location
            if (!loc) { skipped++; continue }
            const exists = updated.some(
                (c) => c.x === loc.x && c.y === loc.y && c.z === loc.z && c.dimension === loc.dimension
            )
            if (exists) { skipped++; continue }
            try {
                const detail = await api.getBlock({ x: loc.x, y: loc.y, z: loc.z, dim: loc.dimension })
                updated.push({
                    x: loc.x,
                    y: loc.y,
                    z: loc.z,
                    dimension: loc.dimension,
                    name: detail.block.localizedName || detail.block.registryName,
                })
                added++
            } catch {
                skipped++
            }
        }
        saveCoords(updated)
        enqueueSnackbar(`已添加 ${added} 个节点到 AE 列表，跳过 ${skipped} 个`, { variant: "success" })
        setMousePos(null)
    }

    if (!api) {
        return (
            <Container sx={{ pt: 10, textAlign: "center" }}>
                <CircularProgress size={80} />
                <Typography sx={{ mt: 2 }}>正在初始化 API...</Typography>
            </Container>
        )
    }

    return (
        <Container sx={{ p: 1 }}>
            <H2>AE 节点列表</H2>
            {error && (
                <Alert severity="error" sx={{ mb: 2 }}>
                    {error}
                </Alert>
            )}

            {!error && (
                <Grid container spacing={2} sx={{ mb: 2 }}>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="primary">
                                    {nodes.length}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    总节点数
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="success">
                                    {nodes.filter((n) => n.active).length}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    活跃节点
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="info">
                                    {nodes.filter((n) => n.meetsChannel).length}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    满足频道
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="secondary">
                                    {nodes.filter((n) => n.isPart).length}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    部件节点
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="warning">
                                    {nodes.filter((n) => n.isIActionHost).length}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    操作主机
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4">
                                    {nodes.reduce((sum, n) => sum + (n.idlePowerUsage || 0), 0).toFixed(0)}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    总待机功耗 (AE/t)
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                </Grid>
            )}

            <Grid container spacing={2} sx={{ mb: 2, alignItems: "center", justifyContent: "center" }}>
                <Grid>
                    <TextField
                        label="搜索节点"
                        placeholder="输入任意内容快速筛选"
                        onChange={(e) => apiRef.current?.setQuickFilterValues([e.target.value])}
                        size="small"
                        sx={{ minWidth: 280 }}
                    />
                </Grid>
                <Grid>
                    <Typography variant="body2" color="primary">
                        共 {displayRows.length} / {nodes.length} 个节点
                    </Typography>
                </Grid>
            </Grid>

            <Paper sx={{ height: "70vh", width: "100%", mb: 2, position: "relative" }}>
                <DataGrid
                    apiRef={apiRef}
                    rows={nodes}
                    columns={columns}
                    loading={nodes.length == 0}
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
                    <Button size="small" variant="contained" startIcon={<AddToQueueIcon />} onClick={handleAddToAEList}>添加到AE节点列表</Button>
                    <Button size="small" variant="outlined" startIcon={<MapIcon />}
                        onClick={() => enqueueSnackbar("查看区块地图功能待实现", { variant: "info" })}
                    >查看区块地图</Button>
                    <Button size="small" color="inherit" startIcon={<CloseIcon />} onClick={() => {
                        setRowSelectionModel({ type: "include", ids: new Set() })
                        setMousePos(null)
                    }}>取消选择</Button>
                </Popover>
            </Paper>
            <Footer searchParams={searchParams} />
        </Container>
    )
}
