package com.mosaicgem.plugin;

import com.mosaicgem.plugin.command.MosaicGemCommand;
import com.mosaicgem.plugin.config.ConfigManager;
import com.mosaicgem.plugin.listener.InteractionListener;
import com.mosaicgem.plugin.service.GemService;
import com.mosaicgem.plugin.util.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class MosaicGemPlugin extends JavaPlugin {

    private static MosaicGemPlugin instance;

    private ConfigManager configManager;
    private ItemFactory itemFactory;
    private GemService gemService;
    private InteractionListener interactionListener;
    private MosaicGemCommand command;

    public static MosaicGemPlugin instance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResourceIfAbsent("gems.yml");
        saveResourceIfAbsent("punchers.yml");
        saveResourceIfAbsent("removers.yml");

        configManager = new ConfigManager(this);
        configManager.load();

        itemFactory = new ItemFactory(this);
        gemService = new GemService(this, configManager, itemFactory);
        interactionListener = new InteractionListener(configManager, itemFactory, gemService);
        Bukkit.getPluginManager().registerEvents(interactionListener, this);

        command = new MosaicGemCommand(this, configManager, itemFactory);
        PluginCommand pluginCommand = getCommand("mosaicgem");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getLogger().info("MosaicGem 已启用 (Folia 26.2)");
    }

    @Override
    public void onDisable() {
        getLogger().info("MosaicGem 已禁用");
    }

    public void reloadConfigs() {
        reloadConfig();
        configManager.load();
        getLogger().info("配置已重载");
    }

    private void saveResourceIfAbsent(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }
}
