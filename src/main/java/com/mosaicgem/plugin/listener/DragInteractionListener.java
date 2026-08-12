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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * 拖拽交互：光标持有工具时点击目标物品。
 */
public final class DragInteractionListener extends InteractionSupport implements Listener {

    public DragInteractionListener(ConfigManager configs, ItemFactory factory, GemService service) {
        super(configs, factory, service);
    }

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
        boolean cursorIsTool = factory.getToolType(cursor) != null;
        // 只有光标上已经吸附工具时，本次点击才被视为拖拽操作
        if (!cursorIsTool) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        // 点击空格：原样放置，不拦截
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        // 目标是插件物品（工具）：可能是同类工具合并或换位，交给原版处理，不拦截、不提示
        if (factory.getToolType(clicked) != null) {
            return;
        }

        GemService.Combo combo = service.findCombo(cursor, clicked);
        if (combo == null) {
            event.setCancelled(true);
            send(player, configs.message("tool-config-missing"));
            if (player.getGameMode() == GameMode.CREATIVE) {
                player.updateInventory();
            }
            return;
        }
        event.setCancelled(true);

        OperationResult result = service.perform(combo, player);
        if (!result.consumeTool()) {
            send(player, result.message());
            if (player.getGameMode() == GameMode.CREATIVE) {
                player.updateInventory();
            }
            return;
        }

        // 一次行为只消耗一个工具，剩余继续吸附在光标上
        if (player.getGameMode() == GameMode.CREATIVE) {
            // 创造模式下直接修改槽位并强制刷新，避免客户端残留/复制
            event.getClickedInventory().setItem(event.getSlot(), result.targetItem());
            event.getView().setCursor(consumeOne(cursor));
            player.updateInventory();
        } else {
            event.setCursor(consumeOne(cursor));
            event.setCurrentItem(result.targetItem());
        }
        if (result.returnItem() != null) {
            giveItem(player, result.returnItem());
        }
        refreshSxAttributes(player);
        send(player, result.message());
    }
}
