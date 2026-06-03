"use client"

import { H2 } from "@/components/H2"
import { SelectableIconPaper } from "@/components/SelectableIconPaper"
import { useAPI } from "@/data/api"
import ViewListIcon from '@mui/icons-material/ViewList'
import ViewModuleIcon from '@mui/icons-material/ViewModule'
import {
    Alert,
    Box,
    Chip,
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
import type { Block } from "@shirokasoke/webapi-sdk"
import { enqueueSnackbar } from "notistack"
import { useEffect, useRef, useState } from "react"
import BlockIcon from "./BlockIcon"
import CustomPagination from "./CustomPagination"


const columns: GridColDef<Block>[] = [
    {
        field: "id",
        headerName: "ID",
        width: 80,
        type: "number",
        filterable: true,
    },
    {
        field: "localizedName",
        headerName: "本地化名称",
        width: 220,
        filterable: true,
    },
    {
        field: "registryName",
        headerName: "注册名",
        width: 320,
        filterable: true,
    },
    {
        field: "lightLevel",
        headerName: "光照",
        width: 80,
        type: "number",
        filterable: true,
    },
    {
        field: "isOpaqueCube",
        headerName: "不透明",
        width: 100,
        type: "boolean",
        filterable: true,
        renderCell: (params) => (
            <Chip
                label={params.value ? "是" : "否"}
                color={params.value ? "success" : "default"}
                size="small"
            />
        ),
    },
    {
        field: "isNormalCube",
        headerName: "标准方块",
        width: 100,
        type: "boolean",
        filterable: true,
        renderCell: (params) => (
            <Chip
                label={params.value ? "是" : "否"}
                color={params.value ? "success" : "default"}
                size="small"
            />
        ),
    },
    {
        field: "resistance",
        headerName: "抗爆性",
        width: 100,
        filterable: true,
    },
    {
        field: "slipperiness",
        headerName: "滑度",
        width: 100,
        type: "number",
        filterable: true,
    },
]

export default function BlocksPage() {
    const api = useAPI()
    const [blocks, setBlocks] = useState<Block[]>([])
    const [error, setError] = useState<string | null>(null)

    const [select, setSelect] = useState<"include" | "exclude">("include")
    const [selectionModel, setSelectionModel] = useState<string[]>([])

    const [viewMode, setViewMode] = useState<"list" | "icon">("list")

    const [paginationModel, setPaginationModel] = useState({ page: 0, pageSize: 50 })

    const [displayRows, setDisplayRows] = useState<Block[]>([])

    const [sortM, setSortM] = useState<GridSortModel>()
    const [filterM, setFilterM] = useState<GridFilterModel>()
    const apiRef = useRef<GridApiCommunity>(null)

    useEffect(() => {
        if (!api) return
        setError(null)
        api.getBlocks()
            .then((data) => {
                setBlocks(data)
                setDisplayRows(data)
            })
            .catch((e) => setError(e instanceof Error ? e.message : "加载失败"))
    }, [api != undefined])


    useEffect(() => {
        if (!apiRef.current) return
        const timer = setTimeout(() => {
            const entries = gridExpandedSortedRowEntriesSelector(apiRef)
            setDisplayRows(entries.map((e) => e.model as Block))
        }, 200)
        return () => clearTimeout(timer)
    }, [filterM, sortM])

    if (!api) {
        return (
            <Container sx={{ pt: 10, textAlign: "center" }}>
                <CircularProgress size={80} />
                <Typography sx={{ mt: 2 }}>正在初始化 API...</Typography>
            </Container>
        )
    }

    let iconPageCount = 0
    let iconPageBlocks: Block[] = []
    if (viewMode === "icon") {
        iconPageCount = Math.ceil(displayRows.length / paginationModel.pageSize)
        iconPageBlocks = displayRows.slice(
            paginationModel.page * paginationModel.pageSize,
            (paginationModel.page + 1) * paginationModel.pageSize
        )
    }

    const toggleSelection = (registryName: string) => {
        setSelectionModel((prev) =>
            prev.includes(registryName)
                ? prev.filter((id) => id !== registryName)
                : [...prev, registryName]
        )
    }

    return (
        <Container sx={{ p: 0 }}>
            <H2>Blocks</H2>
            <Grid container spacing={2} sx={{ mb: 2, alignItems: "center", justifyContent: "center" }}>
                <Grid>
                    <TextField label="搜索方块" placeholder="输入名称、注册名或 ID"
                        disabled={viewMode == "icon"}
                        onChange={(e) => apiRef.current?.setQuickFilterValues([e.target.value])}
                        size="small"
                        sx={{ minWidth: 280 }}
                    />
                </Grid>
                <Grid>
                    <Typography variant="body2" color="primary">
                        共 {displayRows.length} / {blocks.length} 个方块
                    </Typography>
                </Grid>
                <Grid>
                    <Typography variant="body2" color="secondary">
                        已选 {selectionModel.length} 个
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

            {error && (
                <Alert severity="error" sx={{ mb: 2 }}>
                    {error}
                </Alert>
            )}

            {viewMode === "list" ? (
                <Paper sx={{ height: "70vh", width: "100%" }}>
                    <DataGrid apiRef={apiRef}
                        rows={blocks}
                        columns={columns}
                        loading={blocks.length == 0}
                        getRowId={(row) => row.registryName}
                        pageSizeOptions={[25, 50, 100, 500]}
                        paginationModel={paginationModel}
                        onPaginationModelChange={setPaginationModel}
                        density="compact"
                        checkboxSelection
                        filterModel={filterM}
                        onFilterModelChange={(m, d) => setFilterM(m)}
                        sortModel={sortM}
                        onSortModelChange={(s) => setSortM(s)}
                        rowSelectionModel={{ type: select, ids: new Set(selectionModel) }}
                        onRowSelectionModelChange={({ type, ids }) => {
                            // setSelect(type);
                            if (type == "exclude") return enqueueSnackbar("不允许全选", { variant: "warning" });
                            setSelectionModel(Array.from(ids) as string[])
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
            ) : (
                <>
                    <Paper sx={{ p: 2, mb: 2 }}>
                        <Box
                            sx={{
                                display: "grid",
                                gridTemplateColumns: "repeat(auto-fill, minmax(72px, 1fr))",
                                gap: 1,
                            }}
                        >
                            {iconPageBlocks.map((block) => {
                                const selected = selectionModel.includes(block.registryName)
                                return (
                                    <Tooltip key={block.registryName}
                                        title={
                                            <Box>
                                                <Typography variant="body2">
                                                    {block.localizedName}
                                                </Typography>
                                                <Typography variant="caption" color="#aaa" component="div">
                                                    #{block.id}{block.meta != null ? `:${block.meta}` : ""}
                                                </Typography>
                                                <Typography variant="caption" color="#55aaff" component="div">
                                                    {block.registryName}
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
                                        <SelectableIconPaper elevation={selected ? 16 : 0} selected={selected} onClick={() => toggleSelection(block.registryName)}>
                                            <BlockIcon api={api} block={block} />
                                        </SelectableIconPaper>
                                    </Tooltip>
                                )
                            })}
                        </Box>
                        {displayRows.length === 0 && blocks.length != 0 && (
                            <Typography align="center" sx={{ py: 4 }}>
                                无匹配结果
                            </Typography>
                        )}
                    </Paper>
                    {iconPageCount > 1 && (
                        <Box sx={{ display: "flex", justifyContent: "center" }}>
                            <Pagination
                                count={iconPageCount}
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
        </Container>
    )
}
