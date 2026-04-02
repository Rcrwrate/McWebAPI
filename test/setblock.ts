import { xyz } from "./worldInfo"

async function main() {
    let [x, y, z] = await xyz()
    y += 100
    await setblock(x, y, z, 0)
    await setblock(x, y, z, 24, 2)
    await setblock(x, y, z, 10000)
}

async function setblock(x: number, y: number, z: number, id: number, metadataIn: number = 0, flag: number = 2) {
    const r = await fetch(`http://localhost:40002/setblock?x=${x}&y=${y}&z=${z}`, {
        method: "POST",
        body: JSON.stringify({ id, metadataIn, flag })
    })

    console.log(r.status)
    console.log(await r.text())
}


main().then()