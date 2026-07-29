import { useAPI } from "@/data/api";
import { formatCount } from "@/data/format";
import { Badge, Skeleton } from "@mui/material";
import type { AEStack, ItemStack } from "@shirokasoke/webapi-sdk";
import { useEffect, useState } from "react";

const err_img = "/block.png"

export default function ItemIcon({ api, item, badge }: { api: NonNullable<ReturnType<typeof useAPI>>; item: AEStack, badge?: boolean }) {
    const [url, setUrl] = useState<string | null>(null)
    useEffect(() => {
        let objectUrl: string | null = null
        let icon: Promise<ArrayBuffer>
        if (!("damage" in item)) {
            icon = api.getFluidIcon({ id: item.id, name: item.name })
        } else if (item.registryName == "ae2fc:fluid_drop") {
            icon = api.getFluidIcon({ name: item.nbt?.Fluid as string })
        } else if (item.registryName == "ae2fc:fluid_packet") {
            const fluidStack = item.nbt?.FluidStack as Record<string, unknown> | undefined
            icon = api.getFluidIcon({ name: fluidStack?.FluidName as string })
        } else {
            icon = api.getItemIcon({ id: item.id, damage: item.damage, tag: item.nbtWrite })
        }

        icon.then((buf) => {
            objectUrl = URL.createObjectURL(new Blob([buf], { type: "image/png" }))
            setUrl(objectUrl)
        }).catch(() => setUrl(err_img))

        return () => {
            if (objectUrl) URL.revokeObjectURL(objectUrl)
        }
    }, [api, item])
    if (!url) return <Skeleton variant="rectangular" width={48} height={48} />

    const displayName = item.localizedName ?? ("name" in item ? item.name : item.registryName)

    return badge ? <Badge badgeContent={formatCount(item.stackSize)}
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
            alt={displayName}
            onContextMenu={(e) => e.preventDefault()}
            style={{ width: 48, height: 48, imageRendering: "pixelated", WebkitTouchCallout: "none", userSelect: "none" }}
        />
    </Badge> :
        <img
            key={url}
            src={url}
            alt={displayName}
            onContextMenu={(e) => e.preventDefault()}
            style={{ width: 48, height: 48, imageRendering: "pixelated", WebkitTouchCallout: "none", userSelect: "none" }}
        />
}
