package io.github.miklires.mmotd.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Placeholders {
    private static final Pattern TOKEN = Pattern.compile("\\{([a-z][a-z0-9_]{0,31})}");
    private Placeholders() {}

    public static String apply(String input, Map<String, String> values) {
        if (input == null || input.isEmpty()) return "";
        Matcher matcher = TOKEN.matcher(input);
        StringBuilder result = new StringBuilder(input.length());
        while (matcher.find()) {
            String replacement = values.get(matcher.group(1));
            if (replacement == null) matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            else matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
