package io.github.miklires.mmotd.util;

import java.util.Map;

public final class Placeholders {
    private Placeholders() {}
    public static String apply(String input, Map<String, String> values) {
        String result = input == null ? "" : input;
        for (var entry : values.entrySet()) result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        return result;
    }
}
