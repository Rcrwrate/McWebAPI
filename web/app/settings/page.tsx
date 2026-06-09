"use client"

import CachedIcon from '@mui/icons-material/Cached';
import ColorLensIcon from '@mui/icons-material/ColorLens';
import ImageIcon from '@mui/icons-material/Image';
import SettingsIcon from '@mui/icons-material/Settings';
import { Button, FormControl, Grid, MenuItem, Select, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, styled } from "@mui/material";
import { enqueueSnackbar } from "notistack";
import { useEffect, useState } from "react";
import ColorSetting from "./Color";
import { WebApiClient } from "@shirokasoke/webapi-sdk"

const H2 = styled("h2")(({ theme }) => ({
    backgroundColor: theme.palette.mode === 'dark' ? '#1A2027' : '#e5dfdf',
    padding: theme.spacing(1),
    textAlign: 'center',
    color: theme.palette.mode === 'dark' ? '#ffffff' : 'black',
    borderRadius: 12,
    margin: theme.spacing(1)
}));

const def = {
    url: "http://127.0.0.1:40002/",
    auth: ""
}

export default function Setting() {
    const [url, setUrl] = useState(def.url)
    const [auth, setAuth] = useState(def.auth)
    const [data, setdata] = useState<{ key: string; length: number; }[]>([])
    const [CacheStatus, setCS] = useState(false)

    async function cache() {
        const r = await navigator.serviceWorker.getRegistrations()
        if (r.length > 0) {
            setCS(true)
        } else {
            setCS(false)
        }
        const keys = await caches.keys()
        setdata(await Promise.all(keys.map(async i => {
            const t = await caches.open(i)
            return {
                key: i,
                length: (await t.keys()).length
            }
        })))
    }

    function key(k: string) {
        switch (k) {
            case "cross-origin":
                return k + "(引用文件)"
            case "pages-rsc":
                return k + "(动态数据)"
            case "pages-rsc-prefetch":
                return k + "(预加载)"
            case "next-static-css-assets":
                return k + "(静态文件)"
            case "next-static-js-assets":
                return k + "(静态文件)"
            case "static-data-assets":
                return k + "(静态数据)"
            case "static-image-assets":
                return k + "(图片缓存)"
            case "pages":
                return k + "(页面缓存)"
            default:
                return k
        }
    }

    useEffect(() => {
        setUrl(localStorage.getItem("url") ?? def.url)
        setAuth(localStorage.getItem("auth") ?? def.auth)
        cache()
    }, [])


    return <>
        <title>设置</title>
        <Grid container sx={{ color: "text.primary", textAlign: "center", justifyContent: "center", alignItems: "stretch" }} spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
                <H2>
                    <SettingsIcon /> WebAPI地址
                </H2>
                <Grid container sx={{ p: 2, justifyContent: "center" }} spacing={2}>
                    <Grid size={{ xs: 12, md: 6 }}>
                        <TextField label="WebAPI地址" variant="outlined" value={url} onChange={(e) => { setUrl(e.target.value) }} />
                    </Grid>
                    <Grid size={{ xs: 12, md: 6 }}>
                        <TextField label="Auth" variant="outlined" value={auth} onChange={(e) => { setAuth(e.target.value) }} />
                    </Grid>

                    <Button variant="contained" color="success" onClick={() => {
                        localStorage.setItem("url", url)
                        if (auth.length > 0) {
                            localStorage.setItem("auth", auth)
                        } else {
                            localStorage.removeItem("auth")
                        }
                        enqueueSnackbar("保存成功，重新加载后生效", { variant: "success" })
                    }}>保存修改</Button>
                    <Button variant="contained" color="info" onClick={async () => {
                        const api = new WebApiClient({ baseUrl: url, authToken: auth, fetch: (input: RequestInfo | URL, init?: RequestInit) => window.fetch(input, init) })
                        try {
                            const r = await api.getRoot()
                            enqueueSnackbar(`连接成功，服务端版本 ${r.version}`, { variant: "success" })
                        } catch (e) {
                            enqueueSnackbar(`连接失败 ${e}`, { variant: "error" })
                        }
                    }}>测试连接</Button>
                    <Button variant="contained" color="error" onClick={() => {
                        setUrl(def.url)
                        setAuth(def.auth)
                        enqueueSnackbar("已重置，但未保存", { variant: "warning" })
                    }}>重置设置</Button>
                </Grid>
                <H2>
                    <CachedIcon /> 缓存控制
                </H2>
                <Stack direction="row" spacing={2} sx={{ justifyContent: "center", flexWrap: "wrap" }} useFlexGap >
                    <p>当前缓存服务</p>
                    <Button variant="outlined" color={CacheStatus ? "success" : "error"} onClick={async () => {
                        if (CacheStatus) {
                            localStorage.setItem("noSw", "true")
                            const r = await navigator.serviceWorker.getRegistrations()
                            await Promise.all(r.map(i => i.unregister()))
                            enqueueSnackbar(`已尝试注销服务进程`, { variant: 'info' })
                        } else {
                            localStorage.setItem("noSw", "false")
                            await navigator.serviceWorker.register("/sw.js")
                            enqueueSnackbar(`已尝试注册服务进程`, { variant: 'info' })
                        }
                        cache()
                    }}>
                        {CacheStatus ? "已启用" : "未启用"}
                    </Button>
                </Stack>

                <div style={{ padding: 10 }}>
                    <Table sx={{ "th": { textAlign: 'center' }, "td": { textAlign: 'center' } }}>
                        <TableHead>
                            <TableRow>
                                <TableCell>储存桶</TableCell>
                                <TableCell>总数</TableCell>
                                <TableCell>管理</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {data.map((row) => (
                                <TableRow
                                    key={row.key}
                                    sx={{ '&:last-child td, &:last-child th': { border: 0 } }}>
                                    <TableCell component="th" scope="row">
                                        {key(row.key)}
                                    </TableCell>
                                    <TableCell>{row.length}</TableCell>
                                    <TableCell>
                                        <Button variant="contained" color="warning" onClick={async () => {
                                            const status = await caches.delete(row.key)
                                            cache()
                                            if (status) {
                                                enqueueSnackbar(`${row.key}清除成功`, { variant: 'success' })
                                            } else {
                                                enqueueSnackbar(`${row.key}清除失败`, { variant: 'error' })
                                            }
                                        }}>
                                            清空
                                        </Button>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                    <Button variant="contained" sx={{ m: 3 }} color="error" onClick={async () => {
                        await Promise.all(data.map(async i => caches.delete(i.key)))
                        cache()
                        enqueueSnackbar(`已清空`, { variant: 'info' })
                    }}>
                        全部清空
                    </Button>
                </div>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
                <H2>
                    <ImageIcon />图片来源
                </H2>
                <div style={{ paddingTop: 10 }}>
                    <p>暂不支持采用其他图片来源</p>
                    <FormControl>
                        <Select value={"default"} disabled>
                            <MenuItem value="default">默认(跟随API提供)</MenuItem>
                        </Select>
                    </FormControl>
                </div>
            </Grid>
            <Grid size={12}>
                <H2>
                    <ColorLensIcon />主题配色
                </H2>
                <div style={{ paddingTop: 10 }}>
                    <ColorSetting />
                </div >
            </Grid>
        </Grid>
    </>
}