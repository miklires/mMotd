package io.github.miklires.mmotd.model;

import org.bukkit.util.CachedServerIcon;

public record MotdTemplate(String id, String line1, String line2, String text, CachedServerIcon icon) {}
