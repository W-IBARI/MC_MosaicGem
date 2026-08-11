package com.mosaicgem.plugin.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * 物品配置基类（宝石 / 打孔器 / 拆卸器公共字段）。
 */
public abstract class ItemDefinition {

    private final String id;
    private final Material material;
    private final boolean isEnchant;
    private final String name;
    private final List<String> lore;
    private final Integer customModelData;
    private final List<String> targetType;
    private final List<String> targetMaterial;

    protected ItemDefinition(String id, ConfigurationSection section) {
        this.id = id;
        this.material = Material.matchMaterial(section.getString("material", "PAPER"));
        this.isEnchant = section.getBoolean("isEnchant", false);
        this.name = section.getString("name", id);
        this.lore = section.getStringList("lore");
        this.customModelData = section.contains("custom-model-data") ? section.getInt("custom-model-data") : null;
        this.targetType = section.getStringList("targetType");
        this.targetMaterial = section.getStringList("targetMaterial");
    }

    public boolean isValid() {
        return material != null;
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public boolean isEnchant() {
        return isEnchant;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public Integer getCustomModelData() {
        return customModelData;
    }

    public List<String> getTargetType() {
        return targetType;
    }

    public List<String> getTargetMaterial() {
        return targetMaterial;
    }
}
