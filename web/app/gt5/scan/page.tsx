"use client"

import { H2 } from "@/components/H2"
import { RContainer } from "@/components/RContainer"
import { useAPI } from "@/data/api"
import KeyboardBackspaceIcon from "@mui/icons-material/KeyboardBackspace"
import RadarIcon from "@mui/icons-material/Radar"
import SaveIcon from '@mui/icons-material/Save'
import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    CircularProgress,
    LinearProgress,
    Paper,
    Stack,
    Step,
    StepContent,
    StepLabel,
    Stepper,
    Typography,
    useTheme,
} from "@mui/material"
import type { GT5ScanMachine } from "@shirokasoke/webapi-sdk"
import { default as LinkC } from "next/link"
import { enqueueSnackbar } from "notistack"
import { useCallback, useEffect, useRef, useState } from "react"
import { saveMachines } from "../data"

interface ChunkTarget {
    chunkX: number
    chunkZ: number
    dim: number
}

interface LogEntry {
    time: string
    message: string
    level: "info" | "success" | "warning" | "error"
}

const STEPS = [
    { label: "获取已加载区块", desc: "调用 /chunks 接口收集所有已加载的区块" },
    { label: "提交扫描任务", desc: "并发提交每个区块的扫描任务到 /gt5/scan" },
    { label: "扫描区块中", desc: "轮询所有扫描任务状态，直至全部完成" },
    { label: "扫描完成", desc: "汇总结果" },
]
const POLL_INTERVAL = 300
const SUBMIT_CONCURRENCY = 8
const POLL_CONCURRENCY = 16

async function parallel<T>(tasks: (() => Promise<T>)[], concurrency: number): Promise<T[]> {
    const results: T[] = new Array(tasks.length)
    let next = 0
    const workers = Array.from({ length: Math.min(concurrency, tasks.length) }, async () => {
        while (next < tasks.length) {
            const i = next++
            results[i] = await tasks[i]()
        }
    })
    await Promise.all(workers)
    return results
}

function nowTime(): string {
    const d = new Date()
    const pad = (n: number) => String(n).padStart(2, "0")
    return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${String(d.getMilliseconds()).padStart(3, "0")}`
}

export default function GT5ScanPage() {
    const theme = useTheme()
    const logColor: Record<LogEntry["level"], string> = {
        info: theme.palette.text.disabled,
        success: theme.palette.success.main,
        warning: theme.palette.warning.main,
        error: theme.palette.error.main,
    }

    const api = useAPI()
    const [scanning, setScanning] = useState(false)
    const [activeStep, setActiveStep] = useState(-1)
    const [error, setError] = useState<string | null>(null)

    const [totalChunks, setTotalChunks] = useState(0)
    const [scannedChunks, setScannedChunks] = useState(0)
    const [failedChunks, setFailedChunks] = useState(0)
    const [machines, setMachines] = useState<(GT5ScanMachine & { dimension: number })[]>([])

    const [logs, setLogs] = useState<Record<number, LogEntry[]>>({})
    const logEndRefs = useRef<(HTMLDivElement | null)[]>([])

    const addLog = useCallback((step: number, message: string, level: LogEntry["level"] = "info") => {
        setLogs((prev) => ({
            ...prev,
            [step]: [...(prev[step] ?? []), { time: nowTime(), message, level }].slice(-500),
        }))
    }, [])

    // 自动滚动到底部
    useEffect(() => {
        const el = logEndRefs.current[activeStep]
        if (el) el.scrollTop = el.scrollHeight
    }, [logs, activeStep])

    const reset = () => {
        setActiveStep(-1)
        setError(null)
        setTotalChunks(0)
        setScannedChunks(0)
        setFailedChunks(0)
        setMachines([])
        setLogs({})
    }

    const handleScan = async () => {
        if (!api) return
        setScanning(true)
        reset()

        try {
            setActiveStep(0)
            addLog(0, "开始获取已加载区块列表...", "info")
            const t0 = performance.now()
            const chunksData = await api.getChunks()
            const targets: ChunkTarget[] = []
            for (const [dimStr, dimData] of Object.entries(chunksData)) {
                const dim = parseInt(dimStr, 10)
                const loaded = dimData.chunks.filter((c) => c.isChunkLoaded)
                addLog(0, `维度 ${dim} (${dimData.name}): 共 ${dimData.chunks.length} 区块, 已加载 ${loaded.length}`, "info")
                for (const c of loaded) {
                    targets.push({ chunkX: c.chunkX, chunkZ: c.chunkZ, dim })
                }
            }
            setTotalChunks(targets.length)
            addLog(0, `获取完成, 耗时 ${(performance.now() - t0).toFixed(0)}ms, 共 ${targets.length} 个已加载区块`, "success")
            if (targets.length === 0) {
                addLog(0, "没有已加载的区块, 终止扫描", "warning")
                enqueueSnackbar("没有已加载的区块", { variant: "warning" })
                setActiveStep(-1)
                setScanning(false)
                return
            }
            setActiveStep(1)
            addLog(1, `开始并发提交 ${targets.length} 个扫描任务 (并发 ${SUBMIT_CONCURRENCY})...`, "info")
            const t1 = performance.now()
            let submitFail = 0
            const submissions = (
                await parallel(
                    targets.map((t, i) => async () => {
                        try {
                            const s = await api.submitGT5ChunkScan({ chunkX: t.chunkX, chunkZ: t.chunkZ, dim: t.dim })
                            addLog(1, `[${i + 1}/${targets.length}] 已提交 dim=${t.dim} chunk=(${t.chunkX},${t.chunkZ}) → jobId=${s.id} (total=${s.total})`, "info")
                            return s
                        } catch (e) {
                            submitFail++
                            const msg = e instanceof Error ? e.message : String(e)
                            addLog(1, `[${i + 1}/${targets.length}] 提交失败 dim=${t.dim} chunk=(${t.chunkX},${t.chunkZ}): ${msg}`, "error")
                            return null
                        }
                    }),
                    SUBMIT_CONCURRENCY
                )
            ).filter((s): s is NonNullable<typeof s> => s !== null)
            const jobIds = submissions.map((s) => s.id)
            addLog(1, `提交完成, 耗时 ${(performance.now() - t1).toFixed(0)}ms, 成功 ${jobIds.length} / 失败 ${submitFail}`, submitFail > 0 ? "warning" : "success")
            setFailedChunks(submitFail)

            if (jobIds.length === 0) {
                addLog(1, "所有任务提交均失败, 无法继续扫描", "error")
                enqueueSnackbar("所有任务提交失败", { variant: "error" })
                setActiveStep(3)
                addLog(3, `扫描结束: 提交全部失败, 共 0 台 GT5 设备`, "error")
                setScanning(false)
                return
            }
            setActiveStep(2)
            addLog(2, `开始轮询 ${jobIds.length} 个任务状态 (并发 ${POLL_CONCURRENCY}, 间隔 ${POLL_INTERVAL}ms)...`, "info")
            const t2 = performance.now()
            const done = new Set<number>()
            const failed = new Set<number>()
            const collectedMachines: (GT5ScanMachine & { dimension: number })[] = []
            let round = 0
            while (done.size + failed.size < jobIds.length) {
                round++
                const pending = jobIds
                    .map((_, i) => i)
                    .filter((i) => !done.has(i) && !failed.has(i))
                    .slice(0, POLL_CONCURRENCY)
                await Promise.all(
                    pending.map(async (i) => {
                        try {
                            const job = await api.getGT5ScanJob({ id: jobIds[i] })
                            if (job.status === "completed") {
                                done.add(i)
                                const jobMachines = (job.result?.machines ?? []).map((m) => ({ ...m, dimension: job.dimension }))
                                collectedMachines.push(...jobMachines)
                                addLog(2, `完成 dim=${job.dimension} chunk=(${job.chunkX},${job.chunkZ}) 设备=${jobMachines.length} 耗时=${job.durationMs ?? "-"}ms`, jobMachines.length > 0 ? "success" : "info")
                            }
                        } catch (e) {
                            failed.add(i)
                            const msg = e instanceof Error ? e.message : String(e)
                            addLog(2, `轮询失败 jobId=${jobIds[i]}: ${msg}`, "error")
                        }
                    })
                )
                setScannedChunks(done.size)
                setFailedChunks(submitFail + failed.size)
                setMachines([...collectedMachines])
                if (done.size + failed.size < jobIds.length) {
                    await new Promise((r) => setTimeout(r, POLL_INTERVAL))
                }
            }
            addLog(2, `全部轮询结束, 耗时 ${(performance.now() - t2).toFixed(0)}ms, 成功 ${done.size} / 失败 ${failed.size}`, failed.size > 0 ? "warning" : "success")
            setActiveStep(3)
            addLog(3, `扫描完成: 共 ${targets.length} 个区块, 成功扫描 ${done.size} / 提交失败 ${submitFail} / 轮询失败 ${failed.size}`, "success")
            enqueueSnackbar(`扫描完成`, { variant: "success" })
        } catch (e) {
            const msg = e instanceof Error ? e.message : "扫描失败"
            setError(msg)
            addLog(activeStep, `错误: ${msg}`, "error")
            enqueueSnackbar("扫描失败", { variant: "error" })
        } finally {
            setScanning(false)
        }
    }

    if (!api) {
        return (
            <RContainer sx={{ pt: 10, textAlign: "center" }}>
                <CircularProgress size={80} />
                <Typography sx={{ mt: 2 }}>正在初始化 API...</Typography>
            </RContainer>
        )
    }

    const processed = scannedChunks + failedChunks
    const progress = totalChunks > 0 ? (processed / totalChunks) * 100 : 0

    return (
        <RContainer>
            <H2>GT5 区块扫描</H2>
            {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
            <Stack spacing={2} direction="row" useFlexGap sx={{ p: 3, flexWrap: 'wrap', justifyContent: 'center' }}>
                <Button variant="contained" size="large" color="primary" disabled={scanning} onClick={handleScan}
                    startIcon={scanning ? <CircularProgress size={20} color="inherit" /> : <RadarIcon />}>
                    {scanning ? "扫描中..." : "扫描已加载区域的所有GT设备"}
                </Button>
                <Button variant="contained" size="large" color="success" disabled={machines.length == 0} onClick={() => {
                    const r = saveMachines(machines)
                    enqueueSnackbar(`已保存 ${r.saved} 台设备数据，跳过 ${r.skipped} 台`, { variant: "success" })
                }} startIcon={<SaveIcon />}>
                    保存设备数据
                </Button>
                <Button size="large" variant="contained" color="warning" startIcon={<KeyboardBackspaceIcon />}
                    LinkComponent={LinkC} href={`/gt5`}>返回</Button>
            </Stack>
            <Card elevation={6} sx={{ mb: 3 }}>
                <CardContent>
                    <Stepper activeStep={activeStep} orientation="vertical">
                        {STEPS.map((step, index) => {
                            const stepLogs = logs[index] ?? []
                            return <Step key={step.label}>
                                <StepLabel>
                                    <Box>
                                        <Typography variant="subtitle1" component="span">
                                            {step.label}
                                        </Typography>
                                        <Typography variant="caption" color="textSecondary" sx={{ ml: 1 }}>
                                            {step.desc}
                                        </Typography>
                                    </Box>
                                </StepLabel>
                                <StepContent>
                                    {index === 2 && (
                                        <Box sx={{ mb: 2 }}>
                                            <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
                                                <Typography variant="body2" color="text.secondary">
                                                    已扫描 {scannedChunks} / {totalChunks} 个区块{failedChunks > 0 && ` (失败 ${failedChunks})`}
                                                </Typography>
                                                <Typography variant="body2" color="text.secondary">
                                                    已发现 {machines.length} 台设备
                                                </Typography>
                                            </Box>
                                            <LinearProgress variant="determinate" value={progress} />
                                        </Box>
                                    )}
                                    {index === 3 && (
                                        <Box sx={{ mb: 2, textAlign: "center" }}>
                                            <Typography variant="h6" color="success" gutterBottom>
                                                扫描完成
                                            </Typography>
                                            <Typography color="textSecondary">
                                                共扫描 {scannedChunks} 个区块，发现 {machines.length} 台 GT5 设备
                                                {failedChunks > 0 && `（失败 ${failedChunks} 个）`}
                                            </Typography>
                                            <Typography color="info">
                                                {machines.filter(i => i.machineType == "MULTIBLOCK").length}个多方块结构
                                            </Typography>
                                            <Typography color="info">
                                                {machines.filter(i => i.machineType == "SINGLE").length}个单方块结构
                                            </Typography>
                                            <Typography color="info">
                                                {machines.filter(i => i.machineType == "GENERATOR").length}个单方块发电机
                                            </Typography>
                                            <Typography color="info">
                                                {machines.filter(i => i.machineType == "HATCH").length}个仓室
                                            </Typography>
                                            <Typography color="error">
                                                {machines.filter(i => i.machineType == "UNKNOWN").length}个UNKNOWN
                                            </Typography>
                                        </Box>
                                    )}
                                    {stepLogs.length > 0 && (
                                        <Paper ref={(el) => { logEndRefs.current[index] = el }} variant="outlined" sx={{
                                            maxHeight: 260,
                                            overflowY: "auto",
                                            p: 1,
                                            fontFamily: "monospace",
                                            fontSize: 12,
                                            lineHeight: 1.6,
                                            bgcolor: "background.default",
                                        }}>
                                            {stepLogs.map((log, i) => (
                                                <Box key={i} sx={{ color: logColor[log.level], whiteSpace: "pre-wrap", wordBreak: "break-all" }}>
                                                    [{log.time}] {log.message}
                                                </Box>
                                            ))}
                                        </Paper>
                                    )}
                                </StepContent>
                            </Step>
                        })}
                    </Stepper>
                </CardContent>
            </Card>
        </RContainer>
    )
}
