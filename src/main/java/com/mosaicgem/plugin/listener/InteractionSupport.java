package com.mosaicgem.plugin.listener;

import com.mosaicgem.plugin.MosaicGemPlugin;
import com.mosaicgem.plugin.config.ConfigManager;
import com.mosaicgem.plugin.service.GemService;
import com.mosaicgem.plugin.util.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * 三种交互监听器的公共基类：持有配置/工厂/业务服务，并提供物品返还、工具消耗、
 * 消息发送与 SX-Attribute 属性刷新等公共工具。
 */
abstract class InteractionSupport {

    protected final ConfigManager configs;
    protected final ItemFactory factory;
    protected final GemService service;

    protected InteractionSupport(ConfigManager configs, ItemFactory factory, GemService service) {
        this.configs = configs;
        this.factory = factory;
        this.service = service;
    }

    protected void giveItem(Player player, ItemStack item) {
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
    protected ItemStack consumeOne(ItemStack stack) {
        if (stack == null || stack.getAmount() <= 1) {
            return null;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(stack.getAmount() - 1);
        return copy;
    }

    protected void send(Player player, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        player.sendMessage(ItemFactory.text(configs.prefix() + message));
    }

    /**
     * 操作成功修改了物品后，通知 SX-Attribute 重新计算玩家属性，
     * 避免拖拽镶嵌等场景下属性要等切手/重进才生效。
     */
    protected void refreshSxAttributes(Player player) {
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
