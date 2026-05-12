async function tasks() {
    const x = 251
    const y = 65
    const z = 265
    const r = await fetch("http://localhost:40002/ae/cpu/task?x=250&y=64&z=264", {
        method: "POST",
        body: JSON.stringify({
            id: 5,
            amount: 64
        })
    })
    console.log(r.status)
    console.log(await r.text())
}

async function cancel() {
    const r = await fetch("http://localhost:40002/ae/cpu/cancel?x=250&y=64&z=264", {
        method: "DELETE",
        body: JSON.stringify({
            id: 0,
        })
    })
    console.log(r.status)
    console.log(await r.text())
}

tasks().finally(() => {
    cancel().finally(() => { })
})