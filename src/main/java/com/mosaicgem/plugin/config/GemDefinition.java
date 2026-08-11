package com.mosaicgem.plugin.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宝石配置。
 */
public class GemDefinition extends ItemDefinition {

    private final Integer repetitions;
    private final Map<String, String> random;
    private final String buffType;
    private final List<String> attribute;

    public GemDefinition(String id, ConfigurationSection section) {
        super(id, section);
        this.repetitions = section.contains("repetitions") ? section.getInt("repetitions") : null;
        this.random = new LinkedHashMap<>();
        ConfigurationSection randomSection = section.getConfigurationSection("random");
        if (randomSection != null) {
            for (String key : randomSection.getKeys(false)) {
                random.put(key, randomSection.getString(key));
            }
        }
        this.buffType = section.getString("buffType", "sx_attribute");
        this.attribute = section.getStringList("attribute");
    }

    public Integer getRepetitions() {
        return repetitions;
    }

    public Map<String, String> getRandom() {
        return random;
    }

    public String getBuffType() {
        return buffType;
    }

    public List<String> getAttribute() {
        return attribute;
    }
}
