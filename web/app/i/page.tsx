"use client"

import { H2 } from "@/components/H2"
import { getPanelDef, PANEL_REGISTRY } from "@/components/Panel"
import { RContainer } from "@/components/RContainer"
import { PanelDataProvider, usePanelActions, usePanelData } from "@/data/PanelCache"
import ContentPasteIcon from "@mui/icons-material/ContentPaste"
import DeleteIcon from "@mui/icons-material/Delete"
import DragIndicatorIcon from "@mui/icons-material/DragIndicator"
import EditIcon from "@mui/icons-material/Edit"
import RefreshIcon from "@mui/icons-material/Refresh"
import VisibilityIcon from "@mui/icons-material/Visibility"
import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    CircularProgress,
    FormControl,
    IconButton,
    InputLabel,
    MenuItem,
    Select,
    Stack,
    Tooltip,
    Typography
} from "@mui/material"
import { enqueueSnackbar } from "notistack"
import { useEffect, useMemo, useState } from "react"
import type { LayoutItem } from "react-grid-layout"
import ReactGridLayout, { getCompactor, useContainerWidth } from "react-grid-layout"
import "react-grid-layout/css/styles.css"
import "react-resizable/css/styles.css"

const STORAGE_KEY = "dashboard-config-v1"
const DRAG_HANDLE = "panel-drag-handle"

/** 网格列数自适应：单个 cell 的目标像素宽度 */
const GRID_CELL_WIDTH = 30
/** 列数下限 / 上限 */
const GRID_COLS_MIN = 4

/** 根据容器宽度计算列数，保证不同视图下 cell 实际宽度接近一致 */
function calcCols(width: number): number {
    return Math.max(GRID_COLS_MIN, Math.floor(width / GRID_CELL_WIDTH))
}

/** 剪贴板导入格式：指定面板类型与对应的请求参数 */
interface PanelClipboardSpec {
    /** 面板 ID（对应 PANEL_REGISTRY 中的 id） */
    type: string
    /** API 请求参数，如 { x, y, z, dimension } */
    requestData?: any
}

interface DashboardItem extends LayoutItem {
    panelId: string
    requestData?: any
}

type DashboardConfig = DashboardItem[]

function newId(): string {
    if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID()
    return `p-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

const DEFAULT_CONFIG: DashboardConfig = []

function loadConfig(): DashboardConfig {
    if (typeof window === "undefined") return DEFAULT_CONFIG
    try {
        const raw = localStorage.getItem(STORAGE_KEY)
        if (!raw) return DEFAULT_CONFIG
        const parsed = JSON.parse(raw)
        if (!Array.isArray(parsed)) return DEFAULT_CONFIG
        return parsed as DashboardConfig
    } catch {
        return DEFAULT_CONFIG
    }
}

async function readClipboardSpec(): Promise<PanelClipboardSpec | null> {
    const text = await navigator.clipboard.readText()
    if (!text) return null
    const parsed = JSON.parse(text) as PanelClipboardSpec
    if (!parsed || typeof parsed.type !== "string") {
        throw new Error("剪贴板内容缺少 type 字段")
    }
    return parsed
}

function PanelCardInner({ def, item, editMode, onRemove, }: {
    def: NonNullable<ReturnType<typeof getPanelDef>>
    item: DashboardItem
    editMode: boolean
    onRemove: (id: string) => void
}) {
    const { data, loading, error } = usePanelData(def.panel, item.requestData)

    if (!editMode) return error ? <Alert severity="error" sx={{ py: 0 }}>{error}</Alert> : def.panel.Render(data, item.requestData)
    return <Card sx={{
        height: "100%",
        position: "relative",
        overflow: "hidden",
        // 用 outline 代替 border，不占据布局空间，避免内容区被内缩产生变形
        outline: "1px dashed",
        outlineOffset: "-1px",
        outlineColor: "primary.main",
    }}>
        <CardContent sx={{ height: "100%", overflow: "auto", p: 0, "&:last-child": { pb: 0 } }}>
            {loading ? <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100%" }}>
                <CircularProgress size={28} />
            </Box>
                : error ? <Alert severity="error" sx={{ py: 0 }}>{error}</Alert> : def.panel.Render(data, item.requestData)}
        </CardContent>
        {/* 控制栏以浮层悬浮在内容上方，不挤占内容空间 */}
        <Box sx={(theme) => ({
            position: "absolute",
            top: 0,
            left: 0,
            right: 0,
            zIndex: 1,
            display: "flex",
            flexDirection: "row",
            alignItems: "center",
            px: 0.75,
            py: 0.25,
            borderBottom: 1,
            borderColor: "divider",
            bgcolor: theme.palette.mode === "light" ? "rgba(250, 250, 250, 0.85)" : "rgba(31, 31, 31, 0.85)",
            boxShadow: 1,
        })}>
            <Box className={DRAG_HANDLE} sx={{
                display: "flex",
                flexDirection: "row",
                alignItems: "center",
                gap: 0.5,
                flex: 1,
                minWidth: 0,
                px: 0.75,
                cursor: "grab",
                userSelect: "none",
            }}>
                <DragIndicatorIcon fontSize="small" color="action" />
                <Typography variant="subtitle2" noWrap>{def.title}</Typography>
            </Box>
            <Tooltip title="移除">
                <IconButton size="small" color="error" onClick={(e) => { e.stopPropagation(); onRemove(item.i) }}>
                    <DeleteIcon fontSize="small" />
                </IconButton>
            </Tooltip>
        </Box>
    </Card>
}

function Dashboard() {
    const { width, containerRef, mounted } = useContainerWidth()
    const actions = usePanelActions()
    const [config, setConfig] = useState<DashboardConfig>(DEFAULT_CONFIG)
    const [editMode, setEditMode] = useState(false)
    const [hydrated, setHydrated] = useState(false)
    const [refreshSec, setRefreshSec] = useState(5)

    useEffect(() => {
        setConfig(loadConfig())
        setHydrated(true)
    }, [])

    useEffect(() => {
        if (refreshSec <= 0) return
        actions?.refreshAll()
        const timer = setInterval(() => actions?.refreshAll(), refreshSec * 1000)
        return () => clearInterval(timer)
    }, [refreshSec, actions?.refreshAll])

    const persist = (next: DashboardConfig) => {
        setConfig(next)
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
        } catch (e) {
            enqueueSnackbar(`配置保存失败: ${e}`, { variant: "error" })
        }
    }

    const onLayoutChange = (layout: readonly LayoutItem[]) => {
        if (!hydrated) return
        // 保留 panelId / requestData，用新的布局字段更新
        const byId = new Map(config.map(item => [item.i, item]))
        persist(layout.map(l => ({ ...byId.get(l.i), ...l }) as DashboardItem))
    }

    const handleRemove = (id: string) => persist(config.filter(item => item.i !== id))

    const handleAddFromClipboard = async () => {
        try {
            const spec = await readClipboardSpec()
            if (!spec) {
                enqueueSnackbar("剪贴板为空", { variant: "warning" })
                return
            }
            const def = getPanelDef(spec.type)
            if (!def) {
                enqueueSnackbar(`未知面板类型: ${spec.type}，可用: ${PANEL_REGISTRY.map(p => p.id).join(", ")}`, { variant: "error" })
                return
            }
            const bottomY = config.reduce((m, l) => Math.max(m, l.y + l.h), 0)
            persist([...config, {
                i: newId(),
                panelId: spec.type,
                requestData: spec.requestData,
                x: 0,
                y: bottomY,
                minW: 2,
                minH: 2,
                ...def.panel.size,
            }])
            enqueueSnackbar(`已添加面板: ${def.title}`, { variant: "success" })
        } catch (e) {
            if (e instanceof DOMException && e.name === "NotAllowedError") {
                enqueueSnackbar("读取剪贴板被拒绝，请允许剪贴板权限", { variant: "error" })
            } else {
                enqueueSnackbar(`添加失败: ${e instanceof Error ? e.message : "剪贴板格式错误"}`, { variant: "error" })
            }
        }
    }

    const layout = useMemo(() => config.map(({ panelId: _p, requestData: _r, ...rest }) => rest), [config])
    // 列数随容器宽度自适应
    const cols = useMemo(() => calcCols(width), [width])

    return <Box>
        <Stack direction="row" useFlexGap sx={{ mb: 2, alignItems: "center", justifyContent: "center", flexWrap: 'wrap' }} spacing={2}>
            <Tooltip title="刷新全部面板">
                <IconButton size="small" onClick={() => actions?.refreshAll()}>
                    <RefreshIcon fontSize="small" />
                </IconButton>
            </Tooltip>
            <FormControl size="small" sx={{ minWidth: 120 }}>
                <InputLabel>自动刷新</InputLabel>
                <Select value={refreshSec} label="自动刷新" onChange={(e) => setRefreshSec(e.target.value)}>
                    <MenuItem value={0}>关闭</MenuItem>
                    <MenuItem value={5}>5 秒</MenuItem>
                    <MenuItem value={10}>10 秒</MenuItem>
                    <MenuItem value={30}>30 秒</MenuItem>
                    <MenuItem value={60}>1 分钟</MenuItem>
                    <MenuItem value={300}>5 分钟</MenuItem>
                </Select>
            </FormControl>
            <Tooltip title={editMode ? "退出编辑" : "编辑布局"}>
                <Button size="small" variant={editMode ? "contained" : "outlined"}
                    startIcon={editMode ? <VisibilityIcon /> : <EditIcon />}
                    onClick={() => setEditMode(v => !v)}>
                    {editMode ? "完成" : "编辑"}
                </Button>
            </Tooltip>
            {editMode && (
                <Tooltip title="从剪贴板导入面板">
                    <Button
                        size="small"
                        variant="outlined"
                        startIcon={<ContentPasteIcon />}
                        onClick={handleAddFromClipboard}
                    >
                        粘贴添加
                    </Button>
                </Tooltip>
            )}
        </Stack>

        <div ref={containerRef}>
            {mounted && hydrated && <ReactGridLayout
                layout={layout}
                width={width}
                compactor={getCompactor(null, true)}
                gridConfig={{ cols, rowHeight: 30, margin: [8, 8] }}
                dragConfig={{ enabled: editMode, handle: `.${DRAG_HANDLE}` }}
                resizeConfig={{ enabled: editMode, handles: ["w", "s", "e", "n", "sw", "nw", "se", "ne"] }}
                onLayoutChange={onLayoutChange}>
                {config.map(item => {
                    const def = getPanelDef(item.panelId)
                    return <div key={item.i}>
                        {def ? <PanelCardInner def={def} item={item} editMode={editMode} onRemove={handleRemove} /> :
                            <Card sx={{ height: "100%", display: "flex", alignItems: "center", justifyContent: "center" }}>
                                <Typography color="error">未知面板</Typography>
                            </Card>}
                    </div>
                })}
            </ReactGridLayout>}
        </div>
        {config.length === 0 && <Box sx={{ textAlign: "center", py: 8, color: "text.secondary" }}>
            <Typography>暂无面板，点击「编辑」→「粘贴添加」从剪贴板导入面板</Typography>
            <Typography variant="body2" sx={{ mt: 1 }}>
                在相关面板页面可以长按获取代码
            </Typography>
            <Typography variant="body2" sx={{ mt: 1 }}>
                剪贴板格式: {'{"type":"ae-item-storage","requestData":{"x":0,"y":64,"z":0,"dimension":0}}'}
            </Typography>
        </Box>}
    </Box>
}

export default function DashboardPage() {
    return <RContainer maxWidth={false} disableGutters>
        <H2 sx={{ mx: { xs: 2, sm: 3 } }}>数据大屏</H2>
        <PanelDataProvider>
            <Dashboard />
        </PanelDataProvider>
    </RContainer>
}
