package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.world;

public class BlockUtils {
    public static boolean isReplaceable(BlockState blockState) {
        return blockState.canBeReplaced();
    }

    public static @NotNull Block getBlock(Identifier blockId) {
        return BuiltInRegistries.BLOCK.getValue(blockId);
    }

    public static String getBlockName(Block block) {
        return block.getName().getString();
    }

    public static Identifier getKey(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }

    public static String getKeyString(Block block) {
        return getKey(block).toString();
    }

    public static boolean sideCoversSmallSquare(BlockPos blockPos, Direction direction) {
        return Block.canSupportCenter(world, blockPos, direction);
    }
}
