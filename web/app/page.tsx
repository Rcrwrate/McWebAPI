"use client"

import Link from "next/link";
import style from './style.module.css'
import { Grid, Container, Typography } from "@mui/material";

export default function Home() {
    return (
        <Container sx={{ paddingTop: 10, textAlign: 'center' }}>
            <Grid container>
                <Grid size={{ xs: 12, md: 6 }}>
                    <img
                        loading="lazy"
                        className={style.anime}
                        src="https://cos.elysia.rip/1721705583-1721662118000.jpg"
                        style={{ maxWidth: "100%" }}
                    />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }} sx={{ height: '100vh', color: (theme) => theme.palette.text.primary, width: "100%" }} >
                    <div style={{ height: "20%" }}> </div>
                    <Typography variant="h3">GTNH WebAPI</Typography>
                    <p>GTNH Web控制台</p>
                    <p>项目地址：<Link href='https://github.com/Rcrwrate/McWebAPI'>GITHUB</Link></p>
                    <p>项目地址：<Link href='https://cnb.cool/shirokasoke/McWebAPI'>CNB</Link></p>
                </Grid>
            </Grid>
        </Container>
    )
}
