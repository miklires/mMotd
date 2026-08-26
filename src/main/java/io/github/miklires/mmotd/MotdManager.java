package io.github.miklires.mmotd;

import io.github.miklires.mmotd.model.MotdTemplate;
import io.github.miklires.mmotd.model.ScheduleWindow;
import io.github.miklires.mmotd.util.Placeholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.CachedServerIcon;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class MotdManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final MMotd plugin;
    private volatile State state = State.empty();
    private Map<String, MotdTemplate> templates = Map.of();
    private List<ScheduleWindow> schedule = List.of();

    public MotdManager(MMotd plugin) { this.plugin = plugin; }

    public void reload() throws Exception {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getInt("config-version", 0) != 1) plugin.getLogger().warning("Unknown config-version; loading compatible keys only.");
        Map<String, MotdTemplate> loaded = new LinkedHashMap<>();
        for (Map<?, ?> raw : cfg.getMapList("motd.entries")) {
            String id = Objects.toString(raw.get("id"), "").trim();
            if (id.isEmpty()) continue;
            CachedServerIcon icon = null;
            String iconName = Objects.toString(raw.get("icon"), "").trim();
            if (!iconName.isEmpty()) {
                File iconFile = new File(plugin.getDataFolder(), iconName);
                if (iconFile.isFile()) try { icon = plugin.getServer().loadServerIcon(iconFile); }
                catch (Exception e) { plugin.getLogger().warning("Invalid icon " + iconName + ": " + e.getMessage()); }
            }
            loaded.put(id, new MotdTemplate(id, Objects.toString(raw.get("line1"), ""), Objects.toString(raw.get("line2"), ""), Objects.toString(raw.get("text"), ""), icon));
        }
        if (loaded.isEmpty()) loaded.put("fallback", new MotdTemplate("fallback", "<aqua>Server", "<gray>Welcome", "Welcome", null));
        List<ScheduleWindow> windows = new ArrayList<>();
        for (Map<?, ?> raw : cfg.getMapList("motd.schedule")) try {
            Object dayValue = raw.get("days");
            List<String> days = dayValue instanceof Iterable<?> it ? streamStrings(it) : List.of();
            windows.add(ScheduleWindow.parse(days, Objects.toString(raw.get("time"), "00:00-00:00"), Objects.toString(raw.get("entry"), "")));
        } catch (RuntimeException e) { plugin.getLogger().warning("Skipping invalid schedule entry: " + e.getMessage()); }
        templates = Map.copyOf(loaded);
        schedule = List.copyOf(windows);
        requestRefresh();
    }

    private static List<String> streamStrings(Iterable<?> values) { List<String> out = new ArrayList<>(); for (Object value : values) out.add(String.valueOf(value)); return out; }

    public void refresh() {
        FileConfiguration cfg = plugin.getConfig();
        LocalDateTime now = LocalDateTime.now();
        int actualOnline = plugin.getServer().getOnlinePlayers().size();
        int actualMax = plugin.getServer().getMaxPlayers();
        String mode = cfg.getString("players.mode", "actual").toLowerCase(Locale.ROOT);
        boolean hidden = mode.equals("hidden");
        int online = switch (mode) { case "fixed" -> Math.max(0, cfg.getInt("players.fixed-online", 0)); case "offset" -> Math.max(0, actualOnline + cfg.getInt("players.offset", 0)); default -> actualOnline; };
        int max = mode.equals("fixed") ? Math.max(online, cfg.getInt("players.fixed-max", actualMax)) : actualMax;
        double[] ticks = plugin.getServer().getTPS();
        String tps = String.format(Locale.ROOT, "%.1f", Math.min(20.0, ticks.length == 0 ? 20.0 : ticks[0]));
        Map<String, String> common = new HashMap<>();
        common.put("online", Integer.toString(online)); common.put("max", Integer.toString(max)); common.put("tps", tps);
        common.put("version", plugin.getServer().getMinecraftVersion()); common.put("time", now.format(DateTimeFormatter.ofPattern("HH:mm")));
        common.put("date", now.format(DateTimeFormatter.ISO_LOCAL_DATE)); common.put("unique_players", Integer.toString(plugin.getServer().getOfflinePlayers().length));
        List<MotdTemplate> selected = scheduledTemplates(now);
        List<RenderedMotd> rendered = new ArrayList<>();
        boolean maintenance = cfg.getBoolean("maintenance.enabled", false);
        if (maintenance) {
            rendered.add(render(cfg.getString("maintenance.line1", "<red>Maintenance"), cfg.getString("maintenance.line2", ""), "", null, common));
        } else for (MotdTemplate template : selected) rendered.add(render(template.line1(), template.line2(), template.text(), template.icon(), common));
        List<Component> hover = new ArrayList<>();
        if (cfg.getBoolean("hover.enabled", true)) for (String line : cfg.getStringList("hover.lines")) hover.add(MM.deserialize(Placeholders.apply(line, common)));
        state = new State(List.copyOf(rendered), List.copyOf(hover), hidden, online, max, cfg.getBoolean("version.override", false), cfg.getString("version.label", ""), cfg.getInt("version.protocol", -1), maintenance);
    }
    public void requestRefresh() { plugin.getServer().getGlobalRegionScheduler().execute(plugin, this::refresh); }

    private List<MotdTemplate> scheduledTemplates(LocalDateTime now) {
        for (ScheduleWindow window : schedule) if (window.matches(now)) { MotdTemplate found = templates.get(window.entryId()); if (found != null) return List.of(found); }
        return List.copyOf(templates.values());
    }
    private RenderedMotd render(String l1, String l2, String text, CachedServerIcon icon, Map<String,String> common) {
        Map<String,String> values = new HashMap<>(common); values.put("motd_line", text);
        return new RenderedMotd(MM.deserialize(Placeholders.apply(l1, values)), MM.deserialize(Placeholders.apply(l2, values)), icon);
    }
    public State state() { return state; }
    public RenderedMotd pick() { List<RenderedMotd> list = state.entries(); return list.get(ThreadLocalRandom.current().nextInt(list.size())); }
    public boolean isWhitelisted(String ip) { return plugin.getConfig().getStringList("maintenance.ip-whitelist").contains(ip); }
    public Component kickMessage() { return MM.deserialize(plugin.getConfig().getString("maintenance.kick", "<red>Maintenance")); }
    public void setMaintenance(boolean enabled) { plugin.getConfig().set("maintenance.enabled", enabled); plugin.saveConfig(); requestRefresh(); }
    public record RenderedMotd(Component line1, Component line2, CachedServerIcon icon) {}
    public record State(List<RenderedMotd> entries, List<Component> hover, boolean hidePlayers, int online, int max, boolean overrideVersion, String versionLabel, int protocol, boolean maintenance) {
        static State empty() { return new State(List.of(new RenderedMotd(Component.text("Server"), Component.empty(), null)), List.of(), false, 0, 0, false, "", -1, false); }
    }
}
