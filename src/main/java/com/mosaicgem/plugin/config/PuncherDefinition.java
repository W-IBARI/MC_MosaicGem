package com.mosaicgem.plugin.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 打孔器配置。
 */
public class PuncherDefinition extends ItemDefinition {

    private final int rate;
    private final Integer holesnum;

    public PuncherDefinition(String id, ConfigurationSection section) {
        super(id, section);
        this.rate = Math.max(0, Math.min(100, section.getInt("rate", 100)));
        this.holesnum = section.contains("holesnum") ? section.getInt("holesnum") : null;
    }

    public int getRate() {
        return rate;
    }

    public Integer getHolesnum() {
        return holesnum;
    }
}
