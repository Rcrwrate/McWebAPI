"use client"

import { WebApiClient } from "@shirokasoke/webapi-sdk";
import { createContext, useCallback, useContext, useState } from "react";

function createClient(): WebApiClient {
    return new WebApiClient({
        baseUrl:
            typeof window !== "undefined"
                ? (localStorage.getItem("url") ?? "http://127.0.0.1:40002/")
                : "http://127.0.0.1:40002/",
        authToken:
            typeof window !== "undefined"
                ? (localStorage.getItem("auth") ?? undefined)
                : undefined,
        fetch: (input: RequestInfo | URL, init?: RequestInit) =>
            window.fetch(input, init),
    });
}

interface APIContextValue {
    api: WebApiClient;
    reset: () => void;
}

const APIContext = createContext<APIContextValue | null>(null);

export function APIProvider({ children }: { children: React.ReactNode }) {
    const [api, setApi] = useState<WebApiClient>(() => createClient());

    const reset = useCallback(() => {
        setApi(createClient());
    }, []);

    return (
        <APIContext.Provider value={{ api, reset }}>
            {children}
        </APIContext.Provider>
    );
}

export function useAPI() {
    const ctx = useContext(APIContext);
    // if (!ctx) {
    //     throw new Error("useAPI must be used within APIProvider");
    // }
    return ctx?.api;
}

export function useResetAPI() {
    const ctx = useContext(APIContext);
    if (!ctx) {
        throw new Error("useResetAPI must be used within APIProvider");
    }
    return ctx.reset;
}
