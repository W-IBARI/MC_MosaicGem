package com.mosaicgem.plugin.util;

import org.bukkit.Material;

import java.util.Locale;

/**
 * 装备类型匹配（SWORD / SPEAR / AXE / HELMET / CHESTPLATE / LEGGINGS / BOOTS / ELYTRA）。
 */
public final class TargetMatcher {

    private TargetMatcher() {
    }

    public static boolean matchesType(Material material, String type) {
        if (material == null || type == null) {
            return false;
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "SWORD" -> material.name().endsWith("_SWORD");
            case "SPEAR" -> material == Material.TRIDENT;
            case "AXE" -> material.name().endsWith("_AXE");
            case "HELMET" -> material.name().endsWith("_HELMET");
            case "CHESTPLATE" -> material.name().endsWith("_CHESTPLATE");
            case "LEGGINGS" -> material.name().endsWith("_LEGGINGS");
            case "BOOTS" -> material.name().endsWith("_BOOTS");
            case "ELYTRA" -> material == Material.ELYTRA;
            default -> false;
        };
    }
}
