package com.mosaicgem.plugin.listener;

import com.mosaicgem.plugin.config.ConfigManager;
import com.mosaicgem.plugin.model.OperationResult;
import com.mosaicgem.plugin.service.GemService;
import com.mosaicgem.plugin.util.ItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * 三种交互方式：铁砧、合成台、拖拽。
 */
public class InteractionListener implements Listener {

    private final ConfigManager configs;
    private final ItemFactory factory;
    private final GemService service;

    public InteractionListener(ConfigManager configs, ItemFactory factory, GemService service) {
        this.configs = configs;
        this.factory = factory;
        this.service = service;
    }

    private record MatrixCombo(GemService.Combo combo, int toolIndex, int targetIndex) {
    }

    // ------------------------------------------------------------------
    // 铁砧
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        GemService.Combo combo = service.findCombo(inventory.getItem(0), inventory.getItem(1));
        if (combo == null) {
            return;
        }
        if (!configs.isInteractionEnabled("anvil")) {
            event.setResult(null);
            return;
        }
        event.setResult(service.buildPreview(combo));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnvilResultClick(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof AnvilInventory inventory)) {
            return;
        }
        if (event.getRawSlot() != 2) {
            return;
        }
        GemService.Combo combo = service.findCombo(inventory.getItem(0), inventory.getItem(1));
        if (combo == null) {
            return;
        }
        event.setCancelled(true);
        if (!configs.isInteractionEnabled("anvil")) {
            inventory.setItem(2, null);
            send((Player) event.getWhoClicked(), configs.message("interaction-disabled"));
            return;
        }

        Player player = (Player) event.getWhoClicked();
        OperationResult result = service.perform(combo, player);
        if (!result.consumeTool()) {
            inventory.setItem(2, null);
            send(player, result.message());
            return;
        }

        inventory.setItem(0, null);
        inventory.setItem(1, null);
        inventory.setItem(2, null);
        event.setCursor(result.targetItem());
        if (result.returnItem() != null) {
            giveItem(player, result.returnItem());
        }
        send(player, result.message());
    }

    // ------------------------------------------------------------------
    // 合成台 / 随身合成
    // ------------------------------------------------------------------

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

        inventory.setItem(matrixCombo.toolIndex(), null);
        inventory.setItem(matrixCombo.targetIndex(), null);
        inventory.setResult(null);
        event.setCursor(result.targetItem());
        if (result.returnItem() != null) {
            giveItem(player, result.returnItem());
        }
        send(player, result.message());
    }

    // ------------------------------------------------------------------
    // 拖拽（光标持有工具，点击目标物品）
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDragClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            return;
        }
        // 铁砧/合成台已有专门处理，避免重复触发
        InventoryType type = event.getClickedInventory().getType();
        if (type == InventoryType.ANVIL || type == InventoryType.CRAFTING || type == InventoryType.WORKBENCH) {
            return;
        }
        if (!configs.isInteractionEnabled("drag")) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();
        boolean cursorIsTool = factory.getToolType(cursor) != null;
        boolean clickedIsTool = factory.getToolType(clicked) != null;
        if (cursorIsTool == clickedIsTool) {
            return;
        }

        ItemStack tool = cursorIsTool ? cursor : clicked;
        ItemStack target = cursorIsTool ? clicked : cursor;
        GemService.Combo combo = service.findCombo(tool, target);
        if (combo == null) {
            return;
        }
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        OperationResult result = service.perform(combo, player);
        if (!result.consumeTool()) {
            send(player, result.message());
            return;
        }

        if (cursorIsTool) {
            event.setCursor(null);
            event.setCurrentItem(result.targetItem());
        } else {
            event.setCursor(result.targetItem());
            event.setCurrentItem(null);
        }
        if (result.returnItem() != null) {
            giveItem(player, result.returnItem());
        }
        send(player, result.message());
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

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

    private void giveItem(Player player, ItemStack item) {
        ItemStack copy = item.clone();
        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(copy);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
            send(player, configs.message("inventory-full"));
        }
    }

    private void send(Player player, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        player.sendMessage(ItemFactory.colorize(configs.prefix() + message));
    }
}
