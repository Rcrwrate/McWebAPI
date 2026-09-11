"use client"

import { H2 } from "@/components/H2"
import { RContainer } from "@/components/RContainer"
import { useAPI } from "@/data/api"
import CloudUploadIcon from "@mui/icons-material/CloudUpload"
import DataUsageIcon from "@mui/icons-material/DataUsage"
import ImageIcon from "@mui/icons-material/Image"
import PersonIcon from "@mui/icons-material/Person"
import PublicIcon from "@mui/icons-material/Public"
import RefreshIcon from "@mui/icons-material/Refresh"
import SendIcon from "@mui/icons-material/Send"
import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    CardHeader,
    CircularProgress,
    Divider,
    FormControl,
    Grid,
    IconButton,
    InputLabel,
    LinearProgress,
    Link,
    MenuItem,
    Select,
    Stack,
    TextField,
    Typography,
} from "@mui/material"
import type {
    PlayerPrintResult,
    TPSInfo,
    WorldPrintJobResult,
    WorldPrintSubmitResult,
} from "@shirokasoke/webapi-sdk"
import { enqueueSnackbar } from "notistack"
import { useEffect, useMemo, useRef, useState } from "react"

type Facing = "north" | "south" | "east" | "west"

const FACINGS: Array<{ value: Facing; label: string }> = [
    { value: "south", label: "南 (south)" },
    { value: "north", label: "北 (north)" },
    { value: "east", label: "东 (east)" },
    { value: "west", label: "西 (west)" },
]

/** 请求体上限，与服务端 PlayerPrintHandler/WorldPrintHandler 一致 */
const MAX_FILE_BYTES = 32 * 1024 * 1024
/** 图片单边像素上限，与服务端 PrintUtils.MAX_IMAGE_DIMENSION 一致 */
const MAX_IMAGE_EDGE = 4096
/** 单次世界铺设方块上限，与服务端 WorldPrintHandler.MAX_BLOCKS 一致 */
const MAX_BLOCKS = 65536
/** 世界打印任务轮询间隔 */
const POLL_INTERVAL = 500
/** 玩家名（Java 版用户名）只含字母/数字/下划线，用于从实体列表中预筛玩家 */
const PLAYER_NAME_PATTERN = /^[A-Za-z0-9_]{1,16}$/
/** 确认玩家身份时的并发请求数 */
const PROBE_CONCURRENCY = 8

/** 从 /entities 筛选出的在线玩家 */
interface PlayerEntry {
    name: string
    entityId: number
    dimension: number
    posX: number
    posY: number
    posZ: number
    worldName: string
}

function formatBytes(n: number): string {
    if (n < 1024) return `${n} B`
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(2)} KiB`
    return `${(n / 1024 / 1024).toFixed(2)} MiB`
}

/** 保留 %d 占位符，转义其余 %，避免服务端 String.format 抛异常 */
function escapeFormat(input: string): string {
    return input.replace(/%(?![d%])/g, "%%")
}

function errMsg(e: unknown, fallback: string): string {
    return e instanceof Error ? e.message : fallback
}

export default function ThreeDPrintPage() {
    const api = useAPI()

    // region 图片与打印件参数
    const [file, setFile] = useState<File | null>(null)
    const [preview, setPreview] = useState<string | null>(null)
    const [imageDim, setImageDim] = useState<{ w: number; h: number } | null>(null)
    const [dragOver, setDragOver] = useState(false)
    const [label, setLabel] = useState("")
    const [tooltip, setTooltip] = useState("")
    const inputRef = useRef<HTMLInputElement>(null)
    const previewRef = useRef<string | null>(null)

    // region NBT 大小预估
    const [estimate, setEstimate] = useState<number | null>(null)
    const [estimating, setEstimating] = useState(false)

    // region 投放到世界
    const [worlds, setWorlds] = useState<Record<string, TPSInfo>>({})
    const [x, setX] = useState("")
    const [y, setY] = useState("")
    const [z, setZ] = useState("")
    const [dim, setDim] = useState("0")
    const [facing, setFacing] = useState<Facing>("south")
    const [worldSubmitting, setWorldSubmitting] = useState(false)
    const [submitted, setSubmitted] = useState<WorldPrintSubmitResult | null>(null)
    const [worldJobId, setWorldJobId] = useState<string | null>(null)
    const [worldJob, setWorldJob] = useState<WorldPrintJobResult | null>(null)
    const [worldError, setWorldError] = useState<string | null>(null)

    // region 投放到玩家背包
    const [players, setPlayers] = useState<PlayerEntry[]>([])
    const [playersLoading, setPlayersLoading] = useState(false)
    const [playersError, setPlayersError] = useState<string | null>(null)
    const [selectedPlayerId, setSelectedPlayerId] = useState<number | null>(null)
    const [delivering, setDelivering] = useState(false)
    const [deliverResult, setDeliverResult] = useState<PlayerPrintResult | null>(null)

    useEffect(() => {
        if (!api) return
        api.getTPS()
            .then((data) => setWorlds(data))
            .catch(() => setWorlds({}))
    }, [api != undefined])

    /**
     * 获取在线玩家：/entities 汇总所有维度实体 → 按用户名格式预筛
     * （生物为本地化名或 item.xxx 键）→ 逐个用 /entity?id= 确认存在 Player 字段。
     */
    const loadPlayers = async () => {
        if (!api) return
        setPlayersLoading(true)
        setPlayersError(null)
        try {
            const data = await api.getEntities()
            const candidates: PlayerEntry[] = []
            for (const [dimId, info] of Object.entries(data)) {
                for (const { Entity } of info.loadedEntityList) {
                    if (!PLAYER_NAME_PATTERN.test(Entity.name)) continue
                    candidates.push({
                        name: Entity.name,
                        entityId: Entity.entityId,
                        dimension: Entity.dimension,
                        posX: Entity.posX,
                        posY: Entity.posY,
                        posZ: Entity.posZ,
                        worldName: info.WorldName || dimId,
                    })
                }
            }

            const found: PlayerEntry[] = []
            let cursor = 0
            const worker = async () => {
                while (cursor < candidates.length) {
                    const c = candidates[cursor++]
                    try {
                        const detail = await api.getEntity({ id: c.entityId })
                        if (detail.Player) found.push(c)
                    } catch {
                        // 实体在查询期间被移除等情况，忽略
                    }
                }
            }
            await Promise.all(
                Array.from({ length: Math.min(PROBE_CONCURRENCY, candidates.length) }, worker)
            )

            found.sort((a, b) => a.name.localeCompare(b.name))
            setPlayers(found)
            setSelectedPlayerId((prev) =>
                prev != null && found.some((p) => p.entityId === prev) ? prev : (found[0]?.entityId ?? null)
            )
        } catch (e) {
            setPlayersError(errMsg(e, "获取在线玩家失败"))
            setPlayers([])
            setSelectedPlayerId(null)
        } finally {
            setPlayersLoading(false)
        }
    }

    useEffect(() => {
        if (api) void loadPlayers()
    }, [api != undefined])

    // 卸载时释放预览地址
    useEffect(() => () => {
        if (previewRef.current) URL.revokeObjectURL(previewRef.current)
    }, [])

    // 图片尺寸 → 打印件方块数（每 16×16 像素一个方块）
    const blocks = useMemo(() => imageDim
        ? { cols: Math.ceil(imageDim.w / 16), rows: Math.ceil(imageDim.h / 16) }
        : null, [imageDim])

    // region 事件处理
    const printParams = () => ({
        label: label ? escapeFormat(label) : undefined,
        tooltip: tooltip ? escapeFormat(tooltip) : undefined,
    })

    const estimateSize = async (f: File) => {
        if (!api) return
        setEstimating(true)
        try {
            const r = await api.getPrintSize(f, printParams())
            setEstimate(r.size)
        } catch (e) {
            setEstimate(null)
            enqueueSnackbar(`预估大小失败: ${errMsg(e, "请求失败")}`, { variant: "error" })
        } finally {
            setEstimating(false)
        }
    }

    const handleFile = (f: File | null | undefined) => {
        if (!f) return
        if (!f.type.startsWith("image/")) {
            enqueueSnackbar("请选择图片文件 (png/jpg/bmp 等)", { variant: "warning" })
            return
        }
        if (f.size > MAX_FILE_BYTES) {
            enqueueSnackbar(`图片过大: ${formatBytes(f.size)}，上限 ${formatBytes(MAX_FILE_BYTES)}`, { variant: "error" })
            return
        }

        if (previewRef.current) URL.revokeObjectURL(previewRef.current)
        const url = URL.createObjectURL(f)
        previewRef.current = url
        setPreview(url)
        setFile(f)
        setEstimate(null)
        setImageDim(null)
        setSubmitted(null)
        setWorldJob(null)
        setWorldJobId(null)
        setDeliverResult(null)

        const img = new window.Image()
        img.onload = () => setImageDim({ w: img.naturalWidth, h: img.naturalHeight })
        img.onerror = () => setImageDim(null)
        img.src = url

        void estimateSize(f)
    }

    const handleWorldPrint = async () => {
        if (!api) return
        if (!file) {
            enqueueSnackbar("请先选择图片", { variant: "warning" })
            return
        }
        const px = parseInt(x, 10)
        const py = parseInt(y, 10)
        const pz = parseInt(z, 10)
        const pdim = parseInt(dim, 10)
        if ([px, py, pz, pdim].some((v) => Number.isNaN(v))) {
            enqueueSnackbar("请填写完整的坐标和维度", { variant: "warning" })
            return
        }
        if (blocks) {
            if (imageDim && (imageDim.w > MAX_IMAGE_EDGE || imageDim.h > MAX_IMAGE_EDGE)) {
                enqueueSnackbar(`图片单边不能超过 ${MAX_IMAGE_EDGE} 像素`, { variant: "error" })
                return
            }
            if (blocks.cols * blocks.rows > MAX_BLOCKS) {
                enqueueSnackbar(`需要 ${blocks.cols * blocks.rows} 个方块，超过上限 ${MAX_BLOCKS}`, { variant: "error" })
                return
            }
            if (blocks.rows > py) {
                enqueueSnackbar(`图片高 ${blocks.rows} 格，超过 y 坐标 ${py}（向下最多放 ${py} 格）`, { variant: "error" })
                return
            }
        }

        setWorldSubmitting(true)
        setWorldError(null)
        setSubmitted(null)
        setWorldJob(null)
        setWorldJobId(null)
        try {
            const r = await api.submitWorldPrint(
                { x: px, y: py, z: pz, dim: pdim, facing, ...printParams() },
                file
            )
            setSubmitted(r)
            setWorldJobId(r.id)
            enqueueSnackbar(
                `任务 #${r.id} 已提交：${r.total} 个打印件${r.skipped > 0 ? `，跳过 ${r.skipped} 个透明块` : ""}`,
                { variant: "success" }
            )
        } catch (e) {
            const msg = errMsg(e, "提交失败")
            setWorldError(msg)
            enqueueSnackbar(msg, { variant: "error" })
        } finally {
            setWorldSubmitting(false)
        }
    }

    const handlePlayerPrint = async () => {
        if (!api) return
        if (!file) {
            enqueueSnackbar("请先选择图片", { variant: "warning" })
            return
        }
        const target = players.find((p) => p.entityId === selectedPlayerId)
        if (!target) {
            enqueueSnackbar("请选择在线玩家", { variant: "warning" })
            return
        }

        setDelivering(true)
        setDeliverResult(null)
        try {
            const r = await api.printToPlayer({ id: target.entityId, ...printParams() }, file)
            setDeliverResult(r)
            enqueueSnackbar(
                `${target.name}：共 ${r.total} 件，放入背包 ${r.added} 件${r.dropped > 0 ? `，掉落 ${r.dropped} 件` : ""}`,
                { variant: r.dropped > 0 ? "warning" : "success" }
            )
        } catch (e) {
            enqueueSnackbar(errMsg(e, "投递失败"), { variant: "error" })
        } finally {
            setDelivering(false)
        }
    }

    // 轮询世界打印任务进度
    useEffect(() => {
        if (!api || !worldJobId) return
        let stopped = false
        let timer: ReturnType<typeof setTimeout> | undefined
        const tick = async () => {
            try {
                const job = await api.getWorldPrintJob({ id: worldJobId })
                if (stopped) return
                setWorldJob(job)
                if (job.status !== "completed") {
                    timer = setTimeout(tick, POLL_INTERVAL)
                } else {
                    enqueueSnackbar(
                        `打印任务 #${worldJobId} 完成：成功 ${job.success} / 失败 ${job.failed}`,
                        { variant: job.failed > 0 ? "warning" : "success" }
                    )
                }
            } catch (e) {
                if (!stopped) setWorldError(errMsg(e, "查询任务进度失败"))
            }
        }
        void tick()
        return () => {
            stopped = true
            if (timer) clearTimeout(timer)
        }
    }, [api, worldJobId])

    if (!api) {
        return (
            <RContainer sx={{ pt: 10, textAlign: "center" }}>
                <CircularProgress size={80} />
                <Typography sx={{ mt: 2 }}>正在初始化 API...</Typography>
            </RContainer>
        )
    }

    const worldsLoaded = Object.keys(worlds).length > 0
    const progress = worldJob && worldJob.total > 0
        ? (worldJob.completed / worldJob.total) * 100
        : 0
    const blockCount = blocks ? blocks.cols * blocks.rows : 0

    return (
        <RContainer>
            <title>3D 打印</title>
            <H2><PublicIcon /> 3D 打印</H2>

            <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 7 }}>
                    <Card elevation={6} sx={{ height: "100%" }}>
                        <CardHeader
                            title={<><ImageIcon /> 上传图片</>}
                            subheader={`支持 png/jpg/bmp 等，单边不超过 ${MAX_IMAGE_EDGE} 像素、体积不超过 ${formatBytes(MAX_FILE_BYTES)}`}
                        />
                        <CardContent>
                            <Box
                                onClick={() => inputRef.current?.click()}
                                onDragOver={(e) => { e.preventDefault(); setDragOver(true) }}
                                onDragLeave={() => setDragOver(false)}
                                onDrop={(e) => {
                                    e.preventDefault()
                                    setDragOver(false)
                                    handleFile(e.dataTransfer.files?.[0])
                                }}
                                sx={{
                                    border: "2px dashed",
                                    borderColor: dragOver ? "primary.main" : "divider",
                                    borderRadius: 2,
                                    p: 2,
                                    textAlign: "center",
                                    cursor: "pointer",
                                    bgcolor: dragOver ? "action.hover" : "transparent",
                                    transition: "all 0.2s",
                                    "&:hover": { borderColor: "primary.main", bgcolor: "action.hover" },
                                }}
                            >
                                {preview
                                    ? <Box
                                        component="img"
                                        src={preview}
                                        alt="预览"
                                        sx={{
                                            maxWidth: "100%",
                                            maxHeight: 260,
                                            borderRadius: 1,
                                            imageRendering: "pixelated",
                                            display: "block",
                                            mx: "auto",
                                        }}
                                    />
                                    : <>
                                        <CloudUploadIcon sx={{ fontSize: 56, color: "text.disabled" }} />
                                        <Typography color="text.secondary">
                                            点击选择图片，或将图片拖拽到此处
                                        </Typography>
                                    </>}
                            </Box>
                            <input
                                ref={inputRef}
                                type="file"
                                accept="image/*"
                                hidden
                                onChange={(e) => {
                                    handleFile(e.target.files?.[0])
                                    e.target.value = ""
                                }}
                            />

                            {file && (
                                <Box sx={{ mt: 2 }}>
                                    <Divider sx={{ mb: 1 }} />
                                    <Typography variant="body2" color="text.secondary" noWrap title={file.name}>
                                        文件：{file.name}（{formatBytes(file.size)}）
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        分辨率：{imageDim ? `${imageDim.w} × ${imageDim.h} 像素` : "读取中..."}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary">
                                        打印件：{blocks ? `${blocks.cols} × ${blocks.rows} = ${blockCount} 个方块` : "-"}
                                    </Typography>
                                    <Stack direction="row" spacing={1} sx={{ mt: 1, alignItems: "center", flexWrap: "wrap" }} useFlexGap>
                                        <Typography variant="body2" color="text.secondary">
                                            NBT 预估大小：
                                        </Typography>
                                        {estimating
                                            ? <Stack direction="row" spacing={0.5} sx={{ alignItems: "center" }}>
                                                <CircularProgress size={14} />
                                                <Typography variant="body2" color="text.secondary">计算中...</Typography>
                                            </Stack>
                                            : <Typography variant="body2" color="primary" sx={{ fontWeight: "bold" }}>
                                                {estimate == null ? "未知" : `${estimate} B（${formatBytes(estimate)}）`}
                                            </Typography>}
                                        <Button
                                            size="small"
                                            startIcon={<RefreshIcon />}
                                            disabled={estimating}
                                            onClick={() => estimateSize(file)}
                                        >
                                            重新预估
                                        </Button>
                                    </Stack>
                                </Box>
                            )}
                        </CardContent>
                    </Card>
                </Grid>

                <Grid size={{ xs: 12, md: 5 }}>
                    <Card elevation={6} sx={{ height: "100%" }}>
                        <CardHeader
                            title={<><DataUsageIcon /> 打印件参数</>}
                            subheader="留空则使用服务端默认值，%d 会被替换为打印件的块坐标"
                        />
                        <CardContent>
                            <Stack spacing={2}>
                                <TextField
                                    label="名称 (label)"
                                    fullWidth
                                    value={label}
                                    placeholder="3d-print %d,%d"
                                    onChange={(e) => setLabel(e.target.value)}
                                    slotProps={{ htmlInput: { maxLength: 24 } }}
                                    helperText="最长 24 字符"
                                />
                                <TextField
                                    label="提示 (tooltip)"
                                    fullWidth
                                    multiline
                                    minRows={2}
                                    value={tooltip}
                                    placeholder="created by love.shirokasoke.webapi"
                                    onChange={(e) => setTooltip(e.target.value)}
                                    slotProps={{ htmlInput: { maxLength: 128 } }}
                                    helperText="最长 128 字符"
                                />
                                <Alert severity="info" variant="outlined">
                                    预估大小只计算 NBT 字节数，不会真正打印；修改参数后可点击「重新预估」。
                                </Alert>
                                <Alert severity="warning" variant="outlined">
                                    如果大小过大会影响服务器MSPT/TPS，
                                    如果一定要这么做可以使用我制作的补丁以降低影响：
                                    <Link href="https://github.com/Rcrwrate/AggressivePatch">AggressivePatch</Link>
                                </Alert>
                            </Stack>
                        </CardContent>
                    </Card>
                </Grid>

                <Grid size={{ xs: 12, md: 7 }}>
                    <Card elevation={6} sx={{ height: "100%" }}>
                        <CardHeader
                            title={<><PublicIcon /> 投放到世界</>}
                            subheader="(x, y, z) 为图片左上角方块的位置，图片向右、向下延展"
                        />
                        <CardContent>
                            <Grid container spacing={2} sx={{ alignItems: "center" }}>
                                <Grid size={4}>
                                    <TextField label="X" type="number" value={x} onChange={(e) => setX(e.target.value)} fullWidth />
                                </Grid>
                                <Grid size={4}>
                                    <TextField label="Y" type="number" value={y} onChange={(e) => setY(e.target.value)} fullWidth />
                                </Grid>
                                <Grid size={4}>
                                    <TextField label="Z" type="number" value={z} onChange={(e) => setZ(e.target.value)} fullWidth />
                                </Grid>
                                <Grid size={{ xs: 6, sm: 4 }}>
                                    {worldsLoaded
                                        ? <FormControl fullWidth>
                                            <InputLabel>维度</InputLabel>
                                            <Select value={dim} label="维度" onChange={(e) => setDim(e.target.value)}>
                                                {Object.entries(worlds).map(([dimId, info]) => (
                                                    <MenuItem key={dimId} value={dimId}>
                                                        {info.WorldName || `维度 ${dimId}`} (TPS: {info.TPS.toFixed(1)})
                                                    </MenuItem>
                                                ))}
                                            </Select>
                                        </FormControl>
                                        : <TextField
                                            label="维度"
                                            type="number"
                                            value={dim}
                                            onChange={(e) => setDim(e.target.value)}
                                            fullWidth
                                            helperText="世界列表加载失败，请手动填写"
                                        />}
                                </Grid>
                                <Grid size={{ xs: 6, sm: 4 }}>
                                    <FormControl fullWidth>
                                        <InputLabel>朝向</InputLabel>
                                        <Select value={facing} label="朝向" onChange={(e) => setFacing(e.target.value as Facing)}>
                                            {FACINGS.map((f) => (
                                                <MenuItem key={f.value} value={f.value}>{f.label}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                </Grid>
                                <Grid size={{ xs: 12, sm: 4 }}>
                                    <Button
                                        fullWidth
                                        size="large"
                                        variant="contained"
                                        disabled={worldSubmitting}
                                        startIcon={worldSubmitting ? <CircularProgress size={20} color="inherit" /> : <SendIcon />}
                                        onClick={handleWorldPrint}
                                    >
                                        {worldSubmitting ? "提交中..." : "开始打印"}
                                    </Button>
                                </Grid>
                                {blocks && (
                                    <Grid size={12}>
                                        <Typography variant="body2" color="text.secondary">
                                            将铺设 {blocks.cols} × {blocks.rows} = {blockCount} 个方块，
                                            需保证 y 坐标不小于 {blocks.rows}（方块数为 0 的透明块会跳过）
                                        </Typography>
                                    </Grid>
                                )}
                            </Grid>

                            {worldError && <Alert severity="error" sx={{ mt: 2 }}>{worldError}</Alert>}

                            {submitted && (
                                <Alert severity="success" sx={{ mt: 2 }}>
                                    任务 #{submitted.id}：共 {submitted.total} 个打印件
                                    {submitted.skipped > 0 && `，跳过 ${submitted.skipped} 个透明块`}
                                </Alert>
                            )}

                            {worldJob && (
                                <Box sx={{ mt: 2 }}>
                                    <Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
                                        <Typography variant="body2" color="text.secondary">
                                            进度 {worldJob.completed} / {worldJob.total}
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            成功 {worldJob.success} / 失败 {worldJob.failed}
                                            {worldJob.status === "completed"
                                                ? `（耗时 ${((worldJob.durationMs ?? 0) / 1000).toFixed(1)}s）`
                                                : worldJob.status === "running" ? "（执行中）" : "（排队中）"}
                                        </Typography>
                                    </Box>
                                    <LinearProgress
                                        variant="determinate"
                                        value={progress}
                                        color={worldJob.failed > 0 ? "warning" : "primary"}
                                        sx={{ height: 8, borderRadius: 1 }}
                                    />
                                    {worldJob.failures && worldJob.failures.length > 0 && (
                                        <Box sx={{
                                            mt: 1,
                                            p: 1,
                                            maxHeight: 140,
                                            overflowY: "auto",
                                            bgcolor: "background.default",
                                            borderRadius: 1,
                                            fontFamily: "monospace",
                                            fontSize: 12,
                                            lineHeight: 1.6,
                                        }}>
                                            {worldJob.failures.slice(0, 10).map((f, i) => (
                                                <Box key={i} sx={{ color: "error.main", whiteSpace: "pre-wrap", wordBreak: "break-all" }}>
                                                    ({f.x}, {f.y}, {f.z}) {f.reason}
                                                </Box>
                                            ))}
                                            {(worldJob.failuresTruncated ?? 0) > 0 && (
                                                <Box sx={{ color: "text.disabled" }}>
                                                    ... 另有 {worldJob.failuresTruncated} 条失败未显示
                                                </Box>
                                            )}
                                        </Box>
                                    )}
                                </Box>
                            )}
                        </CardContent>
                    </Card>
                </Grid>

                <Grid size={{ xs: 12, md: 5 }}>
                    <Card elevation={6} sx={{ height: "100%" }}>
                        <CardHeader
                            title={<><PersonIcon /> 投放到玩家背包</>}
                            subheader="从在线玩家中选择目标，打印件进入其背包，放不下的掉落在附近"
                        />
                        <CardContent>
                            <Stack spacing={2}>
                                <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                                    <FormControl fullWidth>
                                        <InputLabel>在线玩家</InputLabel>
                                        <Select
                                            label="在线玩家"
                                            value={selectedPlayerId == null ? "" : String(selectedPlayerId)}
                                            onChange={(e) => setSelectedPlayerId(e.target.value === "" ? null : Number(e.target.value))}
                                            disabled={playersLoading || players.length === 0}
                                        >
                                            {players.map((p) => (
                                                <MenuItem key={p.entityId} value={String(p.entityId)}>
                                                    {p.name} · {p.worldName}（{Math.round(p.posX)}, {Math.round(p.posY)}, {Math.round(p.posZ)}）
                                                </MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                    <IconButton
                                        onClick={() => { void loadPlayers() }}
                                        disabled={playersLoading}
                                        title="刷新在线玩家"
                                    >
                                        {playersLoading ? <CircularProgress size={22} /> : <RefreshIcon />}
                                    </IconButton>
                                </Stack>

                                {playersError && <Alert severity="error">{playersError}</Alert>}
                                {!playersLoading && !playersError && players.length === 0 && (
                                    <Alert severity="info">未检测到在线玩家，玩家进入服务器后可点击刷新</Alert>
                                )}
                                {players.length > 0 && (
                                    <Typography variant="body2" color="text.secondary">
                                        在线玩家 {players.length} 人（来自 /entities 筛选）
                                    </Typography>
                                )}

                                <Button
                                    size="large"
                                    variant="contained"
                                    color="secondary"
                                    disabled={delivering || selectedPlayerId == null}
                                    startIcon={delivering ? <CircularProgress size={20} color="inherit" /> : <PersonIcon />}
                                    onClick={handlePlayerPrint}
                                >
                                    {delivering ? "投递中..." : "投递到背包"}
                                </Button>

                                {deliverResult && (
                                    <Alert severity={deliverResult.dropped > 0 ? "warning" : "success"}>
                                        共生成 {deliverResult.total} 件打印件：
                                        放入背包 {deliverResult.added} 件
                                        {deliverResult.dropped > 0 && `，掉落 ${deliverResult.dropped} 件`}
                                    </Alert>
                                )}
                            </Stack>
                        </CardContent>
                    </Card>
                </Grid>
            </Grid>
        </RContainer>
    )
}
