package com.mosaicgem.plugin.util;

import com.mosaicgem.plugin.MosaicGemPlugin;
import org.bukkit.inventory.ItemStack;

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
public final class CrazyEnchantBridge extends SoftDependencyBridge {

    private static final String PLUGIN_NAME = "CrazyEnchantments";

    private Object starter;
    private Object crazyManager;
    private Object enchantmentBookSettings;

    private Method getStarter;
    private Method getCrazyManager;
    private Method getEnchantmentBookSettings;
    private Method getEnchantmentFromName;
    private Method addEnchantment;
    private Method getEnchantments;
    private Method removeEnchantments;
    private Method getName;
    private Method getCustomName;

    public CrazyEnchantBridge(MosaicGemPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String pluginName() {
        return PLUGIN_NAME;
    }

    @Override
    protected void setup() throws Throwable {
        org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        ClassLoader loader = plugin.getClass().getClassLoader();
        Class<?> pluginClass = loader.loadClass("com.badbones69.crazyenchantments.paper.CrazyEnchantments");
        getStarter = pluginClass.getMethod("getStarter");
        starter = getStarter.invoke(plugin);
        if (starter == null) {
            throw new IllegalStateException("CrazyEnchantments Starter 为 null");
        }

        getCrazyManager = starter.getClass().getMethod("getCrazyManager");
        crazyManager = getCrazyManager.invoke(starter);
        getEnchantmentBookSettings = starter.getClass().getMethod("getEnchantmentBookSettings");
        enchantmentBookSettings = getEnchantmentBookSettings.invoke(starter);
        if (crazyManager == null || enchantmentBookSettings == null) {
            throw new IllegalStateException("CrazyEnchantments 管理器为 null");
        }

        Class<?> enchantmentClass = loader.loadClass("com.badbones69.crazyenchantments.paper.api.objects.CEnchantment");
        getEnchantmentFromName = findMethod(crazyManager.getClass(), "getEnchantmentFromName", String.class);
        addEnchantment = findMethod(crazyManager.getClass(), "addEnchantment", ItemStack.class, enchantmentClass, int.class);
        getEnchantments = findMethod(enchantmentBookSettings.getClass(), "getEnchantments", ItemStack.class);
        removeEnchantments = findMethod(enchantmentBookSettings.getClass(), "removeEnchantments", ItemStack.class, List.class);
        getName = findMethod(enchantmentClass, "getName");
        getCustomName = findMethod(enchantmentClass, "getCustomName");
    }

    @Override
    protected void onAvailable() {
        plugin().getLogger().info("已桥接 CrazyEnchantments，可镶嵌其自定义附魔");
    }

    /**
     * 获取自定义附魔的显示名（优先 Crazy 配置的 CustomName，找不到则返回原始名）。
     */
    public String getDisplayName(String name) {
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
    public Map<String, Integer> getEnchantments(ItemStack item) {
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
    public void setEnchantment(ItemStack item, String name, int level) {
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
    public void removeEnchantments(ItemStack item, Collection<String> names) {
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

    private Object findEnchantment(String name) {
        if (!isAvailable() || name == null) {
            return null;
        }
        try {
            return getEnchantmentFromName.invoke(crazyManager, name);
        } catch (ReflectiveOperationException e) {
            logFailure("查找自定义附魔失败: " + name, e);
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private void logFailure(String message, ReflectiveOperationException e) {
        Throwable cause = e instanceof InvocationTargetException invocation && invocation.getTargetException() != null
                ? invocation.getTargetException()
                : e;
        plugin().getLogger().log(Level.WARNING, "[MosaicGem] " + message + "（" + cause + "）", cause);
    }
}
