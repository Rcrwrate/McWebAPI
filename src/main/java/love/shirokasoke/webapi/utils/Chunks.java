package love.shirokasoke.webapi.utils;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.chunk.Chunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import love.shirokasoke.webapi.Constant;

public class Chunks {

    private static final ObjectMapper mapper = Constant.mapper;

    public static ObjectNode dump(Chunk chunk, ObjectNode data, int dumpEntity) {
        ClassUtils.getClassInfo(chunk, data);
        data.put("chunkX", chunk.xPosition);
        data.put("chunkZ", chunk.zPosition);
        data.put("xStart", chunk.xPosition << 4);
        data.put("zStart", chunk.zPosition << 4);
        data.put("xEnd", (chunk.xPosition << 4) + 15);
        data.put("zEnd", (chunk.zPosition << 4) + 15);
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
        switch (dumpEntity) {
            case 1:
                int entityCount = 0;
                if (chunk.entityLists != null) {
                    for (Object list : chunk.entityLists) {
                        if (list instanceof List) {
                            entityCount += ((List<?>) list).size();
                        }
                    }
                }
                data.put("entityCount", entityCount);
            case 0:
                break;
            case 2:
                ArrayNode entityList = data.putArray("entityList");
                if (chunk.entityLists != null) {
                    for (Object list : chunk.entityLists) {
                        if (list instanceof ArrayList) {
                            ArrayNode entityArrayNode = mapper.createArrayNode();
                            for (Object entity : (ArrayList<?>) list) {
                                entityArrayNode.add(Entitys.dump(entity));
                            }
                            entityList.add(entityArrayNode);
                        }
                    }
                }
            default:
                break;
        }

        // 时间信息
        data.put("inhabitedTime", chunk.inhabitedTime);

        return data;
    }

    public static ObjectNode dump(Chunk chunk) {
        return dump(chunk, mapper.createObjectNode(), 1);
    }

    public static ObjectNode dump(Chunk chunk, ObjectNode data) {
        return dump(chunk, data, 1);
    }

    public static ObjectNode dump(Chunk chunk, int dumpEntity) {
        return dump(chunk, mapper.createObjectNode(), dumpEntity);
    }
}
