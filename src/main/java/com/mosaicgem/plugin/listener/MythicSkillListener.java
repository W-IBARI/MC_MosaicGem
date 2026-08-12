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

import java.util.Locale;

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
                SkillLine skill = parseSkillLine(skillLine, gem);
                if (skill == null || !isAttackTrigger(skill.trigger())) {
                    continue;
                }
                bridge.castSkill(player, skill.name(), event.getEntity());
            }
        }
    }

    /**
     * 解析技能行：支持 {@code 技能名 @触发器} / {@code skill:技能名 @触发器} / 纯技能名（默认 onSwing）。
     */
    private SkillLine parseSkillLine(String line, SocketedGem gem) {
        String resolved = factory.resolve(line, gem.values());
        if (resolved == null || resolved.isBlank()) {
            return null;
        }
        String text = ItemFactory.stripLoreText(resolved);
        String trigger = "SWING";
        int at = text.lastIndexOf('@');
        if (at >= 0 && at + 1 < text.length()) {
            trigger = normalizeTrigger(text.substring(at + 1).trim());
            text = text.substring(0, at).trim();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("skill:")) {
            text = text.substring("skill:".length()).trim();
        }
        return text.isEmpty() ? null : new SkillLine(text, trigger);
    }

    private static String normalizeTrigger(String raw) {
        if (raw == null) {
            return "SWING";
        }
        String value = raw.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("on")) {
            value = value.substring(2);
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private static boolean isAttackTrigger(String trigger) {
        return switch (trigger) {
            case "SWING", "ATTACK", "HIT", "LEFTCLICK" -> true;
            default -> false;
        };
    }

    private record SkillLine(String name, String trigger) {
    }
}
