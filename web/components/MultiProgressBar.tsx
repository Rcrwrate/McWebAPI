"use client"

import { Box, Typography, type TypographyOwnProps, type SxProps, type Theme } from "@mui/material"
import type { AliasesCSSProperties } from "@mui/system"

export interface MultiSegment {
    value: number
    color: AliasesCSSProperties["bgcolor"]
    /** 图例标签 */
    label?: string
}

export interface MultiProgressBarProps {
    /** 各段数据，按顺序从左到右排列 */
    segments: MultiSegment[]
    height?: number
    sx?: SxProps<Theme>
}

export interface MultiProgressLegendProps {
    segments: MultiSegment[]
    variant?: TypographyOwnProps["variant"]
    sx?: SxProps<Theme>
    itemSx?: SxProps<Theme>
}

/**
 * 多段进度条：根据各段 value 比例渲染不同颜色的条带。
 * 当所有 value 之和为 0 时，渲染占位灰色条。
 */
export function MultiProgressBar({
    segments,
    height = 6,
    sx,
}: MultiProgressBarProps) {
    const total = segments.reduce((s, seg) => s + seg.value, 0)

    if (total === 0) {
        return <Box sx={{ height, borderRadius: height / 2 + 1, bgcolor: "action.hover" }} />
    }

    return (
        <Box sx={{
            display: "flex",
            height,
            borderRadius: height / 2 + 1,
            overflow: "hidden",
            ...sx,
        }}>
            {segments.map((seg, i) => {
                const width = seg.value <= 0 ? 0 : (seg.value / total) * 100
                if (width === 0) return null
                return <Box key={i} sx={{
                    width: `${width}%`,
                    bgcolor: seg.color,
                    transition: "width 0.5s cubic-bezier(0.4, 0, 0.2, 1)",
                }} />
            })}
        </Box>
    )
}

/**
 * 多段进度条图例：颜色方块 + 标签，横向排列。
 * 仅渲染带 label 的段。
 */
export function MultiProgressLegend({
    segments,
    variant = "caption",
    sx,
    itemSx,
}: MultiProgressLegendProps) {
    return (
        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1.5, ...sx }}>
            {segments.map((seg, i) => (
                seg.label && (<Box key={i} sx={{ display: "flex", alignItems: "center", gap: 0.5, ...itemSx }}>
                    <Box sx={{ width: 12, height: 12, borderRadius: 1, bgcolor: seg.color }} />
                    <Typography variant={variant} color="textPrimary">{seg.label}</Typography>
                </Box>)
            ))}
        </Box>
    )
}
