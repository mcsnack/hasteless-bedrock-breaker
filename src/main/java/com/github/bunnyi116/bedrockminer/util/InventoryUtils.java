package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

public class InventoryUtils {

    public static NonNullList<ItemStack> getMainStacks(Inventory playerInventory) {
        return playerInventory.getNonEquipmentItems();
    }

    public static int getSelectedSlot() {
        return player.getInventory().getSelectedSlot();
    }

    public static void setSelectedSlot(int slot) {
        player.getInventory().setSelectedSlot(slot);
    }

    public static void autoSwitch(Block block) {
        autoSwitch(block.defaultBlockState());
    }

    public static void autoSwitch(BlockState blockState) {
        // Pick the best tool
        float lastTime = -1;
        int lastSlot = -1;
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack itemStack = playerInventory.getItem(i);
            // Skip items whose remaining durability has hit the warning threshold
            if (InventoryUtils.isItemDamageWarning(itemStack, 5)) {
                continue;
            }
            // Pick the fastest tool
            float blockBreakingTotalTime = PlayerUtils.getBlockBreakingSpeed(blockState, itemStack);
            if (lastTime == -1 || lastTime < blockBreakingTotalTime) {
                lastTime = blockBreakingTotalTime;
                lastSlot = i;
            }
        }
        if (lastSlot != -1) {
            InventoryUtils.switchToSlot(lastSlot);
        }
    }

    public static void swapSlots(LocalPlayer player, MultiPlayerGameMode interactionManager, int sourceSlot, int hotbarIndex) {
        if (player == null || interactionManager == null) {
            return;
        }
        if (player.containerMenu == player.inventoryMenu) {
            if (sourceSlot == hotbarIndex) {   // Same slot
                return;
            }
            interactionManager.handleContainerInput(
                    player.containerMenu.containerId,   // Current container ID
                    sourceSlot,                          // Source slot index
                    hotbarIndex,                         // Target hotbar index (0-8)
                    ContainerInput.SWAP,                      // Swap action
                    player
            );
        }
    }

    public static void pickFromInventory(LocalPlayer player, MultiPlayerGameMode interactionManager, int slot) {
        if (player == null || interactionManager == null) {
            return;
        }

        Inventory playerInventory = player.getInventory();
        // containerMenu is the open container; inventoryMenu is the player's own inventory screen
        if (player.containerMenu != player.inventoryMenu) {
            return; // Only run when no chest/UI is open
        }

        // If the slot is already on the hotbar (0-8), just select it
        if (Inventory.isHotbarSlot(slot)) {
            InventoryUtils.setSelectedSlot(slot);
            return;
        }

        for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
            final ItemStack itemStack = playerInventory.getItem(i);
            if (itemStack.isEmpty()) {
                swapSlots(player, interactionManager, slot, i);
                InventoryUtils.setSelectedSlot(i);
                return;
            }
        }
        swapSlots(player, interactionManager, slot, 6);
        InventoryUtils.setSelectedSlot(6);
    }

    public static void switchToSlot(int slot) {
        // Item is in the inventory, not the hotbar
        if (Inventory.isHotbarSlot(slot)) {
            InventoryUtils.setSelectedSlot(slot);
        } else {
            pickFromInventory(player, interactionManager, slot);
        }
        NetworkUtils.sendPacket(new ServerboundSetCarriedItemPacket(InventoryUtils.getSelectedSlot())); // Notify the server of the held-item change
    }

    public static void switchToItem(int minDamage, Item... items) {
        NonNullList<ItemStack> MainStacks = InventoryUtils.getMainStacks(playerInventory);
        for (int i = 0; i < MainStacks.size(); i++) {
            ItemStack stack = MainStacks.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            for (Item item : items) {
                if (stack.is(item)) {
                    // Skip items whose remaining durability has hit the warning threshold
                    if (minDamage > 0 && InventoryUtils.isItemDamageWarning(stack, minDamage)) {
                        continue;
                    }
                    switchToSlot(i);
                    return;
                }
            }
        }
    }

    public static void switchToItem(Item... items) {
        switchToItem(-1, items);
    }

    /**
     * Whether the item's remaining durability is at or below the given threshold.
     *
     * @param itemStack item to check
     * @param minDamage minimum remaining durability before this returns true
     * @return true if remaining durability &lt;= minDamage; false if the item has no durability
     */
    public static boolean isItemDamageWarning(ItemStack itemStack, int minDamage) {
        int damageMax = itemStack.getMaxDamage();   // Item's max durability
        if (damageMax > 0) {
            int damage = itemStack.getDamageValue();     // Already-used durability
            int damageSurplus = damageMax - damage; // Remaining durability
            return damageSurplus <= minDamage;      // True if remaining durability is at/below the threshold
        }
        return false;   // Item has no durability
    }


    /*** Count of an item in the inventory. ***/
    public static int getInventoryItemCount(Item item) {
        return playerInventory.countItem(item);
    }
}