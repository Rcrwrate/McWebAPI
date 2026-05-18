import { WebApiClient } from "../src/client";

const client = new WebApiClient({
    baseUrl: "http://localhost:40002",
    authToken: "Bearer YOUR_TOKEN_HERE",
});

async function main() {
    try {
        const root = await client.getRoot();
        console.log("Mod:", root.modid, "Version:", root.version);

        const tps = await client.getTPS();
        Object.entries(tps).forEach(([dim, info]) => {
            console.log(`Dimension ${dim}: ${info.TPS.toFixed(2)} TPS (${info.TickTime.toFixed(2)}ms)`);
        });

        const blocks = await client.getBlocks();
        console.log("Registered blocks:", blocks.length);

        const block = await client.getBlock({ posX: 100, posY: 64, posZ: 100, dimension: 0 });
        console.log("Block at (100,64,100):", block.block.localizedName);

        const chunk = await client.getChunk({ chunkX: 6, chunkZ: 6, dim: 0 });
        console.log("Chunk entities:", chunk.entityCount);

        const aeNodes = await client.aeNodes({ posX: 100, posY: 64, posZ: 100, dimension: 0 });
        console.log("AE nodes:", aeNodes.length);
    } catch (err) {
        console.error(err);
    }
}

main();
