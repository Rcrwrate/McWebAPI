package love.shirokasoke.webapi.utils;

import net.minecraft.entity.Entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import love.shirokasoke.webapi.Constant;

public class Entitys {

    private static final ObjectMapper mapper = Constant.mapper;

    public static ObjectNode dump(Object object, ObjectNode dataNode) {
        if (object instanceof Entity) {
            dump((Entity) object, dataNode);
        }
        ClassUtils.getClassInfo(object, dataNode);
        return dataNode;
    }

    public static ObjectNode dump(Object object) {
        return dump(object, mapper.createObjectNode());
    }

    public static void dump(Entity object, ObjectNode dataNode) {
        dataNode.put("name", object.getCommandSenderName());
        dataNode.put("entityId", object.getEntityId());
        dataNode.put(
            "uniqueId",
            object.getUniqueID()
                .toString());
        dataNode.put("dimension", object.dimension);

        // 位置信息
        dataNode.put("posX", object.posX);
        dataNode.put("posY", object.posY);
        dataNode.put("posZ", object.posZ);
        dataNode.put("prevPosX", object.prevPosX);
        dataNode.put("prevPosY", object.prevPosY);
        dataNode.put("prevPosZ", object.prevPosZ);

        // 运动信息
        dataNode.put("motionX", object.motionX);
        dataNode.put("motionY", object.motionY);
        dataNode.put("motionZ", object.motionZ);

        // 旋转信息
        dataNode.put("rotationYaw", object.rotationYaw);
        dataNode.put("rotationPitch", object.rotationPitch);
        dataNode.put("prevRotationYaw", object.prevRotationYaw);
        dataNode.put("prevRotationPitch", object.prevRotationPitch);

        // 尺寸信息
        dataNode.put("width", object.width);
        dataNode.put("height", object.height);
        dataNode.put("yOffset", object.yOffset);
        dataNode.put("stepHeight", object.stepHeight);

        // 碰撞箱位置
        if (object.boundingBox != null) {
            dataNode.put("boundingBoxMinX", object.boundingBox.minX);
            dataNode.put("boundingBoxMinY", object.boundingBox.minY);
            dataNode.put("boundingBoxMinZ", object.boundingBox.minZ);
            dataNode.put("boundingBoxMaxX", object.boundingBox.maxX);
            dataNode.put("boundingBoxMaxY", object.boundingBox.maxY);
            dataNode.put("boundingBoxMaxZ", object.boundingBox.maxZ);
        }

        // 状态信息
        dataNode.put("onGround", object.onGround);
        dataNode.put("isCollided", object.isCollided);
        dataNode.put("isCollidedHorizontally", object.isCollidedHorizontally);
        dataNode.put("isCollidedVertically", object.isCollidedVertically);
        dataNode.put("isDead", object.isDead);
        dataNode.put("isAirBorne", object.isAirBorne);
        dataNode.put("inWater", object.isInWater());
        dataNode.put("fallDistance", object.fallDistance);

        // 时间和状态
        dataNode.put("ticksExisted", object.ticksExisted);
        dataNode.put("fireResistance", object.fireResistance);
        dataNode.put("hurtResistantTime", object.hurtResistantTime);

        // 区块位置
        dataNode.put("chunkCoordX", object.chunkCoordX);
        dataNode.put("chunkCoordY", object.chunkCoordY);
        dataNode.put("chunkCoordZ", object.chunkCoordZ);

        // 骑行信息
        dataNode.put("hasRidingEntity", object.ridingEntity != null);
        dataNode.put("hasRiddenByEntity", object.riddenByEntity != null);

        NBT.dump(object.getEntityData(), dataNode);
    }
}
