package io.github.miklires.mmotd.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public final class MessageBundle {
    private final JavaPlugin plugin;
    private YamlConfiguration messages;
    public MessageBundle(JavaPlugin plugin) { this.plugin = plugin; reload(); }
    public void reload() {
        String locale = plugin.getConfig().getString("language", "en_US");
        String path = "lang/" + locale + ".yml";
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) plugin.saveResource(path, false);
        messages = YamlConfiguration.loadConfiguration(file);
    }
    public String get(String key, String... replacements) {
        String value = messages.getString(key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) value = value.replace("{" + replacements[i] + "}", replacements[i + 1]);
        return value;
    }
    public String prefixed(String key, String... replacements) { return get("prefix") + get(key, replacements); }
}
