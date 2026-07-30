"use client"

import { H2 } from "@/components/H2"
import type { MultiSegment } from "@/components/MultiProgressBar"
import { MultiProgressBar, MultiProgressLegend } from "@/components/MultiProgressBar"
import GTInfoCard from "@/components/Panel/GTinfo"
import { RContainer } from "@/components/RContainer"
import TinyProcess from "@/components/TinyProcess"
import { useAPI } from "@/data/api"
import BoltIcon from "@mui/icons-material/Bolt"
import DeleteIcon from "@mui/icons-material/Delete"
import HelpIcon from "@mui/icons-material/Help"
import Inventory2Icon from "@mui/icons-material/Inventory2"
import PrecisionManufacturingIcon from "@mui/icons-material/PrecisionManufacturing"
import RadarIcon from "@mui/icons-material/Radar"
import RefreshIcon from "@mui/icons-material/Refresh"
import SettingsIcon from "@mui/icons-material/Settings"
import {
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    CircularProgress,
    FormControl,
    Grid,
    IconButton,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Stack,
    Typography
} from "@mui/material"
import { keyframes } from "@mui/system"
import { DataGridPro as DataGrid, type GridColDef, type GridRowSelectionModel } from "@mui/x-data-grid-pro"
import type { GT5MachineInfo, GT5MachineType } from "@shirokasoke/webapi-sdk"
import { default as LinkC } from "next/link"
import { enqueueSnackbar } from "notistack"
import { useEffect, useRef, useState } from "react"
import type { SavedGT5Machine } from "./data"
import { clearSavedMachines, getSavedMachines } from "./data"

interface TypeMeta {
    label: string
    icon: React.ElementType
    color: "primary" | "success" | "warning" | "info" | "error" | "secondary"
}

const TYPE_META: Record<GT5MachineType, TypeMeta> = {
    MULTIBLOCK: { label: "多方块结构", icon: PrecisionManufacturingIcon, color: "primary" },
    SINGLE: { label: "单方块机器", icon: SettingsIcon, color: "info" },
    GENERATOR: { label: "单方块发电机", icon: BoltIcon, color: "warning" },
    HATCH: { label: "仓室", icon: Inventory2Icon, color: "secondary" },
    UNKNOWN: { label: "未知", icon: HelpIcon, color: "error" },
}

const TYPE_ORDER: GT5MachineType[] = ["MULTIBLOCK", "SINGLE", "GENERATOR", "HATCH", "UNKNOWN"]
type MachineStatus = "running" | "maintenance" | "error" | "idle"
const STATUS_COLORS: Record<MachineStatus, string> = {
    running: "success.main",
    maintenance: "warning.main",
    error: "error.main",
    idle: "text.disabled",
}
const STATUS_LABELS: Record<MachineStatus, string> = {
    running: "运行中",
    maintenance: "需要维护",
    error: "错误",
    idle: "空闲",
}
const STATUS_CHIP_COLOR: Record<MachineStatus, "success" | "warning" | "error" | "default"> = {
    running: "success",
    maintenance: "warning",
    error: "error",
    idle: "default",
}

// CRT 扫描线故障效果：静态扫描线 + 快速闪烁 + 下移的高亮扫描束
const crtFlicker = keyframes`
  0%, 100% { opacity: 0; }
  50% { opacity: 1; }
`
// 束高 50%：-100% 时束底恰在卡片顶边(消失)，200% 时束顶恰在卡片底边(消失)
// 两端均在可视区外，循环重置不可见 → 无缝
const crtBeam = keyframes`
  0% { transform: translateY(-100%); }
  100% { transform: translateY(200%); }
`

type GT5Row = GT5MachineInfo & { id: string; status: MachineStatus; }

const MACHINE_COLUMNS: GridColDef<GT5Row>[] = [
    {
        field: "id",
        headerName: "坐标",
        width: 180,
        filterable: true,
    },
    {
        field: "localName",
        headerName: "机器名称",
        flex: 1,
        minWidth: 160,
        filterable: true,
        valueGetter: (_v, row) => row.localName || "-",
    },
    {
        field: "machineType",
        headerName: "类型",
        width: 120,
        filterable: true,
        type: "singleSelect",
        valueOptions: Object.keys(TYPE_META),
        renderCell: (params) => TYPE_META[params.row.machineType]?.label ?? params.row.machineType,
    },
    {
        field: "status",
        headerName: "状态",
        width: 120,
        filterable: true,
        type: "singleSelect",
        valueOptions: ["running", "maintenance", "error", "idle"] as MachineStatus[],
        renderCell: (params) => (
            <Chip size="small" variant={params.row.status === "idle" ? "outlined" : "filled"}
                color={STATUS_CHIP_COLOR[params.row.status]}
                label={STATUS_LABELS[params.row.status]}
            />
        ),
    },
    {
        field: "euPct",
        headerName: "储能",
        width: 160,
        type: "number",
        filterable: true,
        valueGetter: (_v, row) => {
            if (row.state.storedEU != 0 && row.state.euCapacity != 0) { return (row.state.storedEU / row.state.euCapacity) * 100 }
            switch (row.machineType) {
                case "MULTIBLOCK":
                    return row.multi.maxEnergy > 0 ? (row.multi.storedEnergy / row.multi.maxEnergy) * 100 : null
                case "GENERATOR":
                    return row.generator.maxEUStore > 0 ? (row.generator.storedEU / row.generator.maxEUStore) * 100 : null
                default:
                    return null
            }
        },
        renderCell: (params) => {
            if (params.value == null) return <Typography variant="caption" color="textSecondary">无</Typography>
            return <TinyProcess value={params.value} color={params.value < 10 ? "error" : params.value < 30 ? "warning" : "primary"} />
        }
    },
    {
        field: "progress",
        headerName: "进度",
        width: 150,
        type: "number",
        filterable: true,
        valueGetter: (_v, row) => {
            switch (row.machineType) {
                case "MULTIBLOCK":
                    return row.multi.maxProgressTime > 0 ? (row.multi.progressTime / row.multi.maxProgressTime) * 100 : null
                case "SINGLE":
                    return row.single.maxProgressTime > 0 ? (row.single.progressTime / row.single.maxProgressTime) * 100 : null
                default:
                    return null
            }
        },
        renderCell: (params) => {
            if (params.value == null) return <Typography variant="caption" color="textSecondary">空闲</Typography>
            return <TinyProcess value={params.value} color="success" />
        },
    },
    {
        field: "shutDownReason",
        headerName: "停机原因",
        width: 200,
        filterable: true,
        valueGetter: (_v, row) =>
            row.state.wasShutdown ? (row.state.lastShutDownReason?.displayString || "未知") : "-",
    },
]

function machineStatus(m: GT5MachineInfo): MachineStatus {
    if (m.state.wasShutdown) {
        if (m.state.lastShutDownReason?.wasCritical) return "error"
        return "maintenance"
    }
    if (m.machineType === "MULTIBLOCK" && m.multi) {
        const mt = m.multi.maintenance
        if (!mt.wrench || !mt.screwdriver || !mt.softMallet || !mt.hardHammer || !mt.solderingTool || !mt.crowbar) {
            return "maintenance"
        }
    }
    if (m.state.isActive) return "running"
    // isAllowedToWork=false 但无停机原因 = 用户用软锤手动关闭，非故障，按空闲处理
    return "idle"
}

function statusSegments(counts: Record<MachineStatus, number>): MultiSegment[] {
    return (["running", "maintenance", "error", "idle"] as MachineStatus[]).map((s) => ({
        value: counts[s],
        color: STATUS_COLORS[s],
        label: STATUS_LABELS[s],
    }))
}

function countByStatus(machines: GT5Row[]): Record<MachineStatus, number> {
    const counts: Record<MachineStatus, number> = { running: 0, maintenance: 0, error: 0, idle: 0 }
    for (const m of machines) counts[m.status]++
    return counts
}

export default function GT5Page() {
    const api = useAPI()
    const [machines, setMachines] = useState<SavedGT5Machine[]>([])
    const [liveMachines, setLiveMachines] = useState<GT5Row[]>([])
    const [loaded, setLoaded] = useState(false)
    const [rowSelection, setRowSelection] = useState<GridRowSelectionModel>({ type: "include", ids: new Set() })
    useEffect(() => {
        setMachines(getSavedMachines())
        setLoaded(true)
    }, [])

    const counts = TYPE_ORDER.map((t) => ({
        type: t,
        count: machines.filter((m) => m.machineType === t).length,
    }))
    const [refreshSec, setRefreshSec] = useState<number>(5)
    const batchID = useRef("")
    const fetchMachine = async () => {
        if (machines.length > 0 && api) {
            let job
            if (batchID.current) {
                await api.rerunGT5Batch({ id: batchID.current })
                job = await api.waitForGT5BatchJob(batchID.current)
            } else {
                const req = machines.filter(i => ["MULTIBLOCK", "SINGLE", "GENERATOR"].includes(i.machineType)).map(i => ({ x: i.x, y: i.y, z: i.z, dimension: i.dimension }))
                const t = await api.submitGT5Batch(req)
                batchID.current = t.id
                job = await api.waitForGT5BatchJob(t.id)
            }
            setLiveMachines(job.machines?.map((i): GT5Row => ({
                ...i,
                id: `(${i.x}, ${i.y}, ${i.z}) [${i.dimension}]`,
                status: machineStatus(i)
            })) ?? [])
        }
    }

    useEffect(() => {
        if (refreshSec > 0) {
            let id = setInterval(() => fetchMachine().catch((e) => enqueueSnackbar(`${e}`, { variant: "error" })), refreshSec * 1000)
            return () => clearInterval(id)
        }
    }, [machines.length, refreshSec])

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
            <H2>GT5 机器管理</H2>
            <Stack spacing={2} direction="row" useFlexGap sx={{ p: 3, flexWrap: 'wrap', justifyContent: 'center' }}>
                <Button variant="contained" color="primary" startIcon={<RadarIcon />}
                    LinkComponent={LinkC} href={`/gt5/scan`}>扫描GT设备</Button>
                <IconButton onClick={fetchMachine} title="手动刷新">
                    <RefreshIcon />
                </IconButton>
                <FormControl size="small" sx={{ minWidth: 140 }}>
                    <InputLabel>自动刷新</InputLabel>
                    <Select value={refreshSec} label="自动刷新" onChange={(e) => setRefreshSec(Number(e.target.value))}>
                        <MenuItem value={0}>关闭</MenuItem>
                        <MenuItem value={1}>1 秒</MenuItem>
                        <MenuItem value={5}>5 秒</MenuItem>
                        <MenuItem value={10}>10 秒</MenuItem>
                        <MenuItem value={30}>30 秒</MenuItem>
                        <MenuItem value={60}>1 分钟</MenuItem>
                        <MenuItem value={300}>5 分钟</MenuItem>
                    </Select>
                </FormControl>
                <Button variant="outlined" color="error" disabled={machines.length === 0}
                    startIcon={<DeleteIcon />} onClick={() => {
                        clearSavedMachines()
                        setMachines(getSavedMachines())
                        enqueueSnackbar("已清空所有设备数据", { variant: "success" })
                    }}>清空数据</Button>
            </Stack>
            <Box sx={{ m: { xs: 0, sm: 1 }, px: { xs: 0, sm: 2 }, py: 1 }}>
                {!loaded ? (
                    <Box sx={{ textAlign: "center", py: 3 }}>
                        <CircularProgress />
                    </Box>
                ) : machines.length === 0 ? (
                    <Typography color="warning" sx={{ textAlign: "center", py: 4 }}>
                        暂无设备数据
                    </Typography>
                ) : <>
                    <MultiProgressLegend
                        segments={[
                            { value: 1, color: STATUS_COLORS.running, label: STATUS_LABELS.running },
                            { value: 0, color: STATUS_COLORS.maintenance, label: STATUS_LABELS.maintenance },
                            { value: 0, color: STATUS_COLORS.error, label: STATUS_LABELS.error },
                            { value: 0, color: STATUS_COLORS.idle, label: STATUS_LABELS.idle },
                        ]}
                        sx={{ justifyContent: "center", mb: 2 }}
                    />
                    <Grid container spacing={2}>
                        {counts.map(({ type, count }) => {
                            const meta = TYPE_META[type]
                            const Icon = meta.icon
                            const live = liveMachines.filter(m => m.machineType === type)
                            const sc = countByStatus(live)
                            return (
                                <Grid key={type} size={{ xs: 6, sm: 4, md: 4 }}>
                                    <Card sx={{
                                        textAlign: "center",
                                        transition: "transform 0.2s ease, box-shadow 0.2s ease, border-color 0.3s ease",
                                        "&:hover": {
                                            transform: "translateY(-4px)",
                                            boxShadow: 6,
                                        },
                                        ...(sc.error > 0 && {
                                            position: "relative",
                                            overflow: "hidden",
                                            borderColor: (e) => e.palette.error.main,
                                            // 静态扫描线 + 闪烁
                                            "&::before": {
                                                content: '""',
                                                position: "absolute",
                                                inset: 0,
                                                pointerEvents: "none",
                                                zIndex: 1,
                                                backgroundImage: `repeating-linear-gradient(
                                                    0deg,
                                                    rgba(211,47,47,0.20) 0px,
                                                    rgba(211,47,47,0.20) 1px,
                                                    transparent 1px,
                                                    transparent 3px
                                                )`,
                                                animation: `${crtFlicker} 2s infinite`,
                                            },
                                            // 向下扫掠的高亮扫描束
                                            "&::after": {
                                                content: '""',
                                                position: "absolute",
                                                top: 0,
                                                left: 0,
                                                right: 0,
                                                height: "50%",
                                                pointerEvents: "none",
                                                zIndex: 2,
                                                background: `linear-gradient(
                                                    to bottom,
                                                    transparent 0%,
                                                    transparent 35%,
                                                    rgba(211,47,47,0.4) 50%,
                                                    transparent 65%,
                                                    transparent 100%
                                                )`,
                                                animation: `${crtBeam} 2.6s linear infinite`,
                                            },
                                        }),
                                    }}>
                                        <MultiProgressBar segments={statusSegments(sc)} />
                                        <CardContent>
                                            <Icon color={meta.color} sx={{
                                                fontSize: 40,
                                                transition: "transform 0.3s ease",
                                                "&:hover": { transform: "scale(1.15)" },
                                            }} />
                                            <Typography variant="h4">
                                                {count}
                                            </Typography>
                                            <Typography variant="body2" color="textPrimary">
                                                {meta.label}
                                            </Typography>
                                            {live.length > 0 && (
                                                <Typography variant="caption" color={sc.error ? "error" : "textSecondary"} component="div" sx={{ mt: 0.5 }}>
                                                    运行 {sc.running} · 维护 {sc.maintenance} · 错误 {sc.error} · 空闲 {sc.idle}
                                                </Typography>
                                            )}
                                        </CardContent>
                                    </Card>
                                </Grid>
                            )
                        })}
                    </Grid>
                    <Paper sx={{ mt: 3, height: "100vh", width: 1 }}>
                        <DataGrid
                            rows={liveMachines}
                            columns={MACHINE_COLUMNS.map(i => { i.align = "center"; i.headerAlign = "center"; return i })}
                            getRowId={(row) => row.id}
                            loading={liveMachines.length == 0}
                            checkboxSelection
                            pagination
                            rowSelectionModel={rowSelection}
                            onRowSelectionModelChange={setRowSelection}
                            pageSizeOptions={[10, 25, 50, 100]}
                            density="compact"
                            showToolbar
                            initialState={{
                                pagination: { paginationModel: { pageSize: 25 } },
                            }}
                            slotProps={{
                                loadingOverlay: {
                                    variant: "skeleton",
                                    noRowsVariant: "skeleton",
                                },
                            }} />
                    </Paper>
                    <Grid container spacing={{ xs: 1.5, md: 2 }} sx={{ mt: 1 }}>
                        {Array.from(rowSelection.ids).map((id) => {
                            const machine = liveMachines.find(m => m.id === id)
                            return machine ? <Grid key={id} size={{ xs: 12, lg: 6 }}>
                                <GTInfoCard machine={machine} />
                            </Grid> : null
                        })}
                    </Grid>
                </>}
            </Box>
        </RContainer>
    )
}
