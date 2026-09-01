package io.github.miklires.mmotd.command;

import io.github.miklires.mmotd.MMotd;
import io.github.miklires.mmotd.analytics.PingTracker.Audience;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MotdCommand implements CommandExecutor, TabCompleter {
    private final MMotd plugin;
    public MotdCommand(MMotd plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length == 0) { send(sender, "usage", Map.of()); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("mmotd.command." + sub)) { send(sender, "no-permission", Map.of()); return true; }
        try {
            switch (sub) {
                case "reload" -> {
                    var report = plugin.manager().reload();
                    plugin.messages().reload();
                    send(sender, "reloaded", Map.of("entries", Integer.toString(report.entries()),
                            "schedules", Integer.toString(report.schedules()), "skipped", Integer.toString(report.skipped())));
                }
                case "preview" -> {
                    send(sender, "preview-header", Map.of());
                    var motd = plugin.manager().pick("", -1, Audience.ANY);
                    sender.sendMessage(motd.line1());
                    sender.sendMessage(motd.line2());
                }
                case "maintenance" -> maintenance(sender, args);
                case "stats" -> {
                    var stats = plugin.manager().statistics();
                    send(sender, stats.enabled() ? "stats" : "stats-disabled", Map.of(
                            "pings", Long.toString(stats.pings()), "unique", Long.toString(stats.uniquePings()),
                            "joins", Long.toString(stats.joinedAfterPing()), "active", Integer.toString(stats.activeAddresses()),
                            "conversion", String.format(Locale.ROOT, "%.1f", stats.conversionPercent())));
                }
                default -> send(sender, "usage", Map.of());
            }
        } catch (Exception error) {
            String message = error.getMessage() == null ? "unknown error" : error.getMessage();
            send(sender, "command-error", Map.of("error", message));
        }
        return true;
    }

    private void maintenance(CommandSender sender, String[] args) throws Exception {
        if (args.length != 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            send(sender, "usage", Map.of());
            return;
        }
        boolean enabled = args[1].equalsIgnoreCase("on");
        plugin.manager().setMaintenance(enabled);
        send(sender, enabled ? "maintenance-on" : "maintenance-off", Map.of());
    }

    private void send(CommandSender sender, String key, Map<String, String> values) {
        sender.sendMessage(plugin.messages().prefixed(key, values));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        if (args.length == 1) {
            List<String> choices = new ArrayList<>();
            for (String value : List.of("reload", "preview", "maintenance", "stats")) {
                if (sender.hasPermission("mmotd.command." + value)) choices.add(value);
            }
            return choices.stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("maintenance")) {
            return List.of("on", "off").stream().filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
