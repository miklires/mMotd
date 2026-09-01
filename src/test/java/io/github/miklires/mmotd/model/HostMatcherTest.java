package io.github.miklires.mmotd.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostMatcherTest {
    @Test void matchesExactAndWildcardHosts() {
        assertTrue(HostMatcher.matches(List.of("play.example.com"), "PLAY.EXAMPLE.COM."));
        assertTrue(HostMatcher.matches(List.of("*.example.com"), "event.example.com"));
        assertFalse(HostMatcher.matches(List.of("*.example.com"), "example.com"));
        assertFalse(HostMatcher.matches(List.of("*.example.com"), "example.com.attacker.test"));
    }

    @Test void rejectsPathsPortsAndEmptyPatterns() {
        assertThrows(IllegalArgumentException.class, () -> HostMatcher.validate(""));
        assertThrows(IllegalArgumentException.class, () -> HostMatcher.validate("host/path"));
        assertThrows(IllegalArgumentException.class, () -> HostMatcher.validate("host:25565"));
    }
}
