package com.mosaicgem.plugin.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 拆卸器配置。
 */
public class RemoverDefinition extends ItemDefinition {

    private final int rate;

    public RemoverDefinition(String id, ConfigurationSection section) {
        super(id, section);
        this.rate = Math.max(0, Math.min(100, section.getInt("rate", 100)));
    }

    public int getRate() {
        return rate;
    }
}
