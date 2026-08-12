package com.mosaicgem.plugin.util;

import java.util.Locale;

/**
 * MythicMobs 技能行的纯解析器（无 Bukkit 依赖，可单元测试）。
 *
 * <p>支持 MythicCrucible 风格：{@code 技能名 @触发器}、{@code skill:技能名 @触发器} 或纯技能名
 * （默认 {@code SWING}）。触发器名大小写不敏感，{@code on} 前缀可省略。
 */
public final class MythicSkillLine {

    private MythicSkillLine() {
    }

    public record Entry(String name, String trigger) {
    }

    /**
     * 解析技能行，返回技能名与归一化后的触发器（如 {@code SWING} / {@code USE}）。
     * 无法解析时返回 null。
     */
    public static Entry parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        String trigger = "SWING";
        int at = text.lastIndexOf('@');
        if (at >= 0 && at + 1 < text.length()) {
            trigger = normalizeTrigger(text.substring(at + 1).trim());
            text = text.substring(0, at).trim();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("skill:")) {
            text = text.substring("skill:".length()).trim();
        }
        return text.isEmpty() ? null : new Entry(text, trigger);
    }

    /**
     * 生成镶嵌信息展示名：去掉 {@code @触发器} 后缀与 {@code skill:} 前缀。
     */
    public static String displayName(String raw) {
        Entry entry = parse(raw);
        return entry == null ? "" : entry.name();
    }

    /**
     * 归一化触发器：去空白、去 {@code on} 前缀、转大写（{@code onSwing} / {@code SWING} → {@code SWING}）。
     */
    public static String normalizeTrigger(String raw) {
        if (raw == null) {
            return "SWING";
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return "SWING";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("on")) {
            value = value.substring(2);
        }
        return value.toUpperCase(Locale.ROOT);
    }
}
