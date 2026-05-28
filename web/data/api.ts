"use client"

import { WebApiClient } from "@shirokasoke/webapi-sdk";
import { useEffect, useState } from "react";

export function useAPI() {
    const [api, setAPI] = useState<WebApiClient>()

    useEffect(() => {
        setAPI(new WebApiClient({
            baseUrl: localStorage.getItem("url") ?? "http://127.0.0.1:40002/",
            authToken: localStorage.getItem("auth") ?? undefined,
            fetch: (input: RequestInfo | URL, init?: RequestInit) => window.fetch(input, init)
        }))
    }, [typeof window != undefined])

    return api
}