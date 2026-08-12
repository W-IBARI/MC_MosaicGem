package com.mosaicgem.plugin.util;

import com.mosaicgem.plugin.MosaicGemPlugin;
import com.mosaicgem.plugin.config.ConfigManager;
import com.mosaicgem.plugin.config.GemDefinition;
import com.mosaicgem.plugin.model.SocketData;
import com.mosaicgem.plugin.model.SocketedGem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * MythicCrucible 软兼容桥（运行时反射，编译期不依赖）。
 *
 * <p>MythicCrucible 的物品技能系统通过 {@code Profile.runSkills(trigger, ...)} 统一触发
 * （SWING / USE / RIGHTCLICK 等），并会调用玩家 Profile 上注册的外部 {@code SkillHolder}。
 * 本桥为每个在线玩家注册一个外部 SkillHolder 代理：
 * <ul>
 *   <li>触发事件完全由 MythicCrucible 自己的监听器负责，插件不再写技能触发监听</li>
 *   <li>触发时按宝石配置的技能名（支持 {@code 技能名 @触发器} 格式）调用 MythicMobs 施放技能</li>
 *   <li>技能冷却、条件、目标选择仍由 MythicMobs 自身规则处理</li>
 * </ul>
 */
public final class MythicCrucibleBridge {

    private static final String PLUGIN_NAME = "MythicCrucible";
    private static final String SKILL_HOLDER_CLASS = "io.lumine.mythiccrucible.items.SkillHolder";

    private final MosaicGemPlugin plugin;
    private final ConfigManager configs;
    private final ItemFactory factory;
    private final MythicMobsBridge mythicMobs;

    private boolean available;
    private boolean initialized;

    private Class<?> skillHolderClass;
    private Method getProfileManager;
    private Method getPlayerProfile;
    private Method registerExternalHolder;
    private Method unregisterExternalHolder;
    private Method adaptEntity;
    private Method triggerName;

    private final Map<Player, Object> holders = new LinkedHashMap<>();

    public MythicCrucibleBridge(MosaicGemPlugin plugin, ConfigManager configs, ItemFactory factory, MythicMobsBridge mythicMobs) {
        this.plugin = plugin;
        this.configs = configs;
        this.factory = factory;
        this.mythicMobs = mythicMobs;
        init();
    }

    public synchronized boolean isAvailable() {
        init();
        return available;
    }

    /**
     * 为玩家注册外部 SkillHolder（加入服务器时调用）。
     */
    public synchronized void registerPlayer(Player player) {
        if (!isAvailable() || player == null || holders.containsKey(player)) {
            return;
        }
        try {
            Object profile = getProfile(player);
            if (profile == null) {
                return;
            }
            Object holder = createHolderProxy(player);
            registerExternalHolder.invoke(profile, holder);
            holders.put(player, holder);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "注册 MythicCrucible 技能持有者失败: " + player.getName(), e);
        }
    }

    /**
     * 移除玩家的外部 SkillHolder（退出服务器时调用）。
     */
    public synchronized void unregisterPlayer(Player player) {
        if (!isAvailable() || player == null) {
            return;
        }
        Object holder = holders.remove(player);
        if (holder == null) {
            return;
        }
        try {
            Object profile = getProfile(player);
            if (profile != null) {
                unregisterExternalHolder.invoke(profile, holder);
            }
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "移除 MythicCrucible 技能持有者失败: " + player.getName(), e);
        }
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Plugin crucible = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            if (crucible == null) {
                return;
            }
            skillHolderClass = Class.forName(SKILL_HOLDER_CLASS);

            Class<?> mainClass = Class.forName("io.lumine.mythiccrucible.MythicCrucible");
            getProfileManager = mainClass.getMethod("getProfileManager");
            Object profileManager = getProfileManager.invoke(crucible);
            if (profileManager == null) {
                return;
            }
            getPlayerProfile = profileManager.getClass().getMethod("getPlayerProfile", Player.class);

            Class<?> profileClass = Class.forName("io.lumine.mythiccrucible.profiles.Profile");
            registerExternalHolder = profileClass.getMethod("registerExternalHolder", skillHolderClass);
            unregisterExternalHolder = profileClass.getMethod("unregisterExternalHolder", skillHolderClass);

            Class<?> adapterClass = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter");
            adaptEntity = adapterClass.getMethod("adapt", Class.forName("io.lumine.mythic.api.adapters.AbstractEntity"));

            Class<?> triggerClass = Class.forName("io.lumine.mythic.api.skills.SkillTrigger");
            try {
                triggerName = triggerClass.getMethod("name");
            } catch (NoSuchMethodException ignored) {
                triggerName = null;
            }

            available = true;
            plugin.getLogger().info("已桥接 MythicCrucible " + crucible.getDescription().getVersion()
                    + "：mm 技能宝石改用 Crucible 物品技能触发（SWING/USE/RIGHTCLICK 等）");
        } catch (Throwable e) {
            plugin.getLogger().log(Level.WARNING, "MythicCrucible 桥接初始化失败（将回退到内置攻击触发）: " + e, e);
        }
    }

    private Object getProfile(Player player) throws ReflectiveOperationException {
        Object profileManager = getProfileManager.invoke(Bukkit.getPluginManager().getPlugin(PLUGIN_NAME));
        return profileManager == null ? null : getPlayerProfile.invoke(profileManager, player);
    }

    /**
     * 创建实现 MythicCrucible SkillHolder 接口的动态代理。
     */
    private Object createHolderProxy(Player player) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
                case "hasTimerSkills" -> {
                    return false;
                }
                case "runTimerSkills" -> {
                    return null;
                }
                case "getSkills" -> {
                    return new LinkedList<>();
                }
                case "runSkills" -> {
                    if (args == null || args.length < 2) {
                        return null;
                    }
                    if (args.length >= 4 && args[1] != null) {
                        // runSkills(caster, trigger, origin, triggerEntity[, consumer])
                        return execute(player, args[1], args.length >= 4 ? args[3] : null);
                    }
                    // runSkills(parentSkill, metadata)：父技能链由 Crucible/MythicMobs 内部处理，这里不重复施放
                    return null;
                }
                case "toString" -> {
                    return "MosaicGemSkillHolder(" + player.getName() + ")";
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> {
                    return null;
                }
            }
        };
        return Proxy.newProxyInstance(skillHolderClass.getClassLoader(), new Class<?>[]{skillHolderClass}, handler);
    }

    /**
     * Crucible 触发某个技能触发器时执行：检查主手装备上的 mm 技能宝石并施放匹配的技能。
     */
    private boolean execute(Player player, Object trigger, Object triggerEntity) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        String currentTrigger = triggerName(trigger);
        if (currentTrigger == null) {
            return false;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            return false;
        }
        SocketData data = factory.readSocketData(item);
        if (data.gems().isEmpty()) {
            return false;
        }

        Entity target = toBukkitEntity(triggerEntity);
        if (target == null) {
            target = player;
        }
        boolean cast = false;
        for (SocketedGem gem : data.gems()) {
            GemDefinition definition = configs.getGem(gem.id());
            if (definition == null || !ItemFactory.BUFF_TYPE_MM_SKILL.equalsIgnoreCase(definition.getBuffType())) {
                continue;
            }
            for (String line : definition.getAttribute()) {
                SkillEntry entry = parseSkillLine(line, gem);
                if (entry == null || !currentTrigger.equals(entry.trigger())) {
                    continue;
                }
                if (mythicMobs.castSkill(player, entry.name(), target)) {
                    cast = true;
                }
            }
        }
        return cast;
    }

    private String triggerName(Object trigger) {
        if (trigger == null) {
            return null;
        }
        try {
            if (triggerName != null) {
                Object name = triggerName.invoke(trigger);
                if (name != null) {
                    return normalizeTrigger(String.valueOf(name));
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        return normalizeTrigger(String.valueOf(trigger));
    }

    private Entity toBukkitEntity(Object abstractEntity) {
        if (abstractEntity == null || adaptEntity == null) {
            return null;
        }
        try {
            Object entity = adaptEntity.invoke(null, abstractEntity);
            return entity instanceof Entity bukkitEntity ? bukkitEntity : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * 解析宝石配置中的技能行：支持 MythicCrucible 风格 {@code 技能名 @触发器}、
     * {@code skill:技能名 @触发器} 或纯技能名（默认 @onSwing）。
     */
    private SkillEntry parseSkillLine(String line, SocketedGem gem) {
        String resolved = factory.resolve(line, gem.values());
        if (resolved == null || resolved.isBlank()) {
            return null;
        }
        String text = ItemFactory.stripLoreText(resolved);
        String trigger = "SWING";
        int at = text.lastIndexOf('@');
        if (at >= 0 && at + 1 < text.length()) {
            trigger = normalizeTrigger(text.substring(at + 1).trim());
            text = text.substring(0, at).trim();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("skill:")) {
            text = text.substring("skill:".length()).trim();
        }
        if (text.isEmpty()) {
            return null;
        }
        return new SkillEntry(text, trigger);
    }

    private static String normalizeTrigger(String raw) {
        if (raw == null) {
            return "SWING";
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return "SWING";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("on")) {
            value = value.substring(2);
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private record SkillEntry(String name, String trigger) {
    }
}
