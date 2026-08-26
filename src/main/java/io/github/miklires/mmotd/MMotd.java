package io.github.miklires.mmotd;

import io.github.miklires.mmotd.command.MotdCommand;
import io.github.miklires.mmotd.listener.MotdListener;
import io.github.miklires.mmotd.update.UpdateChecker;
import io.github.miklires.mmotd.util.MessageBundle;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public final class MMotd extends JavaPlugin {
    private MotdManager manager;
    private MessageBundle messages;
    private ScheduledTask refreshTask;
    @Override public void onEnable() {
        saveDefaultConfig();
        saveBundledLanguage("en_US"); saveBundledLanguage("ru_RU");
        messages = new MessageBundle(this);
        manager = new MotdManager(this);
        try { manager.reload(); } catch (Exception e) { getLogger().severe("Could not load MOTD configuration: " + e.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        getServer().getPluginManager().registerEvents(new MotdListener(this), this);
        MotdCommand command = new MotdCommand(this);
        if (getCommand("mmotd") != null) { getCommand("mmotd").setExecutor(command); getCommand("mmotd").setTabCompleter(command); }
        refreshTask = getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> manager.refresh(), 20L, 20L);
        if (getConfig().getBoolean("metrics.enabled", true) && getConfig().getInt("metrics.bstats-id", 0) > 0) new Metrics(this, getConfig().getInt("metrics.bstats-id"));
        if (getConfig().getBoolean("updates.enabled", true)) UpdateChecker.checkAsync(this, getConfig().getString("updates.modrinth-project-id", ""));
        getLogger().info("mMotd " + getPluginMeta().getVersion() + " enabled.");
    }
    @Override public void onDisable() { if (refreshTask != null) refreshTask.cancel(); }
    private void saveBundledLanguage(String locale) { String path = "lang/" + locale + ".yml"; if (!new java.io.File(getDataFolder(), path).exists()) saveResource(path, false); }
    public MotdManager manager() { return manager; }
    public MessageBundle messages() { return messages; }
}
