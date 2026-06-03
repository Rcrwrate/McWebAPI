"use client"

import { H2 } from "@/components/H2"
import { useAPI } from "@/data/api"
import AccountTreeIcon from '@mui/icons-material/AccountTree'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import BuildIcon from '@mui/icons-material/Build'
import DeleteIcon from '@mui/icons-material/Delete'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import HelpIcon from '@mui/icons-material/Help'
import InventoryIcon from '@mui/icons-material/Inventory'
import MemoryIcon from '@mui/icons-material/Memory'
import ScienceIcon from '@mui/icons-material/Science'
import StorageIcon from '@mui/icons-material/Storage'
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    CardHeader,
    CircularProgress,
    Container,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    Grid,
    IconButton,
    InputLabel,
    MenuItem,
    Select,
    TextField,
    Typography,
    useMediaQuery
} from "@mui/material"
import type { TPSInfo } from "@shirokasoke/webapi-sdk"
import { default as LinkC } from "next/link"
import { enqueueSnackbar } from "notistack"
import { useEffect, useState } from "react"
import { createArgs, getSavedCoords, saveCoords, type SavedAECoord } from "./coords"

export default function AEPage() {
    const api = useAPI()
    const [worlds, setWorlds] = useState<Record<string, TPSInfo>>({})
    const [error, setError] = useState<string | null>(null)

    const [x, setX] = useState("")
    const [y, setY] = useState("")
    const [z, setZ] = useState("")
    const [dimension, setDimension] = useState<string>("0")
    const [verifying, setVerifying] = useState(false)

    const [saved, setSaved] = useState<SavedAECoord[]>([])
    const [selectedIndex, setSelectedIndex] = useState<number | null>(null)

    const [renameOpen, setRenameOpen] = useState(false)
    const [renameValue, setRenameValue] = useState("")

    const notMo = useMediaQuery('(min-width:600px)')

    useEffect(() => {
        if (!api) return
        api.getTPS()
            .then((data) => setWorlds(data))
            .catch((e) => setError(e instanceof Error ? e.message : "加载世界列表失败"))
    }, [api != undefined])

    useEffect(() => {
        setSaved(getSavedCoords())
    }, [])

    const handleVerifyAndAdd = async () => {
        if (!api) return
        const px = parseInt(x)
        const py = parseInt(y)
        const pz = parseInt(z)
        const dim = parseInt(dimension)
        if (isNaN(px) || isNaN(py) || isNaN(pz) || isNaN(dim)) {
            enqueueSnackbar("请填写完整的坐标和世界", { variant: "warning" })
            return
        }

        setVerifying(true)
        try {
            const result = await api.aeHit({ x: px, y: py, z: pz, dimension: dim })
            const blockDetail = await api.getBlock({ x: px, y: py, z: pz, dim })
            const newCoord: SavedAECoord = {
                x: px, y: py, z: pz, dimension: dim,
                name: blockDetail.block.localizedName || blockDetail.block.registryName
            }
            const updated = [...saved.filter((c) => !(c.x === px && c.y === py && c.z === pz && c.dimension === dim)), newCoord]
            saveCoords(updated)
            setSaved(updated)
            enqueueSnackbar(`验证成功: ${result.message}`, { variant: "success" })
            setX("")
            setY("")
            setZ("")
        } catch (e) {
            enqueueSnackbar(e instanceof Error ? e.message : "验证失败，该坐标不属于 AE 网络", { variant: "error" })
        } finally {
            setVerifying(false)
        }
    }

    const handleDelete = (index: number) => {
        const updated = saved.filter((_, i) => i !== index)
        saveCoords(updated)
        setSaved(updated)
        if (selectedIndex === index) {
            setSelectedIndex(null)
        } else if (selectedIndex !== null && selectedIndex > index) {
            setSelectedIndex(selectedIndex - 1)
        }
    }

    const handleRename = () => {
        if (selectedIndex === null) return
        const updated = saved.map((c, i) => i === selectedIndex ? { ...c, name: renameValue.trim() || c.name } : c)
        saveCoords(updated)
        setSaved(updated)
        setRenameOpen(false)
        enqueueSnackbar("名称修改成功", { variant: "success" })
    }

    if (!api) {
        return (
            <Container sx={{ pt: 10, textAlign: "center" }}>
                <CircularProgress size={80} />
                <Typography sx={{ mt: 2 }}>正在初始化 API...</Typography>
            </Container>
        )
    }

    return (
        <Container sx={{ p: 2 }}>
            <H2>AE 网络管理</H2>

            {error && (
                <Alert severity="error" sx={{ mb: 2 }}>
                    {error}
                </Alert>
            )}

            <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6 }}>
                    <Card elevation={6}>
                        <CardHeader title="添加新的AE节点" />
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
                                <Grid size={{ xs: 10, md: 8 }}>
                                    <FormControl fullWidth>
                                        <InputLabel>世界</InputLabel>
                                        <Select value={dimension} label="世界" onChange={(e) => setDimension(e.target.value as string)} disabled={Object.keys(worlds).length == 0}>
                                            {Object.entries(worlds).map(([dimId, info]) => (
                                                <MenuItem key={dimId} value={dimId}>
                                                    {info.WorldName || dimId} (TPS: {info.TPS.toFixed(1)})
                                                </MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>
                                </Grid>
                                <Grid size={{ xs: 2, md: 4 }}>
                                    {notMo
                                        ? <Button variant="outlined" onClick={handleVerifyAndAdd} disabled={verifying} size="large"
                                            startIcon={verifying ? <CircularProgress color="inherit" /> : <ArrowForwardIcon fontSize="inherit" />}>
                                            {verifying ? "验证中..." : "验证并添加"}
                                        </Button>
                                        : <IconButton onClick={handleVerifyAndAdd} disabled={verifying} >
                                            {verifying ? <CircularProgress color="inherit" size="2rem" /> : <ArrowForwardIcon fontSize="inherit" />}
                                        </IconButton>}
                                </Grid>
                            </Grid>
                        </CardContent>
                    </Card>
                </Grid>

                <Grid size={{ xs: 12, sm: 6 }}>
                    <Card elevation={6}>
                        <CardHeader title={`已保存的 AE 坐标 (${saved.length})`} />
                        <CardContent>
                            {saved.length === 0
                                ? <Alert severity="info">暂无保存的 AE 坐标，请添加</Alert>
                                : <Grid container spacing={1}>
                                    {saved.map((coord, index) => {
                                        let args = ""
                                        if (selectedIndex === index) {
                                            args = createArgs(coord)
                                        }
                                        return <Grid size={{ xs: 12 }} key={`${coord.x}-${coord.y}-${coord.z}-${coord.dimension}`}>
                                            <Accordion expanded={selectedIndex === index}
                                                onChange={(_, expanded) => setSelectedIndex(expanded ? index : null)}
                                                slotProps={{ transition: { unmountOnExit: true } }}>
                                                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                                    <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
                                                        <StorageIcon color={selectedIndex === index ? "primary" : "action"} fontSize="small" />
                                                        <Box>
                                                            <Typography variant="subtitle2">
                                                                {coord.name}
                                                            </Typography>
                                                            <Typography variant="caption" color="text.secondary">
                                                                X: {coord.x}, Y: {coord.y}, Z: {coord.z} (维度: {coord.dimension})
                                                            </Typography>
                                                        </Box>
                                                    </Box>
                                                </AccordionSummary>
                                                <AccordionDetails>
                                                    <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
                                                        <Button size="small" variant="outlined" startIcon={<AccountTreeIcon />}
                                                            LinkComponent={LinkC} href={`/ae/node?${args}`}>节点信息</Button>
                                                        <Button size="small" variant="outlined" startIcon={<MemoryIcon />}>CPU 信息</Button>
                                                        <Button size="small" variant="outlined" startIcon={<InventoryIcon />}>存储物品</Button>
                                                        <Button size="small" variant="outlined" startIcon={<BuildIcon />}>ME 接口</Button>
                                                        <Button size="small" variant="outlined" startIcon={<ScienceIcon />}>合成任务</Button>
                                                        <Button size="small" variant="outlined" startIcon={<HelpIcon />}>支持列表</Button>
                                                        <Button size="small" variant="outlined" onClick={() => { setRenameValue(coord.name); setRenameOpen(true) }} color="secondary">
                                                            重命名
                                                        </Button>
                                                        <Button size="small" variant="outlined" color="error"
                                                            startIcon={<DeleteIcon />} onClick={() => handleDelete(index)}>
                                                            删除此节点
                                                        </Button>
                                                    </Box>
                                                </AccordionDetails>
                                            </Accordion>
                                        </Grid>
                                    })}
                                </Grid>
                            }
                        </CardContent>
                    </Card>
                </Grid>
            </Grid>

            {selectedIndex != null && <Dialog open={renameOpen} onClose={() => setRenameOpen(false)} fullWidth maxWidth="xs">
                <DialogTitle>修改节点名称</DialogTitle>
                <DialogContent>
                    <TextField
                        autoFocus
                        fullWidth
                        label="名称"
                        value={renameValue}
                        onChange={(e) => setRenameValue(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && handleRename()}
                        sx={{ mt: 1 }}
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setRenameOpen(false)}>取消</Button>
                    <Button variant="contained" onClick={handleRename}>保存</Button>
                </DialogActions>
            </Dialog>}
        </Container>
    )
}
