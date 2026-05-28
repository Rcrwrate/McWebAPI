"use client"

import { createTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';


export function useTheme() {
    const getOri = (mode: "light" | "dark") => {
        let theme = createTheme({
            palette: {
                mode: mode,
                ...(
                    mode == "light"
                        ? {
                            text: {
                                primary: "#000000",
                            },
                        }
                        : {
                            text: {
                                primary: "#ffffff",
                                secondary: "#B0B8C4"
                            },
                            background: {
                                default: "#121212",
                                paper: "#1f1f1f"
                            }
                        }
                ),
            }
        })
        return theme
    }

    const [theme, settheme] = useState(getOri("light"))

    const load = () => {
        const d = localStorage.getItem("dark") == "true"
        if (d) {
            const config = localStorage.getItem("darkm")
            if (config) {
                settheme(createTheme(theme, JSON.parse(config)))
            } else {
                settheme(getOri("dark"))
            }
        } else {
            const config = localStorage.getItem("lightm")
            if (config) {
                settheme(createTheme(theme, JSON.parse(config)))
            } else {
                settheme(getOri("light"))
            }
        }
    }

    useEffect(() => {
        load()
    }, [typeof window !== "undefined" ? window.localStorage.dark : ""])

    return theme
}