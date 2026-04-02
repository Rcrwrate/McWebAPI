package love.shirokasoke.webapi.utils;

import net.minecraft.world.chunk.Chunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import love.shirokasoke.webapi.Constant;

public class Chunks {

    private static final ObjectMapper mapper = Constant.mapper;

    public static ObjectNode dump(Chunk chunk, ObjectNode data) {
        ClassUtils.getClassInfo(chunk, data);
        data.put("x", chunk.xPosition);
        data.put("z", chunk.zPosition);
        data.put("lastSaveTime", chunk.lastSaveTime);

        // 基础状态信息
        data.put("isTerrainPopulated", chunk.isTerrainPopulated);
        data.put("isLightPopulated", chunk.isLightPopulated);
        data.put("isModified", chunk.isModified);
        data.put("hasEntities", chunk.hasEntities);
        data.put("isChunkLoaded", chunk.isChunkLoaded);
        data.put("sendUpdates", chunk.sendUpdates);

        // 实体和方块实体数量
        data.put("tileEntityCount", chunk.chunkTileEntityMap != null ? chunk.chunkTileEntityMap.size() : 0);
        int entityCount = 0;
        if (chunk.entityLists != null) {
            for (Object list : chunk.entityLists) {
                if (list instanceof java.util.List) {
                    entityCount += ((java.util.List<?>) list).size();
                }
            }
        }
        data.put("entityCount", entityCount);

        // 时间信息
        data.put("inhabitedTime", chunk.inhabitedTime);

        return data;
    }

    public static ObjectNode dump(Chunk chunk) {
        return dump(chunk, mapper.createObjectNode());
    }
}
