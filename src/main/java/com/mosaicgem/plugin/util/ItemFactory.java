package com.mosaicgem.plugin.util;

import com.mosaicgem.plugin.MosaicGemPlugin;
import com.mosaicgem.plugin.config.ConfigManager;
import com.mosaicgem.plugin.config.GemDefinition;
import com.mosaicgem.plugin.config.ItemDefinition;
import com.mosaicgem.plugin.config.PuncherDefinition;
import com.mosaicgem.plugin.config.RemoverDefinition;
import com.mosaicgem.plugin.config.SocketLoreTemplate;
import com.mosaicgem.plugin.model.SocketData;
import com.mosaicgem.plugin.model.SocketedGem;
import com.mosaicgem.plugin.model.ToolType;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 物品工厂：生成工具物品、读写物品组件（custom data）中的镶嵌数据。
 */
public class ItemFactory {

    /** 属性合并行标记：SX 解析 §X 之前的内容，标记后的加成文字不影响属性计算 */
    public static final String LORE_MARKER = "\u00A7X\u200B";

    private final MosaicGemPlugin plugin;
    private final ConfigManager configs;

    private final NamespacedKey keyItem;
    private final NamespacedKey keyId;
    private final NamespacedKey keyValues;
    private final NamespacedKey keyHoles;
    private final NamespacedKey keyGems;
    private final NamespacedKey keySocketLines;
    private final NamespacedKey keyBaseLines;
    private final NamespacedKey keyUuid;
    private final NamespacedKey keyCount;
    private final NamespacedKey keySources;

    public ItemFactory(MosaicGemPlugin plugin, ConfigManager configs) {
        this.plugin = plugin;
        this.configs = configs;
        this.keyItem = key("item");
        this.keyId = key("id");
        this.keyValues = key("values");
        this.keyHoles = key("holes");
        this.keyGems = key("gems");
        this.keySocketLines = key("socketLines");
        this.keyBaseLines = key("baseLines");
        this.keyUuid = key("uuid");
        this.keyCount = key("count");
        this.keySources = key("sources");
    }

    // ------------------------------------------------------------------
    // 物品生成
    // ------------------------------------------------------------------

    public ItemStack buildGem(GemDefinition definition, Map<String, String> values) {
        return build(definition, ToolType.GEM, values);
    }

    public ItemStack buildPuncher(PuncherDefinition definition) {
        return build(definition, ToolType.PUNCHER, null);
    }

    public ItemStack buildRemover(RemoverDefinition definition) {
        return build(definition, ToolType.REMOVER, null);
    }

    private ItemStack build(ItemDefinition definition, ToolType type, Map<String, String> values) {
        ItemStack item = new ItemStack(definition.getMaterial());
        item.editMeta(meta -> {
            if (definition.isEnchant()) {
                meta.setEnchantmentGlintOverride(true);
            }
            if (definition.getCustomModelData() != null) {
                meta.setCustomModelData(definition.getCustomModelData());
            }
            meta.setDisplayName(colorize(resolve(definition.getName(), values)));
            if (!definition.getLore().isEmpty()) {
                meta.setLore(definition.getLore().stream()
                        .map(line -> colorize(resolve(line, values)))
                        .toList());
            }
        });
        markTool(item, type, definition.getId(), values);
        return item;
    }

    // ------------------------------------------------------------------
    // 工具标记与识别
    // ------------------------------------------------------------------

    public void markTool(ItemStack item, ToolType type, String id, Map<String, String> values) {
        item.editPersistentDataContainer(pdc -> {
            pdc.set(keyItem, PersistentDataType.STRING, type.name().toLowerCase(Locale.ROOT));
            pdc.set(keyId, PersistentDataType.STRING, id);
            if (values != null && !values.isEmpty()) {
                pdc.set(keyValues, PersistentDataType.TAG_CONTAINER, writeMap(pdc.getAdapterContext(), values));
            }
        });
    }

    public ToolType getToolType(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        String value = item.getPersistentDataContainer().get(keyItem, PersistentDataType.STRING);
        return ToolType.fromString(value);
    }

    public String getToolId(ItemStack item) {
        if (item == null) {
            return null;
        }
        return item.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
    }

    public Map<String, String> readValues(ItemStack item) {
        if (item == null) {
            return Map.of();
        }
        PersistentDataContainer container = item.getPersistentDataContainer().get(keyValues, PersistentDataType.TAG_CONTAINER);
        return container == null ? Map.of() : readMap(container);
    }

    // ------------------------------------------------------------------
    // 镶嵌数据读写（孔数 / 宝石列表）
    // ------------------------------------------------------------------

    public SocketData readSocketData(ItemStack item) {
        if (item == null) {
            return new SocketData(0, Map.of(), List.of());
        }
        PersistentDataContainerView pdc = item.getPersistentDataContainer();
        int holes = pdc.getOrDefault(keyHoles, PersistentDataType.INTEGER, 0);
        Map<String, Integer> holeSources = readSources(pdc.get(keySources, PersistentDataType.TAG_CONTAINER));
        if (holeSources.isEmpty() && holes > 0) {
            // 兼容旧数据：只有总孔数、没有来源记录时，统一归入 legacy 来源
            holeSources = new LinkedHashMap<>(Map.of("legacy", holes));
        }
        List<SocketedGem> gems = new ArrayList<>();
        PersistentDataContainer[] array = pdc.get(keyGems, PersistentDataType.TAG_CONTAINER_ARRAY);
        if (array != null) {
            for (PersistentDataContainer gemContainer : array) {
                String id = gemContainer.get(keyId, PersistentDataType.STRING);
                String instanceId = gemContainer.get(keyUuid, PersistentDataType.STRING);
                if (id == null || instanceId == null) {
                    continue;
                }
                gems.add(new SocketedGem(id, instanceId, readMap(gemContainer.get(keyValues, PersistentDataType.TAG_CONTAINER)), readList(gemContainer)));
            }
        }
        return new SocketData(holes, holeSources, gems);
    }

    public void writeSocketData(ItemStack item, int holes, Map<String, Integer> holeSources, List<SocketedGem> gems) {
        item.editPersistentDataContainer(pdc -> {
            if (holes <= 0 && holeSources.isEmpty() && gems.isEmpty()) {
                pdc.remove(keyHoles);
                pdc.remove(keyGems);
                pdc.remove(keySources);
                return;
            }
            pdc.set(keyHoles, PersistentDataType.INTEGER, Math.max(0, holes));
            if (holeSources.isEmpty()) {
                pdc.remove(keySources);
            } else {
                pdc.set(keySources, PersistentDataType.TAG_CONTAINER, writeSources(pdc.getAdapterContext(), holeSources));
            }
            if (gems.isEmpty()) {
                pdc.remove(keyGems);
                return;
            }
            PersistentDataContainer[] array = new PersistentDataContainer[gems.size()];
            for (int i = 0; i < gems.size(); i++) {
                SocketedGem gem = gems.get(i);
                PersistentDataContainer gemContainer = pdc.getAdapterContext().newPersistentDataContainer();
                gemContainer.set(keyId, PersistentDataType.STRING, gem.id());
                gemContainer.set(keyUuid, PersistentDataType.STRING, gem.instanceId());
                if (!gem.values().isEmpty()) {
                    gemContainer.set(keyValues, PersistentDataType.TAG_CONTAINER, writeMap(pdc.getAdapterContext(), gem.values()));
                }
                if (!gem.lines().isEmpty()) {
                    writeList(gemContainer, gem.lines());
                }
                array[i] = gemContainer;
            }
            pdc.set(keyGems, PersistentDataType.TAG_CONTAINER_ARRAY, array);
        });
    }

    private PersistentDataContainer writeSources(PersistentDataAdapterContext context, Map<String, Integer> holeSources) {
        PersistentDataContainer container = context.newPersistentDataContainer();
        int index = 0;
        for (Map.Entry<String, Integer> entry : holeSources.entrySet()) {
            container.set(key("s" + index), PersistentDataType.STRING, entry.getKey());
            container.set(key("c" + index), PersistentDataType.INTEGER, entry.getValue());
            index++;
        }
        container.set(keyCount, PersistentDataType.INTEGER, index);
        return container;
    }

    private Map<String, Integer> readSources(PersistentDataContainer container) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (container == null) {
            return result;
        }
        int count = container.getOrDefault(keyCount, PersistentDataType.INTEGER, 0);
        for (int i = 0; i < count; i++) {
            String source = container.get(key("s" + i), PersistentDataType.STRING);
            Integer value = container.get(key("c" + i), PersistentDataType.INTEGER);
            if (source != null && value != null && value > 0) {
                result.put(source, value);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Lore 操作
    // ------------------------------------------------------------------

    public void appendLore(ItemStack item, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<String> lore = item.getItemMeta() != null && item.getItemMeta().hasLore()
                ? new ArrayList<>(item.getItemMeta().getLore())
                : new ArrayList<>();
        lore.addAll(lines);
        setLore(item, lore);
    }

    public void removeLoreLines(ItemStack item, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        if (item.getItemMeta() == null || !item.getItemMeta().hasLore()) {
            return;
        }
        List<String> lore = new ArrayList<>(item.getItemMeta().getLore());
        for (String line : lines) {
            lore.remove(line);
        }
        setLore(item, lore);
    }

    /**
     * 写入 lore：普通行按传统 § 代码解析；包含合并标记的行，标记部分作为字面文本写入，
     * 避免 Paper 的传统 setLore 把 §X 吞掉。
     */
    public void setLore(ItemStack item, List<String> lore) {
        item.editMeta(meta -> {
            if (lore == null || lore.isEmpty()) {
                meta.lore(null);
                return;
            }
            List<Component> components = new ArrayList<>();
            for (String line : lore) {
                components.add(toComponent(line));
            }
            meta.lore(components);
        });
    }

    private static Component toComponent(String line) {
        int markerIndex = line.indexOf(LORE_MARKER);
        if (markerIndex < 0) {
            return LegacyComponentSerializer.legacySection().deserialize(line);
        }
        Component prefix = LegacyComponentSerializer.legacySection().deserialize(line.substring(0, markerIndex));
        return prefix.append(Component.text(line.substring(markerIndex)));
    }

    // ------------------------------------------------------------------
    // 镶嵌信息 lore（孔位/宝石提示）
    // ------------------------------------------------------------------

    /**
     * 读取上次写入的镶嵌信息行。
     */
    public List<String> readSocketLines(ItemStack item) {
        if (item == null) {
            return List.of();
        }
        PersistentDataContainer container = item.getPersistentDataContainer().get(keySocketLines, PersistentDataType.TAG_CONTAINER);
        return readList(container);
    }

    /**
     * 更新装备上的镶嵌信息 lore：先移除旧信息，再按模版写入新信息。
     */
    public void applySocketLore(ItemStack item, SocketData data, SocketLoreTemplate template) {
        List<String> oldLines = readSocketLines(item);
        if (!oldLines.isEmpty()) {
            removeLoreLines(item, oldLines);
        }

        List<String> newLines = new ArrayList<>();
        if (template.enabled()) {
            // {holes} = 已经镶嵌的孔洞数（宝石数），{max_holes} = 物品可用的孔洞数（已打孔数）
            String holes = String.valueOf(data.gems().size());
            String max = String.valueOf(data.holes());
            String gemCount = String.valueOf(data.gems().size());
            for (String line : template.lines()) {
                newLines.add(colorize(line
                        .replace("{holes}", holes)
                        .replace("{max_holes}", max)
                        .replace("{gem_count}", gemCount)));
            }
            int index = 1;
            for (SocketedGem gem : data.gems()) {
                String gemName = resolveGemName(gem.id());
                String gemValues = resolveGemValues(gem);
                for (String line : template.gemLines()) {
                    newLines.add(colorize(line
                            .replace("{index}", String.valueOf(index))
                            .replace("{gem}", gemName)
                            .replace("{id}", gem.id())
                            .replace("{values}", gemValues)));
                }
                index++;
            }
            if (data.gems().isEmpty() && template.emptyLine() != null && !template.emptyLine().isEmpty()) {
                newLines.add(colorize(template.emptyLine()
                        .replace("{holes}", holes)
                        .replace("{max_holes}", max)));
            }
        }

        if (!newLines.isEmpty()) {
            appendLore(item, newLines);
        }
        writeSocketLines(item, newLines);
    }

    private String resolveGemName(String id) {
        GemDefinition definition = configs.getGem(id);
        return definition == null ? id : colorize(definition.getName());
    }

    private void writeSocketLines(ItemStack item, List<String> lines) {
        item.editPersistentDataContainer(pdc -> {
            if (lines == null || lines.isEmpty()) {
                pdc.remove(keySocketLines);
                return;
            }
            PersistentDataContainer container = pdc.getAdapterContext().newPersistentDataContainer();
            writeList(container, lines);
            pdc.set(keySocketLines, PersistentDataType.TAG_CONTAINER, container);
        });
    }

    /**
     * 读取属性合并前的原始属性行（属性名 -> 原始 lore 行）。
     */
    public Map<String, String> readBaseLines(ItemStack item) {
        if (item == null) {
            return Map.of();
        }
        PersistentDataContainer container = item.getPersistentDataContainer().get(keyBaseLines, PersistentDataType.TAG_CONTAINER);
        return readMap(container);
    }

    /**
     * 保存属性合并前的原始属性行，用于拆卸/重算时还原。
     */
    public void writeBaseLines(ItemStack item, Map<String, String> baseLines) {
        item.editPersistentDataContainer(pdc -> {
            if (baseLines == null || baseLines.isEmpty()) {
                pdc.remove(keyBaseLines);
                return;
            }
            pdc.set(keyBaseLines, PersistentDataType.TAG_CONTAINER, writeMap(pdc.getAdapterContext(), baseLines));
        });
    }

    /**
     * 生成宝石的数值描述（用于镶嵌信息 lore 的 {values} 占位符）。
     */
    public String resolveGemValues(SocketedGem gem) {
        GemDefinition definition = configs.getGem(gem.id());
        if (definition == null) {
            return gem.id();
        }
        return definition.getAttribute().stream()
                .map(line -> resolve(line, gem.values()))
                .map(ItemFactory::stripLoreText)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.joining("、"));
    }

    /**
     * 去掉 lore 文本中的颜色代码（§x）与 <#XXXXXX> 标记，返回纯文本。
     */
    public static String stripLoreText(String line) {
        if (line == null) {
            return "";
        }
        String stripped = line.replaceAll("<#[0-9a-fA-F]{6}>", "");
        stripped = stripped.replaceAll("\u00A7.", "");
        return stripped.trim();
    }

    // ------------------------------------------------------------------
    // 随机数
    // ------------------------------------------------------------------

    public Map<String, String> rollRandom(GemDefinition definition) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : definition.getRandom().entrySet()) {
            result.put(entry.getKey(), rollValue(entry.getValue()));
        }
        return result;
    }

    private String rollValue(String range) {
        String trimmed = range.trim();
        String[] parts = trimmed.split("[~\\-]", 2);
        if (parts.length != 2) {
            return trimmed;
        }
        double min;
        double max;
        try {
            min = Double.parseDouble(parts[0].trim());
            max = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException e) {
            return trimmed;
        }
        if (max < min) {
            double temp = min;
            min = max;
            max = temp;
        }
        int decimals = Math.max(decimalsOf(parts[0]), decimalsOf(parts[1]));
        double value = min + (max - min) * ThreadLocalRandom.current().nextDouble();
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private int decimalsOf(String text) {
        int index = text.indexOf('.');
        return index < 0 ? 0 : text.length() - index - 1;
    }

    // ------------------------------------------------------------------
    // 文本与 PDC 工具
    // ------------------------------------------------------------------

    public String resolve(String text, Map<String, String> values) {
        if (text == null) {
            return null;
        }
        String result = text;
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result = result.replace("${" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    public static String colorize(String text) {
        return text == null ? null : text.replace('&', '\u00A7');
    }

    public static String newInstanceId() {
        return UUID.randomUUID().toString();
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }

    private PersistentDataContainer writeMap(PersistentDataAdapterContext context, Map<String, String> values) {
        PersistentDataContainer container = context.newPersistentDataContainer();
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            container.set(key("n" + index), PersistentDataType.STRING, entry.getKey());
            container.set(key("v" + index), PersistentDataType.STRING, entry.getValue());
            index++;
        }
        container.set(keyCount, PersistentDataType.INTEGER, index);
        return container;
    }

    private Map<String, String> readMap(PersistentDataContainer container) {
        Map<String, String> result = new LinkedHashMap<>();
        if (container == null) {
            return result;
        }
        int count = container.getOrDefault(keyCount, PersistentDataType.INTEGER, 0);
        for (int i = 0; i < count; i++) {
            String name = container.get(key("n" + i), PersistentDataType.STRING);
            String value = container.get(key("v" + i), PersistentDataType.STRING);
            if (name != null && value != null) {
                result.put(name, value);
            }
        }
        return result;
    }

    private void writeList(PersistentDataContainer container, List<String> lines) {
        int index = 0;
        for (String line : lines) {
            container.set(key("l" + index), PersistentDataType.STRING, line);
            index++;
        }
        container.set(keyCount, PersistentDataType.INTEGER, index);
    }

    private List<String> readList(PersistentDataContainer container) {
        List<String> result = new ArrayList<>();
        if (container == null) {
            return result;
        }
        int count = container.getOrDefault(keyCount, PersistentDataType.INTEGER, 0);
        for (int i = 0; i < count; i++) {
            String line = container.get(key("l" + i), PersistentDataType.STRING);
            if (line != null) {
                result.add(line);
            }
        }
        return result;
    }
}
