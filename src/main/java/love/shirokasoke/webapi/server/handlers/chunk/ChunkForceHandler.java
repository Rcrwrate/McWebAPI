package love.shirokasoke.webapi.server.handlers.chunk;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import love.shirokasoke.webapi.MyMod;
import love.shirokasoke.webapi.server.RouteHandler;

public class ChunkForceHandler implements RouteHandler {

    // 存储活跃的chunk tickets和任务信息
    public static class ChunkLoadInfo {

        public Ticket ticket;
        public int chunkX;
        public int chunkZ;
        public int dimension;
        public long startTime;
        public long duration; // 毫秒
        public Timer timer;
        public String ticketKey;

        public ChunkLoadInfo(Ticket ticket, int chunkX, int chunkZ, int dimension, long duration, Timer timer,
            String ticketKey) {
            this.ticket = ticket;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
            this.startTime = System.currentTimeMillis();
            this.duration = duration * 1000L; // 转换为毫秒
            this.timer = timer;
            this.ticketKey = ticketKey;
        }

        public long getRemainingTime() {
            long elapsed = System.currentTimeMillis() - startTime;
            return Math.max(0, duration - elapsed);
        }

        public ObjectNode dump() {
            ObjectNode chunkNode = mapper.createObjectNode();
            chunkNode.put("ticketKey", ticketKey);
            chunkNode.put("chunkX", chunkX);
            chunkNode.put("chunkZ", chunkZ);
            chunkNode.put("minX", chunkX * 16);
            chunkNode.put("maxX", chunkX * 16 + 15);
            chunkNode.put("minZ", chunkZ * 16);
            chunkNode.put("maxZ", chunkZ * 16 + 15);
            chunkNode.put("dimension", dimension);
            chunkNode.put("startTime", startTime);
            chunkNode.put("durationSec", duration / 1000);
            chunkNode.put("remainingSec", getRemainingTime() / 1000);
            chunkNode.put("isActive", ticket != null);
            return chunkNode;
        }
    }

    public static final Map<String, ChunkLoadInfo> activeChunkLoads = new HashMap<>();

    @Override
    public String getPath() {
        return "/chunk/force";
    }

    @Override
    public String getDescription() {
        return "Chunk loading management. GET: list loaded chunks, POST: load/unload chunk";
    }

    @Override
    public void run(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQueryParams(exchange);
        String action = params.get("action");

        if (action == null) {
            handleList(exchange);
            return;
        }

        switch (action) {
            case "load":
                handleLoadChunk(exchange, params);
                break;
            case "unload":
                handleUnloadChunk(exchange, params);
                break;
            default:
                throw new Error(400, "Invalid action. Use 'load' or 'unload'");
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        ArrayNode chunksArray = mapper.createArrayNode();

        for (ChunkLoadInfo info : activeChunkLoads.values()) {
            chunksArray.add(info.dump());
        }

        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        response.put("totalLoaded", activeChunkLoads.size());
        response.set("chunks", chunksArray);

        sendResponse(exchange, 200, response);
    }

    private void handleLoadChunk(HttpExchange exchange, Map<String, String> params) throws IOException {
        int x = Integer.parseInt(params.get("x"));
        int z = Integer.parseInt(params.get("z"));
        int dimension = Integer.parseInt(params.getOrDefault("dim", "0"));
        int duration = Integer.parseInt(params.getOrDefault("duration", "60"));

        if (duration <= 0) {
            throw new Error(400, "Duration must be large then 0 seconds");
        }

        MinecraftServer server = getServer();

        WorldServer world = server.worldServerForDimension(dimension);
        if (world == null) {
            throw new Error(404, "Invalid dimension: " + dimension);
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        String ticketKey = dimension + ":" + chunkX + ":" + chunkZ;

        if (activeChunkLoads.containsKey(ticketKey)) {
            throw new Error(409, "Chunk already being force loaded: " + ticketKey);
        }

        Ticket ticket = ForgeChunkManager.requestTicket(MyMod.INST, world, ForgeChunkManager.Type.NORMAL);

        if (ticket == null) {
            throw new Error(503, "Failed to acquire chunk loading ticket. Too many chunks already loaded.");
        }

        ChunkCoordIntPair chunk = new ChunkCoordIntPair(chunkX, chunkZ);
        ForgeChunkManager.forceChunk(ticket, chunk);

        Timer timer = new Timer();
        TimerTask task = new TimerTask() {

            @Override
            public void run() {
                ChunkLoadInfo info = activeChunkLoads.remove(ticketKey);
                if (info != null && info.ticket != null) {
                    ForgeChunkManager.releaseTicket(info.ticket);
                    MyMod.LOG.info("Released chunk loading ticket for " + ticketKey);
                }
            }
        };

        timer.schedule(task, duration * 1000L);

        ChunkLoadInfo info = new ChunkLoadInfo(ticket, chunkX, chunkZ, dimension, duration, timer, ticketKey);
        activeChunkLoads.put(ticketKey, info);

        ObjectNode response = info.dump();
        response.put("success", true);
        response.put("action", "load");

        sendResponse(exchange, 200, response);
    }

    private void handleUnloadChunk(HttpExchange exchange, Map<String, String> params) throws IOException {
        int x = Integer.parseInt(params.getOrDefault("x", "0"));
        int z = Integer.parseInt(params.getOrDefault("z", "0"));
        int dimension = Integer.parseInt(params.getOrDefault("dim", "0"));

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        String ticketKey = dimension + ":" + chunkX + ":" + chunkZ;

        ChunkLoadInfo info = activeChunkLoads.remove(ticketKey);

        if (info != null) {
            if (info.timer != null) {
                info.timer.cancel();
            }

            if (info.ticket != null) {
                ForgeChunkManager.releaseTicket(info.ticket);
            }

            ObjectNode response = info.dump();
            response.put("success", true);
            response.put("action", "unload");
            sendResponse(exchange, 200, response);
        } else {
            throw new Error(404, "No active chunk loading ticket found for " + ticketKey);
        }
    }
}
