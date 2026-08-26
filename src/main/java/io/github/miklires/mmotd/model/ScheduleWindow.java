package io.github.miklires.mmotd.model;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public record ScheduleWindow(Set<DayOfWeek> days, LocalTime start, LocalTime end, String entryId) {
    public static ScheduleWindow parse(Iterable<String> days, String range, String entryId) {
        EnumSet<DayOfWeek> parsedDays = EnumSet.noneOf(DayOfWeek.class);
        for (String day : days) parsedDays.add(DayOfWeek.valueOf(day.toUpperCase(Locale.ROOT)));
        if (parsedDays.isEmpty()) parsedDays = EnumSet.allOf(DayOfWeek.class);
        String[] parts = range.split("-", -1);
        if (parts.length != 2) throw new IllegalArgumentException("time must be HH:mm-HH:mm");
        return new ScheduleWindow(Set.copyOf(parsedDays), LocalTime.parse(parts[0]), LocalTime.parse(parts[1]), entryId);
    }

    public boolean matches(LocalDateTime now) {
        LocalTime time = now.toLocalTime();
        if (start.equals(end)) return days.contains(now.getDayOfWeek());
        if (start.isBefore(end)) return days.contains(now.getDayOfWeek()) && !time.isBefore(start) && time.isBefore(end);
        if (!time.isBefore(start)) return days.contains(now.getDayOfWeek());
        return time.isBefore(end) && days.contains(now.minusDays(1).getDayOfWeek());
    }
}
