package com.mosaicgem.plugin.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * 装备上的镶嵌信息 lore 模版。
 *
 * @param enabled   是否启用
 * @param lines     信息行，支持 {holes} {max_holes} {gem_count}
 * @param gemLines  每个已镶嵌宝石渲染的行，支持 {index} {gem} {id}
 * @param emptyLine 没有宝石时显示的行（可为空字符串）
 */
public record SocketLoreTemplate(
        boolean enabled,
        List<String> lines,
        List<String> gemLines,
        String emptyLine
) {

    private static final List<String> DEFAULT_LINES = List.of(
            "",
            "&r&f[ &6镶嵌信息 &f]",
            "&r&7孔位: &f{holes}&7/&f{max_holes}"
    );

    private static final List<String> DEFAULT_GEM_LINES = List.of(
            "&r&7宝石{index}: &f{gem}",
            "&r&7  {value_lines}"
    );

    public static SocketLoreTemplate from(ConfigurationSection section) {
        if (section == null) {
            return new SocketLoreTemplate(true, DEFAULT_LINES, DEFAULT_GEM_LINES, "&r&7暂无宝石");
        }
        List<String> lines = section.getStringList("lines");
        if (lines.isEmpty()) {
            lines = DEFAULT_LINES;
        }
        List<String> gemLines = section.getStringList("gem-lines");
        if (gemLines.isEmpty()) {
            gemLines = DEFAULT_GEM_LINES;
        }
        return new SocketLoreTemplate(
                section.getBoolean("enabled", true),
                lines,
                gemLines,
                section.getString("empty-line", "&r&7暂无宝石")
        );
    }
}
