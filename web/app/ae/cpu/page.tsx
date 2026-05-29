"use client"

import CustomPagination from "@/app/blocks/CustomPagination"
import { H2 } from "@/components/H2"
import { useAPI } from "@/data/api"
import RefreshIcon from "@mui/icons-material/Refresh"
import {
    Alert,
    Card,
    CardContent,
    Chip,
    CircularProgress,
    Container,
    FormControl,
    Grid,
    IconButton,
    InputLabel,
    LinearProgress,
    MenuItem,
    Paper,
    Select,
    Typography
} from "@mui/material"
import type { GridFilterModel, GridSortModel } from "@mui/x-data-grid"
import {
    DataGrid,
    GridColDef,
    gridExpandedSortedRowEntriesSelector,
} from "@mui/x-data-grid"
import { GridApiCommunity } from "@mui/x-data-grid/internals"
import type { AECPU } from "@shirokasoke/webapi-sdk"
import { useSearchParams } from "next/navigation"
import { useEffect, useRef, useState } from "react"
import { Footer } from "../Footer"

type AECPURow = AECPU & { id: string; storage: number }

function formatDuration(ms: number): string {
    if (ms < 1000) return `${ms.toFixed(0)}ms`
    const sec = ms / 1000
    if (sec < 60) return `${sec.toFixed(1)}s`
    const min = sec / 60
    if (min < 60) return `${min.toFixed(1)}m`
    const hr = min / 60
    return `${hr.toFixed(1)}h`
}

const BYTE_UNITS = ["B", "KB", "MB", "GB", "TB", "PB", "EB"]

function formatBytes(bytes: number): string {
    if (bytes === 0) return "0 B"
    const sign = bytes < 0 ? "-" : ""
    const abs = Math.abs(bytes)
    const exp = Math.min(Math.floor(Math.log10(abs) / 3), BYTE_UNITS.length - 1)
    const val = abs / Math.pow(1000, exp)
    return `${sign}${val.toFixed(2)} ${BYTE_UNITS[exp]}`
}

const columns: GridColDef<AECPURow>[] = [
    {
        field: "name",
        headerName: "名称",
        width: 200,
        filterable: true,
        valueGetter: (_value, row) => row.name || "-",
    },
    {
        field: "busy",
        headerName: "忙碌",
        width: 100,
        type: "boolean",
        filterable: true,
        renderCell: (params) => (
            <Chip
                label={params.row.busy ? "是" : "否"}
                color={params.row.busy ? "warning" : "success"}
                size="small"
            />
        ),
    },
    {
        field: "storage",
        headerName: "存储使用",
        width: 180,
        filterable: true,
        valueGetter: (_value, row) => row.storage,
        sortComparator: (v1: number, v2: number) => v1 - v2,
        renderCell: (params) => {
            const pct = params.row.storage * 100
            return (
                <div style={{ width: "100%", display: "flex", alignItems: "center", gap: 8, paddingRight: 8 }}>
                    <LinearProgress
                        variant="determinate"
                        value={pct}
                        color={pct > 90 ? "error" : pct > 70 ? "warning" : "primary"}
                        sx={{ flexGrow: 1, height: 6, borderRadius: 1 }}
                    />
                    <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: "nowrap" }}>
                        {pct.toFixed(0)}%
                    </Typography>
                </div>
            )
        },
    },
    {
        field: "availableStorage",
        headerName: "可用存储",
        width: 120,
        type: "number",
        filterable: true,
        valueFormatter: (value: number) => formatBytes(value),
    },
    {
        field: "usedStorage",
        headerName: "已用存储",
        width: 120,
        type: "number",
        filterable: true,
        valueFormatter: (value: number) => formatBytes(value),
    },
    {
        field: "coProcessors",
        headerName: "协同处理器",
        width: 120,
        type: "number",
        filterable: true,
    },
    {
        field: "remainingItemCount",
        headerName: "剩余物品",
        width: 110,
        type: "number",
        filterable: true,
    },
    {
        field: "startItemCount",
        headerName: "初始物品",
        width: 110,
        type: "number",
        filterable: true,
    },
    {
        field: "elapsedTime",
        headerName: "已运行时间",
        width: 130,
        type: "number",
        filterable: true,
        valueFormatter: (value: number) => formatDuration(value),
    },
    {
        field: "craftingAllowMode",
        headerName: "合成模式",
        width: 130,
        filterable: true,
    },
    {
        field: "tasksError",
        headerName: "任务错误",
        width: 200,
        filterable: true,
        valueGetter: (_value, row) => row.tasksError || "-",
    },
]

export default function AECPUPage() {
    const api = useAPI()
    const searchParams = useSearchParams()
    const [cpus, setCpus] = useState<AECPURow[]>([])
    const [error, setError] = useState<string | null>(null)
    const [displayRows, setDisplayRows] = useState<AECPURow[]>([])

    const [sortM, setSortM] = useState<GridSortModel>()
    const [filterM, setFilterM] = useState<GridFilterModel>()
    const apiRef = useRef<GridApiCommunity>(null)

    const [refreshSec, setRefreshSec] = useState<number>(0)

    const x = searchParams.get("x")
    const y = searchParams.get("y")
    const z = searchParams.get("z")
    const dimension = searchParams.get("dimension")

    const loadCPUs = () => {
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
        api.aeCPUs({ x: px, y: py, z: pz, dimension: dim })
            .then((data) => {
                const rows = data.map((c, i): AECPURow => ({
                    ...c,
                    id: String(i),
                    storage: c.availableStorage > 0 ? c.usedStorage / c.availableStorage : 0,
                }))
                setCpus(rows)
                setDisplayRows(rows)
            })
            .catch((e) => setError(e instanceof Error ? e.message : "加载 CPU 失败"))
    }

    useEffect(() => {
        loadCPUs()
    }, [api, x, y, z, dimension])

    useEffect(() => {
        if (refreshSec <= 0) return
        const interval = setInterval(() => {
            loadCPUs()
        }, refreshSec * 1000)
        return () => clearInterval(interval)
    }, [refreshSec, api, x, y, z, dimension])

    useEffect(() => {
        if (!apiRef.current) return
        const timer = setTimeout(() => {
            const entries = gridExpandedSortedRowEntriesSelector(apiRef)
            setDisplayRows(entries.map((e) => e.model as AECPURow))
        }, 200)
        return () => clearTimeout(timer)
    }, [filterM, sortM])

    const totalStorage = cpus.reduce((sum, c) => sum + c.availableStorage, 0)
    const usedStorage = cpus.reduce((sum, c) => sum + c.usedStorage, 0)
    const busyCount = cpus.filter((c) => c.busy).length
    const totalCoProcessors = cpus.reduce((sum, c) => sum + c.coProcessors, 0)

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
            <H2>AE CPU 信息</H2>
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
                                    {cpus.length}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    CPU 总数
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color={busyCount > 0 ? "warning" : "success"}>
                                    {busyCount}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    忙碌中
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="info">
                                    {cpus.filter((c) => !c.busy).length}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    空闲
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="secondary">
                                    {totalCoProcessors}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    协同处理器
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="primary">
                                    {formatBytes(usedStorage)}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    已用存储
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4">
                                    {formatBytes(totalStorage)}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    总存储
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                </Grid>
            )}

            <Grid container spacing={2} sx={{ mb: 2, alignItems: "center", justifyContent: "center" }}>
                <Grid>
                    <IconButton size="small" onClick={loadCPUs} title="手动刷新">
                        <RefreshIcon />
                    </IconButton>
                </Grid>
                <Grid>
                    <FormControl size="small" sx={{ minWidth: 140 }}>
                        <InputLabel>自动刷新</InputLabel>
                        <Select
                            value={refreshSec}
                            label="自动刷新"
                            onChange={(e) => setRefreshSec(Number(e.target.value))}
                        >
                            <MenuItem value={0}>关闭</MenuItem>
                            <MenuItem value={5}>5 秒</MenuItem>
                            <MenuItem value={10}>10 秒</MenuItem>
                            <MenuItem value={30}>30 秒</MenuItem>
                            <MenuItem value={60}>1 分钟</MenuItem>
                            <MenuItem value={300}>5 分钟</MenuItem>
                        </Select>
                    </FormControl>
                </Grid>
                <Grid>
                    <Typography variant="body2" color="primary">
                        共 {displayRows.length} / {cpus.length} 个 CPU
                    </Typography>
                </Grid>
            </Grid>

            <Paper sx={{ height: "70vh", width: "100%", mb: 2 }}>
                <DataGrid
                    apiRef={apiRef}
                    rows={cpus}
                    columns={columns}
                    loading={cpus.length == 0}
                    getRowId={(row) => row.id}
                    pageSizeOptions={[25, 50, 100]}
                    filterModel={filterM}
                    onFilterModelChange={(m) => setFilterM(m)}
                    sortModel={sortM}
                    onSortModelChange={(s) => setSortM(s)}
                    density="compact"
                    slots={{ pagination: CustomPagination }}
                    slotProps={{
                        loadingOverlay: {
                            variant: "skeleton",
                            noRowsVariant: "skeleton",
                        },
                    }} />
            </Paper>
            <Footer searchParams={searchParams} />
        </Container>
    )
}
