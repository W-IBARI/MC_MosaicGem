package com.mosaicgem.plugin.util;

import java.util.Map;

/**
 * 未安装 MythicCrucible 时的回退触发器注册表（数据驱动）。
 *
 * <p>把归一化后的触发器名映射到内部触发类型；新增回退触发器时只需在此登记别名，
 * 并在 {@link com.mosaicgem.plugin.listener.MythicSkillListener} 中补充对应事件的监听。
 */
public final class FallbackSkillTriggers {

    public enum Kind {
        /** 近战攻击命中（EntityDamageByEntityEvent） */
        ATTACK
    }

    private static final Map<String, Kind> ALIASES = Map.ofEntries(
            Map.entry("SWING", Kind.ATTACK),
            Map.entry("ATTACK", Kind.ATTACK),
            Map.entry("HIT", Kind.ATTACK),
            Map.entry("LEFTCLICK", Kind.ATTACK)
    );

    private FallbackSkillTriggers() {
    }

    public static Kind kind(String trigger) {
        if (trigger == null) {
            return null;
        }
        return ALIASES.get(MythicSkillLine.normalizeTrigger(trigger));
    }

    public static boolean isSupported(String trigger) {
        return kind(trigger) != null;
    }
}
