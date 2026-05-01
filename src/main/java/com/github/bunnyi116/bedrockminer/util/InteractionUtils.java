package com.github.bunnyi116.bedrockminer.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Queue;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;


@Environment(EnvType.CLIENT)
public class InteractionUtils {
    public static final float BREAKING_PROGRESS_MAX = 0.6F;
    private static BlockPos currentBreakingPos = new BlockPos(-1, -1, -1);
    private static float currentBreakingProgress;
    private static boolean breakingBlock;
    private static int breakingTicks;
    private static int breakingTickMax;

    private static final Queue<BlockPos> blockQueue = new ArrayDeque<>();

    private static int getBlockBreakingProgress() {
        float breakingProgress = currentBreakingProgress >= BREAKING_PROGRESS_MAX ? 1.0F : currentBreakingProgress;
        return breakingProgress > 0.0F ? (int) (breakingProgress * 10.0F) : -1;
    }

    public static boolean updateBlockBreakingProgress(BlockPos pos, Direction direction, boolean localPrediction) {
        if (!world.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        if (player.blockActionRestricted(world, pos, gameMode)) {
            return false;
        }
        if (!PlayerUtils.canInteractWithBlockAt(pos, 0F)) {
            return false;
        }
        BlockState blockState = world.getBlockState(pos);
        if (gameMode.isCreative()) {    // Creative mode
            setBreakingBlock(true);
            NetworkUtils.sendSequencedPacket((sequence) -> { // Only START is needed because creative mining is instant
                if (!blockState.isAir() && localPrediction) {
                    interactionManager.destroyBlock(pos);
                }
                return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence);
            });
            setBreakingBlock(false);
            return true;
        }
        if (breakingBlock && pos.equals(currentBreakingPos)) {
            if (blockState.isAir()) {
                setBreakingBlock(false);
                return true;
            }
            currentBreakingProgress += PlayerUtils.calcBlockBreakingDelta(blockState);
            if (currentBreakingProgress >= BREAKING_PROGRESS_MAX) {
                NetworkUtils.sendSequencedPacket((sequence) -> {
                    if (!blockState.isAir() && localPrediction) {
                        interactionManager.destroyBlock(pos);
                    }
                    return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence);
                });
                currentBreakingProgress = 0.0F;
                world.destroyBlockProgress(player.getId(), currentBreakingPos, -1);
                setBreakingBlock(false);
                return true;
            } else {
                world.destroyBlockProgress(player.getId(), currentBreakingPos, getBlockBreakingProgress());
            }
            ++breakingTickMax;
        } else {
            if (breakingBlock && !pos.equals(currentBreakingPos)) {
                NetworkUtils.sendPacket(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, currentBreakingPos, direction));
                setBreakingBlock(false);
            }
            currentBreakingProgress += PlayerUtils.calcBlockBreakingDelta(blockState);
            if (currentBreakingProgress >= BREAKING_PROGRESS_MAX) {
                setBreakingBlock(true);
                NetworkUtils.sendSequencedPacket((sequence) -> {
                    if (!blockState.isAir() && localPrediction) {
                        interactionManager.destroyBlock(pos);
                    }
                    return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence);
                });
                setBreakingBlock(false);
                return true;
            } else {
                NetworkUtils.sendSequencedPacket((sequence) -> {
                    if (!blockState.isAir() && currentBreakingProgress == 0.0F) {
                        blockState.attack(world, pos, player);
                    }
                    setBreakingBlock(true);
                    currentBreakingPos = pos;
                    currentBreakingProgress = 0.0F;
                    world.destroyBlockProgress(player.getId(), currentBreakingPos, getBlockBreakingProgress());
                    return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence);
                });
            }
        }
        return false;
    }

    public static void updateBlockBreakingProgress(BlockPos pos, Direction direction) {
        updateBlockBreakingProgress(pos, direction, true);
    }

    public static void updateBlockBreakingProgress(BlockPos pos, boolean localPrediction) {
        updateBlockBreakingProgress(pos, PlayerUtils.getClosestFace(pos), localPrediction);
    }

    public static void updateBlockBreakingProgress(BlockPos pos) {
        updateBlockBreakingProgress(pos, true);
    }

    /**
     * Instamine a block by sending START_DESTROY_BLOCK and STOP_DESTROY_BLOCK packets back-to-back,
     * regardless of the player's actual mining speed. The server treats this as a creative-style
     * break for blocks where progress is nominally instant (pistons/torches/etc when bedrock-breaker
     * exploits relax the check), so it works without Haste/Efficiency.
     *
     * Mirrors fabric-bedrock-miner's instamine path used by the Meteor addon reference.
     */
    public static void instamineBlock(BlockPos pos, Direction direction) {
        if (world == null || player == null || networkHandler == null) return;
        if (!world.getWorldBorder().isWithinBounds(pos)) return;
        BlockState blockState = world.getBlockState(pos);
        if (blockState.isAir()) return;

        NetworkUtils.sendSequencedPacket((sequence) ->
            new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence)
        );
        NetworkUtils.sendSequencedPacket((sequence) ->
            new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction, sequence)
        );

        // Local prediction: destroy client-side immediately so subsequent placement in the
        // same tick sees the block as gone.
        if (interactionManager != null) {
            interactionManager.destroyBlock(pos);
        }

        player.swing(InteractionHand.MAIN_HAND);
    }

    public static void instamineBlock(BlockPos pos) {
        instamineBlock(pos, PlayerUtils.getClosestFace(pos));
    }

    public static void resetBreaking() {
        breakingTicks = 0;
        breakingTickMax = 20;
        setBreakingBlock(false);
    }

    public static void autoResetBreaking() {
        if (!breakingBlock && breakingTicks > 0) {  // Not breaking but ticks accumulated — reset state first
            resetBreaking();
        }
        if (breakingBlock && breakingTicks++ > breakingTickMax) {
            resetBreaking();
        }
    }

    public static boolean isBreakingBlock() {
        return breakingBlock;
    }

    public static void setBreakingBlock(boolean breakingBlock) {
        InteractionUtils.breakingBlock = breakingBlock;
    }

    public static void placement(BlockPos blockPos, Direction facing, @Nullable Item... items) {
        if (blockPos == null || facing == null)
            return;

        if (!BlockUtils.isReplaceable(world.getBlockState(blockPos)))
            return;

        if (!PlayerUtils.canInteractWithBlockAt(blockPos, 0F)) {
            return;
        }
        if (items != null) {
            InventoryUtils.switchToItem(items);
        }
        // Send a look-rotation packet so the placement is on the right face
        if (facing.getAxis().isVertical()) {
            var yaw = switch (facing) {
                case SOUTH -> 180F;
                case EAST -> 90F;
                case NORTH -> 0F;
                case WEST -> -90F;
                default -> player.getYRot();
            };
            var pitch = switch (facing) {
                case UP -> 89F;
                case DOWN -> -89F;
                default -> 0F;
            };
            PlayerLookUtils.sendLookPacket(yaw, pitch);
        }
        // Simulate the click position (mid-air placement)
        var hitPos = blockPos.relative(facing.getOpposite());
        Vec3 hitVec3d = Vec3.atCenterOf(hitPos).relative(facing, 0.5F);   // Center of the placement face
        var hitResult = new BlockHitResult(hitVec3d, facing, blockPos, false);
        interactionManager.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
    }

    public static void placement(BlockPos blockPos, Direction facing) {
        placement(blockPos, facing, (Item) null);
    }

    public static boolean canPlace(ClientLevel world, BlockPos blockPos, BlockState placeBlockState) {
        // Whether the block at the target position can be replaced
        if (!BlockUtils.isReplaceable(world.getBlockState(blockPos))) {
            return false;
        }
        // Check the collision shape of the block being placed
        VoxelShape collisionShape = placeBlockState.getCollisionShape(world, blockPos);
        if (collisionShape.isEmpty()) {
            return true; // No collision — placement is allowed
        }
        for (Entity entity : world.entitiesForRendering()) {
            if (entity instanceof ItemEntity) {
                return true;
            }
            if (entity.isColliding(blockPos, placeBlockState)) {
                return false;
            }
        }
        return true;
    }
}