package com.mosaicgem.plugin.util;

import com.mosaicgem.plugin.MosaicGemPlugin;
import org.bukkit.Bukkit;

import java.util.logging.Level;

/**
 * 软依赖桥接基类：统一“插件是否存在 -> 反射解析 API -> 标记可用”的初始化流程。
 * 子类只需实现 {@link #setup()}（解析并缓存反射句柄）与 {@link #onAvailable()}（输出启用日志）。
 */
public abstract class SoftDependencyBridge {

    private final MosaicGemPlugin plugin;
    private boolean initialized;
    private boolean available;

    protected SoftDependencyBridge(MosaicGemPlugin plugin) {
        this.plugin = plugin;
    }

    protected MosaicGemPlugin plugin() {
        return plugin;
    }

    /**
     * 软依赖插件名（Bukkit PluginManager 中的名称）。
     */
    protected abstract String pluginName();

    /**
     * 解析并缓存该软依赖的反射句柄；失败抛异常由基类统一降级。
     */
    protected abstract void setup() throws Throwable;

    /**
     * 桥接成功后输出日志。
     */
    protected abstract void onAvailable();

    /**
     * 软依赖是否已加载且 API 可调用。
     */
    public final synchronized boolean isAvailable() {
        if (!initialized) {
            initialized = true;
            if (Bukkit.getPluginManager().getPlugin(pluginName()) == null) {
                return false;
            }
            try {
                setup();
                available = true;
                onAvailable();
            } catch (Throwable e) {
                plugin.getLogger().log(Level.WARNING,
                        pluginName() + " 桥接初始化失败（不影响其他功能）: " + e, e);
            }
        }
        return available;
    }
}
