package io.github.miklires.mmotd.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import io.github.miklires.mmotd.MMotd;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MotdListener implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection().toBuilder().hexColors().build();
    private final MMotd plugin;

    public MotdListener(MMotd plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(PaperServerListPingEvent event) {
        var manager = plugin.manager();
        var state = manager.state();
        var audience = manager.recordPing(event.getAddress());
        InetSocketAddress virtualHost = event.getClient().getVirtualHost();
        String hostname = virtualHost == null ? event.getHostname() : virtualHost.getHostString();
        var motd = manager.pick(hostname, event.getClient().getProtocolVersion(), audience);
        event.motd(motd.line1().append(Component.newline()).append(motd.line2()));
        if (motd.icon() != null) event.setServerIcon(motd.icon());
        event.setMaxPlayers(state.max());
        if (state.hidePlayers()) event.setHidePlayers(true);
        else event.setNumPlayers(state.online());
        if (state.overrideVersion()) {
            event.setVersion(state.versionLabel());
            event.setProtocolVersion(state.protocol());
        }
        if (!state.hover().isEmpty()) {
            event.getListedPlayers().clear();
            for (int index = 0; index < state.hover().size(); index++) {
                Component line = state.hover().get(index);
                UUID stableId = UUID.nameUUIDFromBytes(("mMotd:" + index + ':' + LEGACY.serialize(line))
                        .getBytes(StandardCharsets.UTF_8));
                event.getListedPlayers().add(new PaperServerListPingEvent.ListedPlayerInfo(LEGACY.serialize(line), stableId));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    @SuppressWarnings("deprecation") // Required for Bukkit permission checks before a maintenance denial.
    public void onLogin(PlayerLoginEvent event) {
        if (!plugin.manager().state().maintenance() || event.getPlayer().hasPermission("mmotd.maintenance.bypass")) return;
        String ip = event.getAddress() == null ? "" : event.getAddress().getHostAddress();
        if (!plugin.manager().isWhitelisted(ip)) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, plugin.manager().kickMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        InetSocketAddress address = event.getPlayer().getAddress();
        if (address != null) plugin.manager().recordJoin(address.getAddress());
    }
}
