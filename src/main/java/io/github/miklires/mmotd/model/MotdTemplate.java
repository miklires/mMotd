package io.github.miklires.mmotd.model;

import io.github.miklires.mmotd.analytics.PingTracker.Audience;
import org.bukkit.util.CachedServerIcon;

import java.util.List;

public record MotdTemplate(String id, String line1, String line2, String text, CachedServerIcon icon,
                           List<String> hosts, int minimumProtocol, int maximumProtocol,
                           int weight, Audience audience) {
    public MotdTemplate {
        hosts = List.copyOf(hosts);
    }

    public MotdTemplate(String id, String line1, String line2, String text, CachedServerIcon icon) {
        this(id, line1, line2, text, icon, List.of(), -1, -1, 1, Audience.ANY);
    }
}
