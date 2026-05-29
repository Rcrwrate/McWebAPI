"use client"

import CustomPagination from "@/app/blocks/CustomPagination"
import { H2 } from "@/components/H2"
import { useAPI } from "@/data/api"
import ViewListIcon from "@mui/icons-material/ViewList"
import ViewModuleIcon from "@mui/icons-material/ViewModule"
import {
    Alert,
    Badge,
    Box,
    Card,
    CardContent,
    CircularProgress,
    Container,
    Grid,
    Pagination,
    Paper,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Tooltip,
    Typography
} from "@mui/material"
import type { GridFilterModel, GridSortModel } from "@mui/x-data-grid"
import {
    DataGrid,
    GridColDef,
    gridExpandedSortedRowEntriesSelector
} from "@mui/x-data-grid"
import { GridApiCommunity } from "@mui/x-data-grid/internals"
import type { AEItemStack } from "@shirokasoke/webapi-sdk"
import { useSearchParams } from "next/navigation"
import { useEffect, useRef, useState } from "react"
import { Footer } from "../Footer"
import ItemIcon from "../ItemIcon"

function formatCount(n: number): string {
    if (!n || n < 1000) return String(n || 0)
    if (n < 1000000) return `${(n / 1000).toFixed(1)}k`
    if (n < 1000000000) return `${(n / 1000000).toFixed(1)}M`
    return `${(n / 1000000000).toFixed(1)}G`
}

type AEItemRow = AEItemStack & { uid: string }

const columns: GridColDef<AEItemRow>[] = [
    {
        field: "localizedName",
        headerName: "名称",
        width: 240,
        filterable: true,
    },
    {
        field: "registryName",
        headerName: "注册名",
        width: 320,
        filterable: true,
    },
    {
        field: "id",
        headerName: "ID",
        width: 80,
        type: "number",
        filterable: true,
    },
    {
        field: "damage",
        headerName: "元数据",
        width: 90,
        type: "number",
        filterable: true,
    },
    {
        field: "stackSize",
        headerName: "数量",
        width: 120,
        type: "number",
        filterable: true,
        valueFormatter: (value: number) => formatCount(value),
    },
    {
        field: "nbtstr",
        headerName: "NBT",
        width: 300,
        filterable: true,
    }
]

export default function AEItemPage() {
    const api = useAPI()
    const searchParams = useSearchParams()
    const [items, setItems] = useState<AEItemRow[]>([])
    const [error, setError] = useState<string | null>(null)

    const [viewMode, setViewMode] = useState<"list" | "icon">("icon")
    const [paginationModel, setPaginationModel] = useState({ page: 0, pageSize: 50 })

    const [displayRows, setDisplayRows] = useState<AEItemRow[]>([])

    const [sortM, setSortM] = useState<GridSortModel>()
    const [filterM, setFilterM] = useState<GridFilterModel>()
    const apiRef = useRef<GridApiCommunity>(null)

    const x = searchParams.get("x")
    const y = searchParams.get("y")
    const z = searchParams.get("z")
    const dimension = searchParams.get("dimension")

    useEffect(() => {
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
        api.aeItems({ x: px, y: py, z: pz, dimension: dim })
            .then((data) => {
                const rows = data.map((it): AEItemRow => ({
                    ...it,
                    uid: `${it.id}-${it.damage}-${it.nbtstr ?? ""}`,
                }))
                setItems(rows)
                setDisplayRows(rows)
            })
            .catch((e) => setError(e instanceof Error ? e.message : "加载物品失败"))
    }, [api, x, y, z, dimension])

    useEffect(() => {
        if (!apiRef.current) return
        const timer = setTimeout(() => {
            const entries = gridExpandedSortedRowEntriesSelector(apiRef)
            setDisplayRows(entries.map((e) => e.model as AEItemRow))
        }, 200)
        return () => clearTimeout(timer)
    }, [filterM, sortM])

    let pageCount = 0
    let pageItems: AEItemRow[] = []
    if (viewMode === "icon") {
        pageCount = Math.ceil(displayRows.length / paginationModel.pageSize)
        pageItems = displayRows.slice(
            paginationModel.page * paginationModel.pageSize,
            (paginationModel.page + 1) * paginationModel.pageSize
        )
    }

    if (!api) {
        return (
            <Container sx={{ pt: 10, textAlign: "center" }}>
                <CircularProgress size={80} />
                <Typography sx={{ mt: 2 }}>正在初始化 API...</Typography>
            </Container>
        )
    }

    const totalCount = items.reduce((s, it) => s + (it.stackSize || 0), 0)

    return (
        <Container sx={{ p: 1 }}>
            <H2>AE 存储物品</H2>
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
                                    {items.length}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    物品种类
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                    <Grid size={{ xs: 6, sm: 4, md: 2 }}>
                        <Card>
                            <CardContent sx={{ textAlign: "center", py: 2 }}>
                                <Typography variant="h4" color="info">
                                    {formatCount(totalCount)}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    物品总数
                                </Typography>
                            </CardContent>
                        </Card>
                    </Grid>
                </Grid>
            )}

            <Grid container spacing={2} sx={{ mb: 2, alignItems: "center", justifyContent: "center" }}>
                <Grid>
                    <TextField label="搜索物品" placeholder="输入名称、注册名或 ID" size="small" sx={{ minWidth: 280 }}
                        disabled={viewMode == "icon"}
                        onChange={(e) => apiRef.current?.setQuickFilterValues([e.target.value])} />
                </Grid>
                <Grid>
                    <Typography variant="body2" color="primary">
                        共 {displayRows.length} / {items.length} 种物品
                    </Typography>
                </Grid>
                <Grid>
                    <ToggleButtonGroup
                        value={viewMode}
                        exclusive
                        size="small"
                        onChange={(_, v) => {
                            if (!v || v === viewMode) return
                            setViewMode(v)
                        }}
                    >
                        <ToggleButton value="list">
                            <ViewListIcon />
                        </ToggleButton>
                        <ToggleButton value="icon">
                            <ViewModuleIcon />
                        </ToggleButton>
                    </ToggleButtonGroup>
                </Grid>
            </Grid>

            {viewMode === "list" ? (
                <Paper sx={{ height: "70vh", width: "100%", mb: 2 }}>
                    <DataGrid
                        apiRef={apiRef}
                        rows={items}
                        columns={columns}
                        loading={items.length === 0}
                        getRowId={(row) => row.uid}
                        pageSizeOptions={[25, 50, 100, 500]}
                        paginationModel={paginationModel}
                        onPaginationModelChange={setPaginationModel}
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
                        }}
                    />
                </Paper>
            ) : (
                <>
                    <Paper sx={{ p: 2, mb: 2 }}>
                        <Box
                            sx={{
                                display: "grid",
                                gridTemplateColumns: "repeat(auto-fill, minmax(64px, 1fr))",
                                gap: 1.5,
                            }}
                        >
                            {pageItems.map((item) => (
                                <Tooltip
                                    key={item.uid}
                                    title={
                                        <Box>
                                            <Typography variant="body2">{item.localizedName}</Typography>
                                            <Typography variant="caption" color="#aaa" component="div">
                                                #{item.id}{item.damage ? `:${item.damage}` : ""}
                                            </Typography>
                                            <Typography variant="caption" color="#55aaff" component="div">
                                                {item.registryName}
                                            </Typography>
                                            <Typography variant="caption" color="primary" component="div">
                                                数量: {item.stackSize ?? 0}
                                            </Typography>
                                        </Box>
                                    }
                                    arrow
                                    placement="top"
                                    slotProps={{
                                        tooltip: {
                                            sx: {
                                                bgcolor: "rgba(16, 0, 32, 0.92)",
                                                border: "1px solid rgba(80, 0, 160, 0.7)",
                                                boxShadow: 4,
                                                maxWidth: 320,
                                                "& .MuiTooltip-arrow": {
                                                    color: "rgba(16, 0, 32, 0.92)",
                                                },
                                            },
                                        },
                                    }}
                                >
                                    <Box
                                        sx={{
                                            position: "relative",
                                            display: "flex",
                                            alignItems: "center",
                                            justifyContent: "center",
                                            aspectRatio: "1 / 1",
                                            p: 0.5,
                                        }}
                                    >
                                        <Badge
                                            badgeContent={formatCount(item.stackSize || 0)}
                                            color="primary"
                                            overlap="rectangular"
                                            anchorOrigin={{ vertical: "top", horizontal: "right" }}
                                            sx={{
                                                "& .MuiBadge-badge": {
                                                    fontSize: "0.65rem",
                                                    minWidth: 16,
                                                    height: 16,
                                                    padding: "0 3px",
                                                    borderRadius: "8px",
                                                },
                                            }}
                                        >
                                            <ItemIcon api={api} item={item} />
                                        </Badge>
                                    </Box>
                                </Tooltip>
                            ))}
                        </Box>
                        {pageItems.length === 0 && !(items.length === 0) && (
                            <Typography color="text.secondary" align="center" sx={{ py: 4 }}>
                                无匹配结果
                            </Typography>
                        )}
                    </Paper>
                    {pageCount > 1 && (
                        <Box sx={{ display: "flex", justifyContent: "center" }}>
                            <Pagination
                                count={pageCount}
                                page={paginationModel.page + 1}
                                onChange={(_, p) => {
                                    setPaginationModel(prev => ({ ...prev, page: p - 1 }))
                                }}
                                showFirstButton
                                showLastButton
                            />
                        </Box>
                    )}
                </>
            )}
            <Footer searchParams={searchParams} />
        </Container>
    )
}
