import Box from "@mui/material/Box"
import Tooltip from "@mui/material/Tooltip"
import Typography from "@mui/material/Typography"
import type { ItemStack } from "@shirokasoke/webapi-sdk"
import type { JSX } from "react/jsx-runtime"

export default function MCToolitip({ item, k, children }: { item: ItemStack, k: string, children: JSX.Element }) {
    return <Tooltip key={k} arrow placement="top"
        title={<Box>
            <Typography variant="body2">{item.localizedName}</Typography>
            <Typography variant="caption" sx={{ color: "#aaa" }} component="div">
                #{item.id}{item.damage ? `:${item.damage}` : ""}
            </Typography>
            <Typography variant="caption" sx={{ color: "#55aaff" }} component="div">
                {item.registryName}
            </Typography>
            <Typography variant="caption" sx={{ color: "#66b2ff" }} component="div">
                数量: {item.stackSize ?? 0}
            </Typography>
        </Box>}
        slotProps={{
            tooltip: {
                sx: {
                    bgcolor: "rgba(16, 0, 32, 0.92)",
                    border: "1px solid rgba(80, 0, 160, 0.7)",
                    boxShadow: 4,
                    maxWidth: 320,
                    "& .MuiTooltip-arrow": {
                        color: "rgba(16, 0, 32, 0.92)",
                    },
                },
            },
        }}>
        {children}
    </Tooltip>
}