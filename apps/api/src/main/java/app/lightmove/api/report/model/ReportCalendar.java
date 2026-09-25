package app.lightmove.api.report.model;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * The mandate's timeline as the progress chapter buckets it: day 0 and week 0 begin at kickoff, and
 * the last bucket is the one {@code asOf} falls in. Days are counted in UTC, the zone every stored
 * instant is in, so a row filed at 23:30 in Riyadh lands in one bucket on every read.
 */
public record ReportCalendar(LocalDate kickoff, LocalDate asOf, LocalDate targetDate) {

    private static final int DAYS_PER_WEEK = 7;

    public static ReportCalendar of(Instant kickoff, LocalDate targetDate, Clock clock) {
        LocalDate kickoffDate = dateOf(kickoff);
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return new ReportCalendar(kickoffDate, today.isBefore(kickoffDate) ? kickoffDate : today, targetDate);
    }

    public int dayCount() {
        return dayIndexOf(asOf) + 1;
    }

    public int weekCount() {
        return weekIndexOf(asOf) + 1;
    }

    /** The bucket a moment falls in, clamped into the calendar: nothing predates kickoff or postdates today. */
    public int dayIndexOf(Instant moment) {
        return dayIndexOf(dateOf(moment));
    }

    public int weekIndexOf(Instant moment) {
        return dayIndexOf(moment) / DAYS_PER_WEEK;
    }

    public LocalDate endOfWeek(int weekIndex) {
        return kickoff.plusDays((long) weekIndex * DAYS_PER_WEEK + DAYS_PER_WEEK - 1);
    }

    public int daysBetween(Instant moment, LocalDate date) {
        return (int) ChronoUnit.DAYS.between(dateOf(moment), date);
    }

    private int dayIndexOf(LocalDate date) {
        long sinceKickoff = ChronoUnit.DAYS.between(kickoff, date);
        long lastDay = ChronoUnit.DAYS.between(kickoff, asOf);
        return (int) Math.clamp(sinceKickoff, 0, lastDay);
    }

    private int weekIndexOf(LocalDate date) {
        return dayIndexOf(date) / DAYS_PER_WEEK;
    }

    public static LocalDate dateOf(Instant moment) {
        return moment.atOffset(ZoneOffset.UTC).toLocalDate();
    }
}
