"use client"

import { H2 } from "@/components/H2"
import { RContainer } from "@/components/RContainer"
import { useAPI } from "@/data/api"
import RadarIcon from "@mui/icons-material/Radar"
import {
    Box,
    Button,
    Card,
    CardActionArea,
    CardContent,
    CircularProgress,
    Typography,
} from "@mui/material"
import { default as LinkC } from "next/link"

export default function GT5Page() {
    const api = useAPI()

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
            <H2>GT5 机器管理</H2>
            <Box sx={{ display: "flex", justifyContent: "center" }}>
                <Button size="large" variant="contained" color="primary" startIcon={<RadarIcon />}
                    LinkComponent={LinkC} href={`/gt5/scan`}>扫描GT设备</Button>
            </Box>
        </RContainer>
    )
}
