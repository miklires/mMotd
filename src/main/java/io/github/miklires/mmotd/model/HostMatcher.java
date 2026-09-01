package io.github.miklires.mmotd.model;

import java.util.List;
import java.util.Locale;

public final class HostMatcher {
    private HostMatcher() {}

    public static String normalize(String host) {
        String value = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    public static boolean matches(List<String> patterns, String host) {
        if (patterns.isEmpty()) return true;
        String normalized = normalize(host);
        for (String raw : patterns) {
            String pattern = normalize(raw);
            if (pattern.equals("*")) return true;
            if (pattern.startsWith("*.") && normalized.endsWith(pattern.substring(1))
                    && normalized.length() > pattern.length() - 1) return true;
            if (pattern.equals(normalized)) return true;
        }
        return false;
    }

    public static void validate(String pattern) {
        String normalized = normalize(pattern);
        if (normalized.isEmpty() || normalized.length() > 253 || normalized.contains("/")
                || normalized.contains("\\") || normalized.contains(":")) {
            throw new IllegalArgumentException("invalid hostname pattern: " + pattern);
        }
        String plain = normalized.startsWith("*.") ? normalized.substring(2) : normalized;
        if ((!normalized.equals("*") && !plain.matches("[a-z0-9.-]+")) || plain.contains("..")) {
            throw new IllegalArgumentException("invalid hostname pattern: " + pattern);
        }
    }
}
