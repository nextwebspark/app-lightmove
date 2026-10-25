package app.lightmove.api.outreach.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The times a booking link offers an executive: on the half hour, inside the consultant's working hours, an hour
 * ahead at the soonest, at most {@link #HORIZON_DAYS} days out, with the whole call clear of their calendar. Days are
 * read in {@code zone}, the consultant's own, a week to a page; a day they do not work is left off the page. A
 * consultant booking a call themselves is not held to any of it ({@link ConsultantBooking}).
 */
public record FreeSlots(WorkingHours hours, ZoneId zone) {

    static final Duration STEP = Duration.ofMinutes(30);

    /** Nobody is booked into a call that starts before the consultant could read the invite. */
    public static final Duration NOTICE = Duration.ofHours(1);

    /** How far ahead a link may be booked, from today in the consultant's zone. */
    public static final int HORIZON_DAYS = 60;

    /** Calendar days on one page of times. */
    public static final int PAGE_DAYS = 7;

    /** The page's first day: {@code from}, or today when absent or past. Null beyond {@link #HORIZON_DAYS}. */
    public static LocalDate firstDayOf(Instant now, LocalDate from, ZoneId zone) {
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate firstDay = from == null || from.isBefore(today) ? today : from;
        return firstDay.isAfter(today.plusDays(HORIZON_DAYS)) ? null : firstDay;
    }

    /** Where the calendar is read from for the page starting on {@code firstDay}. */
    public static Instant pageStart(LocalDate firstDay, ZoneId zone) {
        return firstDay.atStartOfDay(zone).toInstant();
    }

    /** Where the calendar is read to for the page starting on {@code firstDay}. */
    public static Instant pageEnd(LocalDate firstDay, ZoneId zone) {
        return firstDay.plusDays(PAGE_DAYS).atStartOfDay(zone).toInstant();
    }

    /** The page from {@code firstDay}: each working day of it with its free starts, empty when fully booked. */
    public SlotPage page(Instant now, LocalDate firstDay, Duration length, List<BusyInterval> busy) {
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate latest = today.plusDays(HORIZON_DAYS);
        List<SlotDay> days = new ArrayList<>();
        for (int offset = 0; offset < PAGE_DAYS; offset++) {
            LocalDate day = firstDay.plusDays(offset);
            if (day.isAfter(latest)) {
                break;
            }
            if (isWorked(day)) {
                days.add(new SlotDay(day, startsOn(now, day, length, busy)));
            }
        }
        LocalDate previous = firstDay.minusDays(PAGE_DAYS);
        return new SlotPage(today, latest, firstDay.isAfter(today) ? (previous.isBefore(today) ? today : previous)
                : null, days);
    }

    /**
     * Whether a page could ever offer {@code start}, working hours and the calendar aside: on the half hour, after the
     * notice, within the horizon. Cheap, so a booking is refused on it before anything is asked of the calendar.
     */
    public static boolean isOfferable(Instant now, Instant start, ZoneId zone) {
        ZonedDateTime local = start.atZone(zone);
        return local.getSecond() == 0 && local.getNano() == 0 && local.getMinute() % STEP.toMinutes() == 0
                && !start.isBefore(now.plus(NOTICE))
                && !local.toLocalDate().isAfter(now.atZone(zone).toLocalDate().plusDays(HORIZON_DAYS));
    }

    /** Whether {@code start} may be booked: a time a page could offer, the calendar's busy times included. */
    public boolean books(Instant now, Instant start, Duration length, List<BusyInterval> busy) {
        Instant end = start.plus(length);
        return isOfferable(now, start, zone)
                && hours.covers(start, end)
                && busy.stream().noneMatch(taken -> taken.overlaps(start, end));
    }

    private List<Instant> startsOn(Instant now, LocalDate day, Duration length, List<BusyInterval> busy) {
        List<Instant> starts = new ArrayList<>();
        long stepsInDay = Duration.ofDays(1).dividedBy(STEP);
        for (long step = 0; step < stepsInDay; step++) {
            Instant start = day.atTime(LocalTime.MIDNIGHT.plus(STEP.multipliedBy(step))).atZone(zone).toInstant();
            if (books(now, start, length, busy)) {
                starts.add(start);
            }
        }
        return starts;
    }

    private boolean isWorked(LocalDate day) {
        return !hours.between(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant())
                .isEmpty();
    }
}
