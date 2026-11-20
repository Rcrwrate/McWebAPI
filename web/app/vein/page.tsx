"use client"

import { H2 } from "@/components/H2"
import PersistentDataGrid from "@/components/PersistentDataGrid"
import { RContainer } from "@/components/RContainer"
import { useAPI } from "@/data/api"
import RefreshIcon from "@mui/icons-material/Refresh"
import {
    Alert,
    Chip,
    CircularProgress,
    FormControl,
    Grid,
    IconButton,
    InputLabel,
    MenuItem,
    Paper,
    Select,
    Typography,
} from "@mui/material"
import type { GridColDef } from "@mui/x-data-grid-pro"
import type { GT5Vein, GT5VeinDefDimension, GT5VeinOres, GT5VeinSource } from "@shirokasoke/webapi-sdk"
import { useSearchParams } from "next/navigation"
import { enqueueSnackbar } from "notistack"
import { useEffect, useState } from "react"

type VeinRow = GT5Vein & { id: string }

/** 矿脉来源：VisualProspecting 缓存记录的写入方式 */
const SOURCE_LABELS: Record<GT5VeinSource, string> = {
    UNKNOWN: "未知",
    RESCAN: "重新扫描",
    API: "接口写入",
    GENERATED: "世界生成",
    ADMIN: "管理员",
}

const ORE_ROLE_LABELS: Array<[keyof GT5VeinOres, string]> = [
    ["primary", "主"],
    ["secondary", "副"],
    ["between", "间"],
    ["sporadic", "散"],
]

/** 矿石构成文本，如 `主 磁铁矿 · 副 钒磁铁矿 · 散 金` */
function oreText(ores: GT5VeinOres): string {
    return ORE_ROLE_LABELS
        .filter(([role]) => ores[role])
        .map(([role, label]) => `${label} ${ores[role]}`)
        .join(" · ")
}

const columns: GridColDef<VeinRow>[] = [
    {
        field: "localizedName",
        headerName: "矿脉名称",
        flex: 1,
        minWidth: 180,
        filterable: true,
    },
    {
        field: "vein",
        headerName: "注册名",
        width: 190,
        filterable: true,
    },
    {
        field: "ores",
        headerName: "矿石构成",
        flex: 1,
        minWidth: 220,
        filterable: true,
        valueGetter: (_value, row) => oreText(row.ores),
    },
    {
        field: "blockSize",
        headerName: "规模",
        width: 90,
        type: "number",
        filterable: true,
    },
    {
        field: "x",
        headerName: "中心坐标",
        width: 150,
        filterable: true,
        valueGetter: (_value, row) => `${row.x}, ${row.z}`,
    },
    {
        field: "chunkX",
        headerName: "区块坐标",
        width: 150,
        filterable: true,
        valueGetter: (_value, row) => `${row.chunkX}, ${row.chunkZ}`,
    },
    {
        field: "depleted",
        headerName: "状态",
        width: 100,
        type: "singleSelect",
        valueOptions: ["正常", "枯竭"],
        valueGetter: (_value, row) => row.depleted ? "枯竭" : "正常",
        renderCell: (params) => (
            <Chip
                size="small"
                label={params.value}
                color={params.row.depleted ? "default" : "success"}
                variant={params.row.depleted ? "outlined" : "filled"}
            />
        ),
    },
    {
        field: "source",
        headerName: "来源",
        width: 120,
        type: "singleSelect",
        valueOptions: Object.values(SOURCE_LABELS),
        valueGetter: (_value, row) => SOURCE_LABELS[row.source] ?? row.source,
    },
]

export default function VeinPage() {
    const api = useAPI()
    const searchParams = useSearchParams()
    const urlDim = searchParams.get("dim")

    const [dimensions, setDimensions] = useState<GT5VeinDefDimension[]>([])
    // 默认选中主世界（维度 0），URL 指定时以 URL 为准
    const [dim, setDim] = useState<number>(
        urlDim != null && /^-?\d+$/.test(urlDim) ? parseInt(urlDim, 0) : 0
    )
    const [veins, setVeins] = useState<VeinRow[]>([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const data = async () => {
        api?.getGT5Veins({ dim })
            .then((r) => {
                setVeins(r.veins.map((vein) => ({
                    ...vein,
                    id: `${vein.vein}-${vein.chunkX}-${vein.chunkZ}`,
                })))
            })
            .catch((e) => {
                const msg = e instanceof Error ? e.message : "获取矿脉列表失败"
                setVeins([])
                setError(msg)
                enqueueSnackbar(msg, { variant: "error" })
            })
            .finally(() => setLoading(false))
    }

    // 维度列表取自矿脉定义接口，附带维度名，缺失维度的维度不会出现
    useEffect(() => {
        if (!api) return
        api.getGT5VeinDefs()
            .then((r) => {
                const dims = Object.values(r.dimensions)
                    .sort((a, b) => a.dimension - b.dimension)
                setDimensions(dims)
                // URL 指定的维度不存在时优先回退主世界，再回退第一个可用维度
                setDim((prev) => dims.some((d) => d.dimension === prev)
                    ? prev
                    : dims.find((d) => d.dimension === 0)?.dimension ?? dims[0]?.dimension ?? 0)
            })
            .catch((e) => {
                const msg = e instanceof Error ? e.message : "获取维度列表失败"
                setError(msg)
                enqueueSnackbar(msg, { variant: "error" })
            })
    }, [api])
    // 维度变化时直接拉取该维度全部已生成矿脉
    useEffect(() => { data() }, [api, dim])

    if (!api) {
        return (
            <RContainer sx={{ pt: 10, textAlign: "center" }}>
                <CircularProgress size={80} />
                <Typography sx={{ mt: 2 }}>正在初始化 API...</Typography>
            </RContainer>
        )
    }

    const depleted = veins.filter((vein) => vein.depleted).length

    return (
        <RContainer>
            <H2>矿脉查询</H2>
            {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
            <Grid container spacing={1} sx={{ alignItems: "center", justifyContent: "center", p: 2 }}>
                <Grid size="auto">
                    <FormControl sx={{ minWidth: 240 }}>
                        <InputLabel>维度</InputLabel>
                        <Select
                            value={dimensions.length > 0 ? dim : ""}
                            label="维度"
                            onChange={(e) => setDim(Number(e.target.value))}
                            disabled={dimensions.length === 0}
                        >
                            {dimensions.map((d) => (
                                <MenuItem key={d.dimension} value={d.dimension}>
                                    {d.dimensionName || `维度 ${d.dimension}`}
                                    <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                                        (ID {d.dimension} · {d.veins.length} 种可生成)
                                    </Typography>
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                </Grid>
                <Grid size="auto">
                    <IconButton size="small" title="刷新" onClick={data}>
                        <RefreshIcon />
                    </IconButton>
                </Grid>
                <Grid size="auto">
                    <Typography variant="body2" color="text.secondary">
                        共 {veins.length} 条已生成矿脉
                        {depleted > 0 && `，其中枯竭 ${depleted} 条`}
                    </Typography>
                </Grid>
            </Grid>

            <Paper sx={{ height: "70vh", width: "100%", mb: 2 }}>
                <PersistentDataGrid
                    storageKey="vein"
                    rows={veins}
                    columns={columns}
                    loading={loading}
                    getRowId={(row) => row.id}
                    pageSizeOptions={[25, 50, 100, 500]}
                    slotProps={{
                        loadingOverlay: {
                            variant: "skeleton",
                        },
                    }}
                />
            </Paper>
        </RContainer >
    )
}
