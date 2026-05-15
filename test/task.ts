async function tasks() {
    const r = await fetch("http://localhost:40002/ae/cpu/task?x=258&y=64&z=260", {
        method: "POST",
        body: JSON.stringify({
            "id": 4138,
            "Count": 4,
            "Damage": 0,
            "tag": "CAAFbW9kaWQAE2FwcGxpZWRlbmVyZ2lzdGljczIIAAhpdGVtbmFtZQAhdGlsZS5RdWFydHpQaWxsYXJTbGFiQmxvY2suZG91YmxlCwABeAAAAAIAAADzAAAAAAA="
        })
    })
    console.log(r.status)
    console.log(await r.text())
}

async function cancel() {
    const r = await fetch("http://localhost:40002/ae/cpu/cancel?x=258&y=64&z=260", {
        method: "DELETE",
        body: JSON.stringify({
            id: 0,
        })
    })
    console.log(r.status)
    console.log(await r.text())
}

tasks().finally(() => {
    // cancel().finally(() => { })
})