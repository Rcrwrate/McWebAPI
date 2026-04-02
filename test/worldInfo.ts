async function getworldinfo() {
    return (await fetch("http://localhost:40002/WorldInfo")).json()
}


export async function xyz(worldID: number = 0) {
    const data = await getworldinfo()
    const w = data[worldID].WorldInfo

    return [w.spawnX as number, w.spawnY as number, w.spawnZ as number]
}