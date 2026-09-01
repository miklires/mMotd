package io.github.miklires.mmotd.util;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
class PlaceholdersTest {
    @Test void replacesKnownValuesOnly() { assertEquals("3/{max}", Placeholders.apply("{online}/{max}", Map.of("online","3"))); }
    @Test void doesNotRecursivelyExpandReplacementText() { assertEquals("{max}/10", Placeholders.apply("{online}/{max}", Map.of("online", "{max}", "max", "10"))); }
}
