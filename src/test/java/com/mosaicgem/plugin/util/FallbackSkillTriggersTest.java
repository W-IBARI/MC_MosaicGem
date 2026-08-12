package com.mosaicgem.plugin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackSkillTriggersTest {

    @Test
    void mapsAttackAliases() {
        assertEquals(FallbackSkillTriggers.Kind.ATTACK, FallbackSkillTriggers.kind("SWING"));
        assertEquals(FallbackSkillTriggers.Kind.ATTACK, FallbackSkillTriggers.kind("ATTACK"));
        assertEquals(FallbackSkillTriggers.Kind.ATTACK, FallbackSkillTriggers.kind("HIT"));
        assertEquals(FallbackSkillTriggers.Kind.ATTACK, FallbackSkillTriggers.kind("LEFTCLICK"));
        assertEquals(FallbackSkillTriggers.Kind.ATTACK, FallbackSkillTriggers.kind("onswing"));
    }

    @Test
    void unknownTriggersAreNotSupported() {
        assertNull(FallbackSkillTriggers.kind("USE"));
        assertNull(FallbackSkillTriggers.kind(null));
        assertFalse(FallbackSkillTriggers.isSupported("USE"));
        assertTrue(FallbackSkillTriggers.isSupported("SWING"));
    }
}
