import { type ReadonlyURLSearchParams, useSearchParams } from "next/navigation"

export default function useCoords(i?: ReadonlyURLSearchParams): [number, number, number, number] | [undefined] {
    const searchParams = i ?? useSearchParams()
    const x = searchParams.get("x")
    const y = searchParams.get("y")
    const z = searchParams.get("z")
    const dimension = searchParams.get("dimension")

    if (!x || !y || !z || !dimension) return [undefined]
    const px = parseInt(x)
    const py = parseInt(y)
    const pz = parseInt(z)
    const dim = parseInt(dimension)
    if (isNaN(px) || isNaN(py) || isNaN(pz) || isNaN(dim)) return [undefined]
    return [px, py, pz, dim]
}