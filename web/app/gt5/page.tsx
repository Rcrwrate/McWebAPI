"use client"

import { H2 } from "@/components/H2"
import type { MultiSegment } from "@/components/MultiProgressBar"
import { MultiProgressBar, MultiProgressLegend } from "@/components/MultiProgressBar"
import { RContainer } from "@/components/RContainer"
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
    CircularProgress,
    FormControl,
    Grid,
    IconButton,
    InputLabel,
    MenuItem,
    Select,
    Stack,
    Typography
} from "@mui/material"
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

function machineStatus(m: GT5MachineInfo): MachineStatus {
    if (m.state.isActive) return "running"
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
    return "idle"
}

function statusSegments(counts: Record<MachineStatus, number>): MultiSegment[] {
    return (["running", "maintenance", "error", "idle"] as MachineStatus[]).map((s) => ({
        value: counts[s],
        color: STATUS_COLORS[s],
        label: STATUS_LABELS[s],
    }))
}

function countByStatus(machines: GT5MachineInfo[]): Record<MachineStatus, number> {
    const counts: Record<MachineStatus, number> = { running: 0, maintenance: 0, error: 0, idle: 0 }
    for (const m of machines) counts[machineStatus(m)]++
    return counts
}

export default function GT5Page() {
    const api = useAPI()
    const [machines, setMachines] = useState<SavedGT5Machine[]>([])
    const [liveMachines, setLiveMachines] = useState<GT5MachineInfo[]>([])
    const [loaded, setLoaded] = useState(false)
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
            setLiveMachines(job.machines ?? [])
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
                <IconButton onClick={undefined} title="手动刷新">
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
            <Box sx={{ m: 1, px: 2, py: 1 }}>
                {!loaded ? (
                    <Box sx={{ textAlign: "center", py: 3 }}>
                        <CircularProgress />
                    </Box>
                ) : machines.length === 0 ? (
                    <Typography color="warning" sx={{ textAlign: "center", py: 4 }}>
                        暂无设备数据
                    </Typography>
                ) : <>
                    {liveMachines.length > 0 && (
                        <MultiProgressLegend
                            segments={statusSegments(countByStatus(liveMachines))}
                            sx={{ justifyContent: "center", mb: 2 }}
                        />
                    )}
                    <Grid container spacing={2}>
                        {counts.map(({ type, count }) => {
                            const meta = TYPE_META[type]
                            const Icon = meta.icon
                            const live = liveMachines.filter(m => m.machineType === type)
                            const sc = countByStatus(live)
                            return (
                                <Grid key={type} size={{ xs: 6, sm: 4, md: 4 }}>
                                    <Card sx={{ textAlign: "center" }}>
                                        <MultiProgressBar segments={statusSegments(sc)} />
                                        <CardContent>
                                            <Icon color={meta.color} sx={{ fontSize: 40 }} />
                                            <Typography variant="h4">
                                                {count}
                                            </Typography>
                                            <Typography variant="body2" color="textPrimary">
                                                {meta.label}
                                            </Typography>
                                            {live.length > 0 && (
                                                <Typography variant="caption" color="textSecondary" component="div" sx={{ mt: 0.5 }}>
                                                    运行 {sc.running} · 维护 {sc.maintenance} · 错误 {sc.error} · 空闲 {sc.idle}
                                                </Typography>
                                            )}
                                        </CardContent>
                                    </Card>
                                </Grid>
                            )
                        })}
                    </Grid>
                </>}
            </Box>
        </RContainer>
    )
}
