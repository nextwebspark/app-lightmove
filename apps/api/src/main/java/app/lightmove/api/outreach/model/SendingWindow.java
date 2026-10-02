package app.lightmove.api.outreach.model;

import app.lightmove.api.core.config.OutreachSettings;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * The hours an outreach email may go, in the sender's own zone: their working days, between the
 * window's start and end. A sequence's delays are counted in the same working days.
 */
public record SendingWindow(LocalTime start, LocalTime end, Set<DayOfWeek> workingDays) {

    public SendingWindow {
        if (!start.isBefore(end) || workingDays.isEmpty()) {
            throw new IllegalArgumentException("A sending window needs a start before its end and a working day");
        }
        workingDays = Set.copyOf(EnumSet.copyOf(workingDays));
    }

    public static SendingWindow of(OutreachSettings settings) {
        return new SendingWindow(settings.windowStart(), settings.windowEnd(), Set.copyOf(settings.workingDays()));
    }

    public boolean isOpen(Instant now, ZoneId zone) {
        ZonedDateTime local = now.atZone(zone);
        LocalTime time = local.toLocalTime();
        return workingDays.contains(local.getDayOfWeek()) && !time.isBefore(start) && time.isBefore(end);
    }

    /** {@code now} itself when the window is open, else the next time it opens. */
    public Instant nextOpening(Instant now, ZoneId zone) {
        if (isOpen(now, zone)) {
            return now;
        }
        ZonedDateTime local = now.atZone(zone);
        LocalDate day = local.toLocalTime().isBefore(start) ? local.toLocalDate() : local.toLocalDate().plusDays(1);
        return firstOpeningOnOrAfter(day, zone);
    }

    /** The window's opening on the first working day after {@code now}'s local day — where a full day's cap waits. */
    public Instant openingAfterToday(Instant now, ZoneId zone) {
        return firstOpeningOnOrAfter(now.atZone(zone).toLocalDate().plusDays(1), zone);
    }

    /**
     * {@code days} working days after {@code from}, at the same local time. Zero is {@code from} itself;
     * a weekend in between is not counted, and the send still waits for the window when it lands.
     */
    public Instant addWorkingDays(Instant from, ZoneId zone, int days) {
        ZonedDateTime local = from.atZone(zone);
        int remaining = days;
        while (remaining > 0) {
            local = local.plusDays(1);
            if (workingDays.contains(local.getDayOfWeek())) {
                remaining--;
            }
        }
        return local.toInstant();
    }

    public Instant startOfDay(Instant now, ZoneId zone) {
        return now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
    }

    private Instant firstOpeningOnOrAfter(LocalDate day, ZoneId zone) {
        LocalDate candidate = day;
        while (!workingDays.contains(candidate.getDayOfWeek())) {
            candidate = candidate.plusDays(1);
        }
        return candidate.atTime(start).atZone(zone).toInstant();
    }
}
