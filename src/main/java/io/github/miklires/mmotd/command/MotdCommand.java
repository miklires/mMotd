package io.github.miklires.mmotd.command;

import io.github.miklires.mmotd.MMotd;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.*;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public final class MotdCommand implements CommandExecutor, TabCompleter {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final MMotd plugin;
    public MotdCommand(MMotd plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) { send(sender, "usage"); return true; }
        String sub = args[0].toLowerCase();
        String permission = "mmotd.command." + sub;
        if (!sender.hasPermission(permission)) { send(sender, "no-permission"); return true; }
        try {
            switch (sub) {
                case "reload" -> { plugin.manager().reload(); plugin.messages().reload(); send(sender, "reloaded"); }
                case "preview" -> { send(sender, "preview-header"); var motd = plugin.manager().pick(); sender.sendMessage(motd.line1()); sender.sendMessage(motd.line2()); }
                case "maintenance" -> { if (args.length != 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) { send(sender, "usage"); break; } boolean enabled = args[1].equalsIgnoreCase("on"); plugin.manager().setMaintenance(enabled); send(sender, "maintenance", "state", enabled ? "on" : "off"); }
                default -> send(sender, "usage");
            }
        } catch (Exception e) { send(sender, "reload-error", "error", e.getMessage() == null ? "unknown" : e.getMessage()); }
        return true;
    }
    private void send(CommandSender sender, String key, String... values) { sender.sendMessage(MM.deserialize(plugin.messages().prefixed(key, values))); }
    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) return List.of("reload", "preview", "maintenance").stream().filter(v -> sender.hasPermission("mmotd.command." + v) && v.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("maintenance")) return List.of("on", "off").stream().filter(v -> v.startsWith(args[1].toLowerCase())).toList();
        return List.of();
    }
}
