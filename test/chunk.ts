// Chunk API 测试示例
// 运行: npx ts-node chunk.ts

interface ChunkInfo {
    ticketKey: string;
    chunkX: number;
    chunkZ: number;
    dimension: number;
    dimensionName: string;
    durationSec: number;
    remainingSec: number;
}

interface ChunkListResponse {
    success: boolean;
    totalLoaded: number;
    chunks: ChunkInfo[];
}

interface ChunkActionResponse {
    success: boolean;
    action: 'load' | 'unload';
    message: string;
    ticketKey: string;
    chunkX: number;
    chunkZ: number;
    dimension: number;
    durationSec?: number;
    remainingSec?: number;
}

const API_BASE = 'http://localhost:40002';

async function listChunks(): Promise<ChunkListResponse> {
    console.log('获取强制加载区块列表...');
    const response = await fetch(`${API_BASE}/chunk/force`);
    const data = await response.json();
    console.log('当前加载的区块数:', data.totalLoaded);
    if (data.chunks && data.chunks.length > 0) {
        data.chunks.forEach((chunk: ChunkInfo) => {
            console.log(`  - ${chunk.ticketKey} | ${chunk.dimension} | 剩余: ${chunk.remainingSec}秒`);
        });
    }
    return data;
}

async function loadChunk(x: number, z: number, dimension = 0, duration = 60): Promise<ChunkActionResponse> {
    console.log(`\n加载区块: 坐标(${x}, ${z}), 维度: ${dimension}, 持续: ${duration}秒`);
    const response = await fetch(`${API_BASE}/chunk/force?action=load&x=${x}&z=${z}&dim=${dimension}&duration=${duration}`, {
        method: 'POST'
    });
    const data = await response.json();
    if (data.success) {
        console.log('✓ 加载成功:', data.ticketKey);
        console.log(`  区块坐标: ${data.chunkX}, ${data.chunkZ}`);
        console.log(`  维度: ${data.dimension}`);
        console.log(`  剩余时间: ${data.remainingSec}秒`);
    } else {
        console.log('✗ 加载失败:', data.error || data.message);
    }
    return data;
}

async function unloadChunk(x: number, z: number, dimension = 0): Promise<ChunkActionResponse> {
    console.log(`\n卸载区块: 坐标(${x}, ${z}), 维度: ${dimension}`);
    const response = await fetch(`${API_BASE}/chunk/force?action=unload&x=${x}&z=${z}&dim=${dimension}`, {
        method: 'POST'
    });
    const data = await response.json();
    if (data.success) {
        console.log('✓ 卸载成功:', data.ticketKey);
    } else {
        console.log('✗ 卸载失败:', data.error || data.message);
    }
    return data;
}

// 测试主流程
async function runTests() {
    console.log('=== Chunk API 测试 ===\n');

    // 1. 初始状态
    await listChunks();

    // 2. 加载几个区块
    await loadChunk(100, 200, 0, 120);      // 主世界，2分钟
    await loadChunk(-50, 100, -1, 180);     // 下界，3分钟
    await loadChunk(500, 500, 0, 60);       // 主世界，1分钟

    // 3. 查看状态
    await listChunks();

    // 4. 卸载一个区块
    await unloadChunk(100, 200, 0);

    // 5. 查看最终状态
    await listChunks();

    console.log('\n=== 测试完成 ===');
}

// 演示持续监控
async function monitorChunks(intervalSec = 10) {
    console.log(`\n开始监控，每 ${intervalSec} 秒检查一次...`);
    const interval = setInterval(async () => {
        const data = await listChunks();
        if (data.totalLoaded === 0) {
            console.log('没有区块被强制加载，停止监控');
            clearInterval(interval);
        }
    }, intervalSec * 1000);

    // 运行1分钟后自动停止
    setTimeout(() => {
        clearInterval(interval);
        console.log('\n监控已停止');
    }, 60000);
}

// 运行测试
runTests().then(() => {
    // 测试完成后可选择启动监控
    // monitorChunks(5);
}).catch(console.error);
