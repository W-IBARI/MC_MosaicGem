package com.mosaicgem.plugin.command;

import com.mosaicgem.plugin.MosaicGemPlugin;
import com.mosaicgem.plugin.config.ConfigManager;
import com.mosaicgem.plugin.config.GemDefinition;
import com.mosaicgem.plugin.config.ItemDefinition;
import com.mosaicgem.plugin.config.PuncherDefinition;
import com.mosaicgem.plugin.config.RemoverDefinition;
import com.mosaicgem.plugin.model.SocketData;
import com.mosaicgem.plugin.model.SocketedGem;
import com.mosaicgem.plugin.model.ToolType;
import com.mosaicgem.plugin.util.ItemFactory;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /mosaicgem 指令：reload / give / debug / list
 */
public class MosaicGemCommand implements CommandExecutor, TabCompleter {

    private final MosaicGemPlugin plugin;
    private final ConfigManager configs;
    private final ItemFactory factory;

    public MosaicGemCommand(MosaicGemPlugin plugin, ConfigManager configs, ItemFactory factory) {
        this.plugin = plugin;
        this.configs = configs;
        this.factory = factory;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "give" -> give(sender, args);
            case "debug" -> debug(sender, args);
            case "list" -> list(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    // ------------------------------------------------------------------
    // 子指令
    // ------------------------------------------------------------------

    private boolean reload(CommandSender sender) {
        if (!hasPermission(sender, "mosaicgem.reload")) {
            return true;
        }
        plugin.reloadConfigs();
        send(sender, configs.message("reload-success"));
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "mosaicgem.give")) {
            return true;
        }
        if (args.length < 3) {
            send(sender, configs.message("give-usage"));
            return true;
        }
        ToolType type = ToolType.fromString(args[1]);
        if (type == null) {
            send(sender, configs.message("give-usage"));
            return true;
        }
        String id = args[2];
        ItemDefinition definition = configs.find(type, id);
        if (definition == null) {
            send(sender, configs.message("give-not-found")
                    .replace("{type}", type.name().toLowerCase(Locale.ROOT))
                    .replace("{id}", id));
            return true;
        }
        int amount = args.length >= 4 ? parseAmount(args[3]) : 1;
        Player target = null;
        if (args.length >= 5) {
            target = Bukkit.getPlayerExact(args[4]);
        } else if (sender instanceof Player player) {
            target = player;
        }
        if (target == null) {
            send(sender, configs.message("player-not-found").replace("{player}", args.length >= 5 ? args[4] : "?"));
            return true;
        }

        ItemStack item = switch (type) {
            case GEM -> factory.buildGem((GemDefinition) definition, factory.rollRandom((GemDefinition) definition));
            case PUNCHER -> factory.buildPuncher((PuncherDefinition) definition);
            case REMOVER -> factory.buildRemover((RemoverDefinition) definition);
        };
        item.setAmount(Math.max(1, amount));

        Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            target.getWorld().dropItem(target.getLocation(), rest);
        }
        send(sender, configs.message("give-success")
                .replace("{player}", target.getName())
                .replace("{amount}", String.valueOf(item.getAmount()))
                .replace("{id}", id));
        return true;
    }

    private boolean debug(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "mosaicgem.debug")) {
            return true;
        }
        Player target = null;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        }
        if (target == null) {
            send(sender, configs.message("debug-usage"));
            return true;
        }
        ItemStack item = target.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            send(sender, "&c" + target.getName() + " 主手没有物品");
            return true;
        }

        List<String> lines = new ArrayList<>();
        lines.add("&7===== MosaicGem Debug =====");
        lines.add("&f持有者: &e" + target.getName());
        lines.add("&f物品: &e" + item.getType().name());
        lines.add("&f数量: &e" + item.getAmount());
        if (item.hasItemMeta()) {
            var meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                lines.add("&f显示名: &e" + meta.getDisplayName());
            }
            if (meta.hasCustomModelData()) {
                lines.add("&fCustomModelData: &e" + meta.getCustomModelData());
            }
            if (meta.hasEnchantmentGlintOverride()) {
                lines.add("&f附魔光效: &e" + meta.getEnchantmentGlintOverride());
            }
            if (meta.hasLore()) {
                lines.add("&fLore:");
                for (String lore : meta.getLore()) {
                    lines.add("  &7" + lore);
                }
            }
        }

        ToolType toolType = factory.getToolType(item);
        if (toolType != null) {
            lines.add("&f插件物品: &e" + toolType.name().toLowerCase(Locale.ROOT) + " &7(" + factory.getToolId(item) + ")");
            Map<String, String> values = factory.readValues(item);
            if (!values.isEmpty()) {
                lines.add("&f随机数:");
                values.forEach((name, value) -> lines.add("  &7" + name + " = &e" + value));
            }
        }

        SocketData socketData = factory.readSocketData(item);
        lines.add("&f孔数: &e" + socketData.holes() + " &7/ 已镶嵌: &e" + socketData.gems().size());
        if (!socketData.gems().isEmpty()) {
            lines.add("&f已镶嵌宝石:");
            for (SocketedGem gem : socketData.gems()) {
                lines.add("  &7- &e" + gem.id() + " &7(" + gem.instanceId().substring(0, 8) + ")");
                gem.values().forEach((name, value) -> lines.add("      &7" + name + " = &e" + value));
                for (String line : gem.lines()) {
                    lines.add("      &7注入: " + line);
                }
            }
        }

        PersistentDataContainerView pdc = item.getPersistentDataContainer();
        lines.add("&f组件键: " + pdc.getKeys().stream().map(key -> key.getKey()).toList());
        lines.add("&7===== Debug End =====");
        for (String line : lines) {
            sender.sendMessage(ItemFactory.colorize(line));
        }
        return true;
    }

    private boolean list(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "mosaicgem.list")) {
            return true;
        }
        if (args.length < 2) {
            send(sender, configs.message("list-usage"));
            return true;
        }
        ToolType type = ToolType.fromString(args[1]);
        if (type == null) {
            send(sender, configs.message("list-usage"));
            return true;
        }
        List<String> ids = switch (type) {
            case GEM -> new ArrayList<>(configs.getGems().keySet());
            case PUNCHER -> new ArrayList<>(configs.getPunchers().keySet());
            case REMOVER -> new ArrayList<>(configs.getRemovers().keySet());
        };
        send(sender, "&a" + type.name().toLowerCase(Locale.ROOT) + " (" + ids.size() + "): &f" + String.join(", ", ids));
        return true;
    }

    // ------------------------------------------------------------------
    // Tab 补全
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.addAll(List.of("reload", "give", "debug", "list"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            result.addAll(List.of("gem", "puncher", "remover"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            ToolType type = ToolType.fromString(args[1]);
            if (type != null) {
                result.addAll(switch (type) {
                    case GEM -> configs.getGems().keySet();
                    case PUNCHER -> configs.getPunchers().keySet();
                    case REMOVER -> configs.getRemovers().keySet();
                });
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                result.add(player.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            result.addAll(List.of("gem", "puncher", "remover"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                result.add(player.getName());
            }
        }
        return result.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        send(sender, configs.message("no-permission"));
        return false;
    }

    private int parseAmount(String text) {
        try {
            return Math.max(1, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ItemFactory.colorize(
                "&8[&6MosaicGem&8] &f指令帮助\n"
                        + "&7/mosaicgem reload &8- &f重载配置\n"
                        + "&7/mosaicgem give <gem|puncher|remover> <id> [数量] [玩家] &8- &f给予物品\n"
                        + "&7/mosaicgem debug [玩家] &8- &f查看物品调试信息\n"
                        + "&7/mosaicgem list <gem|puncher|remover> &8- &f查看已配置物品"));
    }

    private void send(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(ItemFactory.colorize(configs.prefix() + message));
    }
}
