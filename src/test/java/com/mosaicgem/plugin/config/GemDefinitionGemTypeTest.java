package com.mosaicgem.plugin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * gemtype 标签字段冒烟测试：
 * 解析多值标签、缺省空列表，以及"同类型宝石数量上限"校验逻辑（与 GemService 一致）。
 */
class GemDefinitionGemTypeTest {

    private static GemDefinition gemWithTypes(String... types) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("material", "PAPER");
        if (types.length > 0) yaml.set("gemtype", List.of(types));
        ConfigurationSection section = yaml;
        return new GemDefinition("测试宝石", section);
    }

    @Test
    void gemTypeParsesMultipleValues() {
        GemDefinition gem = gemWithTypes("攻击", "火属性");
        assertEquals(List.of("攻击", "火属性"), gem.getGemType(), "gemtype 应解析为多值列表");
    }

    @Test
    void gemTypeDefaultsToEmptyWhenAbsent() {
        GemDefinition gem = gemWithTypes();
        assertTrue(gem.getGemType().isEmpty(), "缺省 gemtype 应为空列表（不参与类型计数）");
    }

    /**
     * 复刻 GemService.socket() 中的 gemtype 上限校验逻辑（纯逻辑，不依赖 Bukkit）。
     */
    private static boolean canSocket(GemDefinition gem, List<GemDefinition> socketedGems, Map<String, Integer> limits) {
        Map<String, Long> labelCounts = new java.util.LinkedHashMap<>();
        for (GemDefinition def : socketedGems) {
            for (String label : def.getGemType()) {
                labelCounts.merge(label, 1L, Long::sum);
            }
        }
        for (String label : gem.getGemType()) {
            Integer limit = limits.get(label);
            if (limit != null && limit > 0 && labelCounts.getOrDefault(label, 0L) >= limit) {
                return false;
            }
        }
        return true;
    }

    @Test
    void gemTypeLimitBlocksWhenReached() {
        Map<String, Integer> limits = Map.of("攻击", 2, "火属性", 1);
        GemDefinition gemA = gemWithTypes("攻击");
        GemDefinition gemB = gemWithTypes("攻击", "火属性");
        // 已镶嵌 2 颗"攻击"标签宝石 -> 再镶嵌带"攻击"的宝石应被拦截
        assertFalse(canSocket(gemA, List.of(gemA, gemA), limits), "攻击标签已达上限应拦截");
        // 已镶嵌 1 颗"火属性" -> 再镶嵌带"火属性"的应被拦截
        assertFalse(canSocket(gemB, List.of(gemWithTypes("火属性")), limits), "火属性已达上限应拦截");
        // 未达上限允许
        assertTrue(canSocket(gemA, List.of(), limits), "空装备可镶嵌");
        assertTrue(canSocket(gemB, List.of(gemA), limits), "火属性未达上限可镶嵌");
    }

    @Test
    void gemTypeLimitIgnoresUnconfiguredLabels() {
        Map<String, Integer> limits = Map.of("攻击", 1);
        // 未配置上限的标签不受限制
        assertTrue(canSocket(gemWithTypes("冰属性"), List.of(gemWithTypes("冰属性"), gemWithTypes("冰属性")), limits), "未配置上限的标签不拦截");
        // 无标签宝石不受影响
        assertTrue(canSocket(gemWithTypes(), List.of(gemWithTypes("攻击")), limits), "无标签宝石不参与计数");
    }

    @Test
    void gemTypeLimitZeroMeansUnlimited() {
        Map<String, Integer> limits = Map.of("攻击", 0);
        assertTrue(canSocket(gemWithTypes("攻击"), List.of(gemWithTypes("攻击"), gemWithTypes("攻击")), limits), "limit=0 表示不限制");
    }
}
