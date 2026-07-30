"use client"

import BoltIcon from "@mui/icons-material/Bolt"
import HelpOutlineIcon from "@mui/icons-material/HelpOutlined"
import Inventory2Icon from "@mui/icons-material/Inventory2"
import PersonOutlineIcon from "@mui/icons-material/PersonOutlined"
import PlaceOutlinedIcon from "@mui/icons-material/PlaceOutlined"
import PrecisionManufacturingIcon from "@mui/icons-material/PrecisionManufacturing"
import SettingsIcon from "@mui/icons-material/Settings"
import { Box, Card, Chip, Grid, LinearProgress, Stack, Typography } from "@mui/material"
import { alpha } from "@mui/material/styles"
import type { GT5MachineInfo, GT5MachineType } from "@shirokasoke/webapi-sdk"

const MC_COLORS: Record<string, string> = {
    "0": "#000000",
    "1": "#0000AA",
    "2": "#00AA00",
    "3": "#00AAAA",
    "4": "#AA0000",
    "5": "#AA00AA",
    "6": "#FFAA00",
    "7": "#AAAAAA",
    "8": "#555555",
    "9": "#5555FF",
    "a": "#55FF55",
    "b": "#55FFFF",
    "c": "#FF5555",
    "d": "#FF55FF",
    "e": "#FFFF55",
    "f": "#FFFFFF",
}

const MC_LIGHT_COLORS: Record<string, string> = {
    "0": "#1F2328",
    "1": "#000080",
    "2": "#087A08",
    "3": "#007A7A",
    "4": "#990000",
    "5": "#900090",
    "6": "#9A6500",
    "7": "#686868",
    "8": "#4A4A4A",
    "9": "#3535C4",
    "a": "#168316",
    "b": "#007F7F",
    "c": "#C62828",
    "d": "#B02AB0",
    "e": "#766400",
    "f": "#1F2328",
}

const MACHINE_META: Record<GT5MachineType, { label: string; icon: React.ElementType }> = {
    MULTIBLOCK: { label: "多方块结构", icon: PrecisionManufacturingIcon },
    SINGLE: { label: "单方块机器", icon: SettingsIcon },
    GENERATOR: { label: "发电机", icon: BoltIcon },
    HATCH: { label: "仓室", icon: Inventory2Icon },
    UNKNOWN: { label: "未知设备", icon: HelpOutlineIcon },
}

interface StyledSegment {
    text: string
    colorCode?: string
    bold?: boolean
    italic?: boolean
    underline?: boolean
    strikethrough?: boolean
    obfuscated?: boolean
}

interface MachineStatusMeta {
    label: string
    color: "success" | "warning" | "error" | undefined
    paletteColor: "success.main" | "warning.main" | "error.main" | "text.disabled"
    process: number
}

function parseMCFormat(raw: string): StyledSegment[] {
    const segments: StyledSegment[] = []
    let current: StyledSegment = { text: "" }
    let i = 0

    while (i < raw.length) {
        if (raw[i] === "§" && i + 1 < raw.length) {
            if (current.text) segments.push({ ...current })

            const code = raw[i + 1].toLowerCase()
            if (code === "r") {
                current = { text: "" }
            } else if (code === "l") {
                current = { ...current, text: "", bold: true }
            } else if (code === "o") {
                current = { ...current, text: "", italic: true }
            } else if (code === "n") {
                current = { ...current, text: "", underline: true }
            } else if (code === "m") {
                current = { ...current, text: "", strikethrough: true }
            } else if (code === "k") {
                current = { ...current, text: "", obfuscated: true }
            } else if (MC_COLORS[code]) {
                current = { text: "", colorCode: code }
            }

            i += 2
        } else {
            current.text += raw[i]
            i++
        }
    }

    if (current.text) segments.push({ ...current })
    return segments
}

function getMachineStatus(machine: GT5MachineInfo): MachineStatusMeta {

    let process = 100
    switch (machine.machineType) {
        case "MULTIBLOCK":
            process = machine.multi.maxProgressTime > 0 ? (machine.multi.progressTime / machine.multi.maxProgressTime) * 100 : 100
            break
        case "SINGLE":
            process = machine.single.maxProgressTime > 0 ? (machine.single.progressTime / machine.single.maxProgressTime) * 100 : 100
            break
    }

    if (machine.state.wasShutdown) {
        return machine.state.lastShutDownReason?.wasCritical
            ? { label: "错误", color: "error", paletteColor: "error.main", process }
            : { label: "需要维护", color: "warning", paletteColor: "warning.main", process }
    }

    if (machine.machineType === "MULTIBLOCK") {
        const maintenance = machine.multi.maintenance
        const healthy = Object.values(maintenance).every(Boolean)
        if (!healthy) return { label: "需要维护", color: "warning", paletteColor: "warning.main", process }
    }

    if (machine.state.isActive) {
        return { label: "运行中", color: "success", paletteColor: "success.main", process }
    }
    return { label: "空闲", color: undefined, paletteColor: "text.disabled", process }
}

function GTInfoLine({ line, index }: { line: string; index: number }) {
    const segments = parseMCFormat(line)

    return <Grid container role="listitem" columnSpacing={1} sx={{ py: 0.375, minWidth: 0 }}>
        <Grid size="auto" sx={{ display: { xs: "none", sm: "block" }, width: "2rem" }}>
            <Typography aria-hidden="true" component="span" sx={{
                display: "block",
                color: "text.disabled",
                fontFamily: '"Roboto Mono", "Courier New", monospace',
                fontSize: "0.75rem",
                lineHeight: 1.7,
                textAlign: "right",
                userSelect: "none",
            }}>
                {String(index + 1).padStart(2, "0")}
            </Typography>
        </Grid>
        <Grid size="grow" sx={{ minWidth: 0 }}>
            <Typography variant="body2" component="div" sx={{
                color: "text.secondary",
                fontFamily: '"Roboto Mono", "Courier New", monospace',
                fontSize: { xs: "0.78rem", sm: "0.84rem", lg: "0.875rem" },
                lineHeight: { xs: 1.55, sm: 1.65 },
                overflowWrap: "anywhere",
                whiteSpace: "pre-wrap",
            }}>
                {segments.map((segment, segmentIndex) => (
                    <Box component="span" key={segmentIndex} sx={(theme) => ({
                        color: segment.colorCode
                            ? (theme.palette.mode === "dark" ? MC_COLORS[segment.colorCode] : MC_LIGHT_COLORS[segment.colorCode])
                            : theme.palette.text.secondary,
                        fontWeight: segment.bold ? 700 : 400,
                        fontStyle: segment.italic ? "italic" : "normal",
                        textDecoration: [
                            segment.underline ? "underline" : "",
                            segment.strikethrough ? "line-through" : "",
                        ].filter(Boolean).join(" ") || "none",
                    })}>
                        {segment.text}
                    </Box>
                ))}
            </Typography>
        </Grid>
    </Grid>
}

export interface GTInfoCardProps {
    machine: GT5MachineInfo | null
}

export default function GTInfoCard({ machine }: GTInfoCardProps) {
    if (!machine) return null

    const infoLines = machine.state.Info
    if (!infoLines?.length) return null

    const status = getMachineStatus(machine)
    const meta = MACHINE_META[machine.machineType]
    const MachineIcon = meta.icon

    return <Card sx={{
        minWidth: 0, overflow: "hidden", borderTop: "3px solid",
        borderTopColor: status.paletteColor,
        borderRadius: 2,
    }}>

        <Box sx={(theme) => ({
            p: { xs: 1.5, sm: 2 },
            backgroundColor: alpha(theme.palette[status.color ?? "primary"].main, theme.palette.mode === "dark" ? 0.08 : 0.04),
        })}>
            <Grid container spacing={1.25} sx={{ minWidth: 0, alignItems: "flex-start" }}>
                <Grid size="auto">
                    <Box sx={(theme) => ({
                        width: { xs: 36, sm: 42 },
                        height: { xs: 36, sm: 42 },
                        display: "grid",
                        placeItems: "center",
                        borderRadius: 1.5,
                        color: status.paletteColor,
                        backgroundColor: alpha(theme.palette[status.color ?? "primary"].main, 0.12),
                    })}>
                        <MachineIcon sx={{ fontSize: { xs: 21, sm: 24 } }} />
                    </Box>
                </Grid>
                <Grid size="grow" sx={{ minWidth: 0 }}>
                    <Typography component="h3" sx={{
                        fontSize: { xs: "0.95rem", sm: "1.05rem" },
                        fontWeight: 700,
                        lineHeight: 1.35,
                        overflowWrap: "anywhere",
                    }}>
                        {machine.localName}
                    </Typography>
                    <Typography color="text.secondary" sx={{
                        mt: 0.25,
                        fontFamily: '"Roboto Mono", "Courier New", monospace',
                        fontSize: "0.72rem",
                        lineHeight: 1.35,
                        overflowWrap: "anywhere",
                    }}>
                        {machine.internalName}
                    </Typography>
                </Grid>
                <Grid size="auto">
                    <Chip size="small" color={status.color} variant={status.color ? "outlined" : "filled"} label={status.label} sx={{ fontWeight: 600 }} />
                </Grid>
            </Grid>
            <Stack direction="row" useFlexGap sx={{
                alignItems: "center",
                flexWrap: "wrap",
                columnGap: { xs: 1.25, sm: 2 },
                rowGap: 0.5,
                mt: 1.25,
                color: "text.secondary",
            }}>
                <MachineIcon sx={{ fontSize: 15 }} />
                <Typography variant="caption">{meta.label}</Typography>
                <PlaceOutlinedIcon sx={{ fontSize: 15 }} />
                <Typography variant="caption">
                    ({machine.x}, {machine.y}, {machine.z}) · DIM {machine.dimension}
                </Typography>
                <PersonOutlineIcon sx={{ fontSize: 15, flex: "0 0 auto" }} />
                <Typography variant="caption" sx={{ overflowWrap: "anywhere" }}>{machine.owner}</Typography>
            </Stack>
        </Box>
        <LinearProgress variant="determinate" value={status.process} color={status.color} sx={{ height: 3 }} />
        <Stack role="list" sx={{ px: { xs: 1.5, sm: 2 }, py: { xs: 1.25, sm: 1.5 } }}>
            {infoLines.map((line, index) => <GTInfoLine key={`${index}-${line}`} line={line} index={index} />)}
        </Stack>
    </Card>
}
