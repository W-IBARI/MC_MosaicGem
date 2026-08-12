package com.mosaicgem.plugin.listener;

import com.mosaicgem.plugin.config.ConfigManager;
import com.mosaicgem.plugin.config.GemDefinition;
import com.mosaicgem.plugin.model.SocketData;
import com.mosaicgem.plugin.model.SocketedGem;
import com.mosaicgem.plugin.util.ItemFactory;
import com.mosaicgem.plugin.util.MythicMobsBridge;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * mm 技能宝石：玩家使用镶嵌了 MythicMobs 技能宝石的装备攻击时，
 * 按 MythicMobs 规则施放宝石配置的技能。
 */
public final class MythicSkillListener implements Listener {

    private final ConfigManager configs;
    private final ItemFactory factory;
    private final MythicMobsBridge bridge;

    public MythicSkillListener(ConfigManager configs, ItemFactory factory, MythicMobsBridge bridge) {
        this.configs = configs;
        this.factory = factory;
        this.bridge = bridge;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            return;
        }
        SocketData data = factory.readSocketData(item);
        if (data.gems().isEmpty()) {
            return;
        }
        for (SocketedGem gem : data.gems()) {
            GemDefinition definition = configs.getGem(gem.id());
            if (definition == null || !ItemFactory.BUFF_TYPE_MM_SKILL.equalsIgnoreCase(definition.getBuffType())) {
                continue;
            }
            for (String skillLine : definition.getAttribute()) {
                String skill = factory.resolve(skillLine, gem.values());
                if (skill == null || skill.isBlank()) {
                    continue;
                }
                bridge.castSkill(player, skill.trim(), event.getEntity());
            }
        }
    }
}
