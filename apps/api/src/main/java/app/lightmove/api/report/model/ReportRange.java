package app.lightmove.api.report.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** An inclusive span of UTC days inside the mandate's calendar. */
public record ReportRange(LocalDate from, LocalDate to) {

    /**
     * The range asked for, clamped into {@code [kickoff, asOf]}; a missing end is the calendar's own.
     * Answers null for a {@code from} after {@code to}, judged before clamping: clamped first, two
     * dates both past the calendar would land on one day and a backwards request would pass.
     */
    public static ReportRange within(ReportCalendar calendar, LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate from = requestedFrom == null ? calendar.kickoff() : requestedFrom;
        LocalDate to = requestedTo == null ? calendar.asOf() : requestedTo;
        if (from.isAfter(to)) {
            return null;
        }
        LocalDate clampedFrom = clamp(from, calendar);
        LocalDate clampedTo = clamp(to, calendar);
        return new ReportRange(clampedFrom, clampedTo);
    }

    public int days() {
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }

    public boolean contains(LocalDate day) {
        return !day.isBefore(from) && !day.isAfter(to);
    }

    public int indexOf(LocalDate day) {
        return (int) ChronoUnit.DAYS.between(from, day);
    }

    private static LocalDate clamp(LocalDate day, ReportCalendar calendar) {
        if (day.isBefore(calendar.kickoff())) {
            return calendar.kickoff();
        }
        return day.isAfter(calendar.asOf()) ? calendar.asOf() : day;
    }
}
