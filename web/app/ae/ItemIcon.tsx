import { useAPI } from "@/data/api";
import { formatCount } from "@/data/format";
import { Badge, Skeleton } from "@mui/material";
import type { ItemStack } from "@shirokasoke/webapi-sdk";
import { useEffect, useState } from "react";

const err_img = "/block.png"

export default function ItemIcon({ api, item, badge }: { api: NonNullable<ReturnType<typeof useAPI>>; item: ItemStack, badge?: boolean }) {
    const [url, setUrl] = useState<string | null>(null)
    useEffect(() => {
        let objectUrl: string | null = null
        if (item.registryName == "ae2fc:fluid_drop") {
            api.getFluidIcon({ name: item.nbt?.Fluid as string })
                .then((buf) => {
                    objectUrl = URL.createObjectURL(new Blob([buf], { type: "image/png" }))
                    setUrl(objectUrl)
                })
                .catch(() => setUrl(err_img))
        } else if (item.registryName == "ae2fc:fluid_packet") {
            api.getFluidIcon({ name: (item.nbt?.FluidStack as any)?.FluidName as string })
                .then((buf) => {
                    objectUrl = URL.createObjectURL(new Blob([buf], { type: "image/png" }))
                    setUrl(objectUrl)
                })
                .catch(() => setUrl(err_img))
        } else {
            api.getItemIcon({ id: item.id, damage: item.damage, tag: item.nbtWrite })
                .then((buf) => {
                    objectUrl = URL.createObjectURL(new Blob([buf], { type: "image/png" }))
                    setUrl(objectUrl)
                })
                .catch(() => setUrl(err_img))
        }

        return () => {
            if (objectUrl) URL.revokeObjectURL(objectUrl)
        }
    }, [item.id, item.damage, item.nbtstr])
    if (!url) return <Skeleton variant="rectangular" width={48} height={48} />

    return badge ? <Badge badgeContent={formatCount(item.stackSize || 0)}
        color="primary" overlap="rectangular" max={999}
        anchorOrigin={{ vertical: "top", horizontal: "right" }}
        sx={{
            "& .MuiBadge-badge": {
                fontSize: "0.65rem",
                minWidth: 16,
                height: 16,
                padding: "0 3px",
                borderRadius: "8px",
            },
        }}>
        <img
            key={url}
            src={url}
            alt={item.localizedName}
            onContextMenu={(e) => e.preventDefault()}
            style={{ width: 48, height: 48, imageRendering: "pixelated", WebkitTouchCallout: "none", userSelect: "none" }}
        />
    </Badge> :
        <img
            key={url}
            src={url}
            alt={item.localizedName}
            onContextMenu={(e) => e.preventDefault()}
            style={{ width: 48, height: 48, imageRendering: "pixelated", WebkitTouchCallout: "none", userSelect: "none" }}
        />
}
