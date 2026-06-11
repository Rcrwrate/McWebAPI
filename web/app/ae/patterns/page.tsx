"use client"

import CustomPagination from "@/app/blocks/CustomPagination"
import { H2 } from "@/components/H2"
import MCToolitip from "@/components/MCTooltip"
import { RContainer } from "@/components/RContainer"
import { useAPI } from "@/data/api"
import useCoords from "@/data/useCoords"
import CancelIcon from "@mui/icons-material/Cancel"
import CheckCircleIcon from "@mui/icons-material/CheckCircle"
import MapIcon from "@mui/icons-material/Map"
import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    CircularProgress,
    Grid,
    Paper,
    Stack,
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
import type { AE2Pattern, AEMEInterface, ItemStack } from "@shirokasoke/webapi-sdk"
import { useRouter, useSearchParams } from "next/navigation"
import { useEffect, useRef, useState } from "react"
import { Footer } from "../Footer"
import ItemIcon from "../ItemIcon"

type PatternRow = AE2Pattern & { slot: number } & {
    uid: string
    interfaceName: string
    interfaceLocation: AEMEInterface["location"]
}

function formatCondensedList(items: Array<ItemStack & { count: number }> | undefined): string {
    if (!items || items.length === 0) return "-"
    return items
        .map((i) => `${i.localizedName}${i.count > 1 ? ` x${i.count}` : ""}`)
        .join(", ") || "(空)"
}

const columns: GridColDef<PatternRow>[] = [
    {
        field: "interfaceName",
        headerName: "接口名称",
        width: 160,
        filterable: true,
    },
    {
        field: "slot",
        headerName: "槽位",
        width: 80,
        type: "number",
        filterable: true,
    },
    {
        field: "crafting",
        headerName: "合成类型",
        width: 110,
        filterable: true,
        valueGetter: (_value, row) => row.crafting ? "合成" : "处理",
        renderCell: (params) => (
            <Chip
                label={params.row.crafting ? "合成" : "处理"}
                color={params.row.crafting ? "primary" : "secondary"}
                size="small"
            />
        ),
    },
    {
        field: "condensedOutputs",
        headerName: "输出",
        width: 300,
        filterable: true,
        valueGetter: (_value, row) => formatCondensedList(row.condensedOutputs)
    },
    {
        field: "condensedInputs",
        headerName: "输入",
        width: 300,
        filterable: true,
        valueGetter: (_value, row) => formatCondensedList(row.condensedInputs)
    },
    {
        field: "author",
        headerName: "作者",
        width: 100,
        filterable: true,
        valueGetter: (_value, row) => row.author ?? "-",
    },
    {
        field: "priority",
        headerName: "优先级",
        width: 90,
        type: "number",
        filterable: true,
    },
    {
        field: "substitute",
        headerName: "替代",
        width: 80,
        type: "boolean",
        filterable: true,
        renderCell: (params) => params.row.substitute
            ? <CheckCircleIcon color="success" fontSize="small" />
            : <CancelIcon color="disabled" fontSize="small" />,
    },
    {
        field: "beSubstitute",
        headerName: "可被替代",
        width: 100,
        type: "boolean",
        filterable: true,
        renderCell: (params) => params.row.beSubstitute
            ? <CheckCircleIcon color="success" fontSize="small" />
            : <CancelIcon color="disabled" fontSize="small" />,
    },
    {
        field: "patternParseError",
        headerName: "解析错误",
        width: 150,
        filterable: true,
        valueGetter: (_value, row) => row.patternParseError ?? "-",
        renderCell: (params) => params.row.patternParseError
            ? <Chip label={params.row.patternParseError} color="error" size="small" />
            : <Typography variant="body2">-</Typography>,
    },
]

export default function AEPatternsPage() {
    const api = useAPI()
    const searchParams = useSearchParams()
    const router = useRouter()
    const [x, y, z, dimension] = useCoords(searchParams)
    const [patterns, setPatterns] = useState<PatternRow[]>([])
    const [error, setError] = useState<string | null>(null)
    const [displayRows, setDisplayRows] = useState<PatternRow[]>([])

    const [sortM, setSortM] = useState<GridSortModel>()
    const [filterM, setFilterM] = useState<GridFilterModel>()
    const apiRef = useRef<GridApiCommunity>(null)

    const [rowSelectionModel, setRowSelectionModel] = useState<GridRowSelectionModel>({ type: "include", ids: new Set() })

    useEffect(() => {
        if (!api || !x) return
        setError(null)
        api.aeMEs({ x, y, z, dimension, world: true })
            .then((data) => {
                const rows: PatternRow[] = []
                data.forEach((iface, i) => {
                    iface.patterns.forEach((pat) => {
                        rows.push({
                            ...pat,
                            uid: `${i}-${pat.slot}`,
                            interfaceName: iface.name,
                            interfaceLocation: iface.location,
                        })
                    })
                })
                setPatterns(rows)
                setDisplayRows(rows)
            })
            .catch((e) => setError(e instanceof Error ? e.message : "加载样板列表失败"))
    }, [api != undefined, x, y, z, dimension])

    useEffect(() => {
        if (!apiRef.current) return
        const timer = setTimeout(() => {
            const entries = gridExpandedSortedRowEntriesSelector(apiRef)
            setDisplayRows(entries.map((e) => e.model as PatternRow))
        }, 200)
        return () => clearTimeout(timer)
    }, [filterM, sortM])

    const craftingCount = patterns.filter((p) => p.crafting).length
    const processingCount = patterns.filter((p) => !p.crafting).length
    const errorCount = patterns.filter((p) => p.patternParseError).length

    const selectedRow = patterns.find((p) => rowSelectionModel.ids.has(p.uid))

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
            <H2>样板一览</H2>
            {error && (
                <Alert severity="error" sx={{ mb: 2 }}>
                    {error}
                </Alert>
            )}

            {!error && (
                <Grid container spacing={2} sx={{ mb: 2 }}>
                    <Grid size={{ xs: 6, sm: 4, md: 3 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="primary">
                                    {patterns.length}
                                </Typography>
                                <Typography variant="body2">
                                    总样板数
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 3 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="info">
                                    {craftingCount}
                                </Typography>
                                <Typography variant="body2">
                                    合成型样板
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 3 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="secondary">
                                    {processingCount}
                                </Typography>
                                <Typography variant="body2">
                                    处理型样板
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 3 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color={errorCount > 0 ? "error" : "success"}>
                                    {errorCount}
                                </Typography>
                                <Typography variant="body2">
                                    解析错误
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                </Grid>
            )}

            <Grid container spacing={2} sx={{ mb: 2, alignItems: "center", justifyContent: "center" }}>
                <Grid>
                    <TextField
                        label="搜索样板"
                        placeholder="输入接口名、物品名等快速筛选"
                        onChange={(e) => apiRef.current?.setQuickFilterValues([e.target.value])}
                        size="small"
                        sx={{ minWidth: 280 }}
                    />
                </Grid>
                <Grid>
                    <Typography variant="body2" color="primary">
                        共 {displayRows.length} / {patterns.length} 个样板
                    </Typography>
                </Grid>
            </Grid>

            <Paper sx={{ height: "70vh", width: "100%", mb: 2 }}>
                <DataGrid
                    apiRef={apiRef}
                    rows={patterns}
                    columns={columns.map(i => { i.align = "center"; i.headerAlign = "center"; return i })}
                    loading={patterns.length === 0}
                    getRowId={(row) => row.uid}
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
                    slots={{ pagination: CustomPagination }}
                    slotProps={{
                        loadingOverlay: {
                            variant: "skeleton",
                            noRowsVariant: "skeleton",
                        },
                    }}
                />
            </Paper>

            {selectedRow && rowSelectionModel.ids.size > 0 && (
                <>
                    <Stack direction="row" sx={{ alignItems: "center", justifyContent: "center", flexWrap: 'wrap', mb: 2 }} spacing={1} useFlexGap>
                        <Button variant="outlined" startIcon={<MapIcon />}
                            onClick={() => {
                                const loc = selectedRow.interfaceLocation
                                if (loc) router.push(`/map?x=${loc.x}&z=${loc.z}&dim=${loc.dimension}`)
                            }}
                        >查看区块地图</Button>
                        <Typography variant="body2" color="secondary">
                            已选择: {selectedRow.interfaceName} - 槽位 {selectedRow.slot}
                        </Typography>
                        <Button variant="outlined" color="error" onClick={() => {
                            setRowSelectionModel({ type: "include", ids: new Set() })
                        }}>取消选择</Button>
                    </Stack>
                    <Paper sx={{ p: 1.5, mb: 2 }}>
                        <Grid container spacing={2} sx={{ alignItems: "center", justifyContent: "center" }}>
                            <Chip label={selectedRow.crafting ? "合成" : "处理"} color={selectedRow.crafting ? "primary" : "secondary"} size="small" />
                            {selectedRow.condensedInputs?.map((input, idx) => {
                                input.stackSize = input.count
                                return <MCToolitip key={`i${idx}`} k={`i${idx}`} item={input}>
                                    <Box sx={{ width: 48, height: 48, position: "relative" }}>
                                        <ItemIcon api={api} item={input} badge />
                                    </Box>
                                </MCToolitip>
                            }) ?? <Typography variant="body2">(空)</Typography>}
                            <Grid size="auto">
                                <Typography variant="body2">→</Typography>
                            </Grid>
                            {selectedRow.condensedOutputs?.map((output, idx) => {
                                output.stackSize = output.count
                                return <MCToolitip key={`o${idx}`} k={`o${idx}`} item={output}>
                                    <Box sx={{ width: 48, height: 48, position: "relative" }}>
                                        <ItemIcon api={api} item={output} badge />
                                    </Box>
                                </MCToolitip>
                            }) ?? <Typography variant="body2">(空)</Typography>}
                            <Grid size={12} />
                            {selectedRow.priority !== undefined && <Grid size="auto"><Chip label={`优先级: ${selectedRow.priority}`} size="small" /></Grid>}
                            {selectedRow.author && <Grid size="auto"><Chip label={`作者: ${selectedRow.author}`} size="small" /></Grid>}
                            <Grid size="auto"><Chip label={`替代: ${selectedRow.substitute ? "是" : "否"}`} color={selectedRow.substitute ? "success" : "default"} size="small" /></Grid>
                            <Grid size="auto"><Chip label={`可被替代: ${selectedRow.beSubstitute ? "是" : "否"}`} color={selectedRow.beSubstitute ? "success" : "default"} size="small" /></Grid>
                            {selectedRow.patternParseError && <Grid size="auto"><Chip label={`错误: ${selectedRow.patternParseError}`} color="error" size="small" /></Grid>}
                        </Grid>
                    </Paper>
                </>
            )}

            <Footer args={searchParams.toString()} />
        </RContainer>
    )
}
