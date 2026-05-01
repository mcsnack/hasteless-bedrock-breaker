package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.Debug;
import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.InteractionUtils;
import com.github.bunnyi116.bedrockminer.util.InventoryUtils;
import com.github.bunnyi116.bedrockminer.util.PlayerLookUtils;
import com.github.bunnyi116.bedrockminer.util.PlayerUtils;
import com.google.common.collect.Queues;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Queue;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.player;

public class Task {
    public final ClientLevel world;
    public final Block block;
    public final BlockPos pos;

    private TaskState currentState;
    private TaskState lastState;
    private @Nullable TaskState nextState;
    public final List<TaskPlan> planItems;
    public @Nullable TaskPlan planItem;
    public final Queue<BlockPos> recycledQueue;
    public boolean executeModify;
    private int tickTotalCount;
    private int tickInternalCount;
    private int ticksTotalMax;
    private int ticksTimeoutMax;
    private int tickWaitMax;
    public int retryCount;
    public int retryCountMax;
    public int active;
    public boolean retry;
    public boolean executed;
    public boolean recycled;
    public boolean timeout;
    private boolean tickOccupied;   // Current tick has been claimed
    private boolean requestPickaxe;

    public Task(ClientLevel world, Block block, BlockPos pos) {
        this.world = world;
        this.block = block;
        this.pos = pos;
        this.planItems = TaskPlanTools.findAllPossible(pos);
        this.recycledQueue = Queues.newConcurrentLinkedQueue();
        this.init();
    }

    private void tickOccupied() {
        this.tickOccupied = true;
    }

    public boolean canInteractWithBlockAt() {
        if (this.world == BedrockMiner.world) {
            if (PlayerUtils.canInteractWithBlockAt(pos, 1F)) {
                if (planItem != null) {
                    return planItem.canInteractWithBlockAt();
                }
                return true;
            }
        }
        return false;
    }

    private void setWait(@Nullable TaskState nextState, int tickWaitMax) {
        this.nextState = nextState;
        this.tickWaitMax = Math.max(tickWaitMax, 1);
        this.currentState = TaskState.WAIT_CUSTOM;
        this.tickOccupied();
    }

    private void setModifyLook(TaskPlanItem blockInfo) {
        if (blockInfo != null) {
            debug("Adjust look");
            setModifyLook(blockInfo.facing);
            blockInfo.modify = true;
            this.tickOccupied();
        }
    }

    private void setModifyLook(Direction facing) {
        PlayerLookUtils.set(facing, this);
        this.tickOccupied();
    }

    private void resetModifyLook() {
        if (PlayerLookUtils.isModify()) {
            PlayerLookUtils.reset();
        }
    }

    public void tick() {
        debug("Begin");
        this.tickOccupied = false;  // Reset state
        if (this.currentState == TaskState.COMPLETE) {
            debug("Task complete");
        } else {
            this.lastState = this.currentState; // Snapshot state for debug output
            if (this.tickTotalCount >= this.ticksTotalMax) {
                this.currentState = TaskState.COMPLETE;
                this.tickOccupied();
            }
            if (!this.timeout && this.tickTotalCount >= this.ticksTimeoutMax) {
                this.timeout = true;
                this.currentState = TaskState.TIMEOUT;
            }
            this.tickInternalCount = 0;
            while (tickInternalCount < 10) {
                this.lastState = this.currentState;
                switch (this.currentState) {
                    case INITIALIZE:
                        this.init();
                        break;
                    case WAIT_GAME_UPDATE:
                        this.updateStates();
                        break;
                    case WAIT_CUSTOM:
                        this.waitCustom();
                        break;
                    case FIND:
                        this.find();
                        break;
                    case PLACE_PISTON:
                        this.placePiston();
                        break;
                    case PLACE_REDSTONE_TORCH:
                        this.placeRedstoneTorch();
                        break;
                    case PLACE_SLIME_BLOCK:
                        this.placeSlimeBlock();
                        break;
                    case EXECUTE:
                        this.execute();
                        break;
                    case RETRY:
                        retry = true;
                        if (!this.recycledQueue.isEmpty()) {
                            this.currentState = TaskState.RECYCLED_ITEMS;
                            return;
                        }
                        if (this.retryCount < this.retryCountMax) {
                            this.retryCount++;
                            this.debug("Recycling done; timeout retry: %s", retryCount);
                            this.currentState = TaskState.INITIALIZE;
                        } else {
                            this.currentState = TaskState.COMPLETE;
                            this.tickOccupied();
                        }
                        break;
                    case TIMEOUT:
                        debug("Task timed out");
                        currentState = TaskState.RETRY;
                        break;
                    case FAIL:
                        debug("Task failed");
                        currentState = TaskState.RETRY;
                        break;
                    case RECYCLED_ITEMS:
                        this.recycledItems();
                        break;
                    case COMPLETE:
                        debug("Task complete");
                        break;
                }
                if (this.lastState == this.currentState) {  // No state change — avoid an empty inner loop
                    debug("State unchanged; no inner loop needed");
                    break;
                }
                if (this.tickOccupied) {
                    debug("Tick claimed exclusively");
                    break;
                }
                ++tickInternalCount;
            }
        }
        debug("End\r\n");
        ++tickTotalCount;
    }

    private void placeSlimeBlock() {
        if (planItem == null) {
            this.currentState = TaskState.FIND;
            return;
        }
        InteractionUtils.placement(planItem.slimeBlock.pos, planItem.slimeBlock.facing, Items.SLIME_BLOCK);
        this.addRecycled(planItem.slimeBlock.pos);
        this.resetModifyLook();
        this.currentState = TaskState.WAIT_GAME_UPDATE;
    }

    private void placeRedstoneTorch() {
        if (planItem == null) {
            this.currentState = TaskState.FIND;
            return;
        }
        debug("Redstone torch");
        BlockState placeBlockState;
        if (planItem.redstoneTorch.facing.getAxis().isVertical()) {
            placeBlockState = Blocks.REDSTONE_TORCH.defaultBlockState();
        } else {
            placeBlockState = Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, planItem.redstoneTorch.facing);
        }
        if (InteractionUtils.canPlace(world, planItem.redstoneTorch.pos, placeBlockState)) {
            if (planItem.redstoneTorch.isNeedModify() && !planItem.redstoneTorch.modify) {
                setModifyLook(planItem.redstoneTorch);
                return;
            }
            InteractionUtils.placement(planItem.redstoneTorch.pos, planItem.redstoneTorch.facing, Items.REDSTONE_TORCH);

            BlockState blockState = world.getBlockState(planItem.redstoneTorch.pos);
            if (planItem.redstoneTorch.facing.getAxis().isHorizontal() && blockState.getBlock() instanceof RedstoneWallTorchBlock) {
                world.setBlock(planItem.redstoneTorch.pos,
                        blockState.setValue(RedstoneWallTorchBlock.FACING, planItem.redstoneTorch.facing),
                        Block.UPDATE_ALL
                );
            }
            this.addRecycled(planItem.redstoneTorch.pos);
            if (Config.getInstance().shortTsk) {
                this.currentState = TaskState.WAIT_GAME_UPDATE;
                this.tickOccupied();
            } else {
                this.setWait(TaskState.WAIT_GAME_UPDATE, 3);
            }
            this.resetModifyLook();
        }
    }

    private void placePiston() {
        if (planItem == null) {
            this.currentState = TaskState.FIND;
            return;
        }
        // Break nearby redstone torches (in region mode this prevents tasks from getting stuck retrying)
        final BlockPos[] nearbyRedstoneTorch = TaskPlanTools.findPistonNearbyRedstoneTorch(planItem.piston.pos, world);
        for (final BlockPos pos : nearbyRedstoneTorch) {
            if (world.getBlockState(pos).getBlock() instanceof RedstoneTorchBlock) {
                InteractionUtils.instamineBlock(pos);
            }
        }
        debug("Place piston");
        BlockState placeBlockState = Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, planItem.piston.facing);
        if (InteractionUtils.canPlace(world, planItem.piston.pos, placeBlockState)) {
            if (planItem.piston.isNeedModify() && !planItem.piston.modify) {
                setModifyLook(planItem.piston);
                return;
            }
            InteractionUtils.placement(planItem.piston.pos, planItem.piston.facing, Items.PISTON, Items.STICKY_PISTON);
            BlockState blockState = world.getBlockState(planItem.piston.pos);
            if (blockState.getBlock() instanceof PistonBaseBlock) {
                world.setBlock(planItem.piston.pos, blockState.setValue(PistonBaseBlock.FACING, planItem.piston.facing), Block.UPDATE_ALL);
            }
            this.addRecycled(planItem.piston.pos);
            if (Config.getInstance().shortTsk) {
                this.currentState = TaskState.WAIT_GAME_UPDATE;
                this.tickOccupied();
            } else {
                this.setWait(TaskState.WAIT_GAME_UPDATE, 3);
            }
            this.resetModifyLook();
        } else {
            this.planItem = null;
            this.currentState = TaskState.FIND;
        }
    }

    private void find() {
        if (this.planItem == null) {
            debug("Find plan");
            for (TaskPlan item : planItems) {
                BlockState slimeBlockState = world.getBlockState(item.slimeBlock.pos);
                if (item.canInteractWithBlockAt()) {
                    item.slimeBlock.level -= 1;
                } else {
                    item.slimeBlock.level += 1000;
                }
                if (InventoryUtils.getInventoryItemCount(Items.SLIME_BLOCK) < 1) {
                    item.slimeBlock.level += 1000;
                } else if (BlockUtils.isReplaceable(slimeBlockState)) {
                    item.slimeBlock.level += 1;
                } else if (BlockUtils.sideCoversSmallSquare(item.slimeBlock.pos, item.slimeBlock.facing)) {
                    item.slimeBlock.level -= 1;
                } else {
                    item.slimeBlock.level += 1000;
                }
            }
            planItems.sort(Comparator
                    .comparingInt((TaskPlan scheme) -> scheme.level + scheme.piston.level + scheme.redstoneTorch.level + scheme.slimeBlock.level)
            );
            for (TaskPlan item : planItems) {
                if (!item.isWorldValid()) {
                    continue;
                }
                final BlockPos pistonPos = item.piston.pos;
                final Direction pistonFacing = item.piston.facing;
                final BlockPos pistonHeadPos = pistonPos.relative(pistonFacing);
                final BlockState pistonState = world.getBlockState(pistonPos);
                final BlockState pistonHeadState = world.getBlockState(pistonHeadPos);
                final BlockState pistonDefaultState = Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, pistonFacing);
                final BlockState pistonHeadDefaultState = Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, pistonFacing);
                if (!InteractionUtils.canPlace(world, pistonPos, pistonDefaultState) || !InteractionUtils.canPlace(world, pistonHeadPos, pistonHeadDefaultState)) {
                    if (!(pistonState.is(Blocks.PISTON) && pistonHeadState.is(Blocks.PISTON_HEAD))) {
                        continue;
                    }
                }
                final BlockState redstoneTorchState = world.getBlockState(item.redstoneTorch.pos);
                if (!BlockUtils.isReplaceable(redstoneTorchState)) {  // A block already exists here
                    // Block type at this position
                    if (!(redstoneTorchState.getBlock() instanceof RedstoneTorchBlock
                            || redstoneTorchState.getBlock() instanceof RedstoneWallTorchBlock
                    )) {
                        continue;
                    }
                }
                if (world.getFluidState(item.redstoneTorch.pos).is(FluidTags.WATER)) {
                    continue;
                }
                if (InteractionUtils.canPlace(world, item.slimeBlock.pos, Blocks.SLIME_BLOCK.defaultBlockState())
                        || BlockUtils.sideCoversSmallSquare(item.slimeBlock.pos, item.slimeBlock.facing)) {// Special plan type 1: target must conduct redstone
                    if (item.redstoneTorch.type == 1 && !world.getBlockState(pos).isRedstoneConductor(world, pos)) {
                        continue;
                    }
                    // If a base needs to be placed, check that we have slime blocks
                    if (BlockUtils.isReplaceable(world.getBlockState(item.slimeBlock.pos))
                            && InventoryUtils.getInventoryItemCount(Items.SLIME_BLOCK) < 1) {
//                        MessageUtils.setOverlayMessage(FAIL_MISSING_SLIME);
                        continue;
                    }
                    boolean b = true;
                    if (!TaskManager.getInstance().getActiveBlockTasks().isEmpty()) {
                        for (Task task : TaskManager.getInstance().getActiveBlockTasks()) {
                            if (task == null) continue;
                            if (task.planItem == null) continue;
                            if (pos.equals(task.planItem.piston.pos)) {
                                b = false;
                                break;
                            }
                            if (item.piston.pos.equals(task.planItem.piston.pos)) {
                                b = false;
                                break;
                            }
                            if (item.piston.pos.equals(task.planItem.redstoneTorch.pos)) {
                                b = false;
                                break;
                            }
                            for (Direction direction : Direction.values()) {
                                if (item.piston.pos.equals(task.planItem.redstoneTorch.pos.relative(direction))) {
                                    b = false;
                                    break;
                                }
                            }
                            if (item.piston.pos.equals(task.planItem.slimeBlock.pos)) {
                                b = false;
                                break;
                            }

                            if (item.redstoneTorch.pos.equals(task.planItem.piston.pos)) {
                                b = false;
                                break;
                            }
                            for (Direction direction : Direction.values()) {
                                if (item.redstoneTorch.pos.equals(task.planItem.piston.pos.relative(direction))) {
                                    b = false;
                                    break;
                                }
                            }
                            if (item.redstoneTorch.pos.equals(task.planItem.redstoneTorch.pos)) {
                                b = false;
                                break;
                            }
                            if (item.redstoneTorch.pos.equals(task.planItem.slimeBlock.pos)) {
                                b = false;
                                break;
                            }


                            if (item.slimeBlock.pos.equals(task.planItem.piston.pos)) {
                                b = false;
                                break;
                            }
                            if (item.slimeBlock.pos.equals(task.planItem.redstoneTorch.pos)) {
                                b = false;
                                break;
                            }

                            if (item.piston.pos.relative(item.piston.facing).equals(task.planItem.piston.pos)) {
                                b = false;
                                break;
                            }
                            if (item.piston.pos.relative(item.piston.facing).equals(task.planItem.redstoneTorch.pos)) {
                                b = false;
                                break;
                            }
                            if (item.piston.pos.relative(item.piston.facing).equals(task.planItem.slimeBlock.pos)) {
                                b = false;
                                break;
                            }
                        }
                    }
                    // Type-1 plans (torch directly below target, powering it via redstone conduction)
                    // require the target itself to be a redstone conductor. Bedrock is, so this is valid.
                    if (item.redstoneTorch.type == 1
                            && !world.getBlockState(pos).isRedstoneConductor(world, pos)) {
                        continue;
                    }
                    if (b) {
                        this.planItem = item;
                        break;
                    }
                }
            }
        }
        if (this.planItem == null) {
            this.currentState = TaskState.FAIL;
            MessageUtils.setOverlayMessage(Component.literal(I18n.HANDLE_SEEK.getString().replace("%BlockPos%", pos.toShortString())));
        } else {
            this.debug("Target: %s", pos);
            this.debug("Plan: %s", this.planItem.direction);
            this.debug("Piston: %s", this.planItem.piston);
            this.debug("Base: %s", this.planItem.slimeBlock);
            this.debug("Redstone torch: %s", this.planItem.redstoneTorch);
            this.currentState = TaskState.WAIT_GAME_UPDATE;
        }
    }

    private void recycledItems() {
        if (!recycledQueue.isEmpty()) {
            var blockPos = recycledQueue.peek();
            if (blockPos == null) {
                recycledQueue.remove();
                return;
            }
            var blockState = world.getBlockState(blockPos);
            debug("Recycling: (%s) --> %s", blockPos.toShortString(), blockState.getBlock().getName().getString());
            if (blockState.getBlock().defaultDestroyTime() < 0) {
                recycledQueue.remove();
                return;
            }
            // Tool switch is only for client-side visual consistency; actual breaking goes through the instamine path and doesn't depend on tool speed
            InventoryUtils.autoSwitch(blockState);
            this.requestPickaxe = false;
            InteractionUtils.instamineBlock(blockPos);
            if (BlockUtils.isReplaceable(world.getBlockState(blockPos))) {
                recycledQueue.remove();
            }
        } else {
            debug("Recycling complete");
            if (retry) {
                currentState = TaskState.RETRY;
            } else {
                currentState = TaskState.COMPLETE;
            }
            this.tickOccupied();
        }
    }

    private void execute() {
        if (executed || player == null || planItem == null) {
            return;
        }
        this.updateStates();    // Re-verify conditions before executing
        if (this.currentState != TaskState.EXECUTE) {
            debug("Conditions not met; waiting for update");
            this.currentState = TaskState.WAIT_GAME_UPDATE;
            this.tickOccupied();
            return;
        }
        if (!executeModify && planItem.direction.getAxis().isHorizontal()) {
            this.setModifyLook(planItem.direction.getOpposite());
            this.executeModify = true;
            return;
        } else {
            // Switch to a tool (only for client-side breaking-progress prediction; actual breaking goes through the instamine path and doesn't depend on tool speed)
            InventoryUtils.autoSwitch(world.getBlockState(planItem.piston.pos));
            this.requestPickaxe = false;
            // Break nearby redstone torches
            final BlockPos[] nearbyRedstoneTorch = TaskPlanTools.findPistonNearbyRedstoneTorch(planItem.piston.pos, world);
            for (final BlockPos pos : nearbyRedstoneTorch) {
                if (world.getBlockState(pos).getBlock() instanceof RedstoneTorchBlock) {
                    InteractionUtils.instamineBlock(pos);
                }
            }
            if (world.getBlockState(planItem.redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock) {
                InteractionUtils.instamineBlock(planItem.redstoneTorch.pos);
            }
            InteractionUtils.instamineBlock(planItem.piston.pos);
            InteractionUtils.placement(planItem.piston.pos, planItem.direction.getOpposite(), Items.PISTON, Items.STICKY_PISTON);
            this.addRecycled(planItem.piston.pos);
            if (this.executeModify) {
                this.resetModifyLook();
            }
            this.requestPickaxe = false;
            this.executed = true;
            this.tickOccupied();
        }
        this.currentState = TaskState.WAIT_GAME_UPDATE;
    }

    private void waitCustom() {
        if (--this.tickWaitMax <= 0) {
            this.currentState = this.nextState == null ? TaskState.WAIT_GAME_UPDATE : this.nextState;
            this.tickWaitMax = 0;
            this.debug("Wait done; state set to: %s", this.currentState);
        } else {
            ++this.ticksTotalMax;
            ++this.ticksTimeoutMax;
            this.tickOccupied();
            this.debug("Wait ticks remaining: %s", tickWaitMax);
        }
    }

    private void updateStates() {
        if (!world.getBlockState(pos).is(block)) {
            this.currentState = TaskState.RECYCLED_ITEMS;
            this.debugUpdateStates("Target no longer exists");
            this.tickOccupied();
            return;
        }
        if (this.planItem == null) {
            this.currentState = TaskState.FIND;
            this.debugUpdateStates("No active plan; searching for one");
            return;
        }
        if (world.getBlockState(planItem.piston.pos).is(Blocks.MOVING_PISTON)) {
            this.debugUpdateStates("Piston is moving");
            this.tickOccupied();
            return;
        }
        if (!this.executed) {
            debugUpdateStates("Task not yet executed");

            if (!canInteractWithBlockAt()) {
                this.debugUpdateStates("Current plan is out of interaction range; re-selecting");
                this.currentState = TaskState.FIND;
                this.tickOccupied();
                return;
            }

            // Piston
            if (BlockUtils.isReplaceable(world.getBlockState(this.planItem.piston.pos))) {
                this.debugUpdateStates("[%s] [%s] Piston not placed and slot is replaceable; queueing place", this.planItem.piston.pos.toShortString(), this.planItem.piston.facing);
                this.currentState = TaskState.PLACE_PISTON;
                return;
            }

            BlockPos pistonHeadPos = planItem.piston.pos.relative(planItem.piston.facing);
            if (world.getBlockState(planItem.piston.pos).getBlock() instanceof PistonBaseBlock) {
                if (!world.getBlockState(this.planItem.piston.pos).hasProperty(PistonBaseBlock.EXTENDED)
                        && world.getBlockState(pistonHeadPos).getBlock() instanceof PistonHeadBlock) {
                    return;
                }
            }


//            if (world.getBlockState(this.planItem.piston.pos).getBlock() instanceof PistonBaseBlock) {
//                if (world.getBlockState(this.planItem.piston.pos).get(PistonBaseBlock.FACING) != this.planItem.piston.facing) {
//                    this.debugUpdateStates("[%s] [%s] Piston is placed but facing the wrong direction", this.planItem.piston.pos.toShortString(), this.planItem.piston.facing);
//                    this.currentState = TaskState.FAIL;
//                    return;
//                }
//            }
            // Base block
            if (BlockUtils.isReplaceable(world.getBlockState(this.planItem.slimeBlock.pos))) {
                this.debugUpdateStates("[%s] [%s] Base not placed and slot is replaceable; queueing place", this.planItem.slimeBlock.pos.toShortString(), this.planItem.slimeBlock.facing);
                this.currentState = TaskState.PLACE_SLIME_BLOCK;
                return;
            }
            if (!BlockUtils.sideCoversSmallSquare(this.planItem.slimeBlock.pos, this.planItem.slimeBlock.facing)) {
                this.debugUpdateStates("[%s] [%s] Base placed but not a full solid block", this.planItem.slimeBlock.pos.toShortString(), this.planItem.slimeBlock.facing);
                this.currentState = TaskState.FAIL;
                return;
            }
            // Redstone torch
            if (BlockUtils.isReplaceable(world.getBlockState(this.planItem.redstoneTorch.pos))) {
                this.debugUpdateStates("[%s] [%s] Redstone torch not placed and slot is replaceable; queueing place", this.planItem.redstoneTorch.pos.toShortString(), this.planItem.redstoneTorch.facing);
                this.currentState = TaskState.PLACE_REDSTONE_TORCH;
                return;
            }
//            if (world.getBlockState(this.planItem.redstoneTorch.pos).getBlock() instanceof RedstoneTorchBlock) {
//                boolean b = false;
//                if (world.getBlockState(this.planItem.redstoneTorch.pos).getBlock() instanceof WallRedstoneTorchBlock) {
//                    if (world.getBlockState(this.planItem.redstoneTorch.pos).get(WallRedstoneTorchBlock.FACING) != this.planItem.redstoneTorch.facing) {
//                        b = true;
//                    }
//                } else if (this.planItem.redstoneTorch.facing != Direction.UP) {
//                    b = true;
//                }
//                if (b) {
//                    this.debugUpdateStates("[%s] [%s] Redstone torch placed but in a state that doesn't match the plan", this.planItem.redstoneTorch.pos.toShortString(), this.planItem.redstoneTorch.facing);
//                    this.currentState = TaskState.FAIL;
//                }
//            }
            if (world.getBlockState(this.planItem.piston.pos).getBlock() instanceof PistonBaseBlock) {
                if (world.getBlockState(this.planItem.piston.pos).hasProperty(PistonBaseBlock.EXTENDED)) {
                    this.debugUpdateStates("[%s] [%s] Conditions met; starting execute step", this.planItem.piston.pos.toShortString(), this.planItem.piston.facing);
                    this.currentState = TaskState.EXECUTE;
                    return;
                }
            }
            // State undetermined; claim the tick and wait
            this.debugUpdateStates("？？？");
            this.tickOccupied();
        }
    }

    private void init() {
        for (final Direction direction : Direction.values()) {
            BlockPos pos1 = pos.relative(direction);
            BlockPos pos2 = pos1.above();
            BlockState pistonState = world.getBlockState(pos1);
            if (pistonState.getBlock() instanceof PistonBaseBlock) {
                if (!TaskManager.getInstance().getActiveBlockTasks().isEmpty()) {
                    for (Task task : TaskManager.getInstance().getActiveBlockTasks()) {
                        if (task == null) continue;
                        if (task.planItem == null) continue;
                        if (pos1.equals(task.planItem.piston.pos)) {
                            return;
                        }
                    }
                }
                InteractionUtils.instamineBlock(pos1);
            }
            BlockState pistonUpState = world.getBlockState(pos2);
            if (pistonUpState.getBlock() instanceof PistonBaseBlock) {
                if (!TaskManager.getInstance().getActiveBlockTasks().isEmpty()) {
                    for (Task task : TaskManager.getInstance().getActiveBlockTasks()) {
                        if (task == null) continue;
                        if (task.planItem == null) continue;
                        if (pos2.equals(task.planItem.piston.pos)) {
                            return;
                        }
                    }
                }
                InteractionUtils.instamineBlock(pos2);
            }
        }
        if (TaskManager.getInstance().isBedrockMinerFeatureEnable()) {
            this.retryCountMax = 1;
        }
        this.nextState = null;
        this.tickTotalCount = 0;
        this.ticksTotalMax = 100;
        this.ticksTimeoutMax = 25;
        this.tickWaitMax = 0;
        this.planItem = null;
        this.recycledQueue.clear();
        this.executed = false;
        this.requestPickaxe = false;
        this.recycled = false;
        this.timeout = false;
        this.currentState = TaskState.WAIT_GAME_UPDATE;
        this.retry = false;
        this.find();
    }

    private void debug(String var1, Object... var2) {
        Debug.write("[{}/{}] [{}:{}/{}] [{} -> {}] {}",
                retryCount, retryCountMax,
                tickTotalCount, tickInternalCount, ticksTotalMax,
                lastState, currentState,
                String.format(var1, var2));
    }

    private void debugUpdateStates(String var1, Object... var2) {
        Debug.write("[{}/{}] [{}:{}/{}] [{} -> {}] [state-update] {}",
                retryCount, retryCountMax,
                tickTotalCount, tickInternalCount, ticksTotalMax,
                lastState, currentState,
                String.format(var1, var2));
    }

    private void addRecycled(BlockPos pos) {
        if (!recycledQueue.contains(pos)) {
            recycledQueue.add(pos);
        }
    }

    public boolean isRequestPickaxe() {
        return requestPickaxe;
    }

    public boolean isComplete() {
        return currentState == TaskState.COMPLETE || tickTotalCount >= ticksTotalMax;
    }

    public TaskState getCurrentState() {
        return currentState;
    }

    public boolean isNeedModify() {
        if (this.planItem != null) {
            return this.planItem.piston.isNeedModify() || this.planItem.redstoneTorch.isNeedModify();
        }
        return false;
    }
}