package com.mosaicgem.plugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HexColorLoreTest {

    @Test
    void bonusFormatHexColorsAreApplied() {
        Component component = ItemFactory.toComponent(
                "&r<#FFAA00>（<#1EFF5C>+20<#FFAA00>）"
        );
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        assertFalse(plain.contains("<#"), "hex tags must not remain as literal text: " + plain);
        assertTrue(hasColor(component, TextColor.fromHexString("#FFAA00")));
        assertTrue(hasColor(component, TextColor.fromHexString("#1EFF5C")));
    }

    @Test
    void mergedLineHexColorsAreAppliedAfterMarker() {
        Component component = ItemFactory.toComponent(
                "攻击力：33.90\u00A7X&r<#FFAA00>（<#1EFF5C>+20<#FFAA00>）"
        );
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        assertFalse(plain.contains("<#"), "hex tags must not remain as literal text: " + plain);
        assertTrue(hasColor(component, TextColor.fromHexString("#FFAA00")));
        assertTrue(hasColor(component, TextColor.fromHexString("#1EFF5C")));
    }

    @Test
    void mergedLineKeepsItalicDisabled() {
        Component component = ItemFactory.toComponent(
                "攻击力：33.90\u00A7X&r<#FFAA00>（<#1EFF5C>+20<#FFAA00>）"
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                hasItalicFalse(component),
                "merged line must keep italic disabled: " + component
        );
    }

    private static boolean hasColor(Component component, TextColor color) {
        if (color.equals(component.color())) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasColor(child, color)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasItalicFalse(Component component) {
        if (component.style().decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasItalicFalse(child)) {
                return true;
            }
        }
        return false;
    }
}
