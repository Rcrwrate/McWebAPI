import { useAPI } from "@/data/api";
import { Skeleton } from "@mui/material";
import { Block } from "@shirokasoke/webapi-sdk";
import { useEffect, useState } from "react";

export default function BlockIcon({ api, block }: { api: NonNullable<ReturnType<typeof useAPI>>; block: Block }) {
    const [url, setUrl] = useState<string | null>(null)
    useEffect(() => {
        api.getBlockTile({ id: block.id, meta: block.meta ?? 0 })
            .then((buf) => {
                const blob = new Blob([buf], { type: "image/png" })
                setUrl(URL.createObjectURL(blob))
            })
            .catch(() => setUrl("https://cos.elysia.rip/block.png"))
    }, [block.id, block.meta])
    if (!url) return <Skeleton variant="rectangular" width={64} height={64} />
    return (
        <img
            key={url}
            src={url}
            alt={block.localizedName}
            style={{ width: 64, height: 64, imageRendering: "pixelated" }}
        />
    )
}