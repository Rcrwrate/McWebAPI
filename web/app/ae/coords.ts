export interface SavedAECoord {
    x: number
    y: number
    z: number
    dimension: number
    name: string
}

const STORAGE_KEY = "ae_coords"

export function getSavedCoords(): SavedAECoord[] {
    if (typeof window === "undefined") return []
    try {
        return JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]")
    } catch {
        return []
    }
}

export function saveCoords(coords: SavedAECoord[]) {
    if (typeof window === "undefined") return
    localStorage.setItem(STORAGE_KEY, JSON.stringify(coords))
}

export function createArgs(coord: SavedAECoord) {
    return Object.entries(coord).map((a) => `${a[0]}=${a[1]}`).join("&")
}
