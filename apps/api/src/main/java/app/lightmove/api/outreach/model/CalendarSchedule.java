package app.lightmove.api.outreach.model;

import app.lightmove.api.outreach.constant.WorkingHoursSource;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a calendar says of a stretch of time: when it is taken, and the working week it keeps where it states one
 * ({@code workingWeek} null where it does not — only Outlook does), in its own zone where that could be read.
 */
public record CalendarSchedule(List<BusyInterval> busy, WeeklyHours workingWeek, ZoneId workingZone) {

    public CalendarSchedule {
        busy = List.copyOf(busy);
        if (workingWeek != null && workingWeek.isClosed()) {
            workingWeek = null;
        }
    }

    public static CalendarSchedule busyOnly(List<BusyInterval> busy) {
        return new CalendarSchedule(busy, null, null);
    }

    /** The calendar's own working hours, read in {@code fallbackZone} where it named a zone nobody could read. */
    public Optional<WorkingHours> workingHours(ZoneId fallbackZone) {
        return Optional.ofNullable(workingWeek).map(week -> new WorkingHours(week,
                workingZone == null ? fallbackZone : workingZone, WorkingHoursSource.CALENDAR));
    }

    /** The calendar less the call being moved: its own time is free to move it within. */
    public CalendarSchedule without(Instant start, Instant end) {
        List<BusyInterval> left = new ArrayList<>();
        for (BusyInterval interval : busy) {
            if (!interval.overlaps(start, end)) {
                left.add(interval);
                continue;
            }
            if (interval.start().isBefore(start)) {
                left.add(new BusyInterval(interval.start(), start));
            }
            if (interval.end().isAfter(end)) {
                left.add(new BusyInterval(end, interval.end()));
            }
        }
        return new CalendarSchedule(left, workingWeek, workingZone);
    }

    public boolean isTaken(Instant start, Instant end) {
        return busy.stream().anyMatch(taken -> taken.overlaps(start, end));
    }
}
