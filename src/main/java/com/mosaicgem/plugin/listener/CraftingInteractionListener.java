package com.mosaicgem.plugin.listener;

import com.mosaicgem.plugin.config.ConfigManager;
import com.mosaicgem.plugin.model.OperationResult;
import com.mosaicgem.plugin.service.GemService;
import com.mosaicgem.plugin.util.ItemFactory;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * 合成台 / 随身合成交互：预览与结果点击。
 */
public final class CraftingInteractionListener extends InteractionSupport implements Listener {

    public CraftingInteractionListener(ConfigManager configs, ItemFactory factory, GemService service) {
        super(configs, factory, service);
    }

    private record MatrixCombo(GemService.Combo combo, int toolIndex, int targetIndex) {
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        MatrixCombo matrixCombo = findMatrixCombo(inventory.getMatrix());
        if (matrixCombo == null) {
            return;
        }
        if (!configs.isInteractionEnabled("crafting")) {
            inventory.setResult(null);
            return;
        }
        inventory.setResult(service.buildPreview(matrixCombo.combo()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftResultClick(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof CraftingInventory inventory)) {
            return;
        }
        if (event.getRawSlot() != 0) {
            return;
        }
        MatrixCombo matrixCombo = findMatrixCombo(inventory.getMatrix());
        if (matrixCombo == null) {
            return;
        }
        event.setCancelled(true);
        if (!configs.isInteractionEnabled("crafting")) {
            inventory.setResult(null);
            send((Player) event.getWhoClicked(), configs.message("interaction-disabled"));
            return;
        }

        Player player = (Player) event.getWhoClicked();
        OperationResult result = service.perform(matrixCombo.combo(), player);
        if (!result.consumeTool()) {
            inventory.setResult(null);
            send(player, result.message());
            return;
        }

        // 合成台物品槽：0 为结果槽，矩阵槽从 1 开始
        int toolSlot = matrixCombo.toolIndex() + 1;
        int targetSlot = matrixCombo.targetIndex() + 1;
        inventory.setItem(toolSlot, consumeOne(inventory.getItem(toolSlot)));
        inventory.setItem(targetSlot, null);
        inventory.setResult(null);
        event.setCursor(result.targetItem());
        if (player.getGameMode() == GameMode.CREATIVE) {
            player.updateInventory();
        }
        if (result.returnItem() != null) {
            giveItem(player, result.returnItem());
        }
        refreshSxAttributes(player);
        send(player, result.message());
    }

    private MatrixCombo findMatrixCombo(ItemStack[] matrix) {
        ItemStack tool = null;
        ItemStack target = null;
        int toolIndex = -1;
        int targetIndex = -1;
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (factory.getToolType(item) != null) {
                if (tool != null) {
                    return null;
                }
                tool = item;
                toolIndex = i;
            } else {
                if (target != null) {
                    return null;
                }
                target = item;
                targetIndex = i;
            }
        }
        if (tool == null || target == null) {
            return null;
        }
        GemService.Combo combo = service.findCombo(tool, target);
        return combo == null ? null : new MatrixCombo(combo, toolIndex, targetIndex);
    }
}
