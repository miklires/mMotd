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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessageBundle(this);
        manager = new MotdManager(this);
        try {
            MotdManager.LoadReport report = manager.reload();
            messages.reload();
            getLogger().info("Loaded " + report.entries() + " MOTD entry(s), " + report.schedules()
                    + " schedule(s), " + report.skipped() + " skipped item(s).");
        } catch (Exception error) {
            getLogger().severe("Could not load MOTD configuration: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(new MotdListener(this), this);
        MotdCommand command = new MotdCommand(this);
        if (getCommand("mmotd") != null) {
            getCommand("mmotd").setExecutor(command);
            getCommand("mmotd").setTabCompleter(command);
        }
        long refreshTicks = Math.max(20L, Math.min(1_200L, getConfig().getLong("cache.refresh-ticks", 20L)));
        refreshTask = getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> manager.refresh(), refreshTicks, refreshTicks);
        if (getConfig().getBoolean("metrics.enabled", true) && getConfig().getInt("metrics.bstats-id", 0) > 0) {
            new Metrics(this, getConfig().getInt("metrics.bstats-id"));
        }
        if (getConfig().getBoolean("updates.enabled", true)) {
            UpdateChecker.checkAsync(this, getConfig().getString("updates.modrinth-project-id", ""));
        }
        getLogger().info("mMotd " + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) refreshTask.cancel();
    }

    public MotdManager manager() { return manager; }
    public MessageBundle messages() { return messages; }
}
