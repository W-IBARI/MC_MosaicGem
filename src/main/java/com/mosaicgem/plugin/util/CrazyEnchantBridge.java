package com.mosaicgem.plugin.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * CrazyEnchantments 软兼容桥（运行时反射，编译期不依赖）。
 *
 * <p>CrazyEnchantments 把自定义附魔存在物品 PDC 的 {@code crazyenchantments:enchantments} 字符串里，
 * 同时在 lore 顶部写入展示行。MosaicGem 不直接引用其类，而是通过反射调用：
 * <ul>
 *   <li>{@code CrazyEnchantments#getStarter()}</li>
 *   <li>{@code Starter#getCrazyManager()} / {@code Starter#getEnchantmentBookSettings()}</li>
 *   <li>{@code CrazyManager#getEnchantmentFromName(String)}</li>
 *   <li>{@code CrazyManager#addEnchantment(ItemStack, CEnchantment, int)}</li>
 *   <li>{@code EnchantmentBookSettings#getEnchantments(ItemStack)} / {@code removeEnchantments(...)}</li>
 * </ul>
 */
public final class CrazyEnchantBridge {

    private static final String PLUGIN_NAME = "CrazyEnchantments";

    private static boolean initialized;
    private static boolean available;
    private static String failureReason;

    private static Object starter;
    private static Object crazyManager;
    private static Object enchantmentBookSettings;

    private static Method getStarter;
    private static Method getCrazyManager;
    private static Method getEnchantmentBookSettings;
    private static Method getEnchantmentFromName;
    private static Method addEnchantment;
    private static Method getEnchantments;
    private static Method removeEnchantments;
    private static Method getName;
    private static Method getCustomName;

    private CrazyEnchantBridge() {
    }

    /**
     * CrazyEnchantments 是否已加载且 API 可调用。
     */
    public static synchronized boolean isAvailable() {
        init();
        return available;
    }

    /**
     * 获取自定义附魔的显示名（优先 Crazy 配置的 CustomName，找不到则返回原始名）。
     */
    public static synchronized String getDisplayName(String name) {
        if (!isAvailable() || name == null) {
            return name;
        }
        Object enchantment = findEnchantment(name);
        if (enchantment == null) {
            return name;
        }
        try {
            Object customName = getCustomName.invoke(enchantment);
            return customName != null ? String.valueOf(customName) : name;
        } catch (ReflectiveOperationException e) {
            logFailure("读取自定义附魔显示名失败", e);
            return name;
        }
    }

    /**
     * 读取物品上的全部 CrazyEnchantments 附魔：附魔名 -> 等级。
     */
    @SuppressWarnings("unchecked")
    public static synchronized Map<String, Integer> getEnchantments(ItemStack item) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (!isAvailable() || item == null || item.getType().isAir()) {
            return result;
        }
        try {
            Object raw = getEnchantments.invoke(enchantmentBookSettings, item);
            if (!(raw instanceof Map<?, ?> map)) {
                return result;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object enchantment = entry.getKey();
                if (enchantment == null) {
                    continue;
                }
                String name = String.valueOf(getName.invoke(enchantment));
                Object level = entry.getValue();
                if (level instanceof Number number) {
                    result.put(name, number.intValue());
                }
            }
        } catch (ReflectiveOperationException e) {
            logFailure("读取自定义附魔失败", e);
        }
        return result;
    }

    /**
     * 把指定自定义附魔设置为指定等级（已存在则覆盖，不存在则新建）。
     */
    public static synchronized void setEnchantment(ItemStack item, String name, int level) {
        if (!isAvailable() || item == null || name == null || level <= 0) {
            return;
        }
        Object enchantment = findEnchantment(name);
        if (enchantment == null) {
            return;
        }
        try {
            addEnchantment.invoke(crazyManager, item, enchantment, level);
        } catch (ReflectiveOperationException e) {
            logFailure("写入自定义附魔失败: " + name, e);
        }
    }

    /**
     * 从物品上移除指定自定义附魔（不存在则忽略）。
     */
    @SuppressWarnings("unchecked")
    public static synchronized void removeEnchantments(ItemStack item, Collection<String> names) {
        if (!isAvailable() || item == null || names == null || names.isEmpty()) {
            return;
        }
        List<Object> targets = new ArrayList<>();
        for (String name : names) {
            Object enchantment = findEnchantment(name);
            if (enchantment != null) {
                targets.add(enchantment);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        try {
            removeEnchantments.invoke(enchantmentBookSettings, item, targets);
        } catch (ReflectiveOperationException e) {
            logFailure("移除自定义附魔失败", e);
        }
    }

    private static Object findEnchantment(String name) {
        if (!available || name == null) {
            return null;
        }
        try {
            return getEnchantmentFromName.invoke(crazyManager, name);
        } catch (ReflectiveOperationException e) {
            logFailure("查找自定义附魔失败: " + name, e);
            return null;
        }
    }

    private static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            if (plugin == null) {
                return;
            }
            Class<?> pluginClass = plugin.getClass();
            getStarter = findMethod(pluginClass, "getStarter");
            starter = getStarter.invoke(plugin);
            if (starter == null) {
                return;
            }

            getCrazyManager = findMethod(starter.getClass(), "getCrazyManager");
            crazyManager = getCrazyManager.invoke(starter);
            getEnchantmentBookSettings = findMethod(starter.getClass(), "getEnchantmentBookSettings");
            enchantmentBookSettings = getEnchantmentBookSettings.invoke(starter);
            if (crazyManager == null || enchantmentBookSettings == null) {
                return;
            }

            Class<?> enchantmentClass = Class.forName("com.badbones69.crazyenchantments.paper.api.objects.CEnchantment");
            getEnchantmentFromName = findMethod(crazyManager.getClass(), "getEnchantmentFromName", String.class);
            addEnchantment = findMethod(crazyManager.getClass(), "addEnchantment", ItemStack.class, enchantmentClass, int.class);
            getEnchantments = findMethod(enchantmentBookSettings.getClass(), "getEnchantments", ItemStack.class);
            removeEnchantments = findMethod(enchantmentBookSettings.getClass(), "removeEnchantments", ItemStack.class, List.class);
            getName = findMethod(enchantmentClass, "getName");
            getCustomName = findMethod(enchantmentClass, "getCustomName");

            available = true;
            Bukkit.getLogger().info("[MosaicGem] 已桥接 CrazyEnchantments，可镶嵌其自定义附魔");
        } catch (Throwable e) {
            failureReason = e.getMessage();
            Bukkit.getLogger().log(Level.WARNING, "[MosaicGem] CrazyEnchantments 桥接初始化失败（不影响原版附魔功能）: " + e, e);
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static void logFailure(String message, ReflectiveOperationException e) {
        Throwable cause = e instanceof InvocationTargetException invocation && invocation.getTargetException() != null
                ? invocation.getTargetException()
                : e;
        Bukkit.getLogger().log(Level.WARNING, "[MosaicGem] " + message + "（" + cause + "）", cause);
    }
}
