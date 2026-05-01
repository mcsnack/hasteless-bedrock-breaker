package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

public class PlayerUtils {
    public static double getHorizontalDistanceToPlayer(BlockPos pos) {
        double dx = pos.getX() - player.getX();
        double dz = pos.getZ() - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Returns the face of the target block closest to the player's eye.
     */
    public static Direction getClosestFace(BlockPos targetPos) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 targetCenterPos = Vec3.atCenterOf(targetPos);
        Direction closestFace = null;
        double closestDistanceSquared = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            double offsetX = direction.getStepX() * 0.5;
            double offsetY = direction.getStepY() * 0.5;
            double offsetZ = direction.getStepZ() * 0.5;
            Vec3 facePos = targetCenterPos.add(offsetX, offsetY, offsetZ);
            double distanceSquared = playerPos.distanceToSqr(facePos);
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closestFace = direction;
            }
        }
        return closestFace;
    }

    /**
     * Whether the player is within range to interact with the given block.
     * Used to validate breaking, placement, and right-click interactions.
     */
    public static boolean canInteractWithBlockAt(BlockPos blockPos, double additionalRange) {
        double blockPosX = blockPos.getX();
        double blockPosY = blockPos.getY();
        double blockPosZ = blockPos.getZ();
        double distance = getBlockInteractionRange() + additionalRange;
        double eyePosX = player.getX();
        double eyePosY = player.getEyeY();
        double eyePosZ = player.getZ();
        double dx = Math.max(Math.max(blockPosX - eyePosX, eyePosX - (blockPosX + 1)), 0);
        double dy = Math.max(Math.max(blockPosY - eyePosY, eyePosY - (blockPosY + 1)), 0);
        double dz = Math.max(Math.max(blockPosZ - eyePosZ, eyePosZ - (blockPosZ + 1)), 0);
        return (dx * dx + dy * dy + dz * dz) < (distance * distance);
    }

    /**
     * Returns the player's block interaction range, honoring the configured override
     * when set to a non-zero value.
     */
    public static double getBlockInteractionRange() {
        double override = com.github.bunnyi116.bedrockminer.config.Config.getInstance().interactionRangeOverride;
        if (override > 0.0) {
            return override;
        }
        if (Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.blockInteractionRange();
        }
        return 4.5F;
    }

    /**
     * Compute the per-tick breaking-progress delta for the given block.
     */
    public static float calcBlockBreakingDelta(BlockState state, ItemStack itemStack) {
        float hardness = state.getBlock().defaultDestroyTime();
        if (hardness == -1.0F) {
            return 0.0F;
        } else {
            int i = player.hasCorrectToolForDrops(state) ? 30 : 100;
            return getBlockBreakingSpeed(state, itemStack) / hardness / (float) i;
        }
    }

    public static float calcBlockBreakingDelta(BlockState state) {
        return calcBlockBreakingDelta(state, player.getMainHandItem());
    }

    public static boolean canInstantlyMineBlock(BlockState state, ItemStack itemStack) {
        return PlayerUtils.calcBlockBreakingDelta(state, itemStack) >= InteractionUtils.BREAKING_PROGRESS_MAX;
    }

    public static boolean canInstantlyMineBlock(BlockState state) {
        return canInstantlyMineBlock(state, player.getMainHandItem());
    }

    /**
     * Returns the breaking speed of the given item against the given block.
     *
     * @param blockState the block being broken
     * @param itemStack  the tool/item being used
     * @return effective breaking speed (per tick)
     */
    public static float getBlockBreakingSpeed(BlockState blockState, ItemStack itemStack) {
        var f = itemStack.getDestroySpeed(blockState);  // Base destroy speed of the current item


        // Apply the tool's Efficiency enchantment
        if (f > 1.0F) {
            for (var enchantment : itemStack.getEnchantments().keySet()) {
                var enchantmentKey = enchantment.unwrapKey();
                if (enchantmentKey.isPresent()) {
                    if (enchantmentKey.get() == Enchantments.EFFICIENCY) {
                        int level = EnchantmentHelper.getItemEnchantmentLevel(enchantment, itemStack);
                        if (level > 0 && !itemStack.isEmpty()) {
                            f += (float) (level * level + 1);
                        }
                    }
                }
            }
        }

        // Apply the Haste status effect
        if (MobEffectUtil.hasDigSpeed(player)) {
            f *= 1.0F + (float)(MobEffectUtil.getDigSpeedAmplification(player) + 1) * 0.2F;
        }

        // Apply the Mining Fatigue status effect
        if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
            float g = switch (Objects.requireNonNull(player.getEffect(MobEffects.MINING_FATIGUE)).getAmplifier()) {
                case 0 -> 0.3F;
                case 1 -> 0.09F;
                case 2 -> 0.0027F;
                default -> 8.1E-4F;
            };
            f *= g;
        }

        // Underwater: apply the submerged-mining-speed attribute (and Aqua Affinity)
        f *= (float) player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (player.isEyeInFluid(FluidTags.WATER)) {
            var submergedMiningSpeed = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
            if (submergedMiningSpeed != null) {
                f *= (float) submergedMiningSpeed.getValue();
            }
        }

        // Off-ground penalty
        if (!player.onGround()) {
            f /= 5.0F;
        }
        return f;
    }

}
