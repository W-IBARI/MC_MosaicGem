package com.mosaicgem.plugin;

import com.mosaicgem.plugin.command.MosaicGemCommand;
import com.mosaicgem.plugin.config.ConfigManager;
import com.mosaicgem.plugin.listener.InteractionListener;
import com.mosaicgem.plugin.listener.MythicSkillListener;
import com.mosaicgem.plugin.service.GemService;
import com.mosaicgem.plugin.util.ItemFactory;
import com.mosaicgem.plugin.util.MythicMobsBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class MosaicGemPlugin extends JavaPlugin {

    private static final List<String> MESSAGE_FILES = List.of(
            "messages/zh_cn.yml",
            "messages/en_us.yml"
    );

    private static MosaicGemPlugin instance;

    private ConfigManager configManager;
    private ItemFactory itemFactory;
    private GemService gemService;
    private InteractionListener interactionListener;
    private MythicMobsBridge mythicMobsBridge;
    private MosaicGemCommand command;

    public static MosaicGemPlugin instance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveMessageFiles();
        saveItemFiles();
        saveResourceIfAbsent("permissions.yml");

        configManager = new ConfigManager(this);
        configManager.load();

        itemFactory = new ItemFactory(this, configManager);
        gemService = new GemService(this, configManager, itemFactory);
        interactionListener = new InteractionListener(configManager, itemFactory, gemService);
        Bukkit.getPluginManager().registerEvents(interactionListener, this);

        mythicMobsBridge = new MythicMobsBridge(this, configManager, itemFactory);
        if (mythicMobsBridge.isAvailable()) {
            Bukkit.getPluginManager().registerEvents(new MythicSkillListener(configManager, itemFactory, mythicMobsBridge), this);
        }

        command = new MosaicGemCommand(this, configManager, itemFactory);
        PluginCommand pluginCommand = getCommand("mosaicgem");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getLogger().info("MosaicGem 已启用 (Folia 26.2)，当前语言: " + configManager.language());
    }

    @Override
    public void onDisable() {
        getLogger().info("MosaicGem 已禁用");
    }

    public void reloadConfigs() {
        // 热重载时先补齐缺失的默认配置文件，再重新加载
        saveDefaultConfig();
        saveMessageFiles();
        saveItemFiles();
        saveResourceIfAbsent("permissions.yml");
        reloadConfig();
        configManager.load();
        getLogger().info("配置已重载，当前语言: " + configManager.language());
    }

    private void saveMessageFiles() {
        for (String name : MESSAGE_FILES) {
            saveResourceIfAbsent(name);
        }
    }

    private void saveItemFiles() {
        saveResourceIfAbsent("items/gems.yml");
        saveResourceIfAbsent("items/punchers.yml");
        saveResourceIfAbsent("items/removers.yml");
    }

    private void saveResourceIfAbsent(String name) {
        File target = new File(getDataFolder(), name);
        if (!target.exists()) {
            File parent = target.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            saveResource(name, false);
        }
    }
}
