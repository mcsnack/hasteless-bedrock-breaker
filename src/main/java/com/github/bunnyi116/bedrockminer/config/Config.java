package com.github.bunnyi116.bedrockminer.config;

import com.github.bunnyi116.bedrockminer.task.TaskRegion;
import com.github.bunnyi116.bedrockminer.util.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class Config {
    public boolean disableEmptyHandSwitchToggle = false;
    public boolean disable = false;
    public boolean debug = false;
    public boolean shortTsk = true;
    public List<Integer> floorsBlacklist = new ArrayList<>();
    public List<TaskRegion> ranges = new ArrayList<>();
    public List<String> blockWhitelist = getDefaultBlockWhitelist();
    public int limitMax = 1;
    /** Ticks to wait while out of range before cycling to a different task. 0 = immediate. */
    public int outOfRangeCooldownTicks = 20;
    /** Override for block interaction range (in blocks). 0 = use vanilla (typically 4.5). Higher values require a separate reach-extension mod to actually take effect server-side. */
    public double interactionRangeOverride = 0.0;


    public transient List<String> blockBlacklistServer = getDefaultBlockBlacklistServer();
    public transient Direction[] pistonDirections = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    public transient Direction[] pistonFacings = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    public transient Direction[] redstoneTorchDirections = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    public transient Direction[] redstoneTorchFacings = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public static List<String> getDefaultBlockWhitelist() {
        var list = new ArrayList<String>();
        list.add(BlockUtils.getKeyString(Blocks.BEDROCK));                  // bedrock
        // Disabled by default to prevent accidental destruction
        // list.addTask(BlockUtils.getId(Blocks.END_PORTAL));               // end portal
        // list.addTask(BlockUtils.getId(Blocks.END_PORTAL_FRAME));         // end portal frame
        // list.addTask(BlockUtils.getId(Blocks.END_GATEWAY));              // end gateway
        return list;
    }

    public static List<String> getDefaultBlockBlacklistServer() {
        // Default block blacklist (applied on remote servers, separate from the user blacklist)
        var list = new ArrayList<String>();
        list.add(BlockUtils.getKeyString(Blocks.BARRIER));                    // barrier
        list.add(BlockUtils.getKeyString(Blocks.COMMAND_BLOCK));              // command block
        list.add(BlockUtils.getKeyString(Blocks.CHAIN_COMMAND_BLOCK));        // chain command block
        list.add(BlockUtils.getKeyString(Blocks.REPEATING_COMMAND_BLOCK));    // repeating command block
        list.add(BlockUtils.getKeyString(Blocks.STRUCTURE_VOID));             // structure void
        list.add(BlockUtils.getKeyString(Blocks.STRUCTURE_BLOCK));            // structure block
        list.add(BlockUtils.getKeyString(Blocks.JIGSAW));                     // jigsaw block
        return list;
    }


    public boolean isAllowBlock(Block block) {
        var mc = Minecraft.getInstance();
        // Server-side blacklist check
        if (!mc.isLocalServer()) {
            for (var defaultBlockBlack : blockBlacklistServer) {
                if (BlockUtils.getKeyString(block).equals(defaultBlockBlack)) {
                    return false;
                }
            }
        }
        // User-defined whitelist check
        for (var blockBlack : blockWhitelist) {
            if (BlockUtils.getKeyString(block).equals(blockBlack)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFloorsBlacklist(BlockPos pos) {
        if (!floorsBlacklist.isEmpty()) {  // Floor restriction
            return floorsBlacklist.contains(pos.getY());
        }
        return false;
    }

    public void save() {
        ConfigManager.getInstance().saveConfig();
    }


    public static Config getInstance() {
        return ConfigManager.getInstance().getConfig();
    }
}
