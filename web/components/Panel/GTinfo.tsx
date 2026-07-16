"use client"

import { Card, CardContent, Typography } from "@mui/material"
import type { GT5MachineInfo } from "@shirokasoke/webapi-sdk"

// Minecraft § 颜色码映射
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

interface StyledSegment {
    text: string
    color?: string
    bold?: boolean
    italic?: boolean
    underline?: boolean
    strikethrough?: boolean
    obfuscated?: boolean
}

/**
 * 解析 MC § 格式字符串为带样式片段列表
 */
function parseMCFormat(raw: string): StyledSegment[] {
    const segments: StyledSegment[] = []
    let current: StyledSegment = { text: "" }
    let i = 0

    while (i < raw.length) {
        if (raw[i] === "§" && i + 1 < raw.length) {
            // 提交之前的片段
            if (current.text) {
                segments.push({ ...current })
            }

            const code = raw[i + 1].toLowerCase()

            if (code === "r") {
                // 重置所有样式
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
                // 颜色码会重置样式格式
                current = { text: "", color: MC_COLORS[code] }
            }

            i += 2
        } else {
            current.text += raw[i]
            i++
        }
    }

    if (current.text) {
        segments.push({ ...current })
    }

    return segments
}

/**
 * 渲染单个 Info 行
 */
function GTInfoLine({ line, index }: { line: string; index: number }) {
    const segments = parseMCFormat(line)

    return <Typography key={index} variant="body2" component="div" sx={{
        fontFamily: '"Courier New", monospace',
        fontSize: "0.875rem",
        lineHeight: 1.6,
        py: 0.25,
    }}>
        {segments.map((seg, i) => <span key={i} style={{
            color: seg.color ?? "#AAAAAA",
            fontWeight: seg.bold ? "bold" : "normal",
            fontStyle: seg.italic ? "italic" : "normal",
            textDecoration: [
                seg.underline ? "underline" : "",
                seg.strikethrough ? "line-through" : "",
            ].filter(Boolean).join(" ") || undefined,
        }}>
            {seg.text}
        </span>)}
    </Typography>
}

export interface GTInfoCardProps {
    machine: GT5MachineInfo | null
}

/**
 * GT 设备信息卡片
 * 支持 MC § 色彩码
 */
export default function GTInfoCard({ machine }: GTInfoCardProps) {
    if (!machine) return null

    const infoLines = machine.state.Info
    if (!infoLines || infoLines.length === 0) return null

    return <Card sx={{
        bgcolor: "rgba(16, 0, 32, 0.85)",
        border: "1px solid rgba(80, 0, 160, 0.5)",
        boxShadow: 4,
        backdropFilter: "blur(8px)",
    }}>
        <CardContent sx={{ py: 1.5, "&:last-child": { pb: 1.5 } }}>
            <Typography variant="subtitle2" sx={{
                mb: 1,
                color: "#AAAAAA",
                fontFamily: '"Courier New", monospace',
                borderBottom: "1px solid rgba(80, 0, 160, 0.3)",
                pb: 0.5,
            }}>
                设备信息
            </Typography>
            {infoLines.map((line, i) => (
                <GTInfoLine key={i} line={line} index={i} />
            ))}
        </CardContent>
    </Card>
}
