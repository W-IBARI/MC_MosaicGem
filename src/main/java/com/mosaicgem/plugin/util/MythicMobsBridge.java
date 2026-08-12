package com.mosaicgem.plugin.util;

import com.mosaicgem.plugin.MosaicGemPlugin;
import com.mosaicgem.plugin.config.ConfigManager;
import com.mosaicgem.plugin.config.GemDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

/**
 * MythicMobs 软兼容桥（运行时反射，编译期不依赖）。
 *
 * <p>提供两项能力：
 * <ul>
 *   <li>自定义掉落：监听 {@code MythicDropLoadEvent}，注册名为 {@code mosaicgem} / {@code mgem} 的掉落，
 *       掉落参数为宝石内部名（如 {@code - mosaicgem:附魔测试宝石 1 0.5}），掉落时生成随机宝石</li>
 *   <li>技能施放：通过 {@code MythicMobs#getAPIHelper().castSkill(...)} 施放玩家配置的 MM 技能，
 *       由 MythicMobs 自行处理冷却、条件、目标等规则</li>
 * </ul>
 */
public final class MythicMobsBridge extends SoftDependencyBridge {

    private static final String PLUGIN_NAME = "MythicMobs";
    private static final String EVENT_CLASS = "io.lumine.mythic.bukkit.events.MythicDropLoadEvent";

    private final ConfigManager configs;
    private final ItemFactory factory;

    private Object apiHelper;
    private Method castSkillMethod;
    private String mythicVersion;

    private Class<?> eventClass;
    private Class<?> iDropClass;
    private Class<?> iItemDropClass;
    private Method getDropName;
    private Method getArgument;
    private Method getConfig;
    private Method registerDrop;
    private Method getConfigString;
    private Method adaptItem;

    public MythicMobsBridge(MosaicGemPlugin plugin, ConfigManager configs, ItemFactory factory) {
        super(plugin);
        this.configs = configs;
        this.factory = factory;
    }

    @Override
    protected String pluginName() {
        return PLUGIN_NAME;
    }

    @Override
    protected void setup() throws Throwable {
        Plugin mythic = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        mythicVersion = mythic.getDescription().getVersion();

        Class<?> mythicClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
        Method inst = mythicClass.getMethod("inst");
        Object instance = inst.invoke(null);
        if (instance == null) {
            throw new IllegalStateException("MythicBukkit 实例为 null");
        }
        apiHelper = instance.getClass().getMethod("getAPIHelper").invoke(instance);
        if (apiHelper == null) {
            throw new IllegalStateException("MythicMobs APIHelper 为 null");
        }
        castSkillMethod = apiHelper.getClass().getMethod(
                "castSkill",
                Entity.class,
                String.class,
                Entity.class,
                Location.class,
                Collection.class,
                Collection.class,
                float.class
        );

        eventClass = Class.forName(EVENT_CLASS);
        iDropClass = Class.forName("io.lumine.mythic.api.drops.IDrop");
        iItemDropClass = Class.forName("io.lumine.mythic.api.drops.IItemDrop");
        getDropName = eventClass.getMethod("getDropName");
        getArgument = eventClass.getMethod("getArgument");
        getConfig = eventClass.getMethod("getConfig");
        registerDrop = eventClass.getMethod("register", iDropClass);

        Class<?> lineConfigClass = Class.forName("io.lumine.mythic.api.config.MythicLineConfig");
        getConfigString = lineConfigClass.getMethod("getString", String[].class, String.class, String[].class);

        Class<?> adapterClass = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter");
        adaptItem = adapterClass.getMethod("adapt", ItemStack.class);

        // 动态注册掉落加载事件：Bukkit 要求监听器参数必须是带 getHandlerList 的具体事件类，
        // 这里通过反射拿到事件类后直接注册 EventExecutor，避免编译期依赖 MythicMobs
        Method getHandlerList = eventClass.getMethod("getHandlerList");
        getHandlerList.invoke(null);
        @SuppressWarnings("unchecked")
        Class<? extends Event> eventType = (Class<? extends Event>) eventClass;
        Bukkit.getPluginManager().registerEvent(
                eventType,
                new Listener() {
                },
                EventPriority.NORMAL,
                (listener, event) -> handleDropEvent(event),
                plugin()
        );

        // MythicMobs 先于本插件加载，掉落加载事件已经错过；
        // 重新加载掉落表与怪物配置，让自定义掉落事件再次触发并完成注册
        try {
            Object dropManager = instance.getClass().getMethod("getDropManager").invoke(instance);
            dropManager.getClass().getMethod("loadDropTables").invoke(dropManager);
            Object mobManager = instance.getClass().getMethod("getMobManager").invoke(instance);
            mobManager.getClass().getMethod("loadMobs").invoke(mobManager);
        } catch (ReflectiveOperationException e) {
            plugin().getLogger().log(Level.WARNING,
                    "重新加载 MythicMobs 掉落/怪物配置失败（可执行 /mm reload 手动重载）: " + e, e);
        }
    }

    @Override
    protected void onAvailable() {
        plugin().getLogger().info("已桥接 MythicMobs " + mythicVersion + "：支持自定义宝石掉落与 mm 技能宝石");
    }

    /**
     * 处理 MythicMobs 的掉落加载事件（由 MythicDropListener 转发）。
     */
    public synchronized void handleDropEvent(Event event) {
        if (!isAvailable() || event == null || !eventClass.isInstance(event)) {
            return;
        }
        try {
            String dropName = String.valueOf(getDropName.invoke(event));
            if (!isDropName(dropName)) {
                return;
            }
            String argument = String.valueOf(getArgument.invoke(event));
            Object config = getConfig.invoke(event);
            String gemId = resolveGemId(config, argument);
            Object drop = createDropProxy(gemId);
            registerDrop.invoke(event, drop);
            plugin().getLogger().info("已注册 MythicMobs 自定义掉落: " + dropName + " -> 宝石 " + gemId);
        } catch (ReflectiveOperationException e) {
            plugin().getLogger().log(Level.WARNING, "处理 MythicMobs 掉落注册失败: " + e, e);
        }
    }

    /**
     * 让持有镶嵌装备的玩家向目标施放指定 MythicMobs 技能。
     * 冷却、条件、目标选择等均交由 MythicMobs 自身规则处理。
     *
     * @return true 表示 MythicMobs 接受了施放请求
     */
    public boolean castSkill(Entity caster, String skill, Entity target) {
        if (!isAvailable() || caster == null || target == null || skill == null || skill.isBlank()) {
            return false;
        }
        try {
            Collection<Entity> targets = List.of(target);
            Object result = castSkillMethod.invoke(
                    apiHelper,
                    caster,
                    skill.trim(),
                    caster,
                    caster.getLocation(),
                    targets,
                    List.of(),
                    1.0f
            );
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e instanceof InvocationTargetException invocation && invocation.getTargetException() != null
                    ? invocation.getTargetException()
                    : e;
            plugin().getLogger().log(Level.WARNING, "施放 MythicMobs 技能失败: " + skill + "（" + cause + "）", cause);
            return false;
        }
    }

    private boolean isDropName(String name) {
        return name != null && ("mosaicgem".equalsIgnoreCase(name) || "mgem".equalsIgnoreCase(name));
    }

    private String resolveGemId(Object config, String argument) {
        String gemId = argument == null ? "" : argument.trim();
        try {
            Object fromConfig = getConfigString.invoke(config, new String[]{"id", "gem", "g"}, gemId, new String[0]);
            if (fromConfig != null && !String.valueOf(fromConfig).isBlank()) {
                gemId = String.valueOf(fromConfig).trim();
            }
        } catch (ReflectiveOperationException ignored) {
            // 使用 argument 作为宝石 id
        }
        return gemId;
    }

    /**
     * 用动态代理实现 MythicMobs 的 IItemDrop 接口，避免编译期依赖。
     */
    private Object createDropProxy(String gemId) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getDrop".equals(name) && args != null && args.length >= 2) {
                double amount = args[1] instanceof Number number ? number.doubleValue() : 1.0;
                ItemStack item = buildGem(gemId, amount);
                return adaptItem.invoke(null, item);
            }
            if ("toString".equals(name)) {
                return "MosaicGemDrop(" + gemId + ")";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            return null;
        };
        return Proxy.newProxyInstance(iItemDropClass.getClassLoader(), new Class<?>[]{iItemDropClass}, handler);
    }

    private ItemStack buildGem(String gemId, double amount) {
        GemDefinition definition = configs.getGem(gemId);
        ItemStack item;
        if (definition == null) {
            item = new ItemStack(Material.PAPER);
            item.editMeta(meta -> meta.setDisplayName(ItemFactory.colorize("&c宝石配置缺失: " + gemId)));
        } else {
            item = factory.buildGem(definition, factory.rollRandom(definition));
        }
        item.setAmount(Math.max(1, (int) Math.round(amount)));
        return item;
    }
}
