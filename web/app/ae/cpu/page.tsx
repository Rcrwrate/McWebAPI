"use client"

import CustomPagination from "@/app/blocks/CustomPagination"
import { H2 } from "@/components/H2"
import MCToolitip from "@/components/MCTooltip"
import Percent from "@/components/PerCent"
import { useAPI } from "@/data/api"
import { formatBytes, formatDuration } from "@/data/format"
import useCoords from "@/data/useCoords"
import CancelScheduleSendIcon from '@mui/icons-material/CancelScheduleSend'
import MoreVertIcon from "@mui/icons-material/MoreVert"
import RefreshIcon from "@mui/icons-material/Refresh"
import {
    Alert,
    Box,
    Button,
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
    Menu,
    MenuItem,
    Paper,
    Select,
    Typography
} from "@mui/material"
import type { GridFilterModel, GridRowSelectionModel, GridSortModel } from "@mui/x-data-grid"
import {
    DataGrid,
    GridColDef,
    gridExpandedSortedRowEntriesSelector,
} from "@mui/x-data-grid"
import { GridApiCommunity } from "@mui/x-data-grid/internals"
import type { AECPU } from "@shirokasoke/webapi-sdk"
import { useSearchParams } from "next/navigation"
import { enqueueSnackbar } from "notistack"
import { useEffect, useRef, useState } from "react"
import { Footer } from "../Footer"
import ItemIcon from "../ItemIcon"

type AECPURow = AECPU & { id: number; storage: number }

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
                    <Typography variant="caption" sx={{ whiteSpace: "nowrap" }}>
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
    const [x, y, z, dimension] = useCoords(searchParams)

    const [cpus, setCpus] = useState<AECPURow[]>([])
    const [error, setError] = useState<string | null>(null)
    const [displayRows, setDisplayRows] = useState<AECPURow[]>([])

    const [sortM, setSortM] = useState<GridSortModel>()
    const [filterM, setFilterM] = useState<GridFilterModel>()
    const apiRef = useRef<GridApiCommunity>(null)
    const [rowSelectionModel, setRowSelectionModel] = useState<GridRowSelectionModel>({ type: "include", ids: new Set() })

    const [refreshSec, setRefreshSec] = useState<number>(5)
    const [menuAnchor, setMenuAnchor] = useState<{ el: HTMLElement | null; idx: number }>({ el: null, idx: -1 })

    const loadCPUs = () => {
        if (!api || !x) return
        setError(null)
        api.aeCPUs({ x, y, z, dimension })
            .then((data) => {
                const rows = data.map((c, i): AECPURow => ({
                    ...c,
                    id: i,
                    storage: c.availableStorage > 0 ? c.usedStorage / c.availableStorage : 0,
                }))
                setCpus(rows)
                setDisplayRows(rows)
            })
            .catch((e) => setError(e instanceof Error ? e.message : "加载 CPU 失败"))
    }

    useEffect(() => {
        loadCPUs()
        if (refreshSec <= 0) return
        const interval = setInterval(() => {
            loadCPUs()
        }, refreshSec * 1000)
        return () => clearInterval(interval)
    }, [refreshSec, api != undefined, x, y, z, dimension])

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
                    <Grid size={{ xs: 12, sm: 12, md: 4 }}>
                        <Percent percent={busyCount / cpus.length * 100} title="CPU" subtitle={`${busyCount}忙碌中/${cpus.length - busyCount}空闲`} />
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <LinearProgress variant="determinate" color="secondary" sx={{ height: 6 }} value={100} />
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="secondary">
                                    {totalCoProcessors}
                                </Typography>
                                <Typography variant="body2">
                                    协同处理器
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 3 }}>
                        <Card>
                            <LinearProgress variant="determinate" color="primary" sx={{ height: 6 }} value={100} />
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="primary">
                                    {formatBytes(usedStorage)}
                                </Typography>
                                <Typography variant="body2">
                                    已用存储
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 3 }}>
                        <Card>
                            <LinearProgress variant="determinate" color="inherit" sx={{ height: 6 }} value={100} />
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4">
                                    {formatBytes(totalStorage)}
                                </Typography>
                                <Typography variant="body2">
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
                    rowSelectionModel={rowSelectionModel}
                    onRowSelectionModelChange={(model) => {
                        setRowSelectionModel(model)
                    }}
                    slotProps={{
                        loadingOverlay: {
                            variant: "skeleton",
                            noRowsVariant: "skeleton",
                        },
                    }} />
            </Paper>
            {(() => {
                const selectedCPU = cpus.find((c) => rowSelectionModel.ids.has(c.id))
                if (!selectedCPU) return null
                return (
                    <Box sx={{ mb: 2 }}>
                        <Button fullWidth variant="outlined" color="error" disabled={!selectedCPU.busy}
                            startIcon={<CancelScheduleSendIcon />}
                            onClick={async () => {
                                if (!x) return
                                const r = await api.aeCancel(
                                    { x, y, z, dimension },
                                    {
                                        id: selectedCPU.id,
                                        ...((selectedCPU?.name && selectedCPU.name.length > 0) ? { name: selectedCPU.name } : {}),
                                    }
                                )
                                if (r.wasBusy) {
                                    enqueueSnackbar("已尝试取消合成任务", { variant: "info" })
                                } else {
                                    enqueueSnackbar("CPU无合成任务", { variant: "warning" })
                                }
                                setRowSelectionModel({ type: "include", ids: new Set() })
                                loadCPUs()
                            }}>
                            {selectedCPU.busy
                                ? `取消 ${selectedCPU.name || "CPU"} 正在进行的合成任务 (${selectedCPU?.tasks ? selectedCPU.tasks.length + "个" : "无法获取数量"} )`
                                : `${selectedCPU.name || "CPU"} 无正在进行的合成任务`}
                        </Button>
                        <Box sx={{ display: "flex", flexDirection: "column", gap: 1, pt: 1 }}>
                            {selectedCPU?.tasks?.map((task, idx) => (
                                <Paper key={idx} sx={{ p: 1.5, display: "flex", alignItems: "center", gap: 2, flexWrap: "wrap" }}>
                                    <Typography variant="caption" sx={{ minWidth: 28 }}>
                                        #{idx + 1}
                                    </Typography>
                                    <Box sx={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 2, flexWrap: "wrap" }}>
                                        <Box sx={{ display: "flex", alignItems: "center", gap: 0.5, flexWrap: "wrap" }}>
                                            {task.inputs.map((input, i) => (
                                                <MCToolitip k={`${idx}-${i}-${input.id}`} item={input}>
                                                    <Box sx={{ width: 48, height: 48 }}>
                                                        <ItemIcon api={api} item={input} />
                                                    </Box>
                                                </MCToolitip>
                                            ))}
                                        </Box>
                                        <Typography variant="body2">→</Typography>
                                        <Box sx={{ display: "flex", alignItems: "center", gap: 0.5, flexWrap: "wrap" }}>
                                            {task.outputs.map((output, i) => (
                                                <MCToolitip k={`${idx}-${i}-${output.id}`} item={output}>
                                                    <Box sx={{ width: 48, height: 48 }}>
                                                        <ItemIcon api={api} item={output} />
                                                    </Box>
                                                </MCToolitip>
                                            ))}
                                        </Box>
                                    </Box>
                                    <Typography variant="caption" color="primary" sx={{ minWidth: 50, textAlign: "right" }}>
                                        剩余 {task.remaining}x
                                    </Typography>
                                    <IconButton size="small" onClick={(e) => setMenuAnchor({ el: e.currentTarget, idx })}>
                                        <MoreVertIcon />
                                    </IconButton>
                                    <Menu
                                        anchorEl={menuAnchor.el}
                                        open={menuAnchor.idx === idx}
                                        onClose={() => setMenuAnchor({ el: null, idx: -1 })}
                                        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
                                        transformOrigin={{ vertical: "top", horizontal: "right" }}
                                    >
                                        <MenuItem onClick={() => setMenuAnchor({ el: null, idx: -1 })}>查看区块地图</MenuItem>
                                        <MenuItem onClick={() => setMenuAnchor({ el: null, idx: -1 })}>查看ME接口样板</MenuItem>
                                    </Menu>
                                </Paper>
                            ))}
                        </Box>
                    </Box>
                )
            })()}
            <Footer args={searchParams.toString()} />
        </Container>
    )
}
