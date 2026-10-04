package app.lightmove.api.outreach.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * The times a consultant can offer a call: on the half hour, inside their working day in their own
 * zone, starting no sooner than half an hour from now, with the whole call clear of anything on their
 * calendar. The same window outreach email keeps to.
 */
public record FreeSlots(SendingWindow window, ZoneId zone) {

    static final Duration STEP = Duration.ofMinutes(30);

    /** Nobody is booked into a call that starts before they could read the invite. */
    static final Duration LEAD = Duration.ofMinutes(30);

    /** Working days on one page of times. */
    public static final int PAGE_DAYS = 5;

    /** How far ahead a call may be booked, from today in the consultant's zone. */
    public static final Period HORIZON = Period.ofMonths(6);

    /**
     * The page of {@link #PAGE_DAYS} working days from {@code from} (today when absent or past), empty beyond
     * {@link #HORIZON}. {@code busyBetween} reads the calendar once, for the span the page covers.
     */
    public Optional<SlotPage> page(Instant now, LocalDate from, Duration length,
                                   BiFunction<Instant, Instant, List<BusyInterval>> busyBetween) {
        LocalDate today = todayAt(now);
        LocalDate latest = today.plus(HORIZON);
        LocalDate firstDay = from == null || from.isBefore(today) ? today : from;
        if (firstDay.isAfter(latest)) {
            return Optional.empty();
        }
        List<BusyInterval> busy = busyBetween.apply(startOf(now, firstDay, PAGE_DAYS), endOf(now, firstDay, PAGE_DAYS));
        List<SlotDay> days = offered(now, firstDay, PAGE_DAYS, length, busy);
        return Optional.of(new SlotPage(today, latest, previousFrom(now, days.getFirst().date(), PAGE_DAYS), days));
    }

    /** Whether {@code start} may be booked: a time a page could offer, and not beyond {@link #HORIZON}. */
    public boolean books(Instant now, Instant start, Duration length) {
        return offers(now, start, length) && !start.atZone(zone).toLocalDate().isAfter(todayAt(now).plus(HORIZON));
    }

    /**
     * {@code dayCount} working days from {@code from} — never earlier than today — each with its free starts,
     * empty when fully booked.
     */
    public List<SlotDay> offered(Instant now, LocalDate from, int dayCount, Duration length,
                                 List<BusyInterval> busy) {
        Duration open = Duration.between(window.start(), window.end());
        List<SlotDay> days = new ArrayList<>();
        for (LocalDate day : workingDaysFrom(now, from, dayCount)) {
            Instant opening = day.atTime(window.start()).atZone(zone).toInstant();
            List<Instant> starts = new ArrayList<>();
            for (Duration offset = Duration.ZERO; offset.plus(length).compareTo(open) <= 0; offset = offset.plus(STEP)) {
                Instant start = opening.plus(offset);
                Instant end = start.plus(length);
                if (!start.isBefore(now.plus(LEAD)) && busy.stream().noneMatch(taken -> taken.overlaps(start, end))) {
                    starts.add(start);
                }
            }
            days.add(new SlotDay(day, starts));
        }
        return days;
    }

    /** Whether {@code start} is a time {@link #offered} could have offered, the calendar aside. */
    public boolean offers(Instant now, Instant start, Duration length) {
        ZonedDateTime local = start.atZone(zone);
        if (!window.workingDays().contains(local.getDayOfWeek()) || start.isBefore(now.plus(LEAD))) {
            return false;
        }
        Duration offset = Duration.between(local.toLocalDate().atTime(window.start()).atZone(zone), local);
        return !offset.isNegative()
                && offset.toNanos() % STEP.toNanos() == 0
                && offset.plus(length).compareTo(Duration.between(window.start(), window.end())) <= 0;
    }

    /** The day it is now in the consultant's zone. */
    public LocalDate todayAt(Instant now) {
        return now.atZone(zone).toLocalDate();
    }

    /**
     * Where the page before the one starting on {@code firstShown} starts: {@code dayCount} working days back,
     * stopping at today. Null when no working day lies between today and {@code firstShown}.
     */
    public LocalDate previousFrom(Instant now, LocalDate firstShown, int dayCount) {
        LocalDate today = todayAt(now);
        LocalDate day = firstShown;
        int found = 0;
        while (found < dayCount && day.isAfter(today)) {
            day = day.minusDays(1);
            if (window.workingDays().contains(day.getDayOfWeek())) {
                found++;
            }
        }
        return found == 0 ? null : day;
    }

    /** The instant the first of those days' windows opens, or now if it already has: where the calendar is read from. */
    public Instant startOf(Instant now, LocalDate from, int dayCount) {
        Instant opening = workingDaysFrom(now, from, dayCount).getFirst().atTime(window.start()).atZone(zone)
                .toInstant();
        return opening.isAfter(now) ? opening : now;
    }

    /** The instant the last of those days' windows closes: how far the calendar is asked about. */
    public Instant endOf(Instant now, LocalDate from, int dayCount) {
        List<LocalDate> days = workingDaysFrom(now, from, dayCount);
        return days.getLast().atTime(window.end()).atZone(zone).toInstant();
    }

    private List<LocalDate> workingDaysFrom(Instant now, LocalDate from, int dayCount) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate today = todayAt(now);
        LocalDate day = from.isBefore(today) ? today : from;
        while (days.size() < dayCount) {
            if (window.workingDays().contains(day.getDayOfWeek())) {
                days.add(day);
            }
            day = day.plusDays(1);
        }
        return days;
    }
}
