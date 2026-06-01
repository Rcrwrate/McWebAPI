import Box from "@mui/material/Box";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import LinearProgress from "@mui/material/LinearProgress";
import Typography from "@mui/material/Typography";

export default function Percent({ percent, title, subtitle }: { percent: number, title: string, subtitle?: string }) {
    const color = percent >= 90 ? "error" : percent >= 70 ? "warning" : "primary"
    return <Card>
        <LinearProgress variant="determinate" value={percent} color={color} sx={{ height: 6 }} />
        <CardContent sx={{ py: 2 }}>
            <Box sx={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 2 }}>
                <Typography variant="h4" color={color}>
                    {percent.toFixed(0)}%
                </Typography>
                {subtitle && <Typography variant="body2" color="textDisabled" sx={{ textAlign: "right" }}>
                    {subtitle}
                </Typography>}
            </Box>
            <Typography variant="body2">
                {title}
            </Typography>
        </CardContent>
    </Card>
}