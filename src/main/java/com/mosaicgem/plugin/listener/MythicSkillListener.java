package com.mosaicgem.plugin.listener;

import com.mosaicgem.plugin.util.FallbackSkillTriggers;
import com.mosaicgem.plugin.util.MythicSkillExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * 未安装 MythicCrucible 时的回退触发监听：把 Bukkit 事件按 {@link FallbackSkillTriggers}
 * 的注册表映射到触发器并施放 MM 技能宝石技能。新增回退触发器时在注册表登记别名，
 * 并在此补充对应事件的监听即可。
 */
public final class MythicSkillListener implements Listener {

    private final MythicSkillExecutor executor;

    public MythicSkillListener(MythicSkillExecutor executor) {
        this.executor = executor;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        executor.cast(player, "SWING", event.getEntity());
    }
}
