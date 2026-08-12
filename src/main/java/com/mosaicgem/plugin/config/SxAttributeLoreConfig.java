package com.mosaicgem.plugin.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * SX-Attribute 属性面板合并显示配置（仅作用于 buffType: sx_attribute 的宝石）。
 *
 * @param enabled     是否启用合并显示
 * @param names       额外需要识别的属性名（宝石配置会自动收集，这里用于补充物品自带但宝石未覆盖的属性）
 * @param newLine     宝石属性在物品上不存在时，追加的新属性行模版（{name} {value}）
 * @param bonusFormat 加成标注格式（跟在数值后，{bonus}）
 */
public record SxAttributeLoreConfig(
        boolean enabled,
        List<String> names,
        String newLine,
        String bonusFormat
) {

    private static final String DEFAULT_NEW_LINE = "&r&f{name}：&e{value}";
    private static final String DEFAULT_BONUS_FORMAT = "&r（+{bonus}）";

    public static SxAttributeLoreConfig from(ConfigurationSection section) {
        if (section == null) {
            return new SxAttributeLoreConfig(true, List.of(), DEFAULT_NEW_LINE, DEFAULT_BONUS_FORMAT);
        }
        String newLine = section.getString("new-line", DEFAULT_NEW_LINE);
        if (newLine == null || newLine.isEmpty()) {
            newLine = DEFAULT_NEW_LINE;
        }
        return new SxAttributeLoreConfig(
                section.getBoolean("enabled", true),
                section.getStringList("names"),
                newLine,
                section.getString("bonus-format", DEFAULT_BONUS_FORMAT)
        );
    }
}
