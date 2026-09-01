package io.github.miklires.mmotd.model;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class ScheduleWindowTest {
    @Test void matchesNormalWindow() { var w=ScheduleWindow.parse(java.util.List.of("MONDAY"),"09:00-18:00","day"); assertTrue(w.matches(LocalDateTime.of(2026,8,24,12,0))); assertFalse(w.matches(LocalDateTime.of(2026,8,24,20,0))); }
    @Test void matchesOvernightWindowOnFollowingDay() { var w=ScheduleWindow.parse(java.util.List.of("MONDAY"),"22:00-06:00","night"); assertTrue(w.matches(LocalDateTime.of(2026,8,25,2,0))); assertFalse(w.matches(LocalDateTime.of(2026,8,25,7,0))); }
    @Test void retainsValidatedPriority() { assertEquals(50, ScheduleWindow.parse(java.util.List.of(), "00:00-00:00", "all", 50).priority()); }
}
