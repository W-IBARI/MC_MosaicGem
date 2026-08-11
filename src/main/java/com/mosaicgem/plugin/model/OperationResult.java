package com.mosaicgem.plugin.model;

import org.bukkit.inventory.ItemStack;

/**
 * 一次操作的结果。
 *
 * @param targetItem  操作后的目标物品（未消耗工具时与传入相同）
 * @param returnItem  需要返还给玩家的物品（拆卸时返还的宝石），可能为 null
 * @param consumeTool 本次操作是否消耗工具
 * @param message     发送给玩家的消息（已格式化，未着色），可能为空
 */
public record OperationResult(
        ItemStack targetItem,
        ItemStack returnItem,
        boolean consumeTool,
        String message
) {
}
