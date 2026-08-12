package com.mosaicgem.plugin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MythicSkillLineTest {

    @Test
    void parsesPlainSkillNameWithDefaultSwing() {
        MythicSkillLine.Entry entry = MythicSkillLine.parse("TestSkill");
        assertEquals("TestSkill", entry.name());
        assertEquals("SWING", entry.trigger());
    }

    @Test
    void parsesTriggerSuffix() {
        MythicSkillLine.Entry entry = MythicSkillLine.parse("TestSkill @onSwing");
        assertEquals("TestSkill", entry.name());
        assertEquals("SWING", entry.trigger());
    }

    @Test
    void parsesUppercaseTriggerAndSkillPrefix() {
        MythicSkillLine.Entry entry = MythicSkillLine.parse("skill:Heal @USE");
        assertEquals("Heal", entry.name());
        assertEquals("USE", entry.trigger());
    }

    @Test
    void normalizesTriggerNames() {
        assertEquals("SWING", MythicSkillLine.normalizeTrigger("onSwing"));
        assertEquals("SWING", MythicSkillLine.normalizeTrigger("SWING"));
        assertEquals("RIGHTCLICK", MythicSkillLine.normalizeTrigger("onRightClick"));
        assertEquals("SHOOT", MythicSkillLine.normalizeTrigger("shoot"));
        assertEquals("SWING", MythicSkillLine.normalizeTrigger(null));
        assertEquals("SWING", MythicSkillLine.normalizeTrigger("  "));
    }

    @Test
    void displayNameStripsTriggerAndPrefix() {
        assertEquals("TestSkill", MythicSkillLine.displayName("TestSkill @onSwing"));
        assertEquals("Heal", MythicSkillLine.displayName("skill:Heal @USE"));
        assertEquals("TestSkill", MythicSkillLine.displayName("TestSkill"));
        assertEquals("", MythicSkillLine.displayName(""));
        assertEquals("", MythicSkillLine.displayName(null));
    }

    @Test
    void rejectsEmptySkillName() {
        assertNull(MythicSkillLine.parse("@onSwing"));
        assertNull(MythicSkillLine.parse("skill: @USE"));
        assertNull(MythicSkillLine.parse(""));
    }
}
