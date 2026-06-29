import LinearProgress, { LinearProgressProps } from "@mui/material/LinearProgress";
import Typography from "@mui/material/Typography";

export default function TinyProcess({ value, color }: { value: number, color: LinearProgressProps["color"] }) {
    return <div style={{ width: "100%", height: "100%", display: "flex", alignItems: "center", gap: 8, paddingRight: 8 }}>
        <LinearProgress variant="determinate" value={value} color={color} sx={{ flexGrow: 1, height: 6, borderRadius: 1 }} />
        <Typography variant="caption" sx={{ whiteSpace: "nowrap" }}>
            {value.toFixed(0)}%
        </Typography>
    </div>
}