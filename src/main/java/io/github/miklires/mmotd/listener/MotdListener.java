package io.github.miklires.mmotd.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import io.github.miklires.mmotd.MMotd;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import java.util.UUID;

public final class MotdListener implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection().toBuilder().hexColors().build();
    private final MMotd plugin;
    public MotdListener(MMotd plugin) { this.plugin = plugin; }
    @EventHandler public void onPing(PaperServerListPingEvent event) {
        var manager = plugin.manager(); var state = manager.state(); var motd = manager.pick();
        event.motd(motd.line1().append(Component.newline()).append(motd.line2()));
        if (motd.icon() != null) event.setServerIcon(motd.icon());
        event.setMaxPlayers(state.max());
        if (state.hidePlayers()) event.setHidePlayers(true); else event.setNumPlayers(state.online());
        if (state.overrideVersion()) { event.setVersion(LEGACY.serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(state.versionLabel()))); event.setProtocolVersion(state.protocol()); }
        if (!state.hover().isEmpty()) {
            event.getListedPlayers().clear();
            for (Component line : state.hover()) event.getListedPlayers().add(new PaperServerListPingEvent.ListedPlayerInfo(LEGACY.serialize(line), UUID.randomUUID()));
        }
    }
    @EventHandler public void onLogin(PlayerLoginEvent event) {
        if (!plugin.manager().state().maintenance() || event.getPlayer().hasPermission("mmotd.maintenance.bypass")) return;
        String ip = event.getAddress() == null ? "" : event.getAddress().getHostAddress();
        if (!plugin.manager().isWhitelisted(ip)) event.disallow(PlayerLoginEvent.Result.KICK_OTHER, plugin.manager().kickMessage());
    }
}
