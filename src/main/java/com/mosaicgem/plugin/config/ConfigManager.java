package com.mosaicgem.plugin.config;

import com.mosaicgem.plugin.MosaicGemPlugin;
import com.mosaicgem.plugin.model.ToolType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置管理：主配置 + 宝石 / 打孔器 / 拆卸器。
 */
public class ConfigManager {

    private final MosaicGemPlugin plugin;

    private FileConfiguration config;

    private final Map<String, GemDefinition> gems = new LinkedHashMap<>();
    private final Map<String, PuncherDefinition> punchers = new LinkedHashMap<>();
    private final Map<String, RemoverDefinition> removers = new LinkedHashMap<>();

    public ConfigManager(MosaicGemPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        gems.clear();
        punchers.clear();
        removers.clear();

        gems.putAll(loadDefinitions("gems.yml", GemDefinition::new));
        punchers.putAll(loadDefinitions("punchers.yml", PuncherDefinition::new));
        removers.putAll(loadDefinitions("removers.yml", RemoverDefinition::new));

        int warnings = 0;
        for (GemDefinition gem : gems.values()) {
            if (!"sx_attribute".equalsIgnoreCase(gem.getBuffType())) {
                plugin.getLogger().warning("宝石 [" + gem.getId() + "] 的 buffType 不受支持: " + gem.getBuffType() + "（当前仅支持 sx_attribute，属性将不会注入）");
                warnings++;
            }
        }
        if (warnings > 0) {
            plugin.getLogger().warning("共 " + warnings + " 个宝石使用了不受支持的 buffType");
        }
    }

    private <T extends ItemDefinition> Map<String, T> loadDefinitions(String fileName, DefinitionFactory<T> factory) {
        Map<String, T> result = new LinkedHashMap<>();
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), fileName));
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            T definition = factory.create(key, section);
            if (!definition.isValid()) {
                plugin.getLogger().warning("跳过无效配置: " + fileName + " -> " + key + "（material 无效）");
                continue;
            }
            result.put(key, definition);
        }
        plugin.getLogger().info("已加载 " + fileName + ": " + result.size() + " 个");
        return result;
    }

    private interface DefinitionFactory<T extends ItemDefinition> {
        T create(String id, ConfigurationSection section);
    }

    public ItemDefinition find(ToolType type, String id) {
        if (type == null || id == null) {
            return null;
        }
        return switch (type) {
            case GEM -> gems.get(id);
            case PUNCHER -> punchers.get(id);
            case REMOVER -> removers.get(id);
        };
    }

    public GemDefinition getGem(String id) {
        return gems.get(id);
    }

    public Map<String, GemDefinition> getGems() {
        return gems;
    }

    public Map<String, PuncherDefinition> getPunchers() {
        return punchers;
    }

    public Map<String, RemoverDefinition> getRemovers() {
        return removers;
    }

    public int maxHoles() {
        return Math.max(0, config.getInt("settings.max-holes", 6));
    }

    public boolean isInteractionEnabled(String name) {
        return config.getBoolean("settings.interactions." + name, true);
    }

    public boolean consumeOnFail() {
        return config.getBoolean("settings.consume-on-fail", true);
    }

    public String prefix() {
        return config.getString("messages.prefix", "&8[&6MosaicGem&8] ");
    }

    public String message(String key) {
        return config.getString("messages." + key, key);
    }
}
