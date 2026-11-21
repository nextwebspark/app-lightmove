package app.lightmove.api.notification.model;

import app.lightmove.api.task.model.DueDigest;
import app.lightmove.api.task.model.DueDigestLine;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A person's due tasks sorted into the digest's buckets, in their own working days: a task dated on a weekend counts on
 * the Monday after, it is late from the working day after that, and late work {@value #STALE_AFTER_DAYS}+ days old is
 * stale. Coming up runs to the end of the second working day ahead.
 */
public record DigestBuckets(LocalDate today, ZoneId zone, List<DueDigestLine> late, List<DueDigestLine> fresh,
                            List<DueDigestLine> stale, List<DueDigestLine> dueToday, List<DueDigestLine> comingUp) {

    public static final int STALE_AFTER_DAYS = 14;
    private static final int COMING_UP_WORKING_DAYS = 2;

    public DigestBuckets {
        late = List.copyOf(late);
        fresh = List.copyOf(fresh);
        stale = List.copyOf(stale);
        dueToday = List.copyOf(dueToday);
        comingUp = List.copyOf(comingUp);
    }

    /** Lines in, soonest first; each bucket keeps that order. */
    public static DigestBuckets of(DueDigest due, ZoneId zone, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        List<DueDigestLine> late = due.lines().stream()
                .filter(line -> workingDayOf(line, zone).isBefore(today)).toList();
        return new DigestBuckets(today, zone, late,
                late.stream().filter(line -> daysLate(line, zone, today) < STALE_AFTER_DAYS).toList(),
                late.stream().filter(line -> daysLate(line, zone, today) >= STALE_AFTER_DAYS).toList(),
                due.lines().stream().filter(line -> workingDayOf(line, zone).equals(today)).toList(),
                due.lines().stream().filter(line -> workingDayOf(line, zone).isAfter(today)).toList());
    }

    /** How far ahead the task read must reach: the end of the last working day Coming up covers. */
    public static Instant horizonOf(Instant now, ZoneId zone) {
        LocalDate day = LocalDate.ofInstant(now, zone);
        int workingDays = 0;
        while (workingDays < COMING_UP_WORKING_DAYS) {
            day = day.plusDays(1);
            if (!isWeekend(day)) {
                workingDays++;
            }
        }
        return day.plusDays(1).atStartOfDay(zone).toInstant();
    }

    /** Only late, due-today or mentioned work sends a digest; Coming up alone does not. */
    public boolean hasNews(long unreadMentions) {
        return unreadMentions > 0 || !late.isEmpty() || !dueToday.isEmpty();
    }

    public long daysLate(DueDigestLine line) {
        return daysLate(line, zone, today);
    }

    public boolean isDueToday(DueDigestLine line) {
        return workingDayOf(line, zone).equals(today);
    }

    /** A task counted today though it was dated on the weekend before: its time of day means nothing this morning. */
    public boolean isCarriedFromWeekend(DueDigestLine line) {
        return isDueToday(line) && !dayOf(line, zone).equals(today);
    }

    /** The working day a task is counted on. */
    public LocalDate workingDayOf(DueDigestLine line) {
        return workingDayOf(line, zone);
    }

    public LocalDate dayOf(DueDigestLine line) {
        return dayOf(line, zone);
    }

    private static LocalDate dayOf(DueDigestLine line, ZoneId zone) {
        return LocalDate.ofInstant(line.dueAt(), zone);
    }

    private static LocalDate workingDayOf(DueDigestLine line, ZoneId zone) {
        LocalDate day = dayOf(line, zone);
        while (isWeekend(day)) {
            day = day.plusDays(1);
        }
        return day;
    }

    private static long daysLate(DueDigestLine line, ZoneId zone, LocalDate today) {
        return ChronoUnit.DAYS.between(workingDayOf(line, zone), today);
    }

    private static boolean isWeekend(LocalDate day) {
        return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
