"use client"

import { H2 } from "@/components/H2"
import { RContainer } from "@/components/RContainer"
import { useAPI } from "@/data/api"
import ImageIcon from "@mui/icons-material/Image"
import LinkIcon from "@mui/icons-material/Link"
import TableViewIcon from "@mui/icons-material/TableView"
import ZoomInIcon from "@mui/icons-material/ZoomIn"
import ZoomOutIcon from "@mui/icons-material/ZoomOut"
import {
    Box,
    CircularProgress,
    FormControl,
    Grid,
    IconButton,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    ToggleButton,
    ToggleButtonGroup,
    Tooltip,
    Typography,
    useTheme,
} from "@mui/material"
import type { ChunkMapCell, TPSInfo } from "@shirokasoke/webapi-sdk"
import { enqueueSnackbar } from "notistack"
import { useCallback, useEffect, useRef, useState } from "react"
import { useSearchParams } from "next/navigation"

const CHUNK_BLOCKS = 16
const ZOOM_LEVELS = [1, 2, 4, 8, 16] // pixels per block

type ViewMode = "image" | "heightmap"

interface ChunkEntry {
    img?: HTMLImageElement
    heightCanvas?: HTMLCanvasElement
    data?: ChunkMapCell[][]
    loading: boolean
    error?: string
}

function buildHeightCanvas(data: ChunkMapCell[][]): HTMLCanvasElement {
    const size = data.length
    const canvas = document.createElement("canvas")
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext("2d")!

    let min = Infinity,
        max = -Infinity
    for (const row of data) {
        for (const cell of row) {
            if (cell.y < min) min = cell.y
            if (cell.y > max) max = cell.y
        }
    }
    const range = max - min || 1

    for (let z = 0; z < size; z++) {
        for (let x = 0; x < data[z].length; x++) {
            const cell = data[z][x]
            const t = (cell.y - min) / range
            const r = Math.round(t * 200 + 30)
            const g = Math.round((1 - t) * 100 + t * 180 + 20)
            const b = Math.round((1 - t) * 200 + 30)
            ctx.fillStyle = `rgb(${r},${g},${b})`
            ctx.fillRect(x, z, 1, 1)
        }
    }

    return canvas
}

export default function MapPage() {
    const api = useAPI()
    const theme = useTheme()
    const searchParams = useSearchParams()

    // Read initial params from URL
    const urlX = searchParams.get("x")
    const urlZ = searchParams.get("z")
    const urlDim = searchParams.get("dim")

    // State
    const [dim, setDim] = useState(() => urlDim ? parseInt(urlDim) : 0)
    const [worlds, setWorlds] = useState<Record<string, TPSInfo>>({})
    const [viewMode, setViewMode] = useState<ViewMode>("image")
    const [zoomIdx, setZoomIdx] = useState(2) // ZOOM_LEVELS[2] = 4

    // Display state
    const [centerDisplay, setCenterDisplay] = useState({ x: 0, z: 0 })
    const [hoveredInfo, setHoveredInfo] = useState<{
        blockX: number
        blockZ: number
        cell?: ChunkMapCell
    } | null>(null)
    const [renderTick, setRenderTick] = useState(0)

    // Refs for rendering
    const canvasRef = useRef<HTMLCanvasElement>(null)
    const containerRef = useRef<HTMLDivElement>(null)
    const centerRef = useRef({ x: 0, z: 0 }) // block coords of viewport center
    const cacheRef = useRef(new Map<string, ChunkEntry>())
    const renderFnRef = useRef<() => void>(() => { })
    const animFrameRef = useRef(0)
    const loadingSetRef = useRef(new Set<string>())

    // Concurrency limiter
    const MAX_CONCURRENT = 8
    const concurrentRef = useRef({ running: 0, queue: [] as (() => void)[] })
    const acquire = useCallback(() => new Promise<void>((resolve) => {
        const c = concurrentRef.current
        if (c.running < MAX_CONCURRENT) {
            c.running++
            resolve()
        } else {
            c.queue.push(resolve)
        }
    }), [])
    const release = useCallback(() => {
        const c = concurrentRef.current
        c.running--
        const next = c.queue.shift()
        if (next) {
            c.running++
            next()
        }
    }, [])

    // Flag to skip first render before URL params are applied
    const urlAppliedRef = useRef(false)

    const ppb = ZOOM_LEVELS[zoomIdx] // pixels per block

    // Apply URL params as initial center
    useEffect(() => {
        if (urlAppliedRef.current) return
        urlAppliedRef.current = true
        if (urlX != null && urlZ != null) {
            const x = parseInt(urlX)
            const z = parseInt(urlZ)
            if (!isNaN(x) && !isNaN(z)) {
                centerRef.current = { x, z }
                setCenterDisplay({ x, z })
            }
        }
    }, [urlX, urlZ])

    // Fetch dimensions from getTPS()
    useEffect(() => {
        if (!api) return
        api.getTPS()
            .then((data) => {
                setWorlds(data)
                // Only set default dim if URL didn't specify one
                if (urlDim == null) {
                    const firstKey = Object.keys(data)[0]
                    if (firstKey !== undefined) setDim(parseInt(firstKey))
                }
            })
            .catch(() => { })
    }, [api != undefined])

    // Helper to get dimension label
    const getDimLabel = (dimId: number) => {
        const info = worlds[String(dimId)]
        return info?.WorldName || `维度 ${dimId}`
    }

    // Update URL with current center without adding history entries
    const syncUrl = useCallback(() => {
        const x = centerRef.current.x
        const z = centerRef.current.z
        const params = new URLSearchParams()
        params.set("x", String(Math.round(x)))
        params.set("z", String(Math.round(z)))
        params.set("dim", String(dim))
        window.history.replaceState(null, "", `/map?${params.toString()}`)
    }, [dim])

    const cacheKey = useCallback(
        (cx: number, cz: number) => `${viewMode}:${dim}:${cx}:${cz}`,
        [viewMode, dim],
    )

    // Load a chunk
    const loadChunk = useCallback(
        async (cx: number, cz: number) => {
            if (!api) return
            const key = cacheKey(cx, cz)
            const cache = cacheRef.current
            const existing = cache.get(key)
            if (existing && (existing.img || existing.heightCanvas || existing.error)) return
            if (loadingSetRef.current.has(key)) return

            loadingSetRef.current.add(key)
            cache.set(key, { loading: true })

            await acquire()
            try {
                if (viewMode === "image") {
                    const buf = (await api.getChunkMap(
                        { chunkX: cx, chunkZ: cz, dim },
                    )) as ArrayBuffer
                    const blob = new Blob([buf], { type: "image/png" })
                    const url = URL.createObjectURL(blob)
                    const img = new Image()
                    await new Promise<void>((resolve, reject) => {
                        img.onload = () => resolve()
                        img.onerror = () => reject(new Error("图片加载失败"))
                        img.src = url
                    })
                    cache.set(key, { img, loading: false })
                } else {
                    const data = (await api.getChunkMap(
                        { chunkX: cx, chunkZ: cz, dim },
                        true,
                    )) as ChunkMapCell[][]
                    const heightCanvas = buildHeightCanvas(data)
                    cache.set(key, { heightCanvas, data, loading: false })
                }
                setRenderTick((t) => t + 1)
            } catch (e) {
                const msg = e instanceof Error ? e.message : "加载失败"
                cache.set(key, { loading: false, error: msg })
                setRenderTick((t) => t + 1)
            } finally {
                release()
                loadingSetRef.current.delete(key)
            }
        },
        [api, dim, viewMode, cacheKey, acquire, release],
    )

    // Render function
    const renderMap = useCallback(() => {
        const canvas = canvasRef.current
        if (!canvas || canvas.width === 0 || canvas.height === 0) return
        const ctx = canvas.getContext("2d")
        if (!ctx) return

        const w = canvas.width
        const h = canvas.height
        const centerX = centerRef.current.x
        const centerZ = centerRef.current.z
        const cache = cacheRef.current
        const chunkPx = CHUNK_BLOCKS * ppb

        // Background
        ctx.fillStyle = theme.palette.mode === "dark" ? "#0a0a1a" : "#d8d8e8"
        ctx.fillRect(0, 0, w, h)

        // Visible chunk range
        const leftBlock = centerX - w / (2 * ppb)
        const topBlock = centerZ - h / (2 * ppb)
        const rightBlock = centerX + w / (2 * ppb)
        const bottomBlock = centerZ + h / (2 * ppb)

        const cMinX = Math.floor(leftBlock / CHUNK_BLOCKS) - 1
        const cMinZ = Math.floor(topBlock / CHUNK_BLOCKS) - 1
        const cMaxX = Math.floor(rightBlock / CHUNK_BLOCKS) + 1
        const cMaxZ = Math.floor(bottomBlock / CHUNK_BLOCKS) + 1

        const chunksToLoad: [number, number][] = []

        for (let cz = cMinZ; cz <= cMaxZ; cz++) {
            for (let cx = cMinX; cx <= cMaxX; cx++) {
                const key = cacheKey(cx, cz)
                const entry = cache.get(key)

                const sx = Math.round((cx * CHUNK_BLOCKS - centerX) * ppb + w / 2)
                const sy = Math.round((cz * CHUNK_BLOCKS - centerZ) * ppb + h / 2)

                if (viewMode === "image" && entry?.img) {
                    ctx.imageSmoothingEnabled = ppb <= 4
                    ctx.drawImage(entry.img, sx, sy, chunkPx, chunkPx)
                } else if (viewMode === "heightmap" && entry?.heightCanvas) {
                    ctx.imageSmoothingEnabled = false
                    ctx.drawImage(entry.heightCanvas, sx, sy, chunkPx, chunkPx)
                } else if (entry?.error) {
                    ctx.fillStyle =
                        theme.palette.mode === "dark"
                            ? "rgba(80, 20, 20, 0.5)"
                            : "rgba(255, 200, 200, 0.5)"
                    ctx.fillRect(sx, sy, chunkPx, chunkPx)
                    ctx.fillStyle = theme.palette.mode === "dark" ? "#ff6666" : "#cc0000"
                    ctx.font = "10px monospace"
                    ctx.textAlign = "center"
                    ctx.fillText("✕", sx + chunkPx / 2, sy + chunkPx / 2 + 4)
                } else if (entry?.loading) {
                    ctx.fillStyle =
                        theme.palette.mode === "dark"
                            ? "rgba(40, 40, 80, 0.3)"
                            : "rgba(200, 200, 240, 0.3)"
                    ctx.fillRect(sx, sy, chunkPx, chunkPx)
                    ctx.strokeStyle =
                        theme.palette.mode === "dark"
                            ? "rgba(100,100,255,0.3)"
                            : "rgba(50,50,200,0.3)"
                    ctx.lineWidth = 2
                    ctx.beginPath()
                    ctx.arc(sx + chunkPx / 2, sy + chunkPx / 2, Math.min(12, chunkPx / 4), 0, Math.PI * 2)
                    ctx.stroke()
                } else {
                    chunksToLoad.push([cx, cz])
                }

                // Chunk grid lines
                if (ppb >= 2) {
                    ctx.strokeStyle =
                        theme.palette.mode === "dark"
                            ? "rgba(255,255,255,0.06)"
                            : "rgba(0,0,0,0.06)"
                    ctx.lineWidth = 0.5
                    ctx.strokeRect(sx, sy, chunkPx, chunkPx)
                }

                // Chunk coordinate labels
                if (ppb >= 8) {
                    ctx.fillStyle =
                        theme.palette.mode === "dark"
                            ? "rgba(255,255,255,0.25)"
                            : "rgba(0,0,0,0.2)"
                    ctx.font = "10px monospace"
                    ctx.textAlign = "left"
                    ctx.fillText(`${cx},${cz}`, sx + 3, sy + 12)
                }
            }
        }

        // Crosshair
        ctx.strokeStyle = "rgba(255, 50, 50, 0.5)"
        ctx.lineWidth = 1
        const chLen = Math.min(16, w / 6)
        ctx.beginPath()
        ctx.moveTo(w / 2 - chLen, h / 2)
        ctx.lineTo(w / 2 + chLen, h / 2)
        ctx.moveTo(w / 2, h / 2 - chLen)
        ctx.lineTo(w / 2, h / 2 + chLen)
        ctx.stroke()

        ctx.fillStyle = "rgba(255, 50, 50, 0.7)"
        ctx.beginPath()
        ctx.arc(w / 2, h / 2, 2.5, 0, Math.PI * 2)
        ctx.fill()

        // Load missing chunks (skip during drag)
        if (!dragRef.current.active) {
            for (const [cx, cz] of chunksToLoad) {
                loadChunk(cx, cz)
            }
        }

        // Update center display
        setCenterDisplay({ x: Math.round(centerX), z: Math.round(centerZ) })
    }, [ppb, cacheKey, theme.palette.mode, viewMode, loadChunk])

    // Store render function in ref
    renderFnRef.current = renderMap

    // Render on state changes
    useEffect(() => {
        renderMap()
        console.log("rerender: " + renderTick)
    }, [renderMap, Math.floor(renderTick / (16 / ppb))])
    // 根据缩放程度调整渲染间隔：缩放越小（越远）可见chunk越多，渲染越慢，降低频率

    // Initial canvas sizing + resize observer
    useEffect(() => {
        const container = containerRef.current
        const canvas = canvasRef.current
        if (!container || !canvas) return

        const resize = () => {
            const rect = container.getBoundingClientRect()
            canvas.width = Math.floor(rect.width)
            canvas.height = Math.floor(rect.height)
            renderFnRef.current()
        }

        resize()
        const observer = new ResizeObserver(resize)
        observer.observe(container)
        return () => observer.disconnect()
    }, [])

    // Clear cache when dim or viewMode changes
    useEffect(() => {
        for (const entry of cacheRef.current.values()) {
            if (entry.img?.src?.startsWith("blob:")) URL.revokeObjectURL(entry.img.src)
        }
        cacheRef.current.clear()
        loadingSetRef.current.clear()
        concurrentRef.current.queue = []
        setRenderTick((t) => t + 1)
    }, [dim, viewMode])

    // Wheel zoom (needs passive: false for preventDefault)
    useEffect(() => {
        const canvas = canvasRef.current
        if (!canvas) return

        const handleWheel = (e: WheelEvent) => {
            e.preventDefault()
            const rect = canvas.getBoundingClientRect()
            const mx = e.clientX - rect.left
            const my = e.clientY - rect.top

            const blockX = centerRef.current.x + (mx - canvas.width / 2) / ppb
            const blockZ = centerRef.current.z + (my - canvas.height / 2) / ppb

            const newIdx =
                e.deltaY < 0
                    ? Math.min(zoomIdx + 1, ZOOM_LEVELS.length - 1)
                    : Math.max(zoomIdx - 1, 0)

            if (newIdx !== zoomIdx) {
                const newPpb = ZOOM_LEVELS[newIdx]
                centerRef.current.x = blockX - (mx - canvas.width / 2) / newPpb
                centerRef.current.z = blockZ - (my - canvas.height / 2) / newPpb
                setZoomIdx(newIdx)
            }
        }

        canvas.addEventListener("wheel", handleWheel, { passive: false })
        return () => canvas.removeEventListener("wheel", handleWheel)
    }, [zoomIdx, ppb])

    // Drag handlers
    const dragRef = useRef({
        active: false,
        startX: 0,
        startY: 0,
        startCX: 0,
        startCZ: 0,
    })

    const handlePointerDown = (e: React.PointerEvent) => {
        dragRef.current = {
            active: true,
            startX: e.clientX,
            startY: e.clientY,
            startCX: centerRef.current.x,
            startCZ: centerRef.current.z,
        }
            ; (e.target as HTMLElement).setPointerCapture(e.pointerId)
        if (canvasRef.current) canvasRef.current.style.cursor = "grabbing"
    }

    const handlePointerMove = (e: React.PointerEvent) => {
        const canvas = canvasRef.current
        if (!canvas) return

        // Update hover info
        const rect = canvas.getBoundingClientRect()
        const mx = e.clientX - rect.left
        const my = e.clientY - rect.top
        const blockX = Math.floor(centerRef.current.x + (mx - canvas.width / 2) / ppb)
        const blockZ = Math.floor(centerRef.current.z + (my - canvas.height / 2) / ppb)
        const chunkX = blockX >> 4
        const chunkZ = blockZ >> 4
        const localX = blockX & 15
        const localZ = blockZ & 15

        const key = cacheKey(chunkX, chunkZ)
        const entry = cacheRef.current.get(key)
        const cell = entry?.data?.[localZ]?.[localX]
        setHoveredInfo({ blockX, blockZ, cell })

        // Drag
        if (!dragRef.current.active) return
        const dx = e.clientX - dragRef.current.startX
        const dz = e.clientY - dragRef.current.startY
        centerRef.current.x = dragRef.current.startCX - dx / ppb
        centerRef.current.z = dragRef.current.startCZ - dz / ppb

        cancelAnimationFrame(animFrameRef.current)
        animFrameRef.current = requestAnimationFrame(() => {
            renderFnRef.current()
        })
    }

    const handlePointerUp = () => {
        dragRef.current.active = false
        if (canvasRef.current) canvasRef.current.style.cursor = "grab"
        // Sync URL and trigger chunk loading after drag ends
        syncUrl()
        setRenderTick((t) => t + 1)
    }

    if (!api) {
        return (
            <RContainer sx={{ pt: 10, textAlign: "center" }}>
                <CircularProgress size={80} />
                <Typography sx={{ mt: 2 }}>正在初始化 API...</Typography>
            </RContainer>
        )
    }

    const cx = centerDisplay.x >> 4
    const cz = centerDisplay.z >> 4

    return (
        <RContainer
            maxWidth={false}
            sx={{
                px: { xs: 0.5, sm: 1 },
                display: "flex",
                flexDirection: "column",
                height: { xs: "calc(100vh - 56px)", sm: "calc(100vh - 64px)", md: "calc(100vh - 80px)" },
            }}
        >
            <H2>地图预览</H2>
            <Paper sx={{ p: 1.5, mb: 1, flexShrink: 0 }}>
                <Grid container spacing={1} sx={{ alignItems: "center", justifyContent: "center" }}>
                    <Grid size="auto">
                        <FormControl size="small" sx={{ minWidth: 140 }}>
                            <InputLabel>维度</InputLabel>
                            <Select
                                value={dim}
                                label="维度"
                                onChange={(e) => {
                                    setDim(e.target.value)
                                    syncUrl()
                                }}
                                disabled={Object.keys(worlds).length === 0}
                            >
                                {Object.entries(worlds).map(([dimId, info]) => (
                                    <MenuItem key={dimId} value={parseInt(dimId)}>
                                        {info.WorldName || dimId}
                                        <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                                            ({info.TPS.toFixed(1)} TPS)
                                        </Typography>
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Grid>
                    <Grid size="auto">
                        <ToggleButtonGroup
                            value={viewMode}
                            exclusive
                            size="small"
                            onChange={(_, v) => {
                                if (v && v !== viewMode) setViewMode(v)
                            }}
                        >
                            <ToggleButton value="image">
                                <ImageIcon fontSize="small" sx={{ mr: 0.5 }} />
                                图像
                            </ToggleButton>
                            <ToggleButton value="heightmap">
                                <TableViewIcon fontSize="small" sx={{ mr: 0.5 }} />
                                高度图
                            </ToggleButton>
                        </ToggleButtonGroup>
                    </Grid>
                    <Grid size="auto">
                        <Tooltip title="复制当前地图链接">
                            <IconButton size="small" onClick={() => navigator.clipboard.writeText(location.href).then(
                                () => enqueueSnackbar("链接已复制", { variant: "success" }),
                                () => enqueueSnackbar("复制失败", { variant: "error" }),
                            )}>
                                <LinkIcon />
                            </IconButton>
                        </Tooltip>
                    </Grid>
                </Grid>
            </Paper>

            {/* Map canvas container */}
            <Paper
                ref={containerRef}
                sx={{
                    flex: 1,
                    position: "relative",
                    overflow: "hidden",
                    minHeight: 300,
                }}
            >
                <canvas
                    ref={canvasRef}
                    style={{ display: "block", cursor: "grab", touchAction: "none" }}
                    onPointerDown={handlePointerDown}
                    onPointerMove={handlePointerMove}
                    onPointerUp={handlePointerUp}
                    onPointerCancel={handlePointerUp}
                />

                {/* Zoom controls */}
                <Box
                    sx={{
                        position: "absolute",
                        top: 8,
                        right: 8,
                        display: "flex",
                        flexDirection: "column",
                        gap: 0.5,
                    }}
                >
                    <IconButton
                        size="small"
                        onClick={() => setZoomIdx((i) => Math.min(i + 1, ZOOM_LEVELS.length - 1))}
                        disabled={zoomIdx >= ZOOM_LEVELS.length - 1}
                        sx={{ bgcolor: "background.paper" }}
                    >
                        <ZoomInIcon />
                    </IconButton>
                    <IconButton
                        size="small"
                        onClick={() => setZoomIdx((i) => Math.max(i - 1, 0))}
                        disabled={zoomIdx <= 0}
                        sx={{ bgcolor: "background.paper" }}
                    >
                        <ZoomOutIcon />
                    </IconButton>
                </Box>

                {/* Center info overlay */}
                <Box
                    sx={{
                        position: "absolute",
                        bottom: 8,
                        left: 8,
                        display: "flex",
                        gap: 1,
                        alignItems: "flex-end",
                        flexWrap: "wrap",
                    }}
                >
                    <Paper sx={{ p: 1, fontSize: 12, opacity: 0.5, transition: "opacity 0.2s", "&:hover": { opacity: 1 } }} elevation={2}>
                        <Typography variant="caption" color="text.secondary">
                            中心: ({centerDisplay.x}, {centerDisplay.z}) · Chunk
                            ({cx}, {cz})
                        </Typography>
                        <br />
                        <Typography variant="caption" color="text.secondary">
                            缩放: {ppb}x · 维度: {getDimLabel(dim)}
                        </Typography>
                    </Paper>

                    {hoveredInfo && (
                        <Paper sx={{ p: 1, fontSize: 12, opacity: 0.5, transition: "opacity 0.2s", "&:hover": { opacity: 1 } }} elevation={2}>
                            <Typography variant="caption" color="text.secondary">
                                方块 ({hoveredInfo.blockX}, {hoveredInfo.blockZ})
                            </Typography>
                            {hoveredInfo.cell ? (
                                <>
                                    <br />
                                    <Typography
                                        variant="caption"
                                        sx={{
                                            fontWeight: "bold",
                                            wordBreak: "break-all",
                                        }}
                                    >
                                        {hoveredInfo.cell.name}
                                    </Typography>
                                    <br />
                                    <Typography
                                        variant="caption"
                                        color="text.secondary"
                                    >
                                        meta: {hoveredInfo.cell.meta} · Y:{" "}
                                        {hoveredInfo.cell.y}
                                    </Typography>
                                </>
                            ) : (
                                <>
                                    <br />
                                    <Typography
                                        variant="caption"
                                        color="text.disabled"
                                    >
                                        {viewMode === "heightmap"
                                            ? "区域未加载"
                                            : "高度图模式下显示方块详情"}
                                    </Typography>
                                </>
                            )}
                        </Paper>
                    )}
                </Box>
            </Paper>
        </RContainer>
    )
}
