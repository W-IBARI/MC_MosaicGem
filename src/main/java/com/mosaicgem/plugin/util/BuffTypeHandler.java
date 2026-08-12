package com.mosaicgem.plugin.util;

import com.mosaicgem.plugin.model.SocketedGem;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * buffType 处理器：一个 buffType 的展示与生效逻辑都集中在这里。
 * 新增 buffType 时实现本接口并在 {@link BuffTypeRegistry} 注册即可，
 * 校验 / 镶嵌允许 / 镶嵌信息展示 / 生效重建都会自动走注册表分发。
 */
public interface BuffTypeHandler {

    /**
     * buffType 字符串（如 {@code sx_attribute}）。
     */
    String id();

    /**
     * 该类型的宝石是否需要把属性行写入装备 lore（用于拆卸时移除）。
     */
    default boolean usesLoreLines() {
        return false;
    }

    /**
     * 单行合并展示（多个属性用“、”连接）。
     */
    default String values(SocketedGem gem, ItemFactory factory) {
        return String.join("、", valueLines(gem, factory));
    }

    /**
     * 镶嵌信息中每个属性单独一行的展示。
     */
    List<String> valueLines(SocketedGem gem, ItemFactory factory);

    /**
     * 镶嵌/拆卸后重算物品上的生效数据（如原版属性修饰符、附魔）。
     * 没有持久生效数据的类型无需实现。
     */
    default void rebuild(ItemStack item, List<SocketedGem> gems, ItemFactory factory) {
    }
}
