export function formatDuration(ms: number): string {
    if (ms < 1000) return `${ms.toFixed(0)}ms`
    const sec = ms / 1000
    if (sec < 60) return `${sec.toFixed(1)}s`
    const min = sec / 60
    if (min < 60) return `${min.toFixed(1)}m`
    const hr = min / 60
    return `${hr.toFixed(1)}h`
}

const BYTE_UNITS = ["B", "KB", "MB", "GB", "TB", "PB", "EB"]

export function formatBytes(bytes: number): string {
    if (bytes === 0) return "0 B"
    const sign = bytes < 0 ? "-" : ""
    const abs = Math.abs(bytes)
    const exp = Math.min(Math.floor(Math.log10(abs) / 3), BYTE_UNITS.length - 1)
    const val = abs / Math.pow(1000, exp)
    return `${sign}${val.toFixed(2)} ${BYTE_UNITS[exp]}`
}

export function formatCount(n: number): string {
    if (!n || n < 1000) return String(n || 0)
    if (n < 1000000) return `${(n / 1000).toFixed(1)}k`
    if (n < 1000000000) return `${(n / 1000000).toFixed(1)}M`
    return `${(n / 1000000000).toFixed(1)}G`
}
