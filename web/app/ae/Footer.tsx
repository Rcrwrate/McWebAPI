"use client"

import AccountTreeIcon from '@mui/icons-material/AccountTree'
import BuildIcon from "@mui/icons-material/Build"
import HelpIcon from "@mui/icons-material/Help"
import InventoryIcon from "@mui/icons-material/Inventory"
import KeyboardBackspaceIcon from '@mui/icons-material/KeyboardBackspace'
import MemoryIcon from "@mui/icons-material/Memory"
import ScienceIcon from "@mui/icons-material/Science"
import Box from "@mui/material/Box"
import Button from "@mui/material/Button"
import { default as LinkC } from "next/link"
import { JSX } from 'react/jsx-runtime'

export function Footer({ args }: { args: string }) {
    return <FooterMore args={args}
        childrens={<Button size="small" variant="outlined" color="warning" startIcon={<KeyboardBackspaceIcon />}
            LinkComponent={LinkC} href={`/ae`}>返回</Button>} />
}

export function FooterMore({ args, childrens }: { args: string, childrens: JSX.Element | JSX.Element[] }) {
    return <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, justifyContent: "center" }}>
        <Button size="small" variant="outlined" startIcon={<AccountTreeIcon />}
            LinkComponent={LinkC} href={`/ae/node?${args}`}>节点信息</Button>
        <Button size="small" variant="outlined" startIcon={<MemoryIcon />}
            LinkComponent={LinkC} href={`/ae/cpu?${args}`}>CPU 信息</Button>
        <Button size="small" variant="outlined" startIcon={<InventoryIcon />}
            LinkComponent={LinkC} href={`/ae/item?${args}`}>存储物品</Button>
        <Button size="small" variant="outlined" startIcon={<BuildIcon />}
            LinkComponent={LinkC} href={`/ae/me?${args}`}>ME 接口</Button>
        <Button size="small" variant="outlined" startIcon={<ScienceIcon />}
            LinkComponent={LinkC} href={`/ae/task?${args}`}>合成任务</Button>
        <Button size="small" variant="outlined" startIcon={<HelpIcon />}
            LinkComponent={LinkC} href={`/ae/me/support?${args}`}>支持列表</Button>
        {childrens}
    </Box>
}