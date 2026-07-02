import ItemIcon from "@/app/ae/ItemIcon"
import { useAPI } from "@/data/api"
import { formatCount } from "@/data/format"
import { Box, Card, CardContent, Typography } from "@mui/material"
import { AEItemsResult } from "@shirokasoke/webapi-sdk"
import { useEffect, useState } from "react"

interface coord {
    x: number;
    y: number;
    z: number;
    dimension?: number;
}

type AEItemReq = coord & { id: number, damage: number, nbtWrite?: string }

export function AEItemContent({ data, req }: { data: AEItemsResult, req?: AEItemReq }) {
    const api = useAPI()
    if (!req) return <></>
    const matched = data.items.find(
        i => i.id === req.id && i.damage === req.damage && (i.nbtWrite ?? "") === (req.nbtWrite ?? "")
    )
    const count = matched?.stackSize ?? 0
    const [history, setH] = useState<number[]>([0, 0])
    useEffect(() => {
        setH((prev) => [prev.pop() ?? 0, count])
    }, [data])

    const change = history[1] - history[0]
    return <Card sx={{ height: "100%" }}>
        <CardContent sx={{
            display: "flex", alignItems: "center", justifyContent: "center",
            gap: 1.5, height: "100%", boxSizing: "border-box",
            py: 1.5, "&:last-child": { pb: 1.5 },
        }}>
            {api && matched && <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                <ItemIcon api={api} item={matched} />
            </Box>}
            <Box sx={{ minWidth: 0, flex: 1, textAlign: "center" }}>
                <Typography variant="h5" color={change < 0 ? "error" : change == 0 ? "info" : "success"} noWrap>
                    {formatCount(count)}    <small>{change < 0 ? "-" : "+"}{formatCount(change < 0 ? -change : change)}</small>
                </Typography>
                <Typography variant="body1" noWrap title={matched?.localizedName}>
                    {matched ? matched.localizedName : `物品 #${req.id}:${req.damage}`}
                </Typography>
            </Box>
        </CardContent>
    </Card>
}