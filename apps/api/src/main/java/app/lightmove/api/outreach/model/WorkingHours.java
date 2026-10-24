package app.lightmove.api.outreach.model;

import app.lightmove.api.outreach.constant.WorkingHoursSource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** A consultant's working week in the zone it is kept in, and where it was read from. */
public record WorkingHours(WeeklyHours week, ZoneId zone, WorkingHoursSource source) {

    /**
     * The working stretches overlapping {@code from}–{@code to}, clipped to it. Each day is read in the hours' own
     * zone, so an Outlook week kept in another zone than the consultant's profile still lands on the right instants.
     */
    public List<OpenInterval> between(Instant from, Instant to) {
        List<OpenInterval> open = new ArrayList<>();
        LocalDate last = to.atZone(zone).toLocalDate();
        for (LocalDate day = from.atZone(zone).toLocalDate().minusDays(1); !day.isAfter(last); day = day.plusDays(1)) {
            LocalDate date = day;
            week.on(date.getDayOfWeek()).ifPresent(hours -> {
                Instant opens = date.atTime(hours.start()).atZone(zone).toInstant();
                Instant closes = date.atTime(hours.end()).atZone(zone).toInstant();
                Instant start = opens.isBefore(from) ? from : opens;
                Instant end = closes.isAfter(to) ? to : closes;
                if (start.isBefore(end)) {
                    open.add(new OpenInterval(start, end));
                }
            });
        }
        return open;
    }

    /** Whether {@code start}–{@code end} lies wholly inside one working stretch. */
    public boolean covers(Instant start, Instant end) {
        return between(start, end).stream().anyMatch(interval -> interval.covers(start, end));
    }
}
