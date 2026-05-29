import { useAPI } from "@/data/api"
import { Skeleton } from "@mui/material"
import type { AEItemStack } from "@shirokasoke/webapi-sdk"
import { useEffect, useState } from "react"

export default function ItemIcon({ api, item }: { api: NonNullable<ReturnType<typeof useAPI>>; item: AEItemStack }) {
    const [url, setUrl] = useState<string | null>(null)
    useEffect(() => {
        let objectUrl: string | null = null
        api.getItemIcon({ id: item.id, damage: item.damage, tag: item.nbtWrite })
            .then((buf) => {
                objectUrl = URL.createObjectURL(new Blob([buf], { type: "image/png" }))
                setUrl(objectUrl)
            })
            .catch(() => setUrl("https://cos.elysia.rip/block.png"))
        return () => {
            if (objectUrl) URL.revokeObjectURL(objectUrl)
        }
    }, [api, item.id, item.damage, item.nbtstr])
    if (!url) return <Skeleton variant="rectangular" width={48} height={48} />
    return (
        <img
            key={url}
            src={url}
            alt={item.localizedName}
            style={{ width: 48, height: 48, imageRendering: "pixelated" }}
        />
    )
}
