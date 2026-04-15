package love.shirokasoke.webapi.utils;

import java.util.Collection;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import love.shirokasoke.webapi.Constant;

public class Entitys {

    private static final ObjectMapper mapper = Constant.mapper;

    public static ObjectNode dump(Object object, ObjectNode dataNode) {
        if (object instanceof Entity) {
            dumpEntity((Entity) object, dataNode);
        }
        if (object instanceof EntityLivingBase) {
            dumpEntityLivingBase((EntityLivingBase) object, dataNode);
        }
        ClassUtils.getClassInfo(object, dataNode);
        return dataNode;
    }

    public static ObjectNode dump(Object object) {
        return dump(object, mapper.createObjectNode());
    }

    private static void dumpEntity(Entity object, ObjectNode root) {
        ObjectNode dataNode = root.putObject("Entity");
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
        dataNode.put(
                "RidingEntity",
                object.ridingEntity != null ? object.ridingEntity.getUniqueID()
                        .toString() : null);
        dataNode.put(
                "RiddenByEntity",
                object.riddenByEntity != null ? object.riddenByEntity.getUniqueID()
                        .toString() : null);

        NBT.dump(object.getEntityData(), dataNode);
    }

    private static void dumpEntityLivingBase(EntityLivingBase object, ObjectNode root) {
        ObjectNode dataNode = root.putObject("EntityLivingBase");
        // 生命值信息
        dataNode.put("health", object.getHealth());
        dataNode.put("maxHealth", object.getMaxHealth());
        dataNode.put("absorptionAmount", object.getAbsorptionAmount());
        dataNode.put("prevHealth", object.prevHealth);
        dataNode.put("TotalArmor", object.getTotalArmorValue());
        // 装备信息
        ItemStack[] items = object.getLastActiveItems();
        if (items != null) {
            ArrayNode itemsNode = dataNode.putArray("items");
            for (ItemStack i : items) {
                itemsNode.add(Items.dump(i));
            }
        }

        // 头部旋转信息
        // dataNode.put("rotationYawHead", object.rotationYawHead);
        // dataNode.put("prevRotationYawHead", object.prevRotationYawHead);
        // dataNode.put("renderYawOffset", object.renderYawOffset);
        // dataNode.put("prevRenderYawOffset", object.prevRenderYawOffset);

        // 动画和动作状态
        // dataNode.put("swingProgress", object.swingProgress);
        // dataNode.put("prevSwingProgress", object.prevSwingProgress);
        // dataNode.put("swingProgressInt", object.swingProgressInt);
        // dataNode.put("isSwingInProgress", object.isSwingInProgress);
        // dataNode.put("limbSwing", object.limbSwing);
        // dataNode.put("limbSwingAmount", object.limbSwingAmount);
        // dataNode.put("prevLimbSwingAmount", object.prevLimbSwingAmount);
        // dataNode.put("moveStrafing", object.moveStrafing);
        // dataNode.put("moveForward", object.moveForward);

        // 受伤和攻击状态
        dataNode.put("hurtTime", object.hurtTime);
        dataNode.put("maxHurtTime", object.maxHurtTime);
        dataNode.put("attackTime", object.attackTime);
        dataNode.put("deathTime", object.deathTime);
        dataNode.put("attackedAtYaw", object.attackedAtYaw);
        dataNode.put("arrowCountInEntity", object.getArrowCountInEntity());
        dataNode.put("arrowHitTimer", object.arrowHitTimer);

        // 其他状态
        dataNode.put("entityAge", object.getAge());
        dataNode.put("jumpMovementFactor", object.jumpMovementFactor);
        // dataNode.put("isPlayerSleeping", object.isPlayerSleeping());
        dataNode.put("isOnLadder", object.isOnLadder());
        dataNode.put("canBreatheUnderwater", object.canBreatheUnderwater());
        dataNode.put("maxHurtResistantTime", object.maxHurtResistantTime);
        dataNode.put("cameraPitch", object.cameraPitch);
        dataNode.put("prevCameraPitch", object.prevCameraPitch);

        // 属性信息
        Collection<IAttributeInstance> att = object.getAttributeMap()
                .getAllAttributes();
        if (att != null) {
            ObjectNode attributesNode = dataNode.putObject("attributes");
            for (IAttributeInstance attr : att) {
                String attrName = attr.getAttribute()
                        .getAttributeUnlocalizedName();
                ObjectNode attrNode = attributesNode.putObject(attrName);
                attrNode.put("baseValue", attr.getBaseValue());
                attrNode.put("currentValue", attr.getAttributeValue());
            }
        }

        // 生物类型
        dataNode.put(
                "creatureAttribute",
                object.getCreatureAttribute()
                        .toString());

        // 攻击目标信息
        EntityLivingBase revengeTarget = object.getAITarget();
        if (revengeTarget != null) {
            dataNode.put("revengeTargetId", revengeTarget.getEntityId());
        }

        EntityLivingBase lastAttacker = object.getLastAttacker();
        if (lastAttacker != null) {
            dataNode.put("lastAttackerId", lastAttacker.getEntityId());
        }

        // 药水效果
        Collection<PotionEffect> activeEffects = object.getActivePotionEffects();
        if (activeEffects.size() != 0) {
            ObjectNode potionsNode = dataNode.putObject("activePotionEffects");
            for (PotionEffect effect : activeEffects) {
                ObjectNode effectNode = potionsNode.putObject(String.valueOf(effect.getPotionID()));
                effectNode.put("name", effect.getEffectName());
                effectNode.put("duration", effect.getDuration());
                effectNode.put("amplifier", effect.getAmplifier());
                effectNode.put("isAmbient", effect.getIsAmbient());
            }
        }
    }
}
