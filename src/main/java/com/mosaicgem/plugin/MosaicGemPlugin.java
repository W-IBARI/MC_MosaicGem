package com.mosaicgem.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class MosaicGemPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("MosaicGem 插件已启用 (Folia 26.2)");
    }

    @Override
    public void onDisable() {
        getLogger().info("MosaicGem 插件已禁用");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("mosaicgem")) {
            return false;
        }
        sender.sendMessage("MosaicGem 插件运行正常！");
        return true;
    }
}
