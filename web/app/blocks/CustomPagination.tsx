import { Box, TablePagination, TextField, Button } from "@mui/material"
import { useGridApiContext, useGridSelector, gridPageSelector, gridFilteredRowCountSelector, gridPaginationModelSelector } from "@mui/x-data-grid"
import { useState } from "react"

export default function CustomPagination() {
    const apiRef = useGridApiContext()
    const page = useGridSelector(apiRef, gridPageSelector)
    const rowCount = useGridSelector(apiRef, gridFilteredRowCountSelector)
    const paginationModel = useGridSelector(apiRef, gridPaginationModelSelector)
    const [input, setInput] = useState("")

    return (
        <Box sx={{ display: "flex", alignItems: "center", gap: 1, px: 2 }}>
            <TablePagination
                component="div"
                count={rowCount}
                page={page}
                rowsPerPage={paginationModel.pageSize}
                onPageChange={(_, newPage) => apiRef.current.setPage(newPage)}
                onRowsPerPageChange={(e) =>
                    apiRef.current.setPageSize(Number(e.target.value))
                }
                rowsPerPageOptions={[25, 50, 100, 500]}
                showFirstButton
                showLastButton
                labelRowsPerPage="每页"
                labelDisplayedRows={({ from, to, count }) =>
                    `${from}-${to} / ${count}`
                }
            />
            <TextField
                size="small"
                label="跳至"
                placeholder="页码"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                sx={{ width: 90 }}
                onKeyDown={(e) => {
                    if (e.key === "Enter") {
                        const target = Number(input) - 1
                        if (!Number.isNaN(target) && target >= 0) {
                            apiRef.current.setPage(target)
                        }
                        setInput("")
                    }
                }}
            />
            <Button
                size="small"
                variant="outlined"
                onClick={() => {
                    const target = Number(input) - 1
                    if (!Number.isNaN(target) && target >= 0) {
                        apiRef.current.setPage(target)
                    }
                    setInput("")
                }}
            >
                GO
            </Button>
        </Box>
    )
}