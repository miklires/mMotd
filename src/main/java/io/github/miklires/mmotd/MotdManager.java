package io.github.miklires.mmotd;

import io.github.miklires.mmotd.analytics.PingTracker;
import io.github.miklires.mmotd.analytics.PingTracker.Audience;
import io.github.miklires.mmotd.model.HostMatcher;
import io.github.miklires.mmotd.model.MotdTemplate;
import io.github.miklires.mmotd.model.ScheduleWindow;
import io.github.miklires.mmotd.util.Placeholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.CachedServerIcon;

import java.io.File;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class MotdManager {
    private static final int MAX_ENTRIES = 128;
    private static final int MAX_SCHEDULES = 128;
    private static final int MAX_HOVER_LINES = 64;
    private static final int MAX_TEXT_LENGTH = 1_024;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection().toBuilder().hexColors().build();

    private final MMotd plugin;
    private final PingTracker tracker = new PingTracker();
    private volatile Settings settings = Settings.empty();
    private volatile State state = State.empty();
    private int cachedUniquePlayers;
    private long nextUniqueRefreshMillis;
    private long nextAnalyticsCleanupMillis;

    public MotdManager(MMotd plugin) { this.plugin = plugin; }

    public synchronized LoadReport reload() throws Exception {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getInt("config-version", 0) != 2) {
            plugin.getLogger().warning("Unknown config-version; compatible keys will be loaded where possible.");
        }
        LoadResult result = load(cfg);
        State nextState = buildState(result.settings());
        tracker.configure(result.settings().analyticsEnabled(), result.settings().analyticsTtlMinutes(),
                result.settings().analyticsMaximumAddresses());
        settings = result.settings();
        state = nextState;
        return result.report();
    }

    private LoadResult load(FileConfiguration cfg) throws Exception {
        List<MotdTemplate> loaded = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int skipped = 0;
        List<Map<?, ?>> rawEntries = cfg.getMapList("motd.entries");
        if (rawEntries.size() > MAX_ENTRIES) throw new IllegalArgumentException("motd.entries exceeds " + MAX_ENTRIES);
        for (Map<?, ?> raw : rawEntries) {
            try {
                MotdTemplate template = loadTemplate(raw);
                if (!ids.add(template.id())) throw new IllegalArgumentException("duplicate id: " + template.id());
                validateTemplate(template);
                loaded.add(template);
            } catch (RuntimeException error) {
                skipped++;
                plugin.getLogger().warning("Skipping invalid MOTD entry: " + error.getMessage());
            }
        }
        if (loaded.isEmpty()) throw new IllegalArgumentException("No valid motd.entries were found");
        if (loaded.stream().noneMatch(template -> template.hosts().isEmpty() && template.audience() == Audience.ANY
                && template.minimumProtocol() < 0 && template.maximumProtocol() < 0)) {
            throw new IllegalArgumentException("A global ANY entry without protocol limits is required as fallback");
        }

        List<ScheduleWindow> windows = new ArrayList<>();
        List<Map<?, ?>> rawSchedule = cfg.getMapList("motd.schedule");
        if (rawSchedule.size() > MAX_SCHEDULES) throw new IllegalArgumentException("motd.schedule exceeds " + MAX_SCHEDULES);
        for (Map<?, ?> raw : rawSchedule) {
            try {
                List<String> days = strings(raw.get("days"), 7, 16);
                String entryId = bounded(raw.get("entry"), "", 64);
                if (!ids.contains(entryId)) throw new IllegalArgumentException("unknown entry: " + entryId);
                windows.add(ScheduleWindow.parse(days, bounded(raw.get("time"), "00:00-00:00", 32),
                        entryId, integer(raw.get("priority"), 0, -10_000, 10_000)));
            } catch (RuntimeException error) {
                skipped++;
                plugin.getLogger().warning("Skipping invalid schedule entry: " + error.getMessage());
            }
        }
        windows.sort(Comparator.comparingInt(ScheduleWindow::priority).reversed());

        List<String> hoverLines = cfg.getStringList("hover.lines");
        if (hoverLines.size() > MAX_HOVER_LINES) throw new IllegalArgumentException("hover.lines exceeds " + MAX_HOVER_LINES);
        hoverLines = hoverLines.stream().map(line -> requireText(line, "hover line")).toList();

        String zoneName = cfg.getString("time-zone", "system");
        ZoneId zone = zoneName == null || zoneName.equalsIgnoreCase("system") ? ZoneId.systemDefault() : ZoneId.of(zoneName);
        Instant countdownTarget = parseTarget(cfg.getString("countdown.target", ""));
        MotdTemplate maintenance = specialTemplate("maintenance", cfg, "maintenance");
        MotdTemplate whitelist = specialTemplate("whitelist", cfg, "whitelist-motd");
        List<String> whitelistIps = cfg.getStringList("maintenance.ip-whitelist").stream()
                .map(ip -> ip.trim().toLowerCase(Locale.ROOT)).toList();
        if (whitelistIps.size() > 1_024 || whitelistIps.stream().anyMatch(ip -> !validAddress(ip))) {
            throw new IllegalArgumentException("maintenance.ip-whitelist contains an invalid address");
        }

        String versionLabel = requireText(cfg.getString("version.label", ""), "version.label");
        MM.deserialize(versionLabel);
        String kick = requireText(cfg.getString("maintenance.kick", "<red>Maintenance"), "maintenance.kick");
        Component kickComponent = MM.deserialize(kick);

        Settings next = new Settings(List.copyOf(loaded), List.copyOf(windows),
                cfg.getBoolean("maintenance.enabled", false), maintenance, Set.copyOf(whitelistIps), kickComponent,
                cfg.getBoolean("whitelist-motd.enabled", true), whitelist,
                cfg.getBoolean("hover.enabled", true), List.copyOf(hoverLines),
                parsePlayerMode(cfg.getString("players.mode", "actual")),
                clamp(cfg.getInt("players.offset", 0), -1_000_000, 1_000_000),
                clamp(cfg.getInt("players.fixed-online", 0), 0, 1_000_000),
                clamp(cfg.getInt("players.fixed-max", 100), 0, 1_000_000),
                cfg.getBoolean("version.override", false), LEGACY.serialize(MM.deserialize(versionLabel)),
                clamp(cfg.getInt("version.protocol", -1), -1, 10_000), zone, countdownTarget,
                clamp(cfg.getInt("cache.unique-player-refresh-seconds", 60), 10, 3_600),
                cfg.getBoolean("analytics.enabled", false),
                clamp(cfg.getLong("analytics.address-ttl-minutes", 1_440), 1, 10_080),
                clamp(cfg.getInt("analytics.max-unique-addresses", 10_000), 100, 100_000));
        return new LoadResult(next, new LoadReport(loaded.size(), windows.size(), skipped));
    }

    private MotdTemplate loadTemplate(Map<?, ?> raw) {
        String id = bounded(raw.get("id"), "", 64).toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new IllegalArgumentException("invalid id: " + id);
        Map<?, ?> protocol = raw.get("protocol") instanceof Map<?, ?> map ? map : Map.of();
        int minimumProtocol = integer(protocol.get("min"), -1, -1, 10_000);
        int maximumProtocol = integer(protocol.get("max"), -1, -1, 10_000);
        if (minimumProtocol >= 0 && maximumProtocol >= 0 && minimumProtocol > maximumProtocol) {
            throw new IllegalArgumentException("protocol.min is greater than protocol.max");
        }
        List<String> hosts = strings(raw.get("hosts"), 32, 253).stream().map(HostMatcher::normalize).toList();
        hosts.forEach(HostMatcher::validate);
        Audience audience;
        try { audience = Audience.valueOf(bounded(raw.get("audience"), "ANY", 16).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("invalid audience"); }
        return new MotdTemplate(id, requireText(Objects.toString(raw.get("line1"), ""), "line1"),
                requireText(Objects.toString(raw.get("line2"), ""), "line2"),
                requireText(Objects.toString(raw.get("text"), ""), "text"),
                loadIcon(bounded(raw.get("icon"), "", 255)), hosts, minimumProtocol, maximumProtocol,
                integer(raw.get("weight"), 1, 1, 10_000), audience);
    }

    private MotdTemplate specialTemplate(String id, FileConfiguration cfg, String path) throws Exception {
        return new MotdTemplate(id, requireText(cfg.getString(path + ".line1", ""), path + ".line1"),
                requireText(cfg.getString(path + ".line2", ""), path + ".line2"), "",
                loadIcon(requireText(cfg.getString(path + ".icon", ""), path + ".icon")));
    }

    private CachedServerIcon loadIcon(String iconName) {
        if (iconName.isBlank()) return null;
        Path dataRoot = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path iconPath = dataRoot.resolve(iconName).normalize();
        if (!iconPath.startsWith(dataRoot)) {
            plugin.getLogger().warning("Ignored icon path outside the mMotd data folder: " + iconName);
            return null;
        }
        File iconFile = iconPath.toFile();
        if (!iconFile.isFile()) {
            plugin.getLogger().warning("Icon does not exist: " + iconName);
            return null;
        }
        try {
            Path realRoot = dataRoot.toRealPath();
            Path realIcon = iconPath.toRealPath();
            if (!realIcon.startsWith(realRoot)) {
                plugin.getLogger().warning("Ignored icon symlink outside the mMotd data folder: " + iconName);
                return null;
            }
            return plugin.getServer().loadServerIcon(realIcon.toFile());
        }
        catch (Exception error) {
            plugin.getLogger().warning("Invalid icon " + iconName + ": " + error.getMessage());
            return null;
        }
    }

    private void validateTemplate(MotdTemplate template) {
        Map<String, String> sample = sampleValues();
        render(template, sample);
    }

    public void refresh() {
        try { state = buildState(settings); }
        catch (RuntimeException error) { plugin.getLogger().severe("Could not refresh MOTD cache: " + error.getMessage()); }
    }

    private State buildState(Settings cfg) {
        long nowMillis = System.currentTimeMillis();
        if (nowMillis >= nextUniqueRefreshMillis) {
            cachedUniquePlayers = plugin.getServer().getOfflinePlayers().length;
            nextUniqueRefreshMillis = nowMillis + cfg.uniqueRefreshSeconds() * 1_000L;
        }
        if (nowMillis >= nextAnalyticsCleanupMillis) {
            tracker.cleanup(nowMillis);
            nextAnalyticsCleanupMillis = nowMillis + 60_000L;
        }
        ZonedDateTime now = ZonedDateTime.now(cfg.zone());
        int actualOnline = plugin.getServer().getOnlinePlayers().size();
        int actualMax = plugin.getServer().getMaxPlayers();
        boolean hidden = cfg.playerMode().equals("hidden");
        int online = switch (cfg.playerMode()) {
            case "fixed" -> cfg.fixedOnline();
            case "offset" -> Math.max(0, actualOnline + cfg.playerOffset());
            default -> actualOnline;
        };
        int max = cfg.playerMode().equals("fixed") ? Math.max(online, cfg.fixedMax()) : actualMax;
        double[] ticks = plugin.getServer().getTPS();
        String tps = String.format(Locale.ROOT, "%.1f", Math.min(20.0, ticks.length == 0 ? 20.0 : ticks[0]));
        Map<String, String> common = new HashMap<>();
        common.put("online", Integer.toString(online));
        common.put("max", Integer.toString(max));
        common.put("tps", tps);
        common.put("version", plugin.getServer().getMinecraftVersion());
        common.put("time", now.format(DateTimeFormatter.ofPattern("HH:mm")));
        common.put("date", now.format(DateTimeFormatter.ISO_LOCAL_DATE));
        common.put("unique_players", Integer.toString(cachedUniquePlayers));
        addCountdown(common, cfg.countdownTarget(), now.toInstant());

        List<MotdTemplate> selected;
        if (cfg.maintenance()) selected = List.of(cfg.maintenanceTemplate());
        else if (cfg.whitelistMotdEnabled() && plugin.getServer().hasWhitelist()) selected = List.of(cfg.whitelistTemplate());
        else selected = scheduledTemplates(cfg, now.toLocalDateTime());
        List<RenderedMotd> rendered = selected.stream().map(template -> render(template, common)).toList();
        if (rendered.isEmpty()) throw new IllegalStateException("rendered MOTD cache is empty");

        List<Component> hover = new ArrayList<>();
        if (cfg.hoverEnabled()) {
            for (String line : cfg.hoverLines()) hover.add(MM.deserialize(Placeholders.apply(line, common)));
        }
        return new State(rendered, List.copyOf(hover), hidden, online, max, cfg.overrideVersion(),
                cfg.versionLabel(), cfg.protocol(), cfg.maintenance(), cfg.kickMessage());
    }

    private List<MotdTemplate> scheduledTemplates(Settings cfg, LocalDateTime now) {
        for (ScheduleWindow window : cfg.schedule()) {
            if (window.matches(now)) {
                for (MotdTemplate template : cfg.templates()) if (template.id().equals(window.entryId())) return List.of(template);
            }
        }
        return cfg.templates();
    }

    private RenderedMotd render(MotdTemplate template, Map<String, String> common) {
        Map<String, String> values = new HashMap<>(common);
        values.put("motd_line", template.text());
        return new RenderedMotd(template.id(), MM.deserialize(Placeholders.apply(template.line1(), values)),
                MM.deserialize(Placeholders.apply(template.line2(), values)), template.icon(), template.hosts(),
                template.minimumProtocol(), template.maximumProtocol(), template.weight(), template.audience());
    }

    public RenderedMotd pick(String hostname, int clientProtocol, Audience audience) {
        List<RenderedMotd> entries = state.entries();
        String host = HostMatcher.normalize(hostname);
        boolean specificHost = entries.stream().anyMatch(entry -> !entry.hosts().isEmpty()
                && matches(entry, host, clientProtocol, audience, false, false));
        boolean specificAudience = audience != Audience.ANY && entries.stream().anyMatch(entry ->
                matches(entry, host, clientProtocol, audience, specificHost, false) && entry.audience() == audience);
        int total = 0;
        for (RenderedMotd entry : entries) if (matches(entry, host, clientProtocol, audience, specificHost, specificAudience)) total += entry.weight();
        if (total <= 0) return entries.getFirst();
        int selected = ThreadLocalRandom.current().nextInt(total);
        for (RenderedMotd entry : entries) {
            if (!matches(entry, host, clientProtocol, audience, specificHost, specificAudience)) continue;
            selected -= entry.weight();
            if (selected < 0) return entry;
        }
        return entries.getFirst();
    }

    private boolean matches(RenderedMotd entry, String host, int protocol, Audience audience,
                            boolean requireSpecificHost, boolean requireSpecificAudience) {
        if (requireSpecificHost && entry.hosts().isEmpty()) return false;
        if (!HostMatcher.matches(entry.hosts(), host)) return false;
        if (protocol >= 0 && entry.minimumProtocol() >= 0 && protocol < entry.minimumProtocol()) return false;
        if (protocol >= 0 && entry.maximumProtocol() >= 0 && protocol > entry.maximumProtocol()) return false;
        if (requireSpecificAudience) return entry.audience() == audience;
        return entry.audience() == Audience.ANY || entry.audience() == audience;
    }

    public void requestRefresh() { plugin.getServer().getGlobalRegionScheduler().execute(plugin, this::refresh); }
    public Audience recordPing(InetAddress address) { return tracker.recordPing(address, System.currentTimeMillis()); }
    public void recordJoin(InetAddress address) { tracker.recordJoin(address, System.currentTimeMillis()); }
    public PingTracker.Snapshot statistics() { return tracker.snapshot(); }
    public State state() { return state; }
    public boolean isWhitelisted(String ip) { return settings.maintenanceWhitelist().contains(ip); }
    public Component kickMessage() { return state.kickMessage(); }

    public synchronized LoadReport setMaintenance(boolean enabled) throws Exception {
        plugin.getConfig().set("maintenance.enabled", enabled);
        plugin.saveConfig();
        return reload();
    }

    private static void addCountdown(Map<String, String> values, Instant target, Instant now) {
        long seconds = target == null ? 0 : Math.max(0, Duration.between(now, target).getSeconds());
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        long remainder = seconds % 60;
        values.put("countdown_days", Long.toString(days));
        values.put("countdown_hours", Long.toString(hours));
        values.put("countdown_minutes", Long.toString(minutes));
        values.put("countdown_seconds", Long.toString(remainder));
        values.put("countdown", days > 0 ? String.format(Locale.ROOT, "%dd %02d:%02d:%02d", days, hours, minutes, remainder)
                : String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder));
    }

    private static Instant parseTarget(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return OffsetDateTime.parse(raw.trim()).toInstant(); }
        catch (RuntimeException error) { throw new IllegalArgumentException("countdown.target must be ISO-8601 with an offset"); }
    }

    private static String parsePlayerMode(String raw) {
        String value = raw == null ? "actual" : raw.toLowerCase(Locale.ROOT);
        if (!Set.of("actual", "offset", "fixed", "hidden").contains(value)) throw new IllegalArgumentException("invalid players.mode");
        return value;
    }

    private static boolean validAddress(String value) {
        if (value.isEmpty() || value.length() > 64 || value.contains("/") || value.contains("\\")) return false;
        if (value.contains(":")) return value.matches("[0-9a-f:]+") && value.chars().filter(ch -> ch == ':').count() >= 2;
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) return false;
            if (Integer.parseInt(part) > 255) return false;
        }
        return true;
    }

    private static String bounded(Object value, String fallback, int maximum) {
        String result = Objects.toString(value, fallback).trim();
        if (result.length() > maximum || result.indexOf('\0') >= 0) throw new IllegalArgumentException("text exceeds safe limits");
        return result;
    }

    private static String requireText(String value, String name) {
        String result = value == null ? "" : value;
        if (result.length() > MAX_TEXT_LENGTH || result.indexOf('\0') >= 0) throw new IllegalArgumentException(name + " exceeds safe limits");
        return result;
    }

    private static List<String> strings(Object value, int maximumItems, int maximumLength) {
        if (value == null) return List.of();
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) result.add(bounded(item, "", maximumLength));
        } else result.add(bounded(value, "", maximumLength));
        if (result.size() > maximumItems) throw new IllegalArgumentException("list exceeds safe limits");
        return List.copyOf(result);
    }

    private static int integer(Object value, int fallback, int minimum, int maximum) {
        if (value == null) return fallback;
        try { return clamp(Integer.parseInt(value.toString()), minimum, maximum); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("invalid integer: " + value); }
    }

    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
    private static long clamp(long value, long minimum, long maximum) { return Math.max(minimum, Math.min(maximum, value)); }

    private static Map<String, String> sampleValues() {
        return Map.ofEntries(Map.entry("online", "0"), Map.entry("max", "100"), Map.entry("tps", "20.0"),
                Map.entry("version", "26.2"), Map.entry("time", "12:00"), Map.entry("date", "2026-09-01"),
                Map.entry("unique_players", "0"), Map.entry("countdown", "00:00:00"),
                Map.entry("countdown_days", "0"), Map.entry("countdown_hours", "0"),
                Map.entry("countdown_minutes", "0"), Map.entry("countdown_seconds", "0"));
    }

    public record LoadReport(int entries, int schedules, int skipped) {}
    private record LoadResult(Settings settings, LoadReport report) {}
    public record RenderedMotd(String id, Component line1, Component line2, CachedServerIcon icon,
                               List<String> hosts, int minimumProtocol, int maximumProtocol,
                               int weight, Audience audience) {}
    public record State(List<RenderedMotd> entries, List<Component> hover, boolean hidePlayers, int online, int max,
                        boolean overrideVersion, String versionLabel, int protocol, boolean maintenance,
                        Component kickMessage) {
        static State empty() {
            return new State(List.of(new RenderedMotd("fallback", Component.text("Server"), Component.empty(), null,
                    List.of(), -1, -1, 1, Audience.ANY)), List.of(), false, 0, 0,
                    false, "", -1, false, Component.text("Maintenance"));
        }
    }

    private record Settings(List<MotdTemplate> templates, List<ScheduleWindow> schedule, boolean maintenance,
                            MotdTemplate maintenanceTemplate, Set<String> maintenanceWhitelist, Component kickMessage,
                            boolean whitelistMotdEnabled, MotdTemplate whitelistTemplate,
                            boolean hoverEnabled, List<String> hoverLines, String playerMode, int playerOffset,
                            int fixedOnline, int fixedMax, boolean overrideVersion, String versionLabel, int protocol,
                            ZoneId zone, Instant countdownTarget, int uniqueRefreshSeconds,
                            boolean analyticsEnabled, long analyticsTtlMinutes, int analyticsMaximumAddresses) {
        static Settings empty() {
            MotdTemplate fallback = new MotdTemplate("fallback", "<aqua>Server", "<gray>Welcome", "", null);
            return new Settings(List.of(fallback), List.of(), false, fallback, Set.of(), Component.text("Maintenance"),
                    false, fallback, false, List.of(), "actual", 0, 0, 100, false, "", -1,
                    ZoneId.systemDefault(), null, 60, false, 1_440, 10_000);
        }
    }
}
