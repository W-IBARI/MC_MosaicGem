package com.mosaicgem.plugin.config;

import com.mosaicgem.plugin.MosaicGemPlugin;
import com.mosaicgem.plugin.util.ItemFactory;
import com.mosaicgem.plugin.model.ToolType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 配置管理：主配置 + 多语言消息 + 宝石 / 打孔器 / 拆卸器。
 */
public class ConfigManager {

    private static final String DEFAULT_LANGUAGE = "zh_cn";
    private static final String LEGACY_MESSAGES_FILE = "messages.yml";

    private final MosaicGemPlugin plugin;

    private FileConfiguration config;
    private FileConfiguration messages;
    private String language = DEFAULT_LANGUAGE;

    private final Map<String, GemDefinition> gems = new LinkedHashMap<>();
    private final Map<String, PuncherDefinition> punchers = new LinkedHashMap<>();
    private final Map<String, RemoverDefinition> removers = new LinkedHashMap<>();

    public ConfigManager(MosaicGemPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        language = resolveLanguage(config.getString("settings.language", DEFAULT_LANGUAGE));
        messages = loadMessages(language);
        gems.clear();
        punchers.clear();
        removers.clear();

        gems.putAll(loadDefinitions("gems.yml", GemDefinition::new));
        punchers.putAll(loadDefinitions("punchers.yml", PuncherDefinition::new));
        removers.putAll(loadDefinitions("removers.yml", RemoverDefinition::new));

        int warnings = 0;
        for (GemDefinition gem : gems.values()) {
            if (!ItemFactory.BUFF_TYPE_SX.equalsIgnoreCase(gem.getBuffType())
                    && !ItemFactory.BUFF_TYPE_VANILLA.equalsIgnoreCase(gem.getBuffType())) {
                plugin.getLogger().warning("宝石 [" + gem.getId() + "] 的 buffType 不受支持: " + gem.getBuffType() + "（当前仅支持 sx_attribute / vanilla_attribute，属性将不会注入）");
                warnings++;
            }
        }
        if (warnings > 0) {
            plugin.getLogger().warning("共 " + warnings + " 个宝石使用了不受支持的 buffType");
        }
        plugin.getLogger().info("当前消息语言: " + language);
    }

    /**
     * 规范化 settings.language 的值，防止拼接出非法文件路径。
     */
    private String resolveLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!normalized.matches("[a-z0-9_]+")) {
            plugin.getLogger().warning("settings.language 的值无效: " + raw + "，回退到默认语言 " + DEFAULT_LANGUAGE);
            return DEFAULT_LANGUAGE;
        }
        return normalized;
    }

    /**
     * 加载消息文件：优先读取 messages_<语言>.yml；
     * 中文语言下若存在旧版 messages.yml 则作为迁移来源；
     * 缺失/未定义的消息键回退到内置中文默认值。
     */
    private FileConfiguration loadMessages(String language) {
        File legacy = new File(plugin.getDataFolder(), LEGACY_MESSAGES_FILE);
        FileConfiguration loaded = null;

        if (DEFAULT_LANGUAGE.equals(language) && legacy.exists()) {
            FileConfiguration legacyConfig = YamlConfiguration.loadConfiguration(legacy);
            if (legacyConfig.contains("messages")) {
                loaded = legacyConfig;
                plugin.getLogger().info("检测到旧版 " + LEGACY_MESSAGES_FILE + "，已作为中文语言文件加载");
            }
        }

        if (loaded == null) {
            File selected = new File(plugin.getDataFolder(), "messages" + File.separator + language + ".yml");
            if (selected.exists()) {
                loaded = YamlConfiguration.loadConfiguration(selected);
            } else {
                plugin.getLogger().warning("消息文件缺失: " + selected.getName() + "，回退到 messages/" + DEFAULT_LANGUAGE + ".yml");
                File fallback = new File(plugin.getDataFolder(), "messages" + File.separator + DEFAULT_LANGUAGE + ".yml");
                if (fallback.exists()) {
                    loaded = YamlConfiguration.loadConfiguration(fallback);
                } else if (legacy.exists()) {
                    loaded = YamlConfiguration.loadConfiguration(legacy);
                } else {
                    plugin.getLogger().warning("未找到任何消息文件，使用内置默认文案");
                    loaded = new YamlConfiguration();
                }
            }
        }

        FileConfiguration defaults = bundledMessages("messages/" + DEFAULT_LANGUAGE + ".yml");
        if (defaults != null) {
            loaded.setDefaults(defaults);
        }
        return loaded;
    }

    /**
     * 读取插件内置（jar 内）的默认消息文件。
     */
    private FileConfiguration bundledMessages(String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                plugin.getLogger().warning("内置消息文件不存在: " + name);
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("读取内置消息文件失败: " + name + " - " + e.getMessage());
            return null;
        }
    }

    private <T extends ItemDefinition> Map<String, T> loadDefinitions(String fileName, DefinitionFactory<T> factory) {
        Map<String, T> result = new LinkedHashMap<>();
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "items" + File.separator + fileName));
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

    public String language() {
        return language;
    }

    public String prefix() {
        return messages.getString("messages.prefix", "&8[&6MosaicGem&8] ");
    }

    public String message(String key) {
        return messages.getString("messages." + key, key);
    }

    /**
     * 获取原版属性 id 的显示名（来自语言文件 attribute-names 段）。
     */
    public String attributeName(String id) {
        if (id == null) {
            return id;
        }
        ConfigurationSection section = messages.getConfigurationSection("attribute-names");
        if (section == null) {
            return id;
        }
        Object name = section.getValues(false).get(id);
        return name != null ? name.toString() : id;
    }

    public SocketLoreTemplate socketLore() {
        return SocketLoreTemplate.from(config.getConfigurationSection("socket-lore"));
    }

    public AttributeLoreConfig attributeLore() {
        return AttributeLoreConfig.from(config.getConfigurationSection("attribute-lore"));
    }
}
