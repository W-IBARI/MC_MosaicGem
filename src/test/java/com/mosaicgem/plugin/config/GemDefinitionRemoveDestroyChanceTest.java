package com.mosaicgem.plugin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 拆卸损毁概率（remove-destroy-chance）冒烟测试：
 * 0~100 正常解析、缺失默认 0、越界值钳制到 0~100。
 */
class GemDefinitionRemoveDestroyChanceTest {

    private static GemDefinition gemWithChance(int chance) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("material", "PAPER");
        yaml.set("remove-destroy-chance", chance);
        ConfigurationSection section = yaml;
        return new GemDefinition("测试宝石", section);
    }

    @Test
    void chanceParsesAsConfigured() {
        assertEquals(0, gemWithChance(0).getRemoveDestroyChance(), "0 = 永不损毁");
        assertEquals(50, gemWithChance(50).getRemoveDestroyChance(), "50 = 50% 损毁");
        assertEquals(100, gemWithChance(100).getRemoveDestroyChance(), "100 = 必定损毁");
    }

    @Test
    void chanceDefaultsToZeroWhenAbsent() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("material", "PAPER");
        GemDefinition gem = new GemDefinition("无字段宝石", yaml);
        assertEquals(0, gem.getRemoveDestroyChance(), "缺省应视为 0（不损毁，保持向后兼容）");
    }

    @Test
    void chanceClampsOutOfRangeValues() {
        assertEquals(0, gemWithChance(-5).getRemoveDestroyChance(), "负数钳制到 0");
        assertEquals(100, gemWithChance(150).getRemoveDestroyChance(), "超 100 钳制到 100");
    }
}
