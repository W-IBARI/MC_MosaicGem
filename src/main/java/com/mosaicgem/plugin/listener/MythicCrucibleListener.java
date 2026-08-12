package com.mosaicgem.plugin.listener;

import com.mosaicgem.plugin.util.MythicCrucibleBridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 管理玩家与 MythicCrucible 外部技能持有者的注册/注销。
 */
public final class MythicCrucibleListener implements Listener {

    private final MythicCrucibleBridge bridge;

    public MythicCrucibleListener(MythicCrucibleBridge bridge) {
        this.bridge = bridge;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        bridge.registerPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        bridge.unregisterPlayer(event.getPlayer());
    }
}
