package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.util.CombinedIterator;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.BlockUtils;
import com.github.bunnyi116.bedrockminer.util.InventoryUtils;
import com.github.bunnyi116.bedrockminer.util.PlayerLookUtils;
import com.github.bunnyi116.bedrockminer.util.PlayerUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;
import static com.github.bunnyi116.bedrockminer.I18n.*;

public class TaskManager {
    private static volatile @Nullable TaskManager INSTANCE;
    private final ArrayList<Task> pendingBlockTasks = new ArrayList<>();
    private final ArrayList<Task> activeBlockTasks = new ArrayList<>();
    private final ArrayList<Task> cacheBlockTasks = new ArrayList<>();
    private final List<TaskRegion> pendingRegionTasks = new ArrayList<>();
    private boolean running;
    private boolean processing;
    private boolean bedrockMinerFeatureEnable = true;
    private int sortCount;

    public void tick() {
        if (!gameVariableIsValid()) {
            return;
        }
        if (Config.getInstance().disable || !this.isRunning()) {
            PlayerLookUtils.tick();
            return;
        }
        if (this.pendingBlockTasks.isEmpty() && this.pendingRegionTasks.isEmpty() && Config.getInstance().ranges.isEmpty()) {
            this.removeBlockTaskAll();
            return;
        }
        if (!isAllowExecutionEnvironment(activeBlockTasks.isEmpty())) {
            return;
        }
        // Sort once every 40 ticks
        if (!this.pendingBlockTasks.isEmpty() || !this.activeBlockTasks.isEmpty()) {
            if (sortCount > 0) {
                sortCount--;
            } else {
                sortCount = 40;
                if (this.pendingBlockTasks.size() > 1) {
                    this.pendingBlockTasks.sort((a1, a2) -> {
                        // First sort by Y coordinate descending (higher priority)
                        int dy = a2.pos.getY() - a1.pos.getY();
                        // If Y coordinates differ, return comparison result
                        if (dy != 0) {
                            return dy;
                        }
                        // If Y coordinates are the same, sort by horizontal distance ascending (closer priority)
                        double dist1 = PlayerUtils.getHorizontalDistanceToPlayer(a1.pos);
                        double dist2 = PlayerUtils.getHorizontalDistanceToPlayer(a2.pos);
                        return Double.compare(dist1, dist2);
                    });
                }
                if (this.activeBlockTasks.size() > 1) {
                    this.activeBlockTasks.sort((a1, a2) -> Boolean.compare(a1.isNeedModify(), a2.isNeedModify()));
                }

            }
        }
        boolean execute = false;
        boolean requestPickaxe = false;
        boolean modifyLook = false;
        if (!this.activeBlockTasks.isEmpty()) {
            if (this.activeBlockTasks.size() > 1) {
                for (Task entry : this.activeBlockTasks) {
                    if (entry == null) continue;
                    if (entry.getCurrentState() == TaskState.EXECUTE) {
                        execute = true;
                        break;
                    }
                    if (entry.isRequestPickaxe()) {
                        requestPickaxe = true;
                        break;
                    }
                }
            }
            int resetCountMax = Config.getInstance().outOfRangeCooldownTicks;
            Iterator<Task> iterator = this.activeBlockTasks.iterator();
            while (iterator.hasNext()) {
                Task currentTask = iterator.next();
                if (currentTask == null) continue;
                if (currentTask.world != world || !currentTask.canInteractWithBlockAt()) {
                    if (resetCountMax > 0) {
                        currentTask.active++;
                        if (currentTask.active >= resetCountMax) {
                            // Cooldown expired: move to cache so a new task can be picked
                            this.cacheBlockTasks.add(currentTask);
                            iterator.remove();
                            currentTask.active = 0;
                        } else {
                            MessageUtils.setOverlayMessage(Component.literal("Moved away from current block, cooldown remaining: " + (resetCountMax - currentTask.active)));
                        }
                    } else {
                        // Cooldown disabled: immediately move to cache
                        this.cacheBlockTasks.add(currentTask);
                        iterator.remove();
                        currentTask.active = 0;
                    }
                    continue;
                }
                // In range: reset the out-of-range counter so a previous excursion doesn't carry over
                currentTask.active = 0;
                processing = true;
                if (PlayerLookUtils.getTask() != null && !activeBlockTasks.contains(PlayerLookUtils.getTask())) {
                    PlayerLookUtils.reset();
                }
                if (PlayerLookUtils.isModify()) {
                    if (PlayerLookUtils.getTask() != currentTask) {
                        continue;
                    }
                    modifyLook = true;
                }
                if (execute && currentTask.getCurrentState() != TaskState.EXECUTE) {
                    continue;
                }
                if (requestPickaxe && !currentTask.isRequestPickaxe()) {
                    continue;
                }
                currentTask.tick();
                currentTask.active = 0;
                switch (currentTask.getCurrentState()) {
                    case EXECUTE -> {
                        if (currentTask.planItem != null && !currentTask.planItem.piston.isNeedModify()) {
                            execute = true;
                        } else {
                            return;
                        }
                    }
                    case RECYCLED_ITEMS -> {
                        return;
                    }
                }
                processing = false;
                if (currentTask.isComplete()) {
                    iterator.remove();
                    this.pendingBlockTasks.remove(currentTask);
                    currentTask.active = 0;
                    continue;
                }
                if (modifyLook) {
                    return;
                }
            }
            if (this.activeBlockTasks.size() >= Config.getInstance().limitMax) {
                return;
            }
        }

        // First check if there are cached tasks
        if (!this.cacheBlockTasks.isEmpty()) {
            Iterator<Task> iterator = this.cacheBlockTasks.iterator();
            while (iterator.hasNext()) {
                Task task = iterator.next();
                if (task == null) continue;
                BlockState blockState = world.getBlockState(task.pos);
                if (blockState.isAir() || BlockUtils.isReplaceable(blockState)) {
                    continue;
                }
                if (task.world != world) {
                    continue;
                }
                if (!task.canInteractWithBlockAt()) {
                    continue;
                }
                if (PlayerLookUtils.isModify() && PlayerLookUtils.getTask() != task) {
                    continue;
                }
                iterator.remove();
                this.activeBlockTasks.add(task);
                if (this.activeBlockTasks.size() >= Config.getInstance().limitMax) {
                    return;
                }
                return;
            }
        }

        double playerBlockInteractionRange = PlayerUtils.getBlockInteractionRange();
        int radius = (int) Math.ceil(playerBlockInteractionRange);

        // No active tasks, prepare to select a new one
        Iterator<Task> iterator = this.pendingBlockTasks.iterator();
        while (iterator.hasNext() && this.activeBlockTasks.size() < Config.getInstance().limitMax) {
            Task task = iterator.next();
            if (task == null) continue;
            BlockState blockState = world.getBlockState(task.pos);
            Block block = blockState.getBlock();
            if (blockState.isAir() || BlockUtils.isReplaceable(blockState)) {
                continue;
            }
            if (!Config.getInstance().isAllowBlock(block)) {
                continue;
            }
            if (Config.getInstance().isFloorsBlacklist(task.pos)) {
                continue;
            }
            if (!task.canInteractWithBlockAt()) {
                continue;
            }
            if (task.world != world) {
                iterator.remove();
            }
            if (!this.activeBlockTasks.contains(task)) {
                this.activeBlockTasks.add(task);
            }
            if (this.activeBlockTasks.size() >= Config.getInstance().limitMax) {
                return;
            }
        }

        // No active tasks, prepare to select a new one
        if (this.activeBlockTasks.size() < Config.getInstance().limitMax) {
            // Combined iterator (avoid creating new arrays and wasting memory)
            final CombinedIterator<TaskRegion> iterator2 = new CombinedIterator<>(Config.getInstance().ranges, pendingRegionTasks);
            while (iterator2.hasNext()) {
                TaskRegion range = iterator2.next();
                if (!range.isForWorld(world)) continue;
                BoundingBox rangeBox = BoundingBox.fromCorners(range.pos1, range.pos2);
                BoundingBox playerBox = new BoundingBox(player.blockPosition());
                BoundingBox playerExpandBox = playerBox.inflatedBy(radius);
                if (!rangeBox.intersects(playerExpandBox)) continue;

                for (int dy = radius; dy > -radius; dy--) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            BlockPos blockPos = player.blockPosition().offset(dx, dy, dz);
                            if (!PlayerUtils.canInteractWithBlockAt(blockPos, 1.0F)) {
                                continue;
                            }
                            final BlockState blockState = world.getBlockState(blockPos);
                            final Block block = blockState.getBlock();
                            if (blockState.isAir() || BlockUtils.isReplaceable(blockState)) {
                                continue;
                            }
                            if (!Config.getInstance().isAllowBlock(block)) {
                                continue;
                            }
                            if (Config.getInstance().isFloorsBlacklist(blockPos)) {
                                continue;
                            }
                            final Task task = new Task(world, block, blockPos);
                            if (!task.canInteractWithBlockAt()) {
                                continue;
                            }
                            if (PlayerLookUtils.isModify() && PlayerLookUtils.getTask() != task) {
                                continue;
                            }
                            if (task.world != world) {
                                iterator2.remove();
                                continue;
                            }
                            if (!this.activeBlockTasks.contains(task)) {
                                this.activeBlockTasks.add(task);
                            }
                            if (this.activeBlockTasks.size() >= Config.getInstance().limitMax) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean isAllowExecutionEnvironment(boolean setOverlayMessage) {
        Component msg = null;
        if (gameMode.isCreative()) {
            msg = FAIL_MISSING_SURVIVAL;
        }
        if (interactionManager != null && !interactionManager.getPlayerMode().isSurvival()) {
            msg = FAIL_MISSING_SURVIVAL;
        }
        if (InventoryUtils.getInventoryItemCount(Items.PISTON) < 2) {
            msg = FAIL_MISSING_PISTON;
        }
        if (InventoryUtils.getInventoryItemCount(Items.REDSTONE_TORCH) < 1) {
            msg = FAIL_MISSING_REDSTONETORCH;
        }
        // The instamine path sends START+STOP packets directly and does not depend on
        // vanilla mining speed, so no Efficiency/Haste check is needed here.
        if (msg != null) {
            if (setOverlayMessage) {
                MessageUtils.setOverlayMessage(msg);
            }
            return false;
        }
        return true;
    }

    public void addBlockTask(ClientLevel world, BlockPos pos, Block block) {
        if (Config.getInstance().disable || !isRunning()) {
            return;
        }
        if (!isAllowExecutionEnvironment(true)) {
            return;
        }
        if (!gameMode.isSurvival()) {
            return;
        }
        if (!Config.getInstance().isAllowBlock(block)) {
            return;
        }
        if (Config.getInstance().isFloorsBlacklist(pos)) {  // Floor restriction
            String msg = FLOOR_BLACK_LIST_WARN.getString().replace("(#floor#)", String.valueOf(pos.getY()));
            MessageUtils.setOverlayMessage(Component.literal(msg));
            return;
        }
        for (Task targetBlock : pendingBlockTasks) {
            if (targetBlock.pos.equals(pos)) {
                return;
            }
        }
        pendingBlockTasks.add(new Task(world, block, pos));
    }

    public void removeBlockTask(ClientLevel world, BlockPos pos) {
        final Iterator<Task> iterator = pendingBlockTasks.iterator();
        while (iterator.hasNext()) {
            Task task = iterator.next();
            if (task.pos.equals(pos)) {
                iterator.remove();
                return;
            }
        }
    }

    public void removeBlockTaskAll() {
        this.activeBlockTasks.clear();
        this.cacheBlockTasks.clear();
        this.pendingBlockTasks.clear();
    }

    public void addRegionTask(String name, ClientLevel world, BlockPos pos1, BlockPos pos2) {
        for (TaskRegion range : this.pendingRegionTasks) {
            if (range.name.equals(name)) {
                return;
            }
        }
        this.pendingRegionTasks.add(new TaskRegion(name, world, pos1, pos2));
    }

    public void removeRegionTaskAll(String name) {
        final Iterator<TaskRegion> iterator = pendingRegionTasks.iterator();
        while (iterator.hasNext()) {
            TaskRegion range = iterator.next();
            if (range.name.equals(name)) {
                iterator.remove();
                return;
            }
        }
    }

    public void removeRegionTaskAll() {
        pendingRegionTasks.clear();
    }

    public void removeAll() {
        removeAll(true);
    }

    public void removeAll(boolean showMessage) {
        removeBlockTaskAll();
        removeRegionTaskAll();
        if (showMessage) {
            MessageUtils.addMessage(COMMAND_TASK_CLEAR);
        }
    }

    public void switchToggle(@Nullable Block block) {
        if (Config.getInstance().disable || !Config.getInstance().isAllowBlock(block))
            return;
        this.switchToggle();
    }

    public void switchToggle() {
        if (this.isRunning()) {
            this.removeAll();
            this.setRunning(false);
        } else {
            if (gameMode.isCreative()) { // Only enable in survival mode
                MessageUtils.addMessage(FAIL_MISSING_SURVIVAL);
                return;
            }
            this.setRunning(true);
            if (!client.isLocalServer()) {   // Send warning when on server
                MessageUtils.addMessage(WARN_MULTIPLAYER);
            }
        }
    }

    public void setRunning(boolean running) {
        this.setRunning(running, true);
    }

    public void setRunning(boolean running, boolean showMessage) {
        if (showMessage) {
            if (running) {
                MessageUtils.addMessage(TOGGLE_ON);
            } else {
                MessageUtils.addMessage(TOGGLE_OFF);
            }
        }
        this.running = running;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isProcessing() {
        return processing;
    }

    public boolean isInTasks(ClientLevel world, BlockPos pos) {
        for (Task targetBlock : pendingBlockTasks) {
            if (targetBlock.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public boolean isBedrockMinerFeatureEnable() {
        return bedrockMinerFeatureEnable;
    }

    public void setBedrockMinerFeatureEnable(boolean bedrockMinerFeatureEnable) {
        this.bedrockMinerFeatureEnable = bedrockMinerFeatureEnable;
    }

    public List<Task> getPendingBlockTasks() {
        return pendingBlockTasks;
    }

    public List<TaskRegion> getPendingRegionTasks() {
        return pendingRegionTasks;
    }

    public ArrayList<Task> getActiveBlockTasks() {
        return activeBlockTasks;
    }

    public ArrayList<Task> getCacheBlockTasks() {
        return cacheBlockTasks;
    }

    public static TaskManager getInstance() {
        if (INSTANCE == null) {
            synchronized (TaskManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TaskManager();
                }
            }
        }
        return INSTANCE;
    }

    //region Compatibility methods for BiliXWhite/litematica-printer (author does not update frequently)
    public static void addTask(Block block, BlockPos pos, ClientLevel world) {
        TaskManager.getInstance().addBlockTask(world, pos, block);
    }

    public static boolean isWorking() {
        return TaskManager.getInstance().isRunning();
    }

    public static void setWorking(boolean working) {
        TaskManager.getInstance().setRunning(working);
    }

    public static void clearTask() {
        TaskManager.getInstance().removeAll();
    }
//endregion

}