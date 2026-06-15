"use client"

import { H2 } from "@/components/H2"
import MCToolitip from "@/components/MCTooltip"
import { RContainer } from "@/components/RContainer"
import { SelectableIconPaper } from "@/components/SelectableIconPaper"
import { useAPI } from "@/data/api"
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown'
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight'
import ViewListIcon from '@mui/icons-material/ViewList'
import ViewModuleIcon from '@mui/icons-material/ViewModule'
import {
    Alert,
    Box,
    Chip,
    CircularProgress,
    Grid,
    Pagination,
    Paper,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
    styled
} from "@mui/material"
import type { GridFilterModel, GridSortModel } from "@mui/x-data-grid"
import {
    DataGrid,
    GridColDef,
    gridExpandedSortedRowEntriesSelector
} from "@mui/x-data-grid"
import { GridApiCommunity } from "@mui/x-data-grid/internals"
import type { Item, ItemDetail, ItemStack } from "@shirokasoke/webapi-sdk"
import { enqueueSnackbar } from "notistack"
import { useCallback, useEffect, useRef, useState } from "react"
import CustomPagination from "../blocks/CustomPagination"
import ItemIcon from "./ItemIcon"

// 父物品框 - 有子类型用 warning，普通物品用灰色
const ParentIconPaper = styled(SelectableIconPaper, {
    shouldForwardProp: (prop) => prop !== "hasSubtypes",
})<{ hasSubtypes?: boolean }>(({ theme, hasSubtypes, selected }) => {
    if (hasSubtypes) return {
        border: `2px solid ${selected ? theme.palette.warning.dark : theme.palette.warning.main}`,
        backgroundColor: selected ? theme.palette.warning.main + "20" : undefined,
        "&:hover": {
            opacity: 1,
            transform: "scale(1.05)",
        },
    }
    return {
        border: selected ? `2px solid ${theme.palette.grey[500]}` : undefined,
        backgroundColor: selected ? theme.palette.action.selected : undefined,
        "&:hover": {
            opacity: 1,
            transform: "scale(1.05)",
        },
    }
})

// 子物品框 - 使用 info 颜色
const SubIconPaper = styled(Paper, {
    shouldForwardProp: (prop) => prop !== "$selected",
})<{ $selected?: boolean }>(({ theme, $selected }) => ({
    position: "relative",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    aspectRatio: "1 / 1",
    padding: theme.spacing(0.5),
    cursor: "default",
    border: `2px solid ${$selected ? theme.palette.primary.main : theme.palette.info.main}`,
    borderRadius: theme.shape.borderRadius,
    backgroundColor: $selected ? theme.palette.primary.main + "20" : undefined,
    opacity: $selected ? 1 : 0.85,
    transition: "opacity 0.2s, transform 0.2s",
    "&:hover": {
        opacity: 1,
        transform: "scale(1.05)",
    },
}))

// DataGrid 扁平行类型
interface FlatRow {
    _rowId: string
    _rowType: "parent" | "sub"
    _parentId?: number
    _loading?: boolean
    _expanded?: boolean
    id: number
    registryName: string
    UnlocalizedName: string
    localizedName: string
    HasSubtypes: boolean
    damage?: number
    MaxStackSize?: number
    damageable?: boolean
}

export default function ItemsPage() {
    const api = useAPI()
    const [items, setItems] = useState<FlatRow[]>([])
    const [error, setError] = useState<string | null>(null)

    const [viewMode, setViewMode] = useState<"list" | "icon">("list")

    const [paginationModel, setPaginationModel] = useState({ page: 0, pageSize: 50 })

    const [displayRows, setDisplayRows] = useState<FlatRow[]>([])

    const [selectionModel, setSelectionModel] = useState<string[]>([])

    const [sortM, setSortM] = useState<GridSortModel>()
    const [filterM, setFilterM] = useState<GridFilterModel>()
    const apiRef = useRef<GridApiCommunity>(null)

    // 已请求过的 item id（防止重复加载）
    const loadedIds = useRef<Set<number>>(new Set())

    useEffect(() => {
        if (!api) return
        setError(null)
        api.getItems()
            .then((data: Item[]) => {
                const flatRows: FlatRow[] = data.map((item) => ({
                    _rowId: item.registryName,
                    _rowType: "parent",
                    id: item.id,
                    registryName: item.registryName,
                    UnlocalizedName: item.UnlocalizedName,
                    localizedName: item.localizedName,
                    HasSubtypes: item.HasSubtypes,
                }))
                setItems(flatRows)
                setDisplayRows(flatRows)
            })
            .catch((e) => setError(e instanceof Error ? e.message : "加载失败"))
    }, [api != undefined])

    useEffect(() => {
        if (!apiRef.current) return
        const timer = setTimeout(() => {
            const entries = gridExpandedSortedRowEntriesSelector(apiRef)
            setDisplayRows(entries.map((e) => e.model as FlatRow))
        }, 200)
        return () => clearTimeout(timer)
    }, [filterM, sortM, items.length])

    // 懒加载子物品 - 直接插入到 items 列表中
    const loadSubItems = useCallback(async (itemId: number) => {
        if (!api) return
        if (loadedIds.current.has(itemId)) return

        loadedIds.current.add(itemId)

        // 标记为加载中 + 已展开
        setItems((prev) => {
            const idx = prev.findIndex((r) => r._rowType === "parent" && r.id === itemId)
            if (idx === -1) return prev
            const next = [...prev]
            next[idx] = { ...next[idx], _loading: true, _expanded: true }
            return next
        })

        try {
            const detail: ItemDetail = await api.getItem({ id: itemId })
            const subs = detail.subs ?? []
            setItems((prev) => {
                const idx = prev.findIndex((r) => r._rowType === "parent" && r.id === itemId)
                if (idx === -1) return prev
                const next = [...prev]
                next[idx] = { ...next[idx], _loading: false }
                const subRows: FlatRow[] = subs.map((sub, i) => ({
                    _rowId: `${sub.registryName}:${i}`,
                    _rowType: "sub",
                    _parentId: itemId,
                    id: sub.id,
                    registryName: sub.registryName,
                    UnlocalizedName: sub.UnlocalizedName,
                    localizedName: sub.localizedName,
                    HasSubtypes: sub.HasSubtypes,
                    damage: sub.damage,
                    MaxStackSize: sub.MaxStackSize,
                    damageable: sub.damageable,
                }))
                next.splice(idx + 1, 0, ...subRows)
                return next
            })
        } catch (e) {
            enqueueSnackbar(`加载物品 #${itemId} 子物品失败: ${e instanceof Error ? e.message : "未知错误"}`, { variant: "error" })
        }
    }, [api])

    // 切换选中状态（同步列表模式与图标模式）
    const toggleSelection = (rowId: string) => {
        setSelectionModel((prev) =>
            prev.includes(rowId)
                ? prev.filter((id) => id !== rowId)
                : [...prev, rowId]
        )
    }

    // DataGrid 列定义
    const listColumns: GridColDef<FlatRow>[] = [
        {
            field: "_expand",
            headerName: "",
            width: 44,
            sortable: false,
            filterable: false,
            disableColumnMenu: true,
            renderCell: (params) => {
                const row = params.row as FlatRow
                if (row._rowType === "sub" || !row.HasSubtypes) return null
                return (
                    <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", width: "100%", height: "100%" }}>
                        {row._loading ? (
                            <CircularProgress size={16} />
                        ) : row._expanded ? (
                            <KeyboardArrowDownIcon fontSize="small" color="warning" />
                        ) : (
                            <KeyboardArrowRightIcon fontSize="small" />
                        )}
                    </Box>
                )
            },
        },
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
            width: 200,
            filterable: true,
        },
        {
            field: "registryName",
            headerName: "注册名",
            width: 300,
            filterable: true,
        },
        {
            field: "UnlocalizedName",
            headerName: "未本地化名称",
            width: 280,
            filterable: true,
        },
        {
            field: "HasSubtypes",
            headerName: "有子类型",
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
            field: "damage",
            headerName: "Damage",
            width: 80,
            type: "number",
            filterable: true,
        },
        {
            field: "MaxStackSize",
            headerName: "最大堆叠",
            width: 90,
            type: "number",
            filterable: true,
        },
        {
            field: "damageable",
            headerName: "可损坏",
            width: 80,
            type: "boolean",
            filterable: true,
            valueGetter: (_, row) => row.damageable ?? false,
            renderCell: (params) => {
                if (params.row._rowType === "parent") return null
                return (
                    <Chip
                        label={params.value ? "是" : "否"}
                        color={params.value ? "warning" : "default"}
                        size="small"
                    />
                )
            },
        },
    ]

    if (!api) {
        return (
            <RContainer sx={{ pt: 10, textAlign: "center" }}>
                <CircularProgress size={80} />
                <Typography sx={{ mt: 2 }}>正在初始化 API...</Typography>
            </RContainer>
        )
    }

    const parentCount = items.filter((r) => r._rowType === "parent").length
    const displayParentCount = displayRows.filter((r) => r._rowType === "parent").length
    const subCount = items.filter((r) => r._rowType === "sub").length

    const iconFlatPageCount = Math.ceil(displayRows.length / paginationModel.pageSize)
    const iconFlatPage = displayRows.slice(
        paginationModel.page * paginationModel.pageSize,
        (paginationModel.page + 1) * paginationModel.pageSize
    )

    return (
        <RContainer>
            <H2>Items</H2>
            <Grid container spacing={2} sx={{ mb: 2, alignItems: "center", justifyContent: "center" }}>
                <Grid>
                    <TextField label="搜索物品" placeholder="输入名称、注册名或 ID"
                        disabled={apiRef.current == undefined}
                        onChange={(e) => apiRef.current?.setQuickFilterValues([e.target.value])}
                        size="small"
                        sx={{ minWidth: 280 }}
                    />
                </Grid>
                <Grid>
                    <Typography variant="body2" color="primary">
                        共 {displayParentCount} / {parentCount} 个物品
                    </Typography>
                </Grid>
                <Grid>
                    <Typography variant="body2" color="textDisabled">
                        子物品 {subCount} 个已加载
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

            <Paper sx={{ height: "70vh", width: "100%", display: viewMode === "list" ? undefined : "none" }}>
                <DataGrid
                    apiRef={apiRef}
                    rows={items}
                    columns={listColumns.map(i => { i.align = "center"; i.headerAlign = "center"; return i })}
                    loading={parentCount == 0}
                    getRowId={(row) => row._rowId}
                    pageSizeOptions={[25, 50, 100, 500]}
                    paginationModel={paginationModel}
                    onPaginationModelChange={setPaginationModel}
                    density="compact"
                    checkboxSelection
                    disableRowSelectionOnClick
                    rowSelectionModel={{ type: "include", ids: new Set(selectionModel) }}
                    onRowSelectionModelChange={({ type, ids }) => {
                        if (type == "exclude") return enqueueSnackbar("不允许全选", { variant: "warning" });
                        setSelectionModel(Array.from(ids) as string[])
                    }}
                    filterModel={filterM}
                    onFilterModelChange={(m) => setFilterM(m)}
                    sortModel={sortM}
                    onSortModelChange={(s) => setSortM(s)}
                    onRowClick={(params) => {
                        const row = params.row as FlatRow
                        if (row._rowType === "parent" && row.HasSubtypes && !row._expanded) {
                            loadSubItems(row.id)
                        } else {
                            toggleSelection(row._rowId)
                        }
                    }}
                    getRowClassName={(params) => {
                        const row = params.row as FlatRow
                        if (row._rowType === "sub") return "sub-item-row"
                        if (row.HasSubtypes) return "parent-item-row"
                        return ""
                    }}
                    sx={(theme) => ({
                        "& .sub-item-row": {
                            backgroundColor: theme.palette.info.main + "14",
                            "&.Mui-selected": {
                                backgroundColor: theme.palette.info.main + "26 !important",
                            },
                            "&:hover": {
                                backgroundColor: theme.palette.info.main + "24 !important",
                            },
                        },
                        "& .parent-item-row": {
                            backgroundColor: theme.palette.warning.main + "10",
                            "&.Mui-selected": {
                                backgroundColor: theme.palette.warning.main + "20 !important",
                            },
                            "&:hover": {
                                backgroundColor: theme.palette.warning.main + "1e !important",
                            },
                        },
                    })}
                    slots={{ pagination: CustomPagination }}
                    slotProps={{
                        loadingOverlay: {
                            variant: "skeleton",
                            noRowsVariant: "skeleton",
                        },
                    }}
                />
            </Paper>
            {viewMode === "icon" && (
                <>
                    <Paper sx={{ p: 2, mb: 2 }}>
                        <Box
                            sx={{
                                display: "grid",
                                gridTemplateColumns: "repeat(auto-fill, minmax(72px, 1fr))",
                                gap: 1,
                            }}
                        >
                            {iconFlatPage.map((row) => {
                                const isParent = row._rowType === "parent"
                                const isSelected = selectionModel.includes(row._rowId)
                                return (
                                    <MCToolitip k={row._rowId} item={row as unknown as ItemStack}>
                                        {isParent ? (
                                            <ParentIconPaper
                                                onClick={() => {
                                                    if (row.HasSubtypes && !row._expanded) {
                                                        loadSubItems(row.id)
                                                    } else {
                                                        toggleSelection(row._rowId)
                                                    }
                                                }}
                                                elevation={isSelected ? 16 : 0}
                                                selected={isSelected}
                                                hasSubtypes={row.HasSubtypes}
                                                sx={{ cursor: "pointer", position: "relative" }}
                                            >
                                                <ItemIcon api={api} item={{ ...row, damage: 0, MaxStackSize: 0, damageable: false } as unknown as ItemStack} />
                                                {row.HasSubtypes && !row._expanded && (
                                                    <Box sx={{ position: "absolute", bottom: 1, right: 1, fontSize: 9, color: "text.secondary", lineHeight: 1 }}>▼</Box>
                                                )}
                                            </ParentIconPaper>
                                        ) : (
                                            <SubIconPaper
                                                onClick={() => toggleSelection(row._rowId)}
                                                $selected={isSelected}
                                                sx={{ cursor: "pointer" }}
                                            >
                                                <ItemIcon api={api} item={row as unknown as ItemStack} size={48} />
                                            </SubIconPaper>
                                        )}
                                    </MCToolitip>
                                )
                            })}
                        </Box>
                        {displayRows.length === 0 && parentCount != 0 && (
                            <Typography align="center" sx={{ py: 4 }}>
                                无匹配结果
                            </Typography>
                        )}
                    </Paper>
                    {iconFlatPageCount > 1 && (
                        <Box sx={{ display: "flex", justifyContent: "center" }}>
                            <Pagination
                                count={iconFlatPageCount}
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
        </RContainer>
    )
}
