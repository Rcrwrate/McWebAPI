import { useAPI } from "@/data/api";
import { Skeleton } from "@mui/material";
import type { ItemStack } from "@shirokasoke/webapi-sdk";
import { useEffect, useState } from "react";

export default function ItemIcon({ api, item }: { api: NonNullable<ReturnType<typeof useAPI>>; item: ItemStack }) {
    const [url, setUrl] = useState<string | null>(null)
    useEffect(() => {
        let objectUrl: string | null = null
        if (item.registryName == "ae2fc:fluid_drop") {
            api.getFluidIcon({ name: item.nbt?.Fluid as string })
                .then((buf) => {
                    objectUrl = URL.createObjectURL(new Blob([buf], { type: "image/png" }))
                    setUrl(objectUrl)
                })
                .catch(() => setUrl("https://cos.elysia.rip/block.png"))
        } else if (item.registryName == "ae2fc:fluid_packet") {
            api.getFluidIcon({ name: (item.nbt?.FluidStack as any)?.FluidName as string })
                .then((buf) => {
                    objectUrl = URL.createObjectURL(new Blob([buf], { type: "image/png" }))
                    setUrl(objectUrl)
                })
                .catch(() => setUrl("https://cos.elysia.rip/block.png"))
        } else {
            api.getItemIcon({ id: item.id, damage: item.damage, tag: item.nbtWrite })
                .then((buf) => {
                    objectUrl = URL.createObjectURL(new Blob([buf], { type: "image/png" }))
                    setUrl(objectUrl)
                })
                .catch(() => setUrl("https://cos.elysia.rip/block.png"))
        }

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
