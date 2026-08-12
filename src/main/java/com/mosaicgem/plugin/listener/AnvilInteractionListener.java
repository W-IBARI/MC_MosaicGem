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
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

/**
 * 铁砧交互：预览与结果点击。
 */
public final class AnvilInteractionListener extends InteractionSupport implements Listener {

    public AnvilInteractionListener(ConfigManager configs, ItemFactory factory, GemService service) {
        super(configs, factory, service);
    }

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

        boolean toolInSlot1 = factory.getToolType(inventory.getItem(1)) != null;
        ItemStack toolStack = toolInSlot1 ? inventory.getItem(1) : inventory.getItem(0);
        ItemStack remainingTool = consumeOne(toolStack);
        inventory.setItem(0, toolInSlot1 ? null : remainingTool);
        inventory.setItem(1, toolInSlot1 ? remainingTool : null);
        inventory.setItem(2, null);
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
}
