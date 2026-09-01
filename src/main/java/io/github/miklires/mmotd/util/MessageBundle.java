package io.github.miklires.mmotd.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class MessageBundle {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private volatile YamlConfiguration messages;

    public MessageBundle(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        String locale = plugin.getConfig().getString("language", "en_US");
        if (locale == null || !locale.matches("[a-z]{2}_[A-Z]{2}")) {
            plugin.getLogger().warning("Invalid language; using en_US.");
            locale = "en_US";
        }
        String path = "lang/" + locale + ".yml";
        if (plugin.getResource(path) == null) {
            plugin.getLogger().warning("Unsupported language " + locale + "; using en_US.");
            path = "lang/en_US.yml";
        }
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) plugin.saveResource(path, false);
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (var stream = plugin.getResource(path)) {
            if (stream == null) throw new IllegalStateException("Bundled language is missing: " + path);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            loaded.setDefaults(defaults);
            loaded.options().copyDefaults(true);
            loaded.save(file);
        } catch (Exception error) {
            throw new IllegalStateException("Could not load language file", error);
        }
        for (String key : loaded.getKeys(true)) {
            if (loaded.isString(key)) MM.deserialize(loaded.getString(key, key));
        }
        messages = loaded;
    }

    public Component prefixed(String key, Map<String, String> values) {
        return MM.deserialize(messages.getString("prefix", "") + messages.getString(key, key), resolver(values));
    }

    private static TagResolver resolver(Map<String, String> values) {
        TagResolver.Builder builder = TagResolver.builder();
        values.forEach((key, value) -> builder.resolver(Placeholder.unparsed(key, value == null ? "" : value)));
        return builder.build();
    }
}
