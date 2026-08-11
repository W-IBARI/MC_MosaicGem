package com.mosaicgem.plugin.listener;

import com.mosaicgem.plugin.MosaicGemPlugin;
import com.mosaicgem.plugin.config.ConfigManager;
import com.mosaicgem.plugin.model.OperationResult;
import com.mosaicgem.plugin.service.GemService;
import com.mosaicgem.plugin.util.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
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
import org.bukkit.plugin.Plugin;

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

    /**
     * 从一组工具中消耗一个，返回剩余部分（只剩一个时返回 null）。
     */
    private ItemStack consumeOne(ItemStack stack) {
        if (stack == null || stack.getAmount() <= 1) {
            return null;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(stack.getAmount() - 1);
        return copy;
    }

    private void send(Player player, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        player.sendMessage(ItemFactory.colorize(configs.prefix() + message));
    }

    /**
     * 操作成功修改了物品后，通知 SX-Attribute 重新计算玩家属性，
     * 避免拖拽镶嵌等场景下属性要等切手/重进才生效。
     */
    private void refreshSxAttributes(Player player) {
        Plugin sx = Bukkit.getPluginManager().getPlugin("SX-Attribute");
        if (sx == null) {
            return;
        }
        try {
            Class<?> sxClass = Class.forName("github.saukiya.sxattribute.SXAttribute", true, sx.getClass().getClassLoader());
            Object api = sxClass.getMethod("getApi").invoke(null);
            api.getClass().getMethod("updateData", LivingEntity.class).invoke(api, player);
            api.getClass().getMethod("attributeUpdate", LivingEntity.class).invoke(api, player);
        } catch (ReflectiveOperationException e) {
            MosaicGemPlugin.instance().getLogger().warning("刷新 SX-Attribute 属性失败: " + e.getMessage());
        }
    }
}
