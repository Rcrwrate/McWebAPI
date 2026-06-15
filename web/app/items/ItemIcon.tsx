import { useAPI } from "@/data/api"
import { Skeleton } from "@mui/material"
import type { ItemStack } from "@shirokasoke/webapi-sdk"
import { useEffect, useState } from "react"

const err_img = "/block.png"

export default function ItemIcon({ api, item, size = 48 }: { api: NonNullable<ReturnType<typeof useAPI>>; item: ItemStack; size?: number }) {
    const [url, setUrl] = useState<string | null>(null)

    useEffect(() => {
        let objectUrl: string | null = null
        api.getItemIcon({ id: item.id, damage: item.damage, tag: item.nbtWrite })
            .then((buf) => {
                objectUrl = URL.createObjectURL(new Blob([buf], { type: "image/png" }))
                setUrl(objectUrl)
            })
            .catch(() => setUrl(err_img))

        return () => {
            if (objectUrl) URL.revokeObjectURL(objectUrl)
        }
    }, [item.id, item.damage, item.nbtstr])

    if (!url) return <Skeleton variant="rectangular" width={size} height={size} />

    return (
        <img
            key={url}
            src={url}
            alt={item.localizedName}
            style={{ width: size, height: size, imageRendering: "pixelated" }}
        />
    )
}
