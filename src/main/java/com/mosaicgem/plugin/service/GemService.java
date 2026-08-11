package com.mosaicgem.plugin.service;

import com.mosaicgem.plugin.MosaicGemPlugin;
import com.mosaicgem.plugin.config.ConfigManager;
import com.mosaicgem.plugin.config.GemDefinition;
import com.mosaicgem.plugin.config.ItemDefinition;
import com.mosaicgem.plugin.config.PuncherDefinition;
import com.mosaicgem.plugin.config.RemoverDefinition;
import com.mosaicgem.plugin.model.OperationResult;
import com.mosaicgem.plugin.model.SocketData;
import com.mosaicgem.plugin.model.SocketedGem;
import com.mosaicgem.plugin.model.ToolType;
import com.mosaicgem.plugin.util.ItemFactory;
import com.mosaicgem.plugin.util.TargetMatcher;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 核心业务：打孔、镶嵌、拆卸。
 */
public class GemService {

    private final MosaicGemPlugin plugin;
    private final ConfigManager configs;
    private final ItemFactory factory;

    public GemService(MosaicGemPlugin plugin, ConfigManager configs, ItemFactory factory) {
        this.plugin = plugin;
        this.configs = configs;
        this.factory = factory;
    }

    public record Combo(ToolType toolType, ItemDefinition definition, ItemStack tool, ItemStack target) {
    }

    /**
     * 判断两个物品是否构成「工具 + 目标」组合。
     */
    public Combo findCombo(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.getType().isAir() || second.getType().isAir()) {
            return null;
        }
        ToolType firstType = factory.getToolType(first);
        ToolType secondType = factory.getToolType(second);
        if (firstType != null && secondType == null) {
            ItemDefinition definition = configs.find(firstType, factory.getToolId(first));
            return definition == null ? null : new Combo(firstType, definition, first, second);
        }
        if (secondType != null && firstType == null) {
            ItemDefinition definition = configs.find(secondType, factory.getToolId(second));
            return definition == null ? null : new Combo(secondType, definition, second, first);
        }
        return null;
    }

    /**
     * 执行一次操作。
     */
    public OperationResult perform(Combo combo, Player player) {
        return switch (combo.toolType()) {
            case PUNCHER -> punch((PuncherDefinition) combo.definition(), combo);
            case GEM -> socket((GemDefinition) combo.definition(), combo);
            case REMOVER -> remove((RemoverDefinition) combo.definition(), combo);
        };
    }

    /**
     * 构建预览物品（铁砧/工作台结果槽展示）。
     */
    public ItemStack buildPreview(Combo combo) {
        return switch (combo.toolType()) {
            case PUNCHER -> combo.target().clone();
            case GEM -> {
                GemDefinition definition = (GemDefinition) combo.definition();
                SocketData data = factory.readSocketData(combo.target());
                if (data.holes() <= data.gems().size()) {
                    yield combo.target().clone();
                }
                Map<String, String> values = factory.readValues(combo.tool());
                List<String> lines = resolveLines(definition, values);
                ItemStack preview = combo.target().clone();
                factory.appendLore(preview, lines);
                yield preview;
            }
            case REMOVER -> {
                SocketData data = factory.readSocketData(combo.target());
                if (data.gems().isEmpty()) {
                    yield combo.target().clone();
                }
                SocketedGem last = data.gems().get(data.gems().size() - 1);
                ItemStack preview = combo.target().clone();
                factory.removeLoreLines(preview, last.lines());
                yield preview;
            }
        };
    }

    // ------------------------------------------------------------------
    // 打孔
    // ------------------------------------------------------------------

    private OperationResult punch(PuncherDefinition definition, Combo combo) {
        ItemStack target = combo.target();
        if (!isTargetValid(definition, target)) {
            return fail(configs.message("target-invalid"), target, false);
        }
        SocketData data = factory.readSocketData(target);
        int globalMax = configs.maxHoles();
        if (data.holes() >= globalMax) {
            return fail(configs.message("punch-max-global").replace("{max}", String.valueOf(globalMax)), target, false);
        }
        Map<String, Integer> sources = new LinkedHashMap<>(data.holeSources());
        int sourceCount = sources.getOrDefault(definition.getId(), 0);
        if (definition.getHolesnum() != null && sourceCount >= definition.getHolesnum()) {
            return fail(configs.message("punch-max-source").replace("{max}", String.valueOf(definition.getHolesnum())), target, false);
        }
        if (!roll(definition.getRate())) {
            return fail(configs.message("punch-fail"), target, true);
        }
        sources.put(definition.getId(), sourceCount + 1);
        int newHoles = data.holes() + 1;
        ItemStack result = target.clone();
        factory.writeSocketData(result, newHoles, sources, data.gems());
        factory.applySocketLore(result, new SocketData(newHoles, sources, data.gems()), configs.socketLore(), configs.maxHoles());
        String message = configs.message("punch-success")
                .replace("{holes}", String.valueOf(newHoles))
                .replace("{max}", String.valueOf(globalMax));
        return new OperationResult(result, null, true, message);
    }

    // ------------------------------------------------------------------
    // 镶嵌
    // ------------------------------------------------------------------

    private OperationResult socket(GemDefinition definition, Combo combo) {
        ItemStack target = combo.target();
        if (!isTargetValid(definition, target)) {
            return fail(configs.message("target-invalid"), target, false);
        }
        SocketData data = factory.readSocketData(target);
        if (data.holes() <= 0) {
            return fail(configs.message("socket-no-hole"), target, false);
        }
        if (data.gems().size() >= data.holes()) {
            return fail(configs.message("socket-full"), target, false);
        }
        if (!"sx_attribute".equalsIgnoreCase(definition.getBuffType())) {
            return fail(configs.message("socket-bufftype-unsupported"), target, false);
        }
        if (definition.getRepetitions() != null) {
            long count = data.gems().stream().filter(gem -> gem.id().equals(definition.getId())).count();
            if (count >= definition.getRepetitions()) {
                return fail(configs.message("socket-repeat-limit"), target, false);
            }
        }

        Map<String, String> values = factory.readValues(combo.tool());
        if (values.isEmpty()) {
            values = factory.rollRandom(definition);
        }
        List<String> lines = resolveLines(definition, values);

        SocketedGem socketedGem = new SocketedGem(definition.getId(), ItemFactory.newInstanceId(), values, lines);
        List<SocketedGem> gems = new ArrayList<>(data.gems());
        gems.add(socketedGem);

        ItemStack result = target.clone();
        factory.writeSocketData(result, data.holes(), data.holeSources(), gems);
        factory.appendLore(result, lines);
        factory.applySocketLore(result, new SocketData(data.holes(), data.holeSources(), gems), configs.socketLore(), configs.maxHoles());
        return new OperationResult(result, null, true, configs.message("socket-success"));
    }

    // ------------------------------------------------------------------
    // 拆卸
    // ------------------------------------------------------------------

    private OperationResult remove(RemoverDefinition definition, Combo combo) {
        ItemStack target = combo.target();
        if (!isTargetValid(definition, target)) {
            return fail(configs.message("target-invalid"), target, false);
        }
        SocketData data = factory.readSocketData(target);
        if (data.gems().isEmpty()) {
            return fail(configs.message("remove-empty"), target, false);
        }
        if (!roll(definition.getRate())) {
            return fail(configs.message("remove-fail"), target, true);
        }

        SocketedGem removed = data.gems().get(data.gems().size() - 1);
        List<SocketedGem> gems = new ArrayList<>(data.gems());
        gems.remove(gems.size() - 1);

        ItemStack result = target.clone();
        factory.writeSocketData(result, data.holes(), data.holeSources(), gems);
        factory.removeLoreLines(result, removed.lines());
        factory.applySocketLore(result, new SocketData(data.holes(), data.holeSources(), gems), configs.socketLore(), configs.maxHoles());

        ItemStack returnedGem = buildReturnedGem(removed);
        return new OperationResult(result, returnedGem, true, configs.message("remove-success"));
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private List<String> resolveLines(GemDefinition definition, Map<String, String> values) {
        if (!"sx_attribute".equalsIgnoreCase(definition.getBuffType())) {
            return List.of();
        }
        return definition.getAttribute().stream()
                .map(line -> factory.resolve(line, values))
                .map(ItemFactory::colorize)
                .toList();
    }

    private ItemStack buildReturnedGem(SocketedGem gem) {
        GemDefinition definition = configs.getGem(gem.id());
        if (definition == null) {
            plugin.getLogger().warning("拆卸时找不到宝石配置: " + gem.id() + "，已生成占位物品");
            ItemStack fallback = new ItemStack(Material.PAPER);
            fallback.editMeta(meta -> meta.setDisplayName(ItemFactory.colorize("&c" + gem.id() + "（配置缺失）")));
            factory.markTool(fallback, ToolType.GEM, gem.id(), gem.values());
            return fallback;
        }
        return factory.buildGem(definition, gem.values());
    }

    private boolean isTargetValid(ItemDefinition definition, ItemStack target) {
        if (factory.getToolType(target) != null) {
            return false;
        }
        if (definition.getTargetMaterial().isEmpty() && definition.getTargetType().isEmpty()) {
            return true;
        }
        // 材质限制与类型限制为“且”关系：任一限制不满足都拦截
        if (!definition.getTargetMaterial().isEmpty()) {
            boolean materialMatched = false;
            for (String materialName : definition.getTargetMaterial()) {
                Material material = Material.matchMaterial(materialName);
                if ((material != null && target.getType() == material)
                        || target.getType().name().equalsIgnoreCase(materialName)) {
                    materialMatched = true;
                    break;
                }
            }
            if (!materialMatched) {
                return false;
            }
        }
        if (!definition.getTargetType().isEmpty()) {
            boolean typeMatched = false;
            for (String type : definition.getTargetType()) {
                if (TargetMatcher.matchesType(target.getType(), type)) {
                    typeMatched = true;
                    break;
                }
            }
            if (!typeMatched) {
                return false;
            }
        }
        return true;
    }

    private boolean roll(int rate) {
        return rate >= 100 || (rate > 0 && ThreadLocalRandom.current().nextInt(100) < rate);
    }

    private OperationResult fail(String message, ItemStack target, boolean consumeTool) {
        return new OperationResult(target.clone(), null, consumeTool, message);
    }
}
