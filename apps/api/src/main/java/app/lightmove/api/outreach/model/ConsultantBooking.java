package app.lightmove.api.outreach.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

/**
 * What a consultant may book from their own calendar view: any day, any time on a five-minute mark, any length up to
 * a working day. Their calendar is what refuses a time, never Uncava: working hours only label it.
 */
public final class ConsultantBooking {

    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 480;

    /** The calendar view's widest page. */
    public static final int MAX_VIEW_DAYS = 7;

    static final Duration GRANULARITY = Duration.ofMinutes(5);

    /** Long enough for an invite to land before the call it is for. */
    static final Duration LEAD = Duration.ofMinutes(5);

    static final Period HORIZON = Period.ofMonths(6);

    private ConsultantBooking() {}

    public static boolean isLength(Integer minutes) {
        return minutes != null && minutes >= MIN_MINUTES && minutes <= MAX_MINUTES
                && minutes % GRANULARITY.toMinutes() == 0;
    }

    /** Whether a call may start at {@code start}: on a five-minute mark, a little ahead, and within the horizon. */
    public static boolean allows(Instant now, Instant start, ZoneId zone) {
        return start.getEpochSecond() % GRANULARITY.toSeconds() == 0 && start.getNano() == 0
                && !start.isBefore(now.plus(LEAD))
                && !start.atZone(zone).toLocalDate().isAfter(latestDate(now, zone));
    }

    public static LocalDate latestDate(Instant now, ZoneId zone) {
        return now.atZone(zone).toLocalDate().plus(HORIZON);
    }
}
