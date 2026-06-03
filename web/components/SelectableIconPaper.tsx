import { Paper, styled } from "@mui/material"

export interface SelectableIconPaperProps extends React.ComponentProps<typeof Paper> {
    selected?: boolean
}

export const SelectableIconPaper = styled(Paper, {
    shouldForwardProp: (prop) => prop !== "selected",
})<SelectableIconPaperProps>(({ theme, selected }) => ({
    position: "relative",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    aspectRatio: "1 / 1",
    padding: theme.spacing(0.5),
    cursor: "pointer",
    border: `1px solid ${selected ? theme.palette.primary.main : theme.palette.divider}`,
}))
