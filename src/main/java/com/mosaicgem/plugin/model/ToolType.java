package com.mosaicgem.plugin.model;

import java.util.Locale;

public enum ToolType {
    GEM,
    PUNCHER,
    REMOVER;

    public static ToolType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
